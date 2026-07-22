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

package re.lilith.strand.backend.service

import re.lilith.strand.backend.Config
import re.lilith.strand.backend.db.AuthCodes
import re.lilith.strand.backend.util.Ids
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

class AuthorizedCode(
    val userId: UUID,
    val username: String,
    val nonce: String?,
)

class AuthCodeService(private val config: Config) {

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)

    fun issue(
        userId: UUID,
        username: String,
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        nonce: String?,
    ): String = transaction {
        val code = Ids.socketToken(48)
        AuthCodes.insert {
            it[AuthCodes.code] = code
            it[AuthCodes.userId] = userId
            it[AuthCodes.username] = username
            it[AuthCodes.clientId] = clientId
            it[AuthCodes.redirectUri] = redirectUri
            it[AuthCodes.codeChallenge] = codeChallenge
            it[AuthCodes.nonce] = nonce
            it[expiresAt] = now().plus(config.authCodeTtl)
        }
        code
    }

    fun redeem(code: String, redirectUri: String, codeVerifier: String): AuthorizedCode? = transaction {
        AuthCodes.deleteWhere { expiresAt less now() }
        val row = AuthCodes.selectAll().where { AuthCodes.code eq code }.firstOrNull()
            ?: return@transaction null
        AuthCodes.deleteWhere { AuthCodes.code eq code }
        if (row[AuthCodes.expiresAt].isBefore(now())) return@transaction null
        if (row[AuthCodes.redirectUri] != redirectUri) return@transaction null
        if (!verifyChallenge(codeVerifier, row[AuthCodes.codeChallenge])) return@transaction null
        AuthorizedCode(row[AuthCodes.userId], row[AuthCodes.username], row[AuthCodes.nonce])
    }

    private fun verifyChallenge(verifier: String, challenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return computed == challenge
    }
}
