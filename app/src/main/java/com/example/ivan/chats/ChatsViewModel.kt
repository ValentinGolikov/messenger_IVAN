package com.example.ivan.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ivan.main.ChatDto
import com.example.ivan.main.NetworkClient
import com.example.ivan.main.UserDto
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

    init {
        loadChats()
        loadContacts()
        collectPresenceUpdates()
    }

    fun loadChats() {
        viewModelScope.launch {
            _uiState.value = ChatsUiState.Loading
            try {
                val chats = NetworkClient.getChats(userId)
                _uiState.value = ChatsUiState.Success(chats)

                // Load online statuses for DM chat partners
                val dmPartnerIds = chats
                    .filter { it.type == "dm" }
                    .mapNotNull { it.otherUserId }
                if (dmPartnerIds.isNotEmpty()) {
                    try {
                        val statuses = NetworkClient.getOnlineStatus(dmPartnerIds)
                        _onlineStatuses.value = statuses
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

    class Factory(private val userId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return ChatsViewModel(userId) as T
        }
    }
}
