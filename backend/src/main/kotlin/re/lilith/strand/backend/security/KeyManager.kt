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

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import re.lilith.strand.backend.db.SigningKeys
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

class KeyManager private constructor(val signingKey: RSAKey) {

    val publicJwkSet: JWKSet = JWKSet(signingKey.toPublicJWK())

    fun publicJwksJson(): String = publicJwkSet.toString()

    companion object {
        fun loadOrCreate(): KeyManager {
            val existing = transaction {
                SigningKeys.selectAll()
                    .firstOrNull { it[SigningKeys.active] }
                    ?.get(SigningKeys.jwkJson)
            }
            if (existing != null) {
                return KeyManager(RSAKey.parse(existing))
            }
            val generated = RSAKeyGenerator(2048)
                .keyUse(KeyUse.SIGNATURE)
                .keyIDFromThumbprint(true)
                .generate()
            transaction {
                SigningKeys.insert {
                    it[kid] = generated.keyID
                    it[jwkJson] = generated.toJSONString()
                    it[active] = true
                    it[createdAt] = LocalDateTime.now()
                }
            }
            return KeyManager(generated)
        }
    }
}
