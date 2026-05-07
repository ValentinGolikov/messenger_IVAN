package com.example

import io.ktor.websocket.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton that holds all active WebSocket connections.
 * Shared between Sockets (reading) and Routing (pin broadcasts).
 */
object ConnectionManager {
    val connections = ConcurrentHashMap<Int, DefaultWebSocketServerSession>()

    /** Broadcast a WsEnvelope to all connected participants of [chatId]. */
    suspend fun broadcastToChat(chatId: Int, envelope: WsEnvelope) {
        val json = Json.encodeToString(WsEnvelope.serializer(), envelope)
        val participantIds = DatabaseFactory.dbQuery {
            ChatParticipants.selectAll()
                .where { ChatParticipants.chatId eq chatId }
                .map { it[ChatParticipants.userId] }
        }
        participantIds.forEach { pid ->
            connections[pid]?.send(Frame.Text(json))
        }
    }
}
