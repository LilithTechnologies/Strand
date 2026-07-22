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

package re.lilith.strand.backend.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChallengeRequest(val uuid: String, val username: String)

@Serializable
data class ChallengeResponse(val serverId: String)

@Serializable
data class SessionTokenRequest(val uuid: String, val username: String, val serverId: String)

@Serializable
data class SessionTokenResponse(
    val sessionToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val issuer: String,
)

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String = "openid profile",
)

@Serializable
data class OAuthError(val error: String, @SerialName("error_description") val errorDescription: String? = null)

@Serializable
data class LinkPuidRequest(val productUserId: String)

@Serializable
data class MeResponse(
    val uuid: String,
    val username: String,
    val productUserId: String?,
    val invitesBlocked: Boolean,
)

@Serializable
data class SettingsRequest(val invitesBlocked: Boolean)

@Serializable
data class ResolveRequest(val query: String)

@Serializable
data class ResolveResponse(
    val username: String,
    val uuid: String? = null,
    val productUserId: String? = null,
    val invitesBlocked: Boolean = false,
    val invitable: Boolean = false,
)

@Serializable
data class CreateSessionRequest(val socketName: String? = null)

@Serializable
data class SessionResponse(
    val sessionId: String,
    val inviteCode: String,
    val socketName: String,
    val hostProductUserId: String,
)

@Serializable
data class RedeemRequest(val code: String)

@Serializable
data class RedeemResponse(
    val sessionId: String,
    val hostUsername: String,
    val hostProductUserId: String,
    val socketName: String,
)

@Serializable
data class SendInviteRequest(val targetUsername: String)

@Serializable
data class SendInviteResponse(val inviteId: String)

@Serializable
data class PendingInviteDto(val id: String, val fromName: String, val fromProductUserId: String, val socketName: String)

@Serializable
data class PendingInvitesResponse(val invites: List<PendingInviteDto>)

@Serializable
data class AcceptInviteResponse(val hostUsername: String, val hostProductUserId: String, val socketName: String)

@Serializable
data class ErrorResponse(val error: String, val message: String? = null)
