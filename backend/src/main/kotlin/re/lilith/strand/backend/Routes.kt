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

import re.lilith.strand.backend.model.AcceptInviteResponse
import re.lilith.strand.backend.model.AccessTokenResponse
import re.lilith.strand.backend.model.ChallengeRequest
import re.lilith.strand.backend.model.ChallengeResponse
import re.lilith.strand.backend.model.CreateSessionRequest
import re.lilith.strand.backend.model.ErrorResponse
import re.lilith.strand.backend.model.LinkPuidRequest
import re.lilith.strand.backend.model.OAuthError
import re.lilith.strand.backend.model.PendingInviteDto
import re.lilith.strand.backend.model.PendingInvitesResponse
import re.lilith.strand.backend.model.RedeemRequest
import re.lilith.strand.backend.model.ResolveRequest
import re.lilith.strand.backend.model.SendInviteRequest
import re.lilith.strand.backend.model.SendInviteResponse
import re.lilith.strand.backend.model.SessionTokenRequest
import re.lilith.strand.backend.model.SessionTokenResponse
import re.lilith.strand.backend.model.SettingsRequest
import re.lilith.strand.backend.security.requireUser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

fun Route.strandRoutes(services: AppServices) {
    val config = services.config

    get("/health") { call.respondText("ok") }

    get("/.well-known/openid-configuration") {
        val doc = buildJsonObject {
            put("issuer", JsonPrimitive(config.issuer))
            put("authorization_endpoint", JsonPrimitive("${config.issuer}/oidc/authorize"))
            put("token_endpoint", JsonPrimitive("${config.issuer}/oidc/token"))
            put("jwks_uri", JsonPrimitive("${config.issuer}/.well-known/jwks.json"))
            putJsonArray("response_types_supported") { add(JsonPrimitive("code")) }
            putJsonArray("grant_types_supported") { add(JsonPrimitive("authorization_code")) }
            putJsonArray("subject_types_supported") { add(JsonPrimitive("public")) }
            putJsonArray("id_token_signing_alg_values_supported") { add(JsonPrimitive("RS256")) }
            putJsonArray("code_challenge_methods_supported") { add(JsonPrimitive("S256")) }
            putJsonArray("scopes_supported") { add(JsonPrimitive("openid")); add(JsonPrimitive("profile")) }
        }
        call.respondText(doc.toString(), ContentType.Application.Json)
    }

    get("/.well-known/jwks.json") {
        call.respondText(services.keys.publicJwksJson(), ContentType.Application.Json)
    }

    post("/auth/challenge") {
        val req = call.receive<ChallengeRequest>()
        val uuid = parseUuid(req.uuid) ?: return@post call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("bad_uuid"),
        )
        val serverId = services.challenges.create(uuid, req.username)
        call.respond(ChallengeResponse(serverId))
    }

    post("/auth/session") {
        val req = call.receive<SessionTokenRequest>()
        val uuid = parseUuid(req.uuid) ?: return@post call.respond(
            HttpStatusCode.BadRequest, ErrorResponse("bad_uuid"),
        )
        if (!services.challenges.consume(uuid, req.serverId)) {
            return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("challenge_invalid"))
        }
        val profile = services.mojang.hasJoined(req.username, req.serverId)
        if (profile == null || profile.uuid != uuid) {
            return@post call.respond(HttpStatusCode.Unauthorized, ErrorResponse("mojang_verification_failed"))
        }
        services.users.upsert(profile.uuid, profile.username)
        val issued = services.tokens.issueSession(profile.uuid, profile.username)
        call.respond(SessionTokenResponse(issued.token, expiresIn = issued.expiresIn, issuer = config.issuer))
    }

    get("/oidc/authorize") {
        val userId = call.requireUser(services.tokens) ?: return@get
        val params = call.request.queryParameters
        val clientId = params["client_id"]
        val redirectUri = params["redirect_uri"]
        val responseType = params["response_type"]
        val challenge = params["code_challenge"]
        val method = params["code_challenge_method"]
        val state = params["state"]
        val nonce = params["nonce"]

        if (responseType != "code" || challenge.isNullOrBlank() || method != "S256" ||
            clientId.isNullOrBlank() || redirectUri.isNullOrBlank()
        ) {
            return@get call.respond(HttpStatusCode.BadRequest, OAuthError("invalid_request"))
        }
        if (clientId != config.audience) {
            return@get call.respond(HttpStatusCode.BadRequest, OAuthError("unauthorized_client"))
        }
        val me = services.users.me(userId)
            ?: return@get call.respond(HttpStatusCode.Unauthorized, OAuthError("access_denied"))
        val code = services.authCodes.issue(userId, me.username, clientId, redirectUri, challenge, nonce)
        val separator = if (redirectUri.contains('?')) '&' else '?'
        val location = buildString {
            append(redirectUri).append(separator).append("code=").append(enc(code))
            if (!state.isNullOrBlank()) append("&state=").append(enc(state))
        }
        call.respondRedirect(location, permanent = false)
    }

    post("/oidc/token") {
        val form = call.receiveParameters()
        val grantType = form["grant_type"]
        val code = form["code"]
        val redirectUri = form["redirect_uri"]
        val verifier = form["code_verifier"]
        if (grantType != "authorization_code" || code.isNullOrBlank() ||
            redirectUri.isNullOrBlank() || verifier.isNullOrBlank()
        ) {
            return@post call.respond(HttpStatusCode.BadRequest, OAuthError("invalid_request"))
        }
        val authorized = services.authCodes.redeem(code, redirectUri, verifier)
            ?: return@post call.respond(HttpStatusCode.BadRequest, OAuthError("invalid_grant"))
        val issued = services.tokens.issueAccessToken(authorized.userId, authorized.username, authorized.nonce)
        call.respond(AccessTokenResponse(accessToken = issued.token, idToken = issued.token, expiresIn = issued.expiresIn))
    }

    post("/me/puid") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<LinkPuidRequest>()
        if (req.productUserId.isBlank()) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_puid"))
        }
        services.users.linkPuid(userId, req.productUserId.trim())
        call.respond(HttpStatusCode.NoContent)
    }

    get("/me") {
        val userId = call.requireUser(services.tokens) ?: return@get
        val me = services.users.me(userId)
            ?: return@get call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
        call.respond(me)
    }

    put("/me/settings") {
        val userId = call.requireUser(services.tokens) ?: return@put
        val req = call.receive<SettingsRequest>()
        services.users.setInvitesBlocked(userId, req.invitesBlocked)
        val me = services.users.me(userId)
            ?: return@put call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
        call.respond(me)
    }

    post("/directory/resolve") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<ResolveRequest>()
        val resolved = services.users.resolveByUsername(req.query.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
        if (!resolved.invitable) {
            services.sessions.audit(userId, null, null, "INVITE_BLOCKED", req.query.trim())
        }
        call.respond(resolved)
    }

    post("/sessions") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<CreateSessionRequest>()
        val puid = services.users.productUserId(userId)
            ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("puid_missing", "Report a product user id first"))
        val session = services.sessions.createOrReplace(userId, puid, req.socketName)
        call.respond(session)
    }

    delete("/sessions/{id}") {
        val userId = call.requireUser(services.tokens) ?: return@delete
        val id = call.parameters["id"]?.let { parseUuid(it) }
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_id"))
        if (services.sessions.close(userId, id)) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
    }

    post("/sessions/redeem") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<RedeemRequest>()
        val redeemed = services.sessions.redeem(userId, req.code.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("code_invalid"))
        call.respond(redeemed)
    }

    post("/invites") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<SendInviteRequest>()
        val active = services.sessions.activeSession(userId)
            ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("not_hosting", "Host a world before inviting"))
        val target = services.users.lookup(req.targetUsername.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("user_not_found"))
        if (target.invitesBlocked) {
            services.sessions.audit(userId, target.id, active.id, "INVITE_BLOCKED", req.targetUsername.trim())
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("blocked", "That player has invites blocked"))
        }
        val fromName = services.users.me(userId)?.username ?: "A player"
        val inviteId = services.invites.create(userId, fromName, active.hostProductUserId, active.socketName, active.id, target.id)
        services.sessions.audit(userId, target.id, active.id, "INVITE_SENT", req.targetUsername.trim())
        call.respond(SendInviteResponse(inviteId.toString()))
    }

    get("/invites/pending") {
        val userId = call.requireUser(services.tokens) ?: return@get
        val pending = services.invites.pending(userId).map {
            PendingInviteDto(it.id.toString(), it.fromName, it.fromPuid, it.socketName)
        }
        call.respond(PendingInvitesResponse(pending))
    }

    post("/invites/{id}/accept") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val id = call.parameters["id"]?.let { parseUuid(it) }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_id"))
        val accepted = services.invites.accept(userId, id)
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("invite_not_found"))
        call.respond(AcceptInviteResponse(accepted.hostName, accepted.hostProductUserId, accepted.socketName))
    }

    post("/invites/{id}/decline") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val id = call.parameters["id"]?.let { parseUuid(it) }
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_id"))
        if (services.invites.decline(userId, id)) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound, ErrorResponse("invite_not_found"))
    }
}

private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun parseUuid(value: String): UUID? = try {
    UUID.fromString(value)
} catch (_: IllegalArgumentException) {
    null
}
