package com.example

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

        try {
            Database.connect(
                url = dbUrl,
                driver = "org.postgresql.Driver",
                user = dbUser,
                password = dbPassword,
            )
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
