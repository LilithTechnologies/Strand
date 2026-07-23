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

package re.lilith.strand.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class BackendClient(
    private val baseUrl: String,
    private val clientId: String,
    private val redirectUri: String,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    fun challenge(uuid: String, username: String): ChallengeResponse =
        postJson("/auth/challenge", mapOf("uuid" to uuid, "username" to username), null)

    fun session(uuid: String, username: String, serverId: String): SessionTokenResponse =
        postJson("/auth/session", mapOf("uuid" to uuid, "username" to username, "serverId" to serverId), null)

    fun oidcAuthorize(sessionToken: String, codeChallenge: String): String {
        val query = listOf(
            "response_type" to "code",
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "scope" to "openid profile",
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/oidc/authorize?$query"))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer $sessionToken")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 302 && response.statusCode() != 303) {
            throw BackendException(response.statusCode(), parseError(response.body()) ?: "authorize_failed")
        }
        val location = response.headers().firstValue("Location").orElse(null)
            ?: throw BackendException(response.statusCode(), "no_location")
        val params = parseQuery(location.substringAfter('?', ""))
        params["error"]?.let { throw BackendException(400, it) }
        return params["code"] ?: throw BackendException(400, "no_code")
    }

    fun oidcToken(code: String, codeVerifier: String): String {
        val body = listOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
            "client_id" to clientId,
        ).joinToString("&") { (k, v) -> "$k=${enc(v)}" }

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/oidc/token"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw BackendException(response.statusCode(), parseError(response.body()) ?: "token_failed")
        }
        val obj = json.parseToJsonElement(response.body()) as? JsonObject
            ?: throw BackendException(200, "bad_token_response")
        return (obj["access_token"] as? JsonPrimitive)?.content
            ?: throw BackendException(200, "no_access_token")
    }

    fun me(bearer: String): MeResponse = getJson("/me", bearer)

    fun setInvitesBlocked(bearer: String, blocked: Boolean): MeResponse =
        putJson("/me/settings", mapOf("invitesBlocked" to blocked), bearer)

    fun linkPuid(bearer: String, productUserId: String) =
        sendUnit(base("/me/puid", bearer).POST(bodyOf(mapOf("productUserId" to productUserId))).build())

    fun createSession(bearer: String, socketName: String?): SessionResponse =
        postJson("/sessions", mapOf("socketName" to socketName), bearer)

    fun closeSession(bearer: String, sessionId: String) =
        sendUnit(base("/sessions/$sessionId", bearer).DELETE().build())

    fun redeem(bearer: String, code: String): RedeemResponse =
        postJson("/sessions/redeem", mapOf("code" to code), bearer)

    fun sendInvite(bearer: String, targetUsername: String): SendInviteResponse =
        postJson("/invites", mapOf("targetUsername" to targetUsername), bearer)

    fun pendingInvites(bearer: String): PendingInvitesResponse =
        getJson("/invites/pending", bearer)

    fun acceptInvite(bearer: String, inviteId: String): AcceptInviteResponse =
        postJson("/invites/$inviteId/accept", emptyMap<String, String>(), bearer)

    fun declineInvite(bearer: String, inviteId: String) =
        sendUnit(base("/invites/$inviteId/decline", bearer).POST(bodyOf(emptyMap<String, String>())).build())

    private inline fun <reified T> getJson(path: String, bearer: String?): T =
        json.decodeFromString(send(base(path, bearer).GET().build()))

    private inline fun <reified T> postJson(path: String, body: Any?, bearer: String?): T =
        json.decodeFromString(send(base(path, bearer).POST(bodyOf(body)).build()))

    private inline fun <reified T> putJson(path: String, body: Any?, bearer: String?): T =
        json.decodeFromString(send(base(path, bearer).PUT(bodyOf(body)).build()))

    private fun base(path: String, bearer: String?): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return builder
    }

    private fun bodyOf(body: Any?): HttpRequest.BodyPublisher =
        HttpRequest.BodyPublishers.ofString(json.encodeToString(JsonElement.serializer(), toJson(body)))

    private fun send(request: HttpRequest): String {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw BackendException(response.statusCode(), parseError(response.body()) ?: "http_${response.statusCode()}")
        }
        return response.body()
    }

    private fun sendUnit(request: HttpRequest) {
        send(request)
    }

    private fun parseError(body: String): String? =
        runCatching { (json.parseToJsonElement(body) as? JsonObject)?.get("error")?.let { (it as? JsonPrimitive)?.content } }.getOrNull()

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter { it.isNotEmpty() }
        .associate {
            val parts = it.split('=', limit = 2)
            parts[0] to (parts.getOrNull(1)?.let { v -> java.net.URLDecoder.decode(v, StandardCharsets.UTF_8) } ?: "")
        }

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        else -> JsonPrimitive(value.toString())
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
