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

package re.lilith.strand.backend.mojang

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class MojangClient(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val baseUrl: String = "https://sessionserver.mojang.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasJoined(username: String, serverId: String): VerifiedProfile? {
        val url = "$baseUrl/session/minecraft/hasJoined" +
            "?username=${enc(username)}&serverId=${enc(serverId)}"
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200 || response.body().isNullOrBlank()) return null
        val obj = json.parseToJsonElement(response.body()) as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.content ?: return null
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        return VerifiedProfile(dashUuid(id), name)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun dashUuid(undashed: String): UUID {
        if (undashed.contains('-')) return UUID.fromString(undashed)
        val sb = StringBuilder(undashed)
        sb.insert(20, '-').insert(16, '-').insert(12, '-').insert(8, '-')
        return UUID.fromString(sb.toString())
    }
}

