package re.lilith.strand.backend.db

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object Users : UUIDTable("users") {
    val username = varchar("username", 32).index()
    val productUserId = varchar("product_user_id", 64).nullable().uniqueIndex()
    val invitesBlocked = bool("invites_blocked").default(false)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")
}

object AuthChallenges : Table("auth_challenges") {
    val serverId = varchar("server_id", 64)
    val userId = uuid("user_id")
    val username = varchar("username", 32)
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(serverId)
}

object AuthCodes : Table("auth_codes") {
    val code = varchar("code", 64)
    val userId = uuid("user_id")
    val username = varchar("username", 32)
    val clientId = varchar("client_id", 64)
    val redirectUri = varchar("redirect_uri", 256)
    val codeChallenge = varchar("code_challenge", 128)
    val nonce = varchar("nonce", 64).nullable()
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(code)
}

object Sessions : UUIDTable("sessions") {
    val hostId = uuid("host_id").index()
    val hostProductUserId = varchar("host_product_user_id", 64)
    val socketName = varchar("socket_name", 32)
    val inviteCode = varchar("invite_code", 12).uniqueIndex()
    val active = bool("active").default(true)
    val voiceRoomId = varchar("voice_room_id", 64).default("")
    val voiceEnabled = bool("voice_enabled").default(true)
    val proxNear = integer("prox_near").default(8)
    val proxMax = integer("prox_max").default(48)
    val createdAt = datetime("created_at")
    val expiresAt = datetime("expires_at")
}

object SessionMembers : Table("session_members") {
    val sessionId = uuid("session_id").index()
    val userId = uuid("user_id")
    val joinedAt = datetime("joined_at")

    override val primaryKey = PrimaryKey(sessionId, userId)
}

object Invites : UUIDTable("invites") {
    val fromId = uuid("from_id").index()
    val fromPuid = varchar("from_puid", 64)
    val fromName = varchar("from_name", 32)
    val toId = uuid("to_id").index()
    val sessionId = uuid("session_id").nullable()
    val socketName = varchar("socket_name", 32)
    val status = varchar("status", 16).default("PENDING")
    val createdAt = datetime("created_at")
    val expiresAt = datetime("expires_at")
}

object InviteAudit : UUIDTable("invite_audit") {
    val fromId = uuid("from_id").index()
    val toId = uuid("to_id").nullable()
    val sessionId = uuid("session_id").nullable()
    val action = varchar("action", 24)
    val detail = varchar("detail", 128).nullable()
    val createdAt = datetime("created_at")
}

object SigningKeys : Table("signing_keys") {
    val kid = varchar("kid", 64)
    val jwkJson = text("jwk_json")
    val active = bool("active").default(true)
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(kid)
}
