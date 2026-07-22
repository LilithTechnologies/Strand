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

package re.lilith.strand.backend.security

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond
import re.lilith.strand.backend.model.ErrorResponse
import java.util.UUID

suspend fun ApplicationCall.requireUser(tokens: TokenService): UUID? {
    val header = request.header("Authorization")
    val token = header?.removePrefix("Bearer ")?.trim()
    if (token.isNullOrEmpty()) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Missing bearer token"))
        return null
    }
    val userId = tokens.verifySession(token)
    if (userId == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Invalid or expired token"))
        return null
    }
    return userId
}
