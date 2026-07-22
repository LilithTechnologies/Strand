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

import java.time.Duration

data class Config(
    val port: Int,
    val issuer: String,
    val audience: String,
    val tokenTtl: Duration,
    val sessionTokenTtl: Duration,
    val authCodeTtl: Duration,
    val challengeTtl: Duration,
    val inviteCodeTtl: Duration,
    val sessionTtl: Duration,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val dbPoolSize: Int,
    val seed: Boolean,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun req(key: String): String =
                env[key] ?: error("Missing required environment variable $key")

            fun opt(key: String, default: String): String = env[key] ?: default

            val issuer = req("STRAND_ISSUER").trimEnd('/')

            return Config(
                port = opt("STRAND_PORT", "8080").toInt(),
                issuer = issuer,
                audience = opt("STRAND_OIDC_AUDIENCE", "strand"),
                tokenTtl = Duration.ofSeconds(opt("STRAND_TOKEN_TTL_SECONDS", "3600").toLong()),
                sessionTokenTtl = Duration.ofSeconds(opt("STRAND_SESSION_TOKEN_TTL_SECONDS", "43200").toLong()),
                authCodeTtl = Duration.ofSeconds(opt("STRAND_AUTH_CODE_TTL_SECONDS", "120").toLong()),
                challengeTtl = Duration.ofSeconds(opt("STRAND_CHALLENGE_TTL_SECONDS", "120").toLong()),
                inviteCodeTtl = Duration.ofSeconds(opt("STRAND_INVITE_CODE_TTL_SECONDS", "86400").toLong()),
                sessionTtl = Duration.ofSeconds(opt("STRAND_SESSION_TTL_SECONDS", "86400").toLong()),
                dbUrl = req("STRAND_DB_URL"),
                dbUser = req("STRAND_DB_USER"),
                dbPassword = req("STRAND_DB_PASSWORD"),
                dbPoolSize = opt("STRAND_DB_POOL_SIZE", "10").toInt(),
                seed = opt("STRAND_SEED", "false").toBoolean(),
            )
        }
    }
}
