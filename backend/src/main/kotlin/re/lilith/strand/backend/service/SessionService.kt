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

package re.lilith.strand.backend.service

import re.lilith.strand.backend.Config
import re.lilith.strand.backend.db.InviteAudit
import re.lilith.strand.backend.db.Sessions
import re.lilith.strand.backend.db.Users
import re.lilith.strand.backend.model.RedeemResponse
import re.lilith.strand.backend.model.SessionResponse
import re.lilith.strand.backend.util.Ids
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class ActiveSession(val id: UUID, val socketName: String, val hostProductUserId: String)

class SessionService(private val config: Config) {

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)

    fun createOrReplace(hostId: UUID, hostPuid: String, requestedSocket: String?): SessionResponse = transaction {
        Sessions.update({ (Sessions.hostId eq hostId) and (Sessions.active eq true) }) {
            it[active] = false
        }
        val socketName = (requestedSocket?.takeIf { s -> s.length in 1..30 } ?: Ids.socketToken(24))
        val code = uniqueCode()
        val sessionId = UUID.randomUUID()
        Sessions.insert {
            it[id] = sessionId
            it[Sessions.hostId] = hostId
            it[hostProductUserId] = hostPuid
            it[Sessions.socketName] = socketName
            it[inviteCode] = code
            it[active] = true
            it[createdAt] = now()
            it[expiresAt] = now().plus(config.sessionTtl)
        }
        audit(hostId, null, sessionId, "SESSION_OPEN", null)
        SessionResponse(sessionId.toString(), code, socketName, hostPuid)
    }

    fun activeSession(hostId: UUID): ActiveSession? = transaction {
        Sessions.selectAll()
            .where { (Sessions.hostId eq hostId) and (Sessions.active eq true) }
            .firstOrNull()
            ?.takeIf { it[Sessions.expiresAt].isAfter(now()) }
            ?.let { ActiveSession(it[Sessions.id].value, it[Sessions.socketName], it[Sessions.hostProductUserId]) }
    }

    fun close(hostId: UUID, sessionId: UUID): Boolean = transaction {
        val updated = Sessions.update({ (Sessions.id eq sessionId) and (Sessions.hostId eq hostId) }) {
            it[active] = false
        }
        if (updated > 0) audit(hostId, null, sessionId, "SESSION_CLOSE", null)
        updated > 0
    }

    fun redeem(redeemerId: UUID, code: String): RedeemResponse? = transaction {
        val row = Sessions.selectAll()
            .where { (Sessions.inviteCode eq code.uppercase()) and (Sessions.active eq true) }
            .firstOrNull() ?: return@transaction null
        if (row[Sessions.expiresAt].isBefore(now())) return@transaction null
        val hostId = row[Sessions.hostId]
        val hostName = Users.selectAll().where { Users.id eq hostId }
            .firstOrNull()?.get(Users.username) ?: "unknown"
        audit(redeemerId, hostId, row[Sessions.id].value, "CODE_REDEEM", code.uppercase())
        RedeemResponse(
            sessionId = row[Sessions.id].value.toString(),
            hostUsername = hostName,
            hostProductUserId = row[Sessions.hostProductUserId],
            socketName = row[Sessions.socketName],
        )
    }

    fun audit(fromId: UUID, toId: UUID?, sessionId: UUID?, action: String, detail: String?) = transaction {
        InviteAudit.insert {
            it[InviteAudit.fromId] = fromId
            it[InviteAudit.toId] = toId
            it[InviteAudit.sessionId] = sessionId
            it[InviteAudit.action] = action
            it[InviteAudit.detail] = detail
            it[createdAt] = now()
        }
    }

    private fun uniqueCode(): String {
        repeat(8) {
            val code = Ids.inviteCode()
            val taken = Sessions.selectAll().where { Sessions.inviteCode eq code }.any()
            if (!taken) return code
        }
        return Ids.inviteCode(10)
    }
}
