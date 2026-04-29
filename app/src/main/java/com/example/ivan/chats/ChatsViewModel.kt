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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

sealed class ChatsUiState {
    object Loading : ChatsUiState()
    data class Success(val chats: List<ChatDto>) : ChatsUiState()
    data class Error(val message: String) : ChatsUiState()
}

class ChatsViewModel(private val userId: Int) : ViewModel() {

    // Сырые данные с сервера
    private val _rawChats = MutableStateFlow<List<ChatDto>>(emptyList())

    // Локальные счётчики непрочитанных: chatId -> count
    private val _unread = MutableStateFlow<Map<Int, Int>>(emptyMap())

    // Итоговый UI-стейт: чаты с подставленными локальными счётчиками
    private val _uiState = MutableStateFlow<ChatsUiState>(ChatsUiState.Loading)
    val uiState: StateFlow<ChatsUiState> = _uiState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _searchResults = MutableStateFlow<List<UserDto>>(emptyList())
    val searchResults: StateFlow<List<UserDto>> = _searchResults

    private val _contacts = MutableStateFlow<List<UserDto>>(emptyList())
    val contacts: StateFlow<List<UserDto>> = _contacts

    init {
        loadChats()
        loadContacts()
        WsManager.connect(userId)
        listenIncoming()
        // Объединяем сырые чаты и счётчики в итоговый стейт
        viewModelScope.launch {
            combine(_rawChats, _unread) { chats, unread ->
                if (chats.isEmpty() && _uiState.value is ChatsUiState.Loading) return@combine null
                chats.map { chat ->
                    chat.copy(unreadCount = unread[chat.id] ?: 0)
                }
            }.collect { merged ->
                if (merged != null) {
                    _uiState.value = ChatsUiState.Success(merged)
                }
            }
        }
    }

    private fun listenIncoming() {
        viewModelScope.launch {
            WsManager.incoming.collect { msg ->
                // Обновляем последнее сообщение в списке чатов без запроса к серверу
                val current = _rawChats.value
                val updated = current.map { chat ->
                    if (chat.id == msg.chatId) {
                        chat.copy(
                            lastMessage = com.example.ivan.main.MessageDto(
                                id = msg.id,
                                chatId = msg.chatId,
                                senderId = msg.senderId,
                                senderName = msg.senderName,
                                text = msg.text,
                                timestamp = msg.timestamp
                            )
                        )
                    } else chat
                }
                _rawChats.value = updated

                // Увеличиваем счётчик непрочитанных если сообщение не от нас
                if (msg.senderId != userId) {
                    val cur = _unread.value.toMutableMap()
                    cur[msg.chatId] = (cur[msg.chatId] ?: 0) + 1
                    _unread.value = cur
                }
            }
        }
    }

    fun loadChats() {
        viewModelScope.launch {
            _uiState.value = ChatsUiState.Loading
            try {
                val chats = NetworkClient.getChats(userId)
                _rawChats.value = chats
                // uiState обновится через combine
            } catch (e: Exception) {
                _uiState.value = ChatsUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val chats = NetworkClient.getChats(userId)
                _rawChats.value = chats
                _contacts.value = NetworkClient.getContacts(userId)
            } catch (e: Exception) {
                _uiState.value = ChatsUiState.Error(e.message ?: "Unknown error")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Тихое обновление — не показывает Loading, используется при возврате на экран */
    fun refreshSilent() {
        viewModelScope.launch {
            try {
                val chats = NetworkClient.getChats(userId)
                _rawChats.value = chats
            } catch (e: Exception) {
                // не перебиваем текущий список ошибкой
            }
        }
    }

    /** Сбросить счётчик непрочитанных для конкретного чата (вызывается при открытии) */
    fun clearUnread(chatId: Int) {
        val cur = _unread.value.toMutableMap()
        cur.remove(chatId)
        _unread.value = cur
    }

    private fun loadContacts() {
        viewModelScope.launch {
            try {
                _contacts.value = NetworkClient.getContacts(userId)
            } catch (e: Exception) { /* non-critical */ }
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) { _searchResults.value = emptyList(); return }
        viewModelScope.launch {
            try {
                _searchResults.value = NetworkClient.searchUsers(query, userId)
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

    fun addContact(contactId: Int) {
        viewModelScope.launch {
            try {
                NetworkClient.addContact(userId, contactId)
                loadContacts()
                _searchResults.value = _searchResults.value.map {
                    if (it.id == contactId) it.copy(isMutualContact = false) else it
                }
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun openDm(otherUserId: Int, onReady: (chatId: Int) -> Unit) {
        viewModelScope.launch {
            try {
                val chatId = NetworkClient.openDm(userId, otherUserId)
                refreshSilent()
                onReady(chatId)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun createGroup(title: String, onReady: (chatId: Int, inviteToken: String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = NetworkClient.createGroup(userId, title)
                refreshSilent()
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
