package com.example.ivan.chat

import androidx.lifecycle.ViewModel
import com.example.ivan.BuildConfig
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.main.ChatMessage
import com.example.ivan.main.MessageDto
import com.example.ivan.main.NetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ChatViewModel(private val userId: Int) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages

    private var wsSession: io.ktor.client.plugins.websocket.ClientWebSocketSession? = null

    init {
        loadHistory()
        connectWebSocket()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val history: List<MessageDto> = NetworkClient.httpClient
                    .get(NetworkClient.buildUrl("/messages"))
                    .body()
                _messages.value = history
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                val serverUrl = BuildConfig.SERVER_URL
                NetworkClient.httpClient.webSocket("ws://$serverUrl/chat/$userId") {
                    wsSession = this
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val msg = Json.decodeFromString<MessageDto>(frame.readText())
                            _messages.value += msg
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val msg = ChatMessage(senderId = userId, text = text)
                wsSession?.send(Frame.Text(Json.encodeToString(ChatMessage.serializer(), msg)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(private val userId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(userId) as T
        }
    }
}