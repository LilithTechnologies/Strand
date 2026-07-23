package re.lilith.strand.session

import re.lilith.strand.StrandConfig
import re.lilith.strand.StrandState
import re.lilith.strand.backend.BackendClient
import re.lilith.strand.backend.BackendException
import re.lilith.strand.eos.EosConnectAuth
import re.lilith.strand.eos.EosManager
import re.lilith.strand.eos.EosLoginSession
import re.lilith.strand.invite.PendingInvite
import re.lilith.strand.net.HostBridge
import re.lilith.strand.net.JoinBridge
import re.lilith.strand.net.P2PHub
import re.lilith.strand.voice.MicMode
import re.lilith.strand.voice.VoiceManager
import re.lilith.strand.voice.VoiceParticipant
import gg.sona.eos.common.ProductUserId
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


private class HostState(
    val sessionId: String,
    val inviteCode: String,
    val socketName: String,
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

    @Volatile
    private var session: EosLoginSession? = null
    @Volatile
    private var hostState: HostState? = null
    @Volatile
    private var joinBridge: JoinBridge? = null
    @Volatile
    private var loginFuture: CompletableFuture<EosLoginSession>? = null
    @Volatile
    private var connState: ConnState = ConnState.LoggedOut
    @Volatile
    private var lastError: String? = null
    private val pollerStarted = AtomicBoolean(false)

    @Volatile
    private var voiceSocket: String? = null
    @Volatile
    private var voiceMembersTask: ScheduledFuture<*>? = null

    @Volatile
    var needsMicModePrompt: Boolean = false

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val pending = CopyOnWriteArrayList<PendingInvite>()

    init {
        EosManager.addFrameHook { VoiceManager.onFrame() }
    }

    val isHosting: Boolean get() = hostState != null
    fun currentCode(): String? = hostState?.inviteCode
    fun pendingInvites(): List<PendingInvite> = pending.toList()
    fun invitesBlocked(): Boolean = session?.me?.invitesBlocked ?: false
    fun connectionState(): ConnState = connState
    fun username(): String? = session?.me?.username
    fun lastError(): String? = lastError

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun changed() {
        listeners.forEach { runCatching { it() } }
    }

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
                        hostState = HostState(created.sessionId, created.inviteCode, created.socketName, bridge)
                        val code = if (config.hideCodeInChat) "(hidden)" else created.inviteCode
                        hooks.notify("Your world is live on Strand. Invite code: $code")
                        startVoice(created.socketName, isHost = true)
                        changed()
                    }
                    .onFailure { hooks.notify("Could not start hosting: ${it.message}") }
            }
        }
    }

    fun unhost() {
        val state = hostState ?: return
        hostState = null
        stopVoice()
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
                    .onSuccess {
                        joinSession(
                            ProductUserId.fromString(it.hostProductUserId),
                            it.socketName,
                            it.hostUsername
                        )
                    }
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
        startVoice(socket, isHost = false)
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
                .onSuccess {
                    joinSession(
                        ProductUserId.fromString(it.hostProductUserId),
                        it.socketName,
                        it.hostUsername
                    )
                }
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

    val isInVoice: Boolean get() = voiceSocket != null && VoiceManager.isConnected
    val isVoiceHost: Boolean get() = VoiceManager.isHost

    private fun startVoice(socket: String, isHost: Boolean) {
        val login = session ?: return
        voiceSocket = socket
        executor.execute {
            try {
                val token = backend.voiceToken(login.sessionToken, socket)
                val profile = hooks.profile()
                applyPrefsToVoice()
                VoiceManager.enterRoom(
                    roomId = token.roomId,
                    clientBaseUrl = token.clientBaseUrl,
                    token = token.token,
                    hostProductUserId = token.hostProductUserId,
                    localMcUuid = profile?.uuid ?: "",
                    localName = profile?.username ?: "",
                    near = token.proxNear,
                    max = token.proxMax,
                    voiceEnabled = token.voiceEnabled,
                    isHost = token.host,
                )
                needsMicModePrompt = StrandState.config.voice.micMode == "UNSET"
                refreshMembers()
                scheduleMembers()
                changed()
            } catch (e: Exception) {
                logger.warn("Could not start voice chat", e)
                voiceSocket = null
            }
        }
    }

    private fun stopVoice() {
        voiceMembersTask?.cancel(false)
        voiceMembersTask = null
        voiceSocket = null
        needsMicModePrompt = false
        VoiceManager.leaveRoom()
    }

    fun onLeftWorld() {
        if (hostState == null) stopVoice()
    }

    private fun scheduleMembers() {
        voiceMembersTask?.cancel(false)
        voiceMembersTask = poller.scheduleWithFixedDelay(::refreshMembers, 5, 5, TimeUnit.SECONDS)
    }

    private fun refreshMembers() {
        val login = session ?: return
        val socket = voiceSocket ?: return
        val membersResponse = runCatching { backend.voiceMembers(login.sessionToken, socket) }.getOrNull() ?: return
        membersResponse.members.forEach { VoiceManager.applyIdentity(it.productUserId, it.mcUuid, it.username) }
    }

    private fun applyPrefsToVoice() {
        val v = StrandState.config.voice
        VoiceManager.setMicMode(if (v.micMode == "VOICE_ACTIVITY") MicMode.VOICE_ACTIVITY else MicMode.PUSH_TO_TALK)
        VoiceManager.setMasterVolume(v.masterVolume)
        VoiceManager.setInputGain(v.inputGain)
        VoiceManager.setInputSensitivity(v.inputSensitivity)
        VoiceManager.setInputDevice(v.inputDeviceId)
        VoiceManager.setOutputDevice(v.outputDeviceId)
    }

    fun setMicMode(mode: MicMode) {
        VoiceManager.setMicMode(mode)
        needsMicModePrompt = false
        StrandState.updateVoice { it.copy(micMode = if (mode == MicMode.VOICE_ACTIVITY) "VOICE_ACTIVITY" else "PTT") }
    }

    fun setMasterVolume(v: Float) {
        VoiceManager.setMasterVolume(v)
        StrandState.updateVoice { it.copy(masterVolume = v.coerceIn(0f, 1f)) }
    }

    fun setInputGain(v: Float) {
        VoiceManager.setInputGain(v)
        StrandState.updateVoice { it.copy(inputGain = v.coerceIn(0f, 2f)) }
    }

    fun setInputSensitivity(v: Float) {
        VoiceManager.setInputSensitivity(v)
        StrandState.updateVoice { it.copy(inputSensitivity = v) }
    }

    fun setInputDevice(id: String) {
        VoiceManager.setInputDevice(id)
        StrandState.updateVoice { it.copy(inputDeviceId = id) }
    }

    fun setOutputDevice(id: String) {
        VoiceManager.setOutputDevice(id)
        StrandState.updateVoice { it.copy(outputDeviceId = id) }
    }

    fun setProximityMaxPreference(max: Int) {
        StrandState.updateVoice { it.copy(proximityMax = max.coerceIn(1, 512)) }
    }

    fun setPlayerLocalMuted(p: VoiceParticipant, muted: Boolean) {
        VoiceManager.setParticipantLocallyMuted(p, muted)
        p.mcUuid?.let { uuid ->
            StrandState.updateVoice { v -> v.copy(perPlayerMuted = if (muted) v.perPlayerMuted + uuid else v.perPlayerMuted - uuid) }
        }
    }

    fun setPlayerVolume(mcUuid: String, volume: Float) {
        StrandState.updateVoice { it.copy(perPlayerVolume = it.perPlayerVolume + (mcUuid to volume.coerceIn(0f, 2f))) }
    }

    fun voiceKick(targetPuid: String) {
        val login = session ?: return
        val socket = voiceSocket ?: return
        executor.execute {
            runCatching { backend.voiceKick(login.sessionToken, socket, targetPuid) }
                .onSuccess { hooks.notify("Removed a player from voice.") }
                .onFailure { hooks.notify("Could not kick from voice: ${it.message}") }
        }
    }

    fun voiceMute(targetPuid: String, muted: Boolean) {
        val login = session ?: return
        val socket = voiceSocket ?: return
        executor.execute {
            runCatching { backend.voiceMute(login.sessionToken, socket, targetPuid, muted) }
                .onFailure { hooks.notify("Could not ${if (muted) "mute" else "unmute"} player: ${it.message}") }
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        val login = session ?: return
        val socket = voiceSocket ?: return
        executor.execute {
            runCatching { backend.voiceSettings(login.sessionToken, socket, enabled, null, null) }
                .onSuccess {
                    VoiceManager.setVoiceEnabledLocal(enabled)
                    hooks.notify(if (enabled) "Voice enabled for everyone." else "Voice disabled for everyone.")
                    changed()
                }
                .onFailure { hooks.notify("Could not change voice: ${it.message}") }
        }
    }

    fun setHostRange(near: Int, max: Int) {
        val login = session ?: return
        val socket = voiceSocket ?: return
        VoiceManager.setHostRange(near, max)
        executor.execute { runCatching { backend.voiceSettings(login.sessionToken, socket, null, near, max) } }
    }

    fun shutdown() {
        stopVoice()
        hostState?.bridge?.stop()
        joinBridge?.stop()
    }
}
