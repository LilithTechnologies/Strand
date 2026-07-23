package re.lilith.strand.backend.service

import re.lilith.strand.backend.Config
import re.lilith.strand.backend.db.InviteAudit
import re.lilith.strand.backend.db.SessionMembers
import re.lilith.strand.backend.db.Sessions
import re.lilith.strand.backend.db.Users
import re.lilith.strand.backend.model.RedeemResponse
import re.lilith.strand.backend.model.SessionResponse
import re.lilith.strand.backend.util.Ids
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class ActiveSession(val id: UUID, val socketName: String, val hostProductUserId: String)

data class VoiceSession(
    val id: UUID,
    val hostId: UUID,
    val hostProductUserId: String,
    val socketName: String,
    val voiceRoomId: String,
    val voiceEnabled: Boolean,
    val proxNear: Int,
    val proxMax: Int,
)

data class SessionMember(val userId: UUID, val username: String, val productUserId: String?)

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
            it[voiceRoomId] = Ids.socketToken(24)
            it[voiceEnabled] = true
            it[createdAt] = now()
            it[expiresAt] = now().plus(config.sessionTtl)
        }
        addMemberTx(sessionId, hostId)
        audit(hostId, null, sessionId, "SESSION_OPEN", null)
        SessionResponse(sessionId.toString(), code, socketName, hostPuid)
    }

    private fun addMemberTx(sessionId: UUID, userId: UUID) {
        SessionMembers.insertIgnore {
            it[SessionMembers.sessionId] = sessionId
            it[SessionMembers.userId] = userId
            it[joinedAt] = now()
        }
    }

    private fun voiceSessionRow(row: org.jetbrains.exposed.sql.ResultRow) = VoiceSession(
        id = row[Sessions.id].value,
        hostId = row[Sessions.hostId],
        hostProductUserId = row[Sessions.hostProductUserId],
        socketName = row[Sessions.socketName],
        voiceRoomId = row[Sessions.voiceRoomId],
        voiceEnabled = row[Sessions.voiceEnabled],
        proxNear = row[Sessions.proxNear],
        proxMax = row[Sessions.proxMax],
    )

    fun voiceSessionBySocket(socketName: String): VoiceSession? = transaction {
        Sessions.selectAll()
            .where { (Sessions.socketName eq socketName) and (Sessions.active eq true) }
            .firstOrNull()
            ?.takeIf { it[Sessions.expiresAt].isAfter(now()) }
            ?.let(::voiceSessionRow)
    }

    fun voiceSession(sessionId: UUID): VoiceSession? = transaction {
        Sessions.selectAll()
            .where { (Sessions.id eq sessionId) and (Sessions.active eq true) }
            .firstOrNull()
            ?.let(::voiceSessionRow)
    }

    fun removeMemberByPuid(sessionId: UUID, puid: String) = transaction {
        val userId = Users.selectAll().where { Users.productUserId eq puid }
            .firstOrNull()?.get(Users.id)?.value ?: return@transaction
        SessionMembers.deleteWhere { (SessionMembers.sessionId eq sessionId) and (SessionMembers.userId eq userId) }
    }

    fun isMember(sessionId: UUID, userId: UUID): Boolean = transaction {
        SessionMembers.selectAll()
            .where { (SessionMembers.sessionId eq sessionId) and (SessionMembers.userId eq userId) }
            .any()
    }

    fun members(sessionId: UUID): List<SessionMember> = transaction {
        SessionMembers.join(Users, JoinType.INNER, SessionMembers.userId, Users.id)
            .selectAll()
            .where { SessionMembers.sessionId eq sessionId }
            .map { SessionMember(it[SessionMembers.userId], it[Users.username], it[Users.productUserId]) }
    }

    fun setVoiceConfig(hostId: UUID, sessionId: UUID, enabled: Boolean?, near: Int?, max: Int?): VoiceSession? = transaction {
        val updated = Sessions.update({ (Sessions.id eq sessionId) and (Sessions.hostId eq hostId) and (Sessions.active eq true) }) { row ->
            if (enabled != null) row[voiceEnabled] = enabled
            if (near != null) row[proxNear] = near.coerceIn(1, 512)
            if (max != null) row[proxMax] = max.coerceIn(1, 512)
        }
        if (updated == 0) return@transaction null
        Sessions.selectAll().where { Sessions.id eq sessionId }.first().let(::voiceSessionRow)
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
        addMemberTx(row[Sessions.id].value, redeemerId)
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
