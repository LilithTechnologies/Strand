package re.lilith.strand.backend

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeResponse(val serverId: String)

@Serializable
data class SessionTokenResponse(val sessionToken: String, val tokenType: String = "Bearer", val expiresIn: Long, val issuer: String)

@Serializable
data class MeResponse(val uuid: String, val username: String, val productUserId: String?, val invitesBlocked: Boolean)

@Serializable
data class SessionResponse(val sessionId: String, val inviteCode: String, val socketName: String, val hostProductUserId: String)

@Serializable
data class RedeemResponse(val sessionId: String, val hostUsername: String, val hostProductUserId: String, val socketName: String)

@Serializable
data class SendInviteResponse(val inviteId: String)

@Serializable
data class PendingInviteDto(val id: String, val fromName: String, val fromProductUserId: String, val socketName: String)

@Serializable
data class PendingInvitesResponse(val invites: List<PendingInviteDto> = emptyList())
@Serializable
data class AcceptInviteResponse(val hostUsername: String, val hostProductUserId: String, val socketName: String)

@Serializable
data class VoiceTokenResponse(
    val roomId: String,
    val clientBaseUrl: String,
    val token: String,
    val proxNear: Int,
    val proxMax: Int,
    val voiceEnabled: Boolean,
    val host: Boolean,
    val hostProductUserId: String,
)

@Serializable
data class VoiceMemberEntry(val productUserId: String, val mcUuid: String, val username: String)

@Serializable
data class VoiceMembersResponse(val members: List<VoiceMemberEntry> = emptyList())

@Serializable
data class VoiceSettingsResponse(val voiceEnabled: Boolean, val proxNear: Int, val proxMax: Int)
