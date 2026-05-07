package com.example.ivan.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.BuildConfig
import com.example.ivan.main.ChatMessage
import com.example.ivan.main.MessageDto
import com.example.ivan.main.NetworkClient
import com.example.ivan.main.PinEvent
import com.example.ivan.main.PinnedMessageDto
import com.example.ivan.main.PresenceEvent
import com.example.ivan.main.UserDto
import com.example.ivan.main.WsEnvelope
import com.example.ivan.main.WsManager
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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

    /** Online status of the other user (for DM chats) */
    private val _otherUserOnline = MutableStateFlow(false)
    val otherUserOnline: StateFlow<Boolean> = _otherUserOnline

    /** Currently pinned message (null = nothing pinned) */
    private val _pinnedMessage = MutableStateFlow<PinnedMessageDto?>(null)
    val pinnedMessage: StateFlow<PinnedMessageDto?> = _pinnedMessage

    /** ID of the other user in DM */
    var otherUserId: Int? = null
        private set

    private var wsSession: io.ktor.client.plugins.websocket.ClientWebSocketSession? = null

    init {
        loadHistory()
        loadPinnedMessage()
        connectWebSocket()
        collectStatusUpdates()
        collectPresenceUpdates()
        collectPinUpdates()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val msgs = NetworkClient.getChatMessages(chatId)
                _messages.value = msgs

                // Send read ack for the last message from other users
                val lastFromOther = msgs.lastOrNull { it.senderId != userId }
                if (lastFromOther != null && lastFromOther.id.isNotEmpty()) {
                    WsManager.sendReadAck(chatId, lastFromOther.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPinnedMessage() {
        viewModelScope.launch {
            try {
                _pinnedMessage.value = NetworkClient.getPinnedMessage(chatId)
            } catch (e: Exception) {
                // non-critical
            }
        }
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                NetworkClient.httpClient.webSocket(
                    "ws://${BuildConfig.SERVER_URL}/chat/$userId"
                ) {
                    wsSession = this
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val envelope = Json.decodeFromString<WsEnvelope>(frame.readText())
                            when (envelope.type) {
                                "message" -> {
                                    val msg = Json.decodeFromString<MessageDto>(envelope.payload)
                                    // Only append if it belongs to this chat
                                    if (msg.chatId == chatId) {
                                        _messages.value = _messages.value + msg

                                        // If message is from someone else, send read ack immediately
                                        if (msg.senderId != userId && msg.id.isNotEmpty()) {
                                            WsManager.sendReadAck(chatId, msg.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Collect status updates from WsManager and update message statuses */
    private fun collectStatusUpdates() {
        viewModelScope.launch {
            WsManager.statusUpdates.collect { event ->
                if (event.chatId == chatId) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == event.messageId) {
                            msg.copy(status = event.status)
                        } else msg
                    }
                }
            }
        }
    }

    /** Collect presence updates for the other user in DM */
    private fun collectPresenceUpdates() {
        viewModelScope.launch {
            WsManager.presenceUpdates.collect { event ->
                if (event.userId == otherUserId) {
                    _otherUserOnline.value = event.online
                }
            }
        }
    }

    /** Collect pin updates from WsManager */
    private fun collectPinUpdates() {
        viewModelScope.launch {
            WsManager.pinUpdates.collect { event ->
                if (event.chatId == chatId) {
                    _pinnedMessage.value = event.pinnedMessage
                }
            }
        }
    }

    /** Set the other user ID (for DM chats) and load their online status */
    fun setOtherUser(otherId: Int?) {
        otherUserId = otherId
        if (otherId != null) {
            viewModelScope.launch {
                try {
                    val statuses = NetworkClient.getOnlineStatus(listOf(otherId))
                    _otherUserOnline.value = statuses[otherId] == true
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val msg = ChatMessage(chatId = chatId, text = text)
                wsSession?.send(
                    Frame.Text(Json.encodeToString(ChatMessage.serializer(), msg))
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Pin a message */
    fun pinMessage(messageId: String) {
        viewModelScope.launch {
            try {
                val pinned = NetworkClient.pinMessage(chatId, userId, messageId)
                _pinnedMessage.value = pinned
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Unpin the currently pinned message */
    fun unpinMessage(messageId: String) {
        viewModelScope.launch {
            try {
                NetworkClient.unpinMessage(chatId, userId, messageId)
                _pinnedMessage.value = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Create (or fetch existing) invite link for this chat. */
    fun generateInviteLink() {
        viewModelScope.launch {
            try {
                val token = NetworkClient.createInvite(chatId, userId)
                _inviteToken.value = token
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun searchUsersToInvite(query: String) {
        if (query.isBlank()) {
            _memberSearchResults.value = emptyList()
            return
        }
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
