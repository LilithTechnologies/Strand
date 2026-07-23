package re.lilith.strand.backend.service

import re.lilith.strand.backend.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Base64

data class RoomToken(val clientBaseUrl: String, val token: String)

class VoiceException(val status: Int, message: String) : RuntimeException(message)

class VoiceService(private val eos: Config.EosServerConfig) {

    private val logger = LoggerFactory.getLogger("strand/voice")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val tokenLock = Any()
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var tokenExpiresAtSec: Long = 0

    fun createRoomToken(roomId: String, puid: String, hardMuted: Boolean = false): RoomToken {
        val body = json.encodeToString(
            CreateRoomReq.serializer(),
            CreateRoomReq(listOf(TokenParticipantReq(puid, hardMuted))),
        )
        val request = rtcRequest("/room/${enc(roomId)}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val resp = send(request)
        val parsed = json.decodeFromString(CreateRoomResp.serializer(), resp)
        val token = parsed.participants.firstOrNull { it.puid == puid }?.token
            ?: parsed.participants.firstOrNull()?.token
            ?: throw VoiceException(502, "no_token_in_response")
        return RoomToken(parsed.clientBaseUrl, token)
    }

    fun kick(roomId: String, puid: String) {
        val request = rtcRequest("/room/${enc(roomId)}/participants/${enc(puid)}")
            .DELETE()
            .build()
        send(request)
    }

    fun setHardMute(roomId: String, puid: String, muted: Boolean) {
        val body = json.encodeToString(HardMuteReq.serializer(), HardMuteReq(muted))
        val request = rtcRequest("/room/${enc(roomId)}/participants/${enc(puid)}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        send(request)
    }

    private fun rtcRequest(path: String): HttpRequest.Builder =
        HttpRequest.newBuilder(URI.create("${eos.rtcBase}/rtc/v1/${enc(eos.deploymentId)}$path"))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${accessToken()}")

    private fun send(request: HttpRequest): String {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            logger.warn("RTC admin call failed {} -> {}: {}", request.uri(), response.statusCode(), response.body())
            throw VoiceException(response.statusCode(), "rtc_admin_${response.statusCode()}")
        }
        return response.body()
    }

    private fun accessToken(): String {
        val nowSec = System.currentTimeMillis() / 1000
        cachedToken?.let { if (nowSec < tokenExpiresAtSec) return it }
        synchronized(tokenLock) {
            cachedToken?.let { if (nowSec < tokenExpiresAtSec) return it }
            val basic = Base64.getEncoder()
                .encodeToString("${eos.clientId}:${eos.clientSecret}".toByteArray(StandardCharsets.UTF_8))
            val form = "grant_type=client_credentials&deployment_id=${enc(eos.deploymentId)}"
            val request = HttpRequest.newBuilder(URI.create("${eos.authBase}/auth/v1/oauth/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic $basic")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                logger.error("EOS token request failed {}: {}", response.statusCode(), response.body())
                throw VoiceException(response.statusCode(), "eos_auth_${response.statusCode()}")
            }
            val parsed = json.decodeFromString(OAuthTokenResp.serializer(), response.body())
            cachedToken = parsed.accessToken
            tokenExpiresAtSec = nowSec + parsed.expiresIn - 60
            return parsed.accessToken
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    @Serializable
    private data class TokenParticipantReq(val puid: String, val hardMuted: Boolean = false)
    @Serializable
    private data class CreateRoomReq(val participants: List<TokenParticipantReq>)
    @Serializable
    private data class TokenParticipantResp(val puid: String = "", val token: String = "")
    @Serializable
    private data class CreateRoomResp(
        val clientBaseUrl: String = "",
        val participants: List<TokenParticipantResp> = emptyList(),
    )

    @Serializable
    private data class HardMuteReq(val hardMuted: Boolean)
    @Serializable
    private data class OAuthTokenResp(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 3600,
    )
}
