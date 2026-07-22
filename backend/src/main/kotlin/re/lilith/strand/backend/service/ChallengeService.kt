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
import re.lilith.strand.backend.db.AuthChallenges
import re.lilith.strand.backend.util.Ids
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ChallengeService(private val config: Config) {

    private fun now() = LocalDateTime.now(ZoneOffset.UTC)

    fun create(userId: UUID, username: String): String = transaction {
        AuthChallenges.deleteWhere { AuthChallenges.userId eq userId }
        val serverId = Ids.socketToken(40)
        AuthChallenges.insert {
            it[AuthChallenges.serverId] = serverId
            it[AuthChallenges.userId] = userId
            it[AuthChallenges.username] = username
            it[expiresAt] = now().plus(config.challengeTtl)
        }
        serverId
    }

    fun consume(userId: UUID, serverId: String): Boolean = transaction {
        AuthChallenges.deleteWhere { expiresAt less now() }
        val row = AuthChallenges.selectAll()
            .where { (AuthChallenges.serverId eq serverId) and (AuthChallenges.userId eq userId) }
            .firstOrNull() ?: return@transaction false
        val valid = row[AuthChallenges.expiresAt].isAfter(now())
        AuthChallenges.deleteWhere { AuthChallenges.serverId eq serverId }
        valid
    }
}
