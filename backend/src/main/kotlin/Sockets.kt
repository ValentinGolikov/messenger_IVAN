package com.example

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import java.util.UUID

/**
 * WebSocket endpoint: /chat/{userId}
 *
 * Single connection per user. Messages are dispatched to all participants
 * of the target chat.
 *
 * Client sends:
 *   ChatMessage   { chatId, text, timestamp }
 *   ReadAckRequest (wrapped in WsEnvelope with type="read_ack")
 *
 * Server pushes:
 *   WsEnvelope { type: "message",       payload: <MessageDto JSON> }
 *   WsEnvelope { type: "status_update", payload: <StatusUpdateEvent JSON> }
 *   WsEnvelope { type: "presence",      payload: <PresenceEvent JSON> }
 *   WsEnvelope { type: "pin_update",    payload: <PinEvent JSON> }
 */
fun Application.configureSockets() {
    val connections = ConnectionManager.connections

    routing {
        webSocket("/chat/{userId}") {
            val userId = call.parameters["userId"]?.toInt() ?: return@webSocket
            connections[userId] = this

            // ── Online: mark in Redis and notify contacts ──
            RedisFactory.setOnline(userId)
            broadcastPresence(connections, userId, online = true)

            // Heartbeat coroutine: refresh Redis TTL every 30s
            val heartbeatJob = launch {
                while (isActive) {
                    delay(30_000)
                    RedisFactory.heartbeat(userId)
                }
            }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()

                        // Try to parse as WsEnvelope first (for read_ack and other typed messages)
                        val envelope = runCatching {
                            Json.decodeFromString<WsEnvelope>(text)
                        }.getOrNull()

                        if (envelope != null) {
                            if (envelope.type == "read_ack") {
                                handleReadAck(envelope.payload, userId, connections)
                                continue
                            }
                            if (envelope.type == "typing") {
                                handleTyping(envelope.payload, userId)
                                continue
                            }
                        }

                        // Otherwise treat as ChatMessage
                        val message = runCatching {
                            Json.decodeFromString<ChatMessage>(text)
                        }.getOrNull() ?: continue

                        // Verify user is a participant of the chat
                        val isMember = DatabaseFactory.dbQuery {
                            ChatParticipants.selectAll()
                                .where {
                                    (ChatParticipants.chatId eq message.chatId) and
                                            (ChatParticipants.userId eq userId)
                                }.count() > 0
                        }
                        if (!isMember) continue

                        // Persist in Cassandra
                        val msgId = CassandraFactory.insertMessage(
                            chatId = message.chatId,
                            senderId = userId,
                            text = message.text,
                            timestamp = message.timestamp,
                            status = "sent"
                        )

                        // Fetch sender name
                        val senderName = DatabaseFactory.dbQuery {
                            Users.selectAll()
                                .where { Users.id eq userId }
                                .single()[Users.displayName]
                        }

                        val msgDto = MessageDto(
                            id = msgId.toString(),
                            chatId = message.chatId,
                            senderId = userId,
                            senderName = senderName,
                            text = message.text,
                            timestamp = message.timestamp,
                            status = "sent"
                        )

                        val msgEnvelope = Json.encodeToString(
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
                            val session = connections[pid]
                            if (session != null) {
                                session.send(Frame.Text(msgEnvelope))

                                // If recipient (not sender) received it → mark as delivered
                                if (pid != userId) {
                                    CassandraFactory.updateMessageStatus(message.chatId, msgId, "delivered")

                                    // Notify the sender about delivery
                                    val statusEvent = StatusUpdateEvent(
                                        messageId = msgId.toString(),
                                        chatId = message.chatId,
                                        senderId = userId,
                                        status = "delivered"
                                    )
                                    val statusEnvelope = Json.encodeToString(
                                        WsEnvelope.serializer(),
                                        WsEnvelope(
                                            type = "status_update",
                                            payload = Json.encodeToString(StatusUpdateEvent.serializer(), statusEvent)
                                        )
                                    )
                                    connections[userId]?.send(Frame.Text(statusEnvelope))
                                }
                            }
                        }

                        // Invalidate chat list cache for all participants
                        // so next GET /chats/{userId} returns fresh data
                        RedisFactory.invalidateChatListCacheForUsers(participantIds)
                    }
                }
            } finally {
                heartbeatJob.cancel()
                connections.remove(userId)
                val lastSeenTs = System.currentTimeMillis()
                RedisFactory.setOffline(userId)
                RedisFactory.setLastSeen(userId, lastSeenTs)
                DatabaseFactory.dbQuery {
                    Users.update({ Users.id eq userId }) {
                        it[lastSeenAt] = lastSeenTs
                    }
                }
                broadcastPresence(connections, userId, online = false, lastSeenTs = lastSeenTs)
            }
        }
    }
}

