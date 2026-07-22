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

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import re.lilith.strand.backend.Config
import java.time.Instant
import java.util.Date
import java.util.UUID

class TokenService(private val config: Config, keys: KeyManager) {

    private val kid = keys.signingKey.keyID
    private val signer = RSASSASigner(keys.signingKey)
    private val verifier = RSASSAVerifier(keys.signingKey.toPublicJWK())

    fun issueSession(userId: UUID, username: String): IssuedToken =
        sign(userId, username, SESSION_AUDIENCE, config.sessionTokenTtl.seconds, null)

    fun issueAccessToken(userId: UUID, username: String, nonce: String?): IssuedToken =
        sign(userId, username, config.audience, config.tokenTtl.seconds, nonce)

    fun verifySession(token: String): UUID? = verify(token, SESSION_AUDIENCE)

    private fun sign(userId: UUID, username: String, audience: String, ttl: Long, nonce: String?): IssuedToken {
        val now = Instant.now()
        val builder = JWTClaimsSet.Builder()
            .issuer(config.issuer)
            .subject(userId.toString())
            .audience(audience)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttl)))
            .jwtID(UUID.randomUUID().toString())
            .claim("preferred_username", username)
        if (nonce != null) builder.claim("nonce", nonce)
        val jwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build(),
            builder.build(),
        )
        jwt.sign(signer)
        return IssuedToken(jwt.serialize(), ttl)
    }

    private fun verify(token: String, audience: String): UUID? = try {
        val jwt = SignedJWT.parse(token)
        val claims = jwt.jwtClaimsSet
        when {
            !jwt.verify(verifier) -> null
            claims.issuer != config.issuer -> null
            audience !in (claims.audience ?: emptyList()) -> null
            claims.expirationTime == null || claims.expirationTime.toInstant().isBefore(Instant.now()) -> null
            else -> UUID.fromString(claims.subject)
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val SESSION_AUDIENCE = "strand-session"
    }
}
