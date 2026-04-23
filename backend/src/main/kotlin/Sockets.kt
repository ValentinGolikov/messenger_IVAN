package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket endpoint: /chat/{userId}
 *
 * Single connection per user. Messages are dispatched to all participants
 * of the target chat.
 *
 * Client sends:  ChatMessage  { chatId, text, timestamp }
 * Server pushes: WsEnvelope   { type: "message", payload: <MessageDto JSON> }
 */
fun Application.configureSockets() {
    // userId → active WebSocket session
    val connections = ConcurrentHashMap<Int, DefaultWebSocketServerSession>()

    routing {
        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"]?.toInt() ?: return@webSocket
            connections[userId] = this

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = Json.decodeFromString<ChatMessage>(text)

                        // Verify user is a participant of the chat
                        val isMember = DatabaseFactory.dbQuery {
                            ChatParticipants.selectAll()
                                .where {
                                    (ChatParticipants.chatId eq message.chatId) and
                                            (ChatParticipants.userId eq userId)
                                }.count() > 0
                        }
                        if (!isMember) continue

                        // Persist and fetch sender name
                        val (msgId, senderName) = DatabaseFactory.dbQuery {
                            val insertedId = Messages.insert {
                                it[chatId] = message.chatId
                                it[senderId] = userId
                                it[Messages.text] = message.text
                                it[timestamp] = message.timestamp
                            }[Messages.id]

                            val name = Users.selectAll()
                                .where { Users.id eq userId }
                                .single()[Users.displayName]

                            Pair(insertedId, name)
                        }

                        val msgDto = MessageDto(
                            id = msgId,
                            chatId = message.chatId,
                            senderId = userId,
                            senderName = senderName,
                            text = message.text,
                            timestamp = message.timestamp
                        )

                        val envelope = Json.encodeToString(
                            WsEnvelope.serializer(),
                            WsEnvelope(
                                type = "message",
                                payload = Json.encodeToString(MessageDto.serializer(), msgDto)
                            )
                        )

                        // Find all participants of this chat and push to connected ones
                        val participantIds = DatabaseFactory.dbQuery {
                            ChatParticipants.selectAll()
                                .where { ChatParticipants.chatId eq message.chatId }
                                .map { it[ChatParticipants.userId] }
                        }

                        participantIds.forEach { pid ->
                            connections[pid]?.send(Frame.Text(envelope))
                        }
                    }
                }
            } finally {
                connections.remove(userId)
            }
        }
    }
}
