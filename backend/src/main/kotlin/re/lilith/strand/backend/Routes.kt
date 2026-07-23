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
import re.lilith.strand.backend.model.VoiceKickRequest
import re.lilith.strand.backend.model.VoiceMuteRequest
import re.lilith.strand.backend.model.VoiceMembersEntry
import re.lilith.strand.backend.model.VoiceMembersRequest
import re.lilith.strand.backend.model.VoiceMembersResponse
import re.lilith.strand.backend.model.VoiceSettingsRequest
import re.lilith.strand.backend.model.VoiceSettingsResponse
import re.lilith.strand.backend.model.VoiceTokenRequest
import re.lilith.strand.backend.model.VoiceTokenResponse
import re.lilith.strand.backend.security.requireUser
import re.lilith.strand.backend.service.VoiceException
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

    post("/voice/token") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val voice = services.voice
            ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("voice_unavailable"))
        val req = call.receive<VoiceTokenRequest>()
        val session = services.sessions.voiceSessionBySocket(req.socketName.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found"))
        val isHost = session.hostId == userId
        if (!isHost && !services.sessions.isMember(session.id, userId)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_a_member"))
        }
        val puid = services.users.productUserId(userId)
            ?: return@post call.respond(HttpStatusCode.Conflict, ErrorResponse("puid_missing"))
        val token = try {
            voice.createRoomToken(session.voiceRoomId, puid, hardMuted = !session.voiceEnabled)
        } catch (e: VoiceException) {
            return@post call.respond(HttpStatusCode.BadGateway, ErrorResponse("rtc_error", e.message))
        }
        call.respond(
            VoiceTokenResponse(
                roomId = session.voiceRoomId,
                clientBaseUrl = token.clientBaseUrl,
                token = token.token,
                proxNear = session.proxNear,
                proxMax = session.proxMax,
                voiceEnabled = session.voiceEnabled,
                host = isHost,
                hostProductUserId = session.hostProductUserId,
            ),
        )
    }

    post("/voice/members") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<VoiceMembersRequest>()
        val session = services.sessions.voiceSessionBySocket(req.socketName.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found"))
        if (session.hostId != userId && !services.sessions.isMember(session.id, userId)) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_a_member"))
        }
        val members = services.sessions.members(session.id)
            .mapNotNull { m -> m.productUserId?.let { VoiceMembersEntry(it, m.userId.toString(), m.username) } }
        call.respond(VoiceMembersResponse(members))
    }

    post("/voice/kick") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val voice = services.voice
            ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("voice_unavailable"))
        val req = call.receive<VoiceKickRequest>()
        val session = services.sessions.voiceSessionBySocket(req.socketName.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found"))
        if (session.hostId != userId) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_host"))
        }
        try {
            voice.kick(session.voiceRoomId, req.targetProductUserId.trim())
        } catch (e: VoiceException) {
            return@post call.respond(HttpStatusCode.BadGateway, ErrorResponse("rtc_error", e.message))
        }
        services.sessions.removeMemberByPuid(session.id, req.targetProductUserId.trim())
        services.sessions.audit(userId, null, session.id, "VOICE_KICK", req.targetProductUserId.trim())
        call.respond(HttpStatusCode.NoContent)
    }

    post("/voice/mute") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val voice = services.voice
            ?: return@post call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse("voice_unavailable"))
        val req = call.receive<VoiceMuteRequest>()
        val session = services.sessions.voiceSessionBySocket(req.socketName.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found"))
        if (session.hostId != userId) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_host"))
        }
        try {
            voice.setHardMute(session.voiceRoomId, req.targetProductUserId.trim(), req.muted)
        } catch (e: VoiceException) {
            return@post call.respond(HttpStatusCode.BadGateway, ErrorResponse("rtc_error", e.message))
        }
        services.sessions.audit(userId, null, session.id, if (req.muted) "VOICE_MUTE" else "VOICE_UNMUTE", req.targetProductUserId.trim())
        call.respond(HttpStatusCode.NoContent)
    }

    post("/voice/settings") {
        val userId = call.requireUser(services.tokens) ?: return@post
        val req = call.receive<VoiceSettingsRequest>()
        val session = services.sessions.voiceSessionBySocket(req.socketName.trim())
            ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("session_not_found"))
        if (session.hostId != userId) {
            return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_host"))
        }
        val updated = services.sessions.setVoiceConfig(userId, session.id, req.voiceEnabled, req.proxNear, req.proxMax)
            ?: return@post call.respond(HttpStatusCode.Forbidden, ErrorResponse("not_host"))
        val voice = services.voice
        if (req.voiceEnabled != null && voice != null) {
            val mute = !req.voiceEnabled
            services.sessions.members(session.id).forEach { m ->
                m.productUserId?.let { runCatching { voice.setHardMute(updated.voiceRoomId, it, mute) } }
            }
        }
        call.respond(VoiceSettingsResponse(updated.voiceEnabled, updated.proxNear, updated.proxMax))
    }
}

private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun parseUuid(value: String): UUID? = try {
    UUID.fromString(value)
} catch (_: IllegalArgumentException) {
    null
}
