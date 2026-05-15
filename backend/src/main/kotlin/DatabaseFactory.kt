package com.example

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    fun init() {
        val dbUser = env("DB_USER") ?: env("POSTGRES_USER") ?: "postgres"
        val dbPassword = env("DB_PASSWORD") ?: env("POSTGRES_PASSWORD") ?: "postgres"
        val dbUrl = env("DB_URL") ?: run {
            val host = env("DB_HOST") ?: "localhost"
            val port = env("DB_PORT") ?: "5432"
            val dbName = env("DB_NAME") ?: env("POSTGRES_DB") ?: "postgres"
            "jdbc:postgresql://$host:$port/$dbName"
        }

        // Max pool size: increased for high load (10K+ concurrent users)
        // Rule of thumb: (core_count * 2) + effective_spindle_count
        // For load testing with 10K users, we need more connections
        val maxPoolSize = env("DB_POOL_SIZE")?.toIntOrNull() ?: 100

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = maxPoolSize
            minimumIdle = 5
            idleTimeout = 60_000          // 60s idle before closing
            connectionTimeout = 10_000    // 10s wait for a connection from pool
            maxLifetime = 1_800_000       // 30min max connection lifetime
            poolName = "MessengerHikariPool"
            isAutoCommit = false
        }

        try {
            val dataSource = HikariDataSource(hikariConfig)
            Database.connect(dataSource)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to configure PostgreSQL connection. DB_URL=$dbUrl, DB_USER=$dbUser", e)
        }

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Users,
                Contacts,
                Chats,
                ChatParticipants,
                ChatInvites,
                PinnedMessages,
                DeletedMessagesPerUser
            )
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
