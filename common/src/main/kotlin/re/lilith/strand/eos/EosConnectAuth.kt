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

package re.lilith.strand.eos

import re.lilith.strand.backend.BackendClient
import gg.sona.eos.EosResult
import gg.sona.eos.common.EosExternalCredentialType
import gg.sona.eos.common.ProductUserId
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


object EosConnectAuth {

    private val logger = LoggerFactory.getLogger("strand/connect")
    private val random = SecureRandom()

    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MILLIS = longArrayOf(1_000, 3_000)
    private const val EOS_CALL_TIMEOUT_SECONDS = 30L

    fun login(
        backend: BackendClient,
        uuid: String,
        username: String,
        joinServer: (serverId: String) -> Unit,
    ): Result<EosLoginSession> {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val result = runCatching { attemptLogin(backend, uuid, username, joinServer) }
            result.onSuccess {
                EosManager.localUser = it.productUserId
                logger.info("EOS Connect login succeeded ({})", it.productUserId)
                return result
            }
            result.onFailure { error ->
                lastError = error
                logger.warn("EOS Connect login attempt {}/{} failed: {}", attempt, MAX_ATTEMPTS, error.message)
            }
            if (attempt < MAX_ATTEMPTS) {
                Thread.sleep(RETRY_DELAYS_MILLIS.getOrElse(attempt - 1) { 3_000 })
            }
        }
        return Result.failure(lastError ?: IllegalStateException("EOS Connect login failed"))
    }

    private fun attemptLogin(
        backend: BackendClient,
        uuid: String,
        username: String,
        joinServer: (serverId: String) -> Unit,
    ): EosLoginSession {
        check(EosManager.isInitialized) { "EOS SDK has not been initialized yet" }

        val challenge = backend.challenge(uuid, username)
        joinServer(challenge.serverId)
        val sessionToken = backend.session(uuid, username, challenge.serverId).sessionToken

        val verifier = codeVerifier()
        val authCode = backend.oidcAuthorize(sessionToken, challengeFor(verifier))
        val accessToken = backend.oidcToken(authCode, verifier)

        val userId = performConnectLogin(accessToken)
        backend.linkPuid(sessionToken, userId.toStringValue())
        val me = backend.me(sessionToken)
        return EosLoginSession(sessionToken, userId, me)
    }

    private fun performConnectLogin(accessToken: String): ProductUserId {
        val loginResult = EosManager.call {
            EosManager.platform.connect.login(EosExternalCredentialType.OpenIdAccessToken, accessToken)
        }.awaitEosCallback().awaitEosCallback()

        if (loginResult.result == EosResult.Success) return loginResult.localUserId

        check(loginResult.result == EosResult.InvalidUser) { "EOS_Connect_Login failed: ${loginResult.result}" }

        logger.info("No EOS user mapped yet, creating one")
        val createResult = EosManager.call {
            EosManager.platform.connect.createUser(loginResult.continuanceToken)
        }.awaitEosCallback().awaitEosCallback()

        check(createResult.result == EosResult.Success) { "EOS_Connect_CreateUser failed: ${createResult.result}" }
        return createResult.localUserId
    }

    private fun <T> CompletableFuture<T>.awaitEosCallback(): T = try {
        get(EOS_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    } catch (e: TimeoutException) {
        throw IllegalStateException("EOS did not respond in time (is the platform being ticked?)", e)
    }

    private fun codeVerifier(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun challengeFor(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
