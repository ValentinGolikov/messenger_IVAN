package com.example.ivan.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.R
import com.example.ivan.main.ChatDto
import com.example.ivan.main.UserDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    userId: Int,
    onOpenChat: (chatId: Int, title: String, chatType: String, otherUserId: Int?) -> Unit
) {
    val vm: ChatsViewModel = viewModel(factory = ChatsViewModel.Factory(userId))
    val uiState by vm.uiState.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val onlineStatuses by vm.onlineStatuses.collectAsState()

    var showNewChatSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сообщения", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск людей")
                    }
                    IconButton(onClick = { showNewChatSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Новый чат")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // People search bar
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        vm.searchUsers(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Найти пользователя...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                vm.clearSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (searchResults.isNotEmpty()) {
                Text(
                    "Пользователи",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(searchResults) { user ->
                        UserSearchItem(
                            user = user,
                            onOpenDm = {
                                vm.openDm(user.id) { chatId ->
                                    onOpenChat(chatId, user.displayName, "dm", user.id)
                                }
                            },
                            onAddContact = { vm.addContact(user.id) }
                        )
                    }
                }
            } else {
                when (val s = uiState) {
                    is ChatsUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    is ChatsUiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.message, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { vm.loadChats() }) { Text("Повторить") }
                            }
                        }
                    }

                    is ChatsUiState.Success -> {
                        if (s.chats.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Нет чатов. Нажмите ✏️ чтобы начать.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(s.chats, key = { it.id }) { chat ->
                                    val isOnline = chat.otherUserId?.let { onlineStatuses[it] } ?: false
                                    ChatListItem(
                                        chat = chat,
                                        currentUserId = userId,
                                        isOtherOnline = isOnline,
                                        onClick = {
                                            val title = chatTitle(chat)
                                            onOpenChat(chat.id, title, chat.type, chat.otherUserId)
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom sheet for new chat
    if (showNewChatSheet) {
        NewChatSheet(
            userId = userId,
            vm = vm,
            onOpenChat = { chatId, title ->
                onOpenChat(chatId, title, "group", null)
            },
            onDismiss = { showNewChatSheet = false }
        )
    }
}

@Composable
private fun ChatListItem(
    chat: ChatDto,
    currentUserId: Int,
    isOtherOnline: Boolean,
    onClick: () -> Unit
) {
    val title = chatTitle(chat)
    val lastText = chat.lastMessage?.text ?: "Нет сообщений"
    val time = chat.lastMessage?.timestamp?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder with online indicator
        Box {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = if (chat.type == "dm") MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (chat.type == "dm") MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            // Online indicator for DM chats
            if (chat.type == "dm" && isOtherOnline) {
                Surface(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd),
                    shape = CircleShape,
                    color = Color(0xFF4CAF50),
                    shadowElevation = 2.dp
                ) {}
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = time,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (chat.type == "group") {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.group),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // Status indicator for last message
                if (chat.lastMessage != null && chat.lastMessage.senderId == currentUserId) {
                    val statusText = when (chat.lastMessage.status) {
                        "sent" -> "✓ "
                        "delivered" -> "✓✓ "
                        "read" -> "✓✓ "
                        else -> ""
                    }
                    val statusColor = if (chat.lastMessage.status == "read")
                        Color(0xFF64B5F6) else MaterialTheme.colorScheme.onSurfaceVariant
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                Text(
                    text = if (chat.lastMessage?.senderId == currentUserId)
                        "Вы: $lastText" else lastText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (chat.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge { Text(chat.unreadCount.toString()) }
        }
    }
}

@Composable
private fun UserSearchItem(
    user: UserDto,
    onOpenDm: () -> Unit,
    onAddContact: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDm)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.Medium)
            user.realName?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (user.isMutualContact) {
                Text("✓ Взаимный контакт", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onAddContact) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_person),
                contentDescription = "Добавить в контакты",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onOpenDm) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.message),
                contentDescription = "Написать",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    userId: Int,
    vm: ChatsViewModel,
    onOpenChat: (chatId: Int, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf<String?>(null) } // "dm" | "group"
    var groupTitle by remember { mutableStateOf("") }
    val contacts by vm.contacts.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Новый чат",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (mode == null) {
                ListItem(
                    headlineContent = { Text("Личное сообщение") },
                    supportingContent = { Text("Написать конкретному человеку") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.clickable { mode = "dm" }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Создать группу") },
                    supportingContent = { Text("Групповой чат с несколькими людьми") },
                    leadingContent = {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.group),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { mode = "group" }
                )
            }

            if (mode == "dm") {
                Text(
                    "Выберите контакт для ЛС",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (contacts.isEmpty()) {
                    Text(
                        "Нет контактов. Найдите людей через поиск.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(contacts) { contact ->
                            ListItem(
                                headlineContent = { Text(contact.displayName) },
                                leadingContent = {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                contact.displayName.first().uppercaseChar().toString(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.clickable {
                                    vm.openDm(contact.id) { chatId ->
                                        onOpenChat(chatId, contact.displayName)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (mode == "group") {
                Text(
                    "Название группы",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedTextField(
                    value = groupTitle,
                    onValueChange = { groupTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Введите название...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (groupTitle.isNotBlank()) {
                            vm.createGroup(groupTitle) { chatId, _ ->
                                onOpenChat(chatId, groupTitle)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = groupTitle.isNotBlank()
                ) {
                    Text("Создать группу")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun chatTitle(chat: ChatDto): String =
    when (chat.type) {
        "dm" -> chat.otherUserName ?: "Личные сообщения"
        else -> chat.title ?: "Группа"
    }
