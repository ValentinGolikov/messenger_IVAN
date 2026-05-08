package com.example.ivan.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.main.ChatDto
import com.example.ivan.main.MessageDeletedEvent
import com.example.ivan.main.NetworkClient
import com.example.ivan.main.UserDto
import com.example.ivan.main.UserPresenceDto
import com.example.ivan.main.WsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ChatsUiState {
    object Loading : ChatsUiState()
    data class Success(val chats: List<ChatDto>) : ChatsUiState()
    data class Error(val message: String) : ChatsUiState()
}

class ChatsViewModel(private val userId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatsUiState>(ChatsUiState.Loading)
    val uiState: StateFlow<ChatsUiState> = _uiState

    private val _searchResults = MutableStateFlow<List<UserDto>>(emptyList())
    val searchResults: StateFlow<List<UserDto>> = _searchResults

    private val _contacts = MutableStateFlow<List<UserDto>>(emptyList())
    val contacts: StateFlow<List<UserDto>> = _contacts

    /** Map of userId → online status */
    private val _onlineStatuses = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val onlineStatuses: StateFlow<Map<Int, Boolean>> = _onlineStatuses

    /** Map of userId → lastSeen timestamp */
    private val _lastSeenMap = MutableStateFlow<Map<Int, Long?>>(emptyMap())
    val lastSeenMap: StateFlow<Map<Int, Long?>> = _lastSeenMap

    private fun updateChats(chats: List<ChatDto>) {
        val sortedChats = chats.sortedWith(
            compareByDescending<ChatDto> { it.unreadCount > 0 }
                .thenByDescending { it.lastMessage?.timestamp ?: 0L }
        )
        _uiState.value = ChatsUiState.Success(sortedChats)
    }

    init {
        loadChats()
        loadContacts()
        collectPresenceUpdates()
        collectChatRemoved()
        collectIncomingMessages()
        collectMessageDeleted()
        collectStatusUpdates()
        collectLocalReadEvents()
    }

    fun loadChats() {
        viewModelScope.launch {
            _uiState.value = ChatsUiState.Loading
            try {
                val chats = NetworkClient.getChats(userId)
                updateChats(chats)

                // Load presence (online + lastSeen) for DM chat partners
                val dmPartnerIds = chats
                    .filter { it.type == "dm" }
                    .mapNotNull { it.otherUserId }
                if (dmPartnerIds.isNotEmpty()) {
                    try {
                        val presenceMap = NetworkClient.getUserPresence(dmPartnerIds)
                        _onlineStatuses.value = presenceMap.mapValues { it.value.online }
                        _lastSeenMap.value = presenceMap.mapValues { it.value.lastSeen }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                _uiState.value = ChatsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun loadContacts() {
        viewModelScope.launch {
            try {
                _contacts.value = NetworkClient.getContacts(userId)
            } catch (e: Exception) {
                // non-critical
            }
        }
    }

    /** Collect real-time presence updates from WebSocket */
    private fun collectPresenceUpdates() {
        viewModelScope.launch {
            WsManager.presenceUpdates.collect { event ->
                _onlineStatuses.value = _onlineStatuses.value + (event.userId to event.online)
                if (!event.online && event.lastSeen != null) {
                    _lastSeenMap.value = _lastSeenMap.value + (event.userId to event.lastSeen)
                }
            }
        }
    }

    /** Collect chat removed events (DM deleted for both / group deleted / kicked) */
    private fun collectChatRemoved() {
        viewModelScope.launch {
            WsManager.chatRemoved.collect { event ->
                val current = _uiState.value
                if (current is ChatsUiState.Success) {
                    updateChats(current.chats.filter { it.id != event.chatId })
                }
            }
        }
    }

    /**
     * Collect incoming messages from WsManager.
     * Updates lastMessage and unreadCount for the relevant chat in real time.
     */
    private fun collectIncomingMessages() {
        viewModelScope.launch {
            WsManager.incoming.collect { msg ->
                val current = _uiState.value as? ChatsUiState.Success ?: return@collect
                val updatedChats = current.chats.map { chat ->
                    if (chat.id != msg.chatId) return@map chat
                    val newUnread = if (msg.senderId != userId) chat.unreadCount + 1
                                   else chat.unreadCount
                    chat.copy(
                        lastMessage = msg,
                        unreadCount = newUnread
                    )
                }
                updateChats(updatedChats)
            }
        }
    }

    /**
     * Collect message deleted events.
     * If the deleted message was the last one, reload the chat list to get the new last message.
     */
    private fun collectMessageDeleted() {
        viewModelScope.launch {
            WsManager.messageDeleted.collect { event ->
                val current = _uiState.value as? ChatsUiState.Success ?: return@collect
                val chat = current.chats.find { it.id == event.chatId } ?: return@collect
                // If deleted message was the last one — reload to get correct lastMessage
                if (chat.lastMessage?.id == event.messageId) {
                    loadChats()
                }
            }
        }
    }

    /**
     * Collect status update events (read/delivered).
     * Updates unreadCount and last message status.
     */
    private fun collectStatusUpdates() {
        viewModelScope.launch {
            WsManager.statusUpdates.collect { event ->
                val current = _uiState.value as? ChatsUiState.Success ?: return@collect
                val chat = current.chats.find { it.id == event.chatId } ?: return@collect

                val updatedLastMessage = if (chat.lastMessage?.id == event.messageId) {
                    chat.lastMessage.copy(status = event.status)
                } else if (event.status == "read" && chat.lastMessage?.senderId == event.senderId) {
                    chat.lastMessage.copy(status = "read")
                } else {
                    chat.lastMessage
                }

                // If the read message is from someone else (not me), reset unreadCount
                val newUnread = if (event.status == "read" && event.senderId != userId) {
                    0
                } else {
                    chat.unreadCount
                }

                val updatedChats = current.chats.map { c ->
                    if (c.id == chat.id) c.copy(unreadCount = newUnread, lastMessage = updatedLastMessage) else c
                }
                updateChats(updatedChats)
            }
        }
    }

    /**
     * Clear unread count locally when we send a read acknowledgment.
     * This ensures the counter updates instantly even if the server doesn't echo a status_update back to the reader.
     */
    private fun collectLocalReadEvents() {
        viewModelScope.launch {
            WsManager.chatReadLocally.collect { chatId ->
                val current = _uiState.value as? ChatsUiState.Success ?: return@collect
                val updatedChats = current.chats.map { chat ->
                    if (chat.id == chatId) chat.copy(unreadCount = 0) else chat
                }
                updateChats(updatedChats)
            }
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _searchResults.value = NetworkClient.searchUsers(query, userId)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    fun addContact(contactId: Int) {
        viewModelScope.launch {
            try {
                NetworkClient.addContact(userId, contactId)
                loadContacts()
                // Re-run search to update isMutualContact flags
                _searchResults.value = _searchResults.value.map {
                    if (it.id == contactId) it.copy(isMutualContact = false) // will be mutual once they add back
                    else it
                }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    /**
     * Opens (or creates) a DM with [otherUserId].
     * Returns the chatId via [onReady].
     */
    fun openDm(otherUserId: Int, onReady: (chatId: Int) -> Unit) {
        viewModelScope.launch {
            try {
                val chatId = NetworkClient.openDm(userId, otherUserId)
                loadChats()
                onReady(chatId)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun createGroup(title: String, onReady: (chatId: Int, inviteToken: String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = NetworkClient.createGroup(userId, title)
                loadChats()
                onReady(resp.chatId, resp.inviteToken)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    /** Delete a DM chat */
    fun deleteChat(chatId: Int, forAll: Boolean) {
        viewModelScope.launch {
            try {
                NetworkClient.deleteChat(chatId, userId, forAll)
                // Remove from local list
                val current = _uiState.value
                if (current is ChatsUiState.Success) {
                    updateChats(current.chats.filter { it.id != chatId })
                }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    class Factory(private val userId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatsViewModel(userId) as T
        }
    }
}
