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
