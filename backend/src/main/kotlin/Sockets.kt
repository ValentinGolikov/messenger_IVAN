package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.util.concurrent.ConcurrentHashMap

fun Application.configureSockets() {
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

                        val senderName = DatabaseFactory.dbQuery {
                            Messages.insert {
                                it[Messages.senderId] = userId
                                it[Messages.text] = message.text
                                it[Messages.timestamp] = message.timestamp
                            }

                            Users.selectAll()
                                .where { Users.id eq userId }
                                .single()[Users.displayName]
                        }

                        val outgoing = Json.encodeToString(
                            MessageDto.serializer(),
                            MessageDto(
                                senderId = userId,
                                senderName = senderName,
                                text = message.text,
                                timestamp = message.timestamp
                            )
                        )

                        connections.values.forEach { session ->
                            session.send(Frame.Text(outgoing))
                        }
                    }
                }
            } finally {
                connections.remove(userId)
            }
        }
    }
}

