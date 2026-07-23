package re.lilith.strand.backend

import re.lilith.strand.backend.db.Db
import re.lilith.strand.backend.mojang.MojangClient
import re.lilith.strand.backend.model.ErrorResponse
import re.lilith.strand.backend.security.KeyManager
import re.lilith.strand.backend.security.TokenService
import re.lilith.strand.backend.service.AuthCodeService
import re.lilith.strand.backend.service.ChallengeService
import re.lilith.strand.backend.service.InviteService
import re.lilith.strand.backend.service.SessionService
import re.lilith.strand.backend.service.UserService
import re.lilith.strand.backend.service.VoiceService
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main() {
    val config = Config.fromEnv(Dotenv.load())
    Db.connect(config)
    val keys = KeyManager.loadOrCreate()
    val services = AppServices(
        config = config,
        keys = keys,
        tokens = TokenService(config, keys),
        users = UserService(),
        challenges = ChallengeService(config),
        authCodes = AuthCodeService(config),
        sessions = SessionService(config),
        invites = InviteService(),
        mojang = MojangClient(),
        voice = config.eos?.let { VoiceService(it) },
    )
    Seeder.run(config, services)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(services)
    }.start(wait = true)
}

fun Application.module(services: AppServices) {
    install(DefaultHeaders)
    install(CallLogging) { level = Level.INFO }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled error", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error"))
        }
    }
    routing {
        strandRoutes(services)
    }
}
