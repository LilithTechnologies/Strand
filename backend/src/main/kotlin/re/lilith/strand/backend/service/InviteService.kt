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

import re.lilith.strand.backend.db.Invites
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class PendingInviteRow(val id: UUID, val fromName: String, val fromPuid: String, val socketName: String)
data class AcceptedInvite(val hostName: String, val hostProductUserId: String, val socketName: String)

class InviteService {

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)
    private val ttl = Duration.ofMinutes(15)

    fun create(
        fromId: UUID,
        fromName: String,
        fromPuid: String,
        socketName: String,
        sessionId: UUID?,
        toId: UUID,
    ): UUID = transaction {
        Invites.deleteWhere { (Invites.fromId eq fromId) and (Invites.toId eq toId) and (Invites.status eq "PENDING") }
        val id = UUID.randomUUID()
        Invites.insert {
            it[Invites.id] = id
            it[Invites.fromId] = fromId
            it[Invites.fromName] = fromName
            it[Invites.fromPuid] = fromPuid
            it[Invites.toId] = toId
            it[Invites.sessionId] = sessionId
            it[Invites.socketName] = socketName
            it[status] = "PENDING"
            it[createdAt] = now()
            it[expiresAt] = now().plus(ttl)
        }
        id
    }

    fun pending(toId: UUID): List<PendingInviteRow> = transaction {
        Invites.deleteWhere { expiresAt less now() }
        Invites.selectAll()
            .where { (Invites.toId eq toId) and (Invites.status eq "PENDING") }
            .map { PendingInviteRow(it[Invites.id].value, it[Invites.fromName], it[Invites.fromPuid], it[Invites.socketName]) }
    }

    fun accept(toId: UUID, inviteId: UUID): AcceptedInvite? = transaction {
        val row = Invites.selectAll()
            .where { (Invites.id eq inviteId) and (Invites.toId eq toId) and (Invites.status eq "PENDING") }
            .firstOrNull() ?: return@transaction null
        if (row[Invites.expiresAt].isBefore(now())) return@transaction null
        Invites.update({ Invites.id eq inviteId }) { it[status] = "ACCEPTED" }
        AcceptedInvite(row[Invites.fromName], row[Invites.fromPuid], row[Invites.socketName])
    }

    fun decline(toId: UUID, inviteId: UUID): Boolean = transaction {
        Invites.update({ (Invites.id eq inviteId) and (Invites.toId eq toId) and (Invites.status eq "PENDING") }) {
            it[status] = "DECLINED"
        } > 0
    }
}
