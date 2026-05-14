package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.upsert

/**
 * Test-only routes for seeding data in staging/load-test environments.
 *
 * IMPORTANT: These routes are only registered when the ENABLE_TEST_ROUTES
 * environment variable is set to "true". Never set this in production.
 *
 * Provides:
 *  POST /test/seed/user  — create or upsert a test user (bypasses Yandex OAuth)
 *  GET  /test/seed/user  — get userId by yandexId
 *  DELETE /test/seed/all — wipe all test data (users with yandexId starting with "test_")
 */
fun Application.configureTestRoutes() {
    val enabled = System.getenv("ENABLE_TEST_ROUTES")?.lowercase() == "true"
    if (!enabled) {
        log.info("Test routes are DISABLED (set ENABLE_TEST_ROUTES=true to enable in staging)")
        return
    }

    log.warn("⚠️  Test routes are ENABLED — do NOT use in production!")

    routing {

        /** Create or update a test user, returns { userId } */
        post("/test/seed/user") {
            val body = call.receive<SeedUserRequest>()

            val userId = DatabaseFactory.dbQuery {
                Users.upsert(Users.yandexId) {
                    it[yandexId]     = body.yandexId
                    it[displayName]  = body.displayName
                    it[realName]     = body.realName
                    it[email]        = body.email
                }[Users.id]
            }

            call.respond(mapOf("userId" to userId))
        }

        /** Get userId by yandexId */
        get("/test/seed/user") {
            val yandexId = call.request.queryParameters["yandexId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing yandexId")

            val userId = DatabaseFactory.dbQuery {
                Users.selectAll()
                    .where { Users.yandexId eq yandexId }
                    .singleOrNull()
                    ?.get(Users.id)
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(mapOf("userId" to userId))
        }

        /** Delete all test users (yandexId starts with "test_") and their data */
        delete("/test/seed/all") {
            val deleted = DatabaseFactory.dbQuery {
                val testUserIds = Users.selectAll()
                    .where { Users.yandexId like "test_%" }
                    .map { it[Users.id] }

                if (testUserIds.isEmpty()) return@dbQuery 0

                // Cascade deletes handle contacts, chat_participants, etc.
                // But we need to remove chats created by test users explicitly
                val testChatIds = Chats.selectAll()
                    .where { Chats.createdBy inList testUserIds }
                    .map { it[Chats.id] }

                if (testChatIds.isNotEmpty()) {
                    testChatIds.forEach { chatId ->
                        CassandraFactory.deleteAllChatMessages(chatId)
                    }
                    PinnedMessages.deleteWhere { PinnedMessages.chatId inList testChatIds }
                    DeletedMessagesPerUser.deleteWhere { DeletedMessagesPerUser.chatId inList testChatIds }
                    ChatInvites.deleteWhere { ChatInvites.chatId inList testChatIds }
                    ChatParticipants.deleteWhere { ChatParticipants.chatId inList testChatIds }
                    Chats.deleteWhere { Chats.id inList testChatIds }
                }

                Users.deleteWhere { Users.yandexId like "test_%" }
            }

            call.respond(mapOf("deleted" to deleted))
        }
    }
}

@Serializable
data class SeedUserRequest(
    val yandexId: String,
    val displayName: String,
    val realName: String? = null,
    val email: String? = null,
)
