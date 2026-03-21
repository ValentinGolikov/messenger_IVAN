package com.example

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val dbUser = System.getenv("DB_USER") ?: "default_user"
        val dbPass = System.getenv("DB_PASSWORD") ?: "default_pass"

        Database.connect(
            url = "jdbc:postgresql://localhost:5432/PostgresLearning",
            driver = "org.postgresql.Driver",
            user = dbUser,
            password = dbPass
        )
        transaction { SchemaUtils.create(Users, Messages) }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}