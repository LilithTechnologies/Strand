/*
 * Strand - Open your Minecraft world to anyone, anywhere.
 * Copyright (C) 2026 Lilith Technologies LLC <hello@lilith.re>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package re.lilith.strand.session

import re.lilith.strand.StrandConfig
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.backend.BackendException
import re.lilith.strand.eos.EosConnectAuth
import re.lilith.strand.eos.EosManager
import re.lilith.strand.eos.EosLoginSession
import re.lilith.strand.invite.PendingInvite
import re.lilith.strand.net.HostBridge
import re.lilith.strand.net.JoinBridge
import re.lilith.strand.net.P2PHub
import gg.sona.eos.common.ProductUserId
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


private class HostState(
    val sessionId: String,
    val inviteCode: String,
    val bridge: HostBridge,
)

class SessionController(
    private val config: StrandConfig,
    private val backend: BackendClient,
    private val hooks: ClientHooks,
) {
    private val logger = LoggerFactory.getLogger("strand/session")
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "strand-worker").apply { isDaemon = true }
    }
    private val poller = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "strand-invite-poll").apply { isDaemon = true }
    }

    @Volatile private var session: EosLoginSession? = null
    @Volatile private var hostState: HostState? = null
    @Volatile private var joinBridge: JoinBridge? = null
    @Volatile private var loginFuture: CompletableFuture<EosLoginSession>? = null
    @Volatile private var connState: ConnState = ConnState.LoggedOut
    @Volatile private var lastError: String? = null
    private val pollerStarted = AtomicBoolean(false)

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val pending = CopyOnWriteArrayList<PendingInvite>()

    val isHosting: Boolean get() = hostState != null
    fun currentCode(): String? = hostState?.inviteCode
    fun pendingInvites(): List<PendingInvite> = pending.toList()
    fun invitesBlocked(): Boolean = session?.me?.invitesBlocked ?: false
    fun connectionState(): ConnState = connState
    fun username(): String? = session?.me?.username
    fun lastError(): String? = lastError

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { listeners.forEach { runCatching { it() } } }

    @Synchronized
    fun ensureLogin(): CompletableFuture<EosLoginSession> {
        session?.let { return CompletableFuture.completedFuture(it) }
        loginFuture?.let { return it }
        if (!EosManager.isInitialized) {
            return CompletableFuture.failedFuture(IllegalStateException("EOS is not available"))
        }
        val profile = hooks.profile()
            ?: return CompletableFuture.failedFuture(IllegalStateException("No Minecraft profile"))

        val future = CompletableFuture<EosLoginSession>()
        loginFuture = future
        connState = ConnState.Connecting
        changed()
        executor.execute {
            EosConnectAuth.login(backend, profile.uuid, profile.username, hooks::joinServer)
                .onSuccess { result ->
                    session = result
                    connState = ConnState.LoggedIn
                    lastError = null
                    P2PHub.install()
                    startPoller()
                    hooks.notify("Connected to Strand as ${result.me.username}.")
                    future.complete(result)
                    loginFuture = null
                    changed()
                }
                .onFailure { error ->
                    logger.error("Strand login failed", error)
                    connState = ConnState.Failed
                    lastError = error.message
                    hooks.notify("Strand login failed: ${error.message}")
                    future.completeExceptionally(error)
                    loginFuture = null
                    changed()
                }
        }
        return future
    }

    private fun startPoller() {
        if (!pollerStarted.compareAndSet(false, true)) return
        poller.scheduleWithFixedDelay(::pollInvites, 1, 3, TimeUnit.SECONDS)
    }

    private fun pollInvites() {
        val login = session ?: return
        val response = runCatching { backend.pendingInvites(login.sessionToken) }.getOrNull() ?: return
        val known = pending.map { it.inviteId }.toSet()
        val fresh = response.invites.map { PendingInvite(it.id, it.fromProductUserId, it.fromName, it.socketName) }
        val newOnes = fresh.filter { it.inviteId !in known }

        pending.clear()
        pending.addAll(fresh)

        if (newOnes.isNotEmpty()) {
            for (invite in newOnes) {
                val name = invite.hostName.ifBlank { "A player" }
                hooks.notify("$name invited you to their world. Open Strand to accept.")
                hooks.toast("Strand invite", "$name invited you to play")
            }
            changed()
        } else if (fresh.size != known.size) {
            changed()
        }
    }

    fun host() {
        val port = hooks.lanPort()
        if (port == null) {
            hooks.openHostToLanScreen { host() }
            return
        }
        ensureLogin().whenComplete { login, error ->
            if (error != null || login == null) return@whenComplete
            executor.execute {
                runCatching { backend.createSession(login.sessionToken, null) }
                    .onSuccess { created ->
                        hostState?.bridge?.stop()
                        val bridge = HostBridge(created.socketName, port)
                        bridge.start()
                        hostState = HostState(created.sessionId, created.inviteCode, bridge)
                        val code = if (config.hideCodeInChat) "(hidden)" else created.inviteCode
                        hooks.notify("Your world is live on Strand. Invite code: $code")
                        changed()
                    }
                    .onFailure { hooks.notify("Could not start hosting: ${it.message}") }
            }
        }
    }

    fun unhost() {
        val state = hostState ?: return
        hostState = null
        state.bridge.stop()
        session?.let { login ->
            executor.execute { runCatching { backend.closeSession(login.sessionToken, state.sessionId) } }
        }
        hooks.notify("Stopped hosting on Strand.")
        changed()
    }

    fun joinByCode(code: String) {
        ensureLogin().whenComplete { login, error ->
            if (error != null || login == null) return@whenComplete
            executor.execute {
                runCatching { backend.redeem(login.sessionToken, code) }
                    .onSuccess { joinSession(ProductUserId.fromString(it.hostProductUserId), it.socketName, it.hostUsername) }
                    .onFailure { hooks.notify("Invalid or expired invite code.") }
            }
        }
    }

    private fun joinSession(hostPuid: ProductUserId, socket: String, hostName: String) {
        joinBridge?.stop()
        val bridge = JoinBridge(hostPuid, socket)
        bridge.start()
        joinBridge = bridge
        hooks.notify("Joining ${hostName.ifBlank { "host" }} over Strand...")
        hooks.connectToLocal(bridge.port)
    }

    fun invite(username: String) {
        if (hostState == null) {
            hooks.notify("Host your world on Strand before inviting.")
            return
        }
        ensureLogin().whenComplete { login, error ->
            if (error != null || login == null) return@whenComplete
            executor.execute {
                try {
                    backend.sendInvite(login.sessionToken, username)
                    hooks.notify("Invited $username.")
                } catch (e: BackendException) {
                    val code = hostState?.inviteCode
                    when (e.error) {
                        "blocked" -> hooks.notify("$username has invites blocked. Share your code instead: $code")
                        "not_hosting" -> hooks.notify("Host your world before inviting.")
                        "user_not_found" -> hooks.notify("No Strand player named $username. They must join Strand once first.")
                        else -> hooks.notify("Could not invite $username: ${e.error}")
                    }
                } catch (e: Exception) {
                    hooks.notify("Could not invite $username: ${e.message}")
                }
            }
        }
    }

    fun acceptInvite(inviteId: String? = null): Boolean {
        val login = session ?: return false
        val invite = (if (inviteId != null) pending.firstOrNull { it.inviteId == inviteId } else pending.firstOrNull())
            ?: return false
        pending.remove(invite)
        changed()
        executor.execute {
            runCatching { backend.acceptInvite(login.sessionToken, invite.inviteId) }
                .onSuccess { joinSession(ProductUserId.fromString(it.hostProductUserId), it.socketName, it.hostUsername) }
                .onFailure { hooks.notify("Could not accept invite: ${it.message}") }
        }
        return true
    }

    fun declineInvite(inviteId: String? = null): Boolean {
        val login = session ?: return false
        val invite = (if (inviteId != null) pending.firstOrNull { it.inviteId == inviteId } else pending.firstOrNull())
            ?: return false
        pending.remove(invite)
        changed()
        executor.execute { runCatching { backend.declineInvite(login.sessionToken, invite.inviteId) } }
        hooks.notify("Declined invite from ${invite.hostName.ifBlank { "player" }}.")
        return true
    }

    fun setInvitesBlocked(blocked: Boolean) {
        ensureLogin().whenComplete { login, error ->
            if (error != null || login == null) return@whenComplete
            executor.execute {
                runCatching { backend.setInvitesBlocked(login.sessionToken, blocked) }
                    .onSuccess { updated ->
                        session = EosLoginSession(login.sessionToken, login.productUserId, updated)
                        if (blocked) {
                            hooks.notify("Invites blocked. Others can only join with a code you share.")
                        } else {
                            hooks.notify("Invites are now allowed.")
                        }
                        changed()
                    }
                    .onFailure { hooks.notify("Could not update invite settings: ${it.message}") }
            }
        }
    }

    fun shutdown() {
        hostState?.bridge?.stop()
        joinBridge?.stop()
    }
}
