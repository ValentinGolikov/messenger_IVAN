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
 * Открывает одно соединение на пользователя и раздаёт входящие сообщения
 * всем подписчикам через SharedFlow.
 */
object WsManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incoming = MutableSharedFlow<MessageDto>(extraBufferCapacity = 64)
    val incoming: SharedFlow<MessageDto> = _incoming.asSharedFlow()

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

                                if (envelope.type == "message") {
                                    val msg = runCatching {
                                        Json.decodeFromString<MessageDto>(envelope.payload)
                                    }.getOrNull() ?: continue
                                    _incoming.emit(msg)
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

    fun disconnect() {
        session = null
        connectedUserId = -1
    }
}