private suspend fun handleTyping(payload: String, userId: Int) {
    val event = runCatching { Json.decodeFromString<TypingEvent>(payload) }.getOrNull() ?: return
    // Ignore spoofed userId in payload; trust socket user
    val safeEvent = TypingEvent(chatId = event.chatId, userId = userId, typing = event.typing)
    val envelope = WsEnvelope(
        type = "typing",
        payload = Json.encodeToString(TypingEvent.serializer(), safeEvent)
    )
    ConnectionManager.broadcastToChat(event.chatId, envelope)
}

/**
 * Handle a read_ack from a client: mark messages as read in Cassandra
 * and notify the original senders.
 */
private suspend fun handleReadAck(
    payload: String,
    readerUserId: Int,
    connections: java.util.concurrent.ConcurrentHashMap<Int, DefaultWebSocketServerSession>
) {
    val ack = runCatching {
        Json.decodeFromString<ReadAckRequest>(payload)
    }.getOrNull() ?: return

    val lastMsgUuid = runCatching { UUID.fromString(ack.lastMessageId) }.getOrNull() ?: return

    // Mark messages as read in Cassandra and get (messageId, senderId) pairs
    val updated = CassandraFactory.markMessagesAsRead(ack.chatId, lastMsgUuid, readerUserId)

    // Notify each sender that their message was read
    for ((messageId, senderId) in updated) {
        val statusEvent = StatusUpdateEvent(
            messageId = messageId.toString(),
            chatId = ack.chatId,
            senderId = senderId,
            status = "read"
        )
        val envelope = Json.encodeToString(
            WsEnvelope.serializer(),
            WsEnvelope(
                type = "status_update",
                payload = Json.encodeToString(StatusUpdateEvent.serializer(), statusEvent)
            )
        )
        connections[senderId]?.send(Frame.Text(envelope))
    }
}

/**
 * Broadcast presence (online/offline) to all contacts of the user
 * who are currently connected.
 */
private suspend fun broadcastPresence(
    connections: java.util.concurrent.ConcurrentHashMap<Int, DefaultWebSocketServerSession>,
    userId: Int,
    online: Boolean,
    lastSeenTs: Long? = null
) {
    val event = PresenceEvent(userId = userId, online = online, lastSeen = if (!online) lastSeenTs else null)
    val envelope = Json.encodeToString(
        WsEnvelope.serializer(),
        WsEnvelope(
            type = "presence",
            payload = Json.encodeToString(PresenceEvent.serializer(), event)
        )
    )

    // Get all chat participants who share a chat with this user
    val relatedUserIds = DatabaseFactory.dbQuery {
        val userChatIds = ChatParticipants.selectAll()
            .where { ChatParticipants.userId eq userId }
            .map { it[ChatParticipants.chatId] }

        if (userChatIds.isEmpty()) return@dbQuery emptySet<Int>()

        ChatParticipants.selectAll()
            .where { (ChatParticipants.chatId inList userChatIds) and (ChatParticipants.userId neq userId) }
            .map { it[ChatParticipants.userId] }
            .toSet()
    }

    relatedUserIds.forEach { pid ->
        connections[pid]?.send(Frame.Text(envelope))
    }
}
