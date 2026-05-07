package com.example.ivan.main

import com.example.ivan.BuildConfig
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Singleton WebSocket manager.
 * Открывает одно соединение на пользователя и раздаёт входящие события
 * всем подписчикам через SharedFlow.
 */
object WsManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Incoming messages
    private val _incoming = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incoming: SharedFlow<MessageDto> = _incoming.asSharedFlow()

    // Status updates (delivered / read)
    private val _statusUpdates = MutableSharedFlow<StatusUpdateEvent>(extraBufferCapacity = 64)
    val statusUpdates: SharedFlow<StatusUpdateEvent> = _statusUpdates.asSharedFlow()

    // Presence events (online / offline)
    private val _presenceUpdates = MutableSharedFlow<PresenceEvent>(extraBufferCapacity = 64)
    val presenceUpdates: SharedFlow<PresenceEvent> = _presenceUpdates.asSharedFlow()

    // Pin events (pin / unpin)
    private val _pinUpdates = MutableSharedFlow<PinEvent>(extraBufferCapacity = 64)
    val pinUpdates: SharedFlow<PinEvent> = _pinUpdates.asSharedFlow()

    // Текущая сессия — нужна для отправки сообщений
    private var session: io.ktor.client.plugins.websocket.ClientWebSocketSession? = null

    private var connectedUserId: Int = -1

    fun connect(userId: Int) {
        if (connectedUserId == userId && session != null) return
        connectedUserId = userId
        scope.launch {
            while (true) {
                try {
                    NetworkClient.httpClient.webSocket(
                        "ws://${BuildConfig.SERVER_URL}/chat/$userId"
                    ) {
                        session = this
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val envelope = runCatching {
                                    Json.decodeFromString<WsEnvelope>(frame.readText())
                                }.getOrNull() ?: continue

                                when (envelope.type) {
                                    "message" -> {
                                        val msg = runCatching {
                                            Json.decodeFromString<MessageDto>(envelope.payload)
                                        }.getOrNull() ?: continue
                                        _incoming.emit(msg)
                                    }
                                    "status_update" -> {
                                        val event = runCatching {
                                            Json.decodeFromString<StatusUpdateEvent>(envelope.payload)
                                        }.getOrNull() ?: continue
                                        _statusUpdates.emit(event)
                                    }
                                    "presence" -> {
                                        val event = runCatching {
                                            Json.decodeFromString<PresenceEvent>(envelope.payload)
                                        }.getOrNull() ?: continue
                                        _presenceUpdates.emit(event)
                                    }
                                    "pin_update" -> {
                                        val event = runCatching {
                                            Json.decodeFromString<PinEvent>(envelope.payload)
                                        }.getOrNull() ?: continue
                                        _pinUpdates.emit(event)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    session = null
                    delay(3_000) // переподключение через 3 сек
                }
            }
        }
    }

    suspend fun send(frame: Frame) {
        session?.send(frame)
    }

    /** Send a read acknowledgment for a chat */
    fun sendReadAck(chatId: Int, lastMessageId: String) {
        scope.launch {
            try {
                val ack = ReadAckRequest(chatId = chatId, lastMessageId = lastMessageId)
                val envelope = WsEnvelope(
                    type = "read_ack",
                    payload = Json.encodeToString(ReadAckRequest.serializer(), ack)
                )
                session?.send(Frame.Text(Json.encodeToString(WsEnvelope.serializer(), envelope)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        session = null
        connectedUserId = -1
    }
}
