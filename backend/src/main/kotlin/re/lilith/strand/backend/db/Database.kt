package re.lilith.strand.backend.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import re.lilith.strand.backend.Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object Db {
    fun connect(config: Config): Database {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.dbPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        val database = Database.connect(HikariDataSource(hikari))
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(
                Users, AuthChallenges, AuthCodes, Sessions, SessionMembers, Invites, InviteAudit, SigningKeys,
            )
        }
        return database
    }
}
