package com.example.ivan.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.main.ChatMessage
import com.example.ivan.main.MessageDto
import com.example.ivan.main.NetworkClient
import com.example.ivan.main.UserDto
import com.example.ivan.main.WsManager
import io.ktor.websocket.Frame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ChatViewModel(
    private val userId: Int,
    val chatId: Int
) : ViewModel() {

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages

    private val _inviteToken = MutableStateFlow<String?>(null)
    val inviteToken: StateFlow<String?> = _inviteToken

    private val _memberSearchResults = MutableStateFlow<List<UserDto>>(emptyList())
    val memberSearchResults: StateFlow<List<UserDto>> = _memberSearchResults

    init {
        loadHistory()
        // WsManager уже подключён из ChatsViewModel; просто слушаем входящие
        listenIncoming()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                _messages.value = NetworkClient.getChatMessages(chatId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenIncoming() {
        viewModelScope.launch {
            WsManager.incoming.collect { msg ->
                if (msg.chatId == chatId) {
                    // Добавляем только если ещё нет (дедупликация)
                    if (_messages.value.none { it.id == msg.id }) {
                        _messages.value = _messages.value + msg
                    }
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val msg = ChatMessage(chatId = chatId, text = text)
                WsManager.send(Frame.Text(Json.encodeToString(ChatMessage.serializer(), msg)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateInviteLink() {
        viewModelScope.launch {
            try {
                _inviteToken.value = NetworkClient.createInvite(chatId, userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchUsersToInvite(query: String) {
        if (query.isBlank()) { _memberSearchResults.value = emptyList(); return }
        viewModelScope.launch {
            try {
                _memberSearchResults.value = NetworkClient.searchUsers(query, userId)
            } catch (e: Exception) {
                _memberSearchResults.value = emptyList()
            }
        }
    }

    fun inviteUser(targetId: Int, onResult: (method: String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = NetworkClient.inviteUserToChat(chatId, userId, targetId)
                onResult(result["method"] ?: "unknown")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    class Factory(private val userId: Int, private val chatId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(userId, chatId) as T
        }
    }
}
