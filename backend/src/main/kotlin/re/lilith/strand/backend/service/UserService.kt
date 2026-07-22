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

import re.lilith.strand.backend.db.Users
import re.lilith.strand.backend.model.MeResponse
import re.lilith.strand.backend.model.ResolveResponse
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class UserLookup(val id: UUID, val username: String, val productUserId: String?, val invitesBlocked: Boolean)

class UserService {

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)

    fun upsert(userId: UUID, username: String) = transaction {
        val exists = Users.selectAll().where { Users.id eq userId }.any()
        if (exists) {
            Users.update({ Users.id eq userId }) {
                it[Users.username] = username
                it[updatedAt] = now()
            }
        } else {
            Users.insert {
                it[id] = userId
                it[Users.username] = username
                it[invitesBlocked] = false
                it[createdAt] = now()
                it[updatedAt] = now()
            }
        }
    }

    fun linkPuid(userId: UUID, productUserId: String) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.productUserId] = productUserId
            it[updatedAt] = now()
        }
    }

    fun setInvitesBlocked(userId: UUID, blocked: Boolean) = transaction {
        Users.update({ Users.id eq userId }) {
            it[invitesBlocked] = blocked
            it[updatedAt] = now()
        }
    }

    fun me(userId: UUID): MeResponse? = transaction {
        Users.selectAll().where { Users.id eq userId }.firstOrNull()?.let {
            MeResponse(
                uuid = userId.toString(),
                username = it[Users.username],
                productUserId = it[Users.productUserId],
                invitesBlocked = it[Users.invitesBlocked],
            )
        }
    }

    fun productUserId(userId: UUID): String? = transaction {
        Users.selectAll().where { Users.id eq userId }
            .firstOrNull()?.get(Users.productUserId)
    }

    fun lookup(username: String): UserLookup? = transaction {
        Users.selectAll()
            .where { Users.username.lowerCase() eq username.lowercase() }
            .firstOrNull()
            ?.let { UserLookup(it[Users.id].value, it[Users.username], it[Users.productUserId], it[Users.invitesBlocked]) }
    }

    fun resolveByUsername(username: String): ResolveResponse? = transaction {
        val row = Users.selectAll()
            .where { Users.username.lowerCase() eq username.lowercase() }
            .firstOrNull() ?: return@transaction null
        val blocked = row[Users.invitesBlocked]
        val puid = row[Users.productUserId]
        val invitable = !blocked && puid != null
        ResolveResponse(
            username = row[Users.username],
            uuid = if (invitable) row[Users.id].value.toString() else null,
            productUserId = if (invitable) puid else null,
            invitesBlocked = blocked,
            invitable = invitable,
        )
    }
}
