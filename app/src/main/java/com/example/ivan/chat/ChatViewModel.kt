package com.example.ivan.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.BuildConfig
import com.example.ivan.main.ChatMessage
import com.example.ivan.main.MemberDto
import com.example.ivan.main.MessageDto
import com.example.ivan.main.NetworkClient
import com.example.ivan.main.PinEvent
import com.example.ivan.main.PinnedMessageDto
import com.example.ivan.main.PresenceEvent
import com.example.ivan.main.UserDto
import com.example.ivan.main.UserPresenceDto
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

    /** Last seen timestamp for the other user in DM */
    private val _otherUserLastSeen = MutableStateFlow<Long?>(null)
    val otherUserLastSeen: StateFlow<Long?> = _otherUserLastSeen

    /** All pinned messages (sorted by pinnedAt DESC) */
    private val _pinnedMessages = MutableStateFlow<List<PinnedMessageDto>>(emptyList())
    val pinnedMessages: StateFlow<List<PinnedMessageDto>> = _pinnedMessages

    /** Group members list */
    private val _members = MutableStateFlow<List<MemberDto>>(emptyList())
    val members: StateFlow<List<MemberDto>> = _members

    /** Current user's role in this chat */
    private val _myRole = MutableStateFlow("member")
    val myRole: StateFlow<String> = _myRole

    /** ID of the other user in DM */
    var otherUserId: Int? = null
        private set

    init {
        loadHistory()
        loadPinnedMessages()
        collectIncomingMessages()
        collectStatusUpdates()
        collectPresenceUpdates()
        collectPinUpdates()
        collectMessageDeleted()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val msgs = NetworkClient.getChatMessages(chatId, userId)
                _messages.value = msgs

                // Send read ack for all messages from other users
                val lastFromOther = msgs.lastOrNull { it.senderId != userId }
                if (lastFromOther != null && lastFromOther.id.isNotEmpty()) {
                    WsManager.sendReadAck(chatId, lastFromOther.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadPinnedMessages() {
        viewModelScope.launch {
            try {
                _pinnedMessages.value = NetworkClient.getPinnedMessages(chatId)
            } catch (e: Exception) {
                // non-critical
            }
        }
    }

    fun loadMembers() {
        viewModelScope.launch {
            try {
                val memberList = NetworkClient.getMembers(chatId)
                _members.value = memberList
                _myRole.value = memberList.find { it.id == userId }?.role ?: "member"
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun collectIncomingMessages() {
        viewModelScope.launch {
            WsManager.incoming.collect { msg ->
                // Only append if it belongs to this chat
                if (msg.chatId == chatId) {
                    // Check if message is already in list (could happen if loadHistory returns it right as WS sends it)
                    val exists = _messages.value.any { it.id == msg.id }
                    if (!exists) {
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

    /** Collect status updates from WsManager and update message statuses */
    private fun collectStatusUpdates() {
        viewModelScope.launch {
            WsManager.statusUpdates.collect { event ->
                if (event.chatId == chatId) {
                    val targetMsg = _messages.value.find { it.id == event.messageId }
                    if (targetMsg != null) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == event.messageId) {
                                msg.copy(status = event.status)
                            } else if (event.status == "read" && msg.senderId == event.senderId && msg.timestamp <= targetMsg.timestamp) {
                                msg.copy(status = "read")
                            } else {
                                msg
                            }
                        }
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
                    if (!event.online && event.lastSeen != null) {
                        _otherUserLastSeen.value = event.lastSeen
                    }
                }
            }
        }
    }

    /** Collect pin updates from WsManager */
    private fun collectPinUpdates() {
        viewModelScope.launch {
            WsManager.pinUpdates.collect { event ->
                if (event.chatId == chatId) {
                    when (event.action) {
                        "pin" -> {
                            event.pinnedMessage?.let { pinned ->
                                // Add to list (remove if already present, then prepend)
                                _pinnedMessages.value = listOf(pinned) +
                                    _pinnedMessages.value.filter { it.messageId != pinned.messageId }
                            }
                        }
                        "unpin" -> {
                            val msgId = event.pinnedMessage?.messageId
                            if (msgId != null) {
                                _pinnedMessages.value = _pinnedMessages.value.filter { it.messageId != msgId }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Collect message deleted events */
    private fun collectMessageDeleted() {
        viewModelScope.launch {
            WsManager.messageDeleted.collect { event ->
                if (event.chatId == chatId) {
                    _messages.value = _messages.value.filter { it.id != event.messageId }
                }
            }
        }
    }

    /** Set the other user ID (for DM chats) and load their online status + lastSeen */
    fun setOtherUser(otherId: Int?) {
        otherUserId = otherId
        if (otherId != null) {
            viewModelScope.launch {
                try {
                    val presenceMap = NetworkClient.getUserPresence(listOf(otherId))
                    val presence = presenceMap[otherId]
                    _otherUserOnline.value = presence?.online == true
                    _otherUserLastSeen.value = presence?.lastSeen
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
                WsManager.send(Frame.Text(Json.encodeToString(ChatMessage.serializer(), msg)))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Delete a message */
    fun deleteMessage(messageId: String, forAll: Boolean) {
        viewModelScope.launch {
            try {
                NetworkClient.deleteMessage(chatId, messageId, userId, forAll)
                if (!forAll) {
                    // Remove locally for "delete for self"
                    _messages.value = _messages.value.filter { it.id != messageId }
                }
                // "for all" will be handled by WS event
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
                // WS event will update the list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Unpin a specific message */
    fun unpinMessage(messageId: String) {
        viewModelScope.launch {
            try {
                NetworkClient.unpinMessage(chatId, userId, messageId)
                // WS event will update the list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Leave group */
    fun leaveGroup(newOwnerId: Int? = null, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                NetworkClient.leaveGroup(chatId, userId, newOwnerId)
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Delete group (owner only) */
    fun deleteGroup(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                NetworkClient.deleteGroup(chatId, userId)
                onDone()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Kick a member */
    fun kickMember(targetId: Int) {
        viewModelScope.launch {
            try {
                NetworkClient.kickMember(chatId, userId, targetId)
                loadMembers()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Transfer ownership */
    fun transferOwner(targetId: Int) {
        viewModelScope.launch {
            try {
                NetworkClient.transferOwner(chatId, userId, targetId)
                loadMembers()
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
