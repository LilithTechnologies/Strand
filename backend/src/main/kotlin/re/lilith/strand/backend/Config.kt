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
    val eos: EosServerConfig?,
) {
    data class EosServerConfig(
        val clientId: String,
        val clientSecret: String,
        val deploymentId: String,
        val authBase: String,
        val rtcBase: String,
    )

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): Config {
            fun req(key: String): String =
                env[key] ?: error("Missing required environment variable $key")

            fun opt(key: String, default: String): String = env[key] ?: default

            val issuer = req("STRAND_ISSUER").trimEnd('/')

            val eosClientId = env["STRAND_EOS_CLIENT_ID"]
            val eosClientSecret = env["STRAND_EOS_CLIENT_SECRET"]
            val eosDeploymentId = env["STRAND_EOS_DEPLOYMENT_ID"]
            val eos = if (!eosClientId.isNullOrBlank() && !eosClientSecret.isNullOrBlank() && !eosDeploymentId.isNullOrBlank()) {
                EosServerConfig(
                    clientId = eosClientId,
                    clientSecret = eosClientSecret,
                    deploymentId = eosDeploymentId,
                    authBase = opt("STRAND_EOS_AUTH_BASE", "https://api.epicgames.dev").trimEnd('/'),
                    rtcBase = opt("STRAND_EOS_RTC_BASE", "https://api.epicgames.dev").trimEnd('/'),
                )
            } else null

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
                eos = eos,
            )
        }
    }
}
