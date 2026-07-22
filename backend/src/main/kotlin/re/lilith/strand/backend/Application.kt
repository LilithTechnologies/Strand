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

import re.lilith.strand.backend.db.Db
import re.lilith.strand.backend.mojang.MojangClient
import re.lilith.strand.backend.model.ErrorResponse
import re.lilith.strand.backend.module
import re.lilith.strand.backend.security.KeyManager
import re.lilith.strand.backend.security.TokenService
import re.lilith.strand.backend.service.AuthCodeService
import re.lilith.strand.backend.service.ChallengeService
import re.lilith.strand.backend.service.InviteService
import re.lilith.strand.backend.service.SessionService
import re.lilith.strand.backend.service.UserService
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
