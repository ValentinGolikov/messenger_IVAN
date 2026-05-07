package com.example.ivan.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.R
import com.example.ivan.main.ChatDto
import com.example.ivan.main.UserDto
import com.example.ivan.chat.formatLastSeen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    userId: Int,
    onOpenChat: (chatId: Int, title: String, chatType: String, otherUserId: Int?) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val vm: ChatsViewModel = viewModel(factory = ChatsViewModel.Factory(userId))
    val uiState by vm.uiState.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val onlineStatuses by vm.onlineStatuses.collectAsState()
    val lastSeenMap by vm.lastSeenMap.collectAsState()

    var showNewChatSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var deleteChatTarget by remember { mutableStateOf<ChatDto?>(null) }

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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
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
                            // Разделяем чаты на личные и групповые
                            val dmChats = s.chats.filter { it.type == "dm" }
                            val groupChats = s.chats.filter { it.type == "group" }
                            
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                // Секция личных сообщений
                                if (dmChats.isNotEmpty()) {
                                    item {
                                        SectionHeader(
                                            title = "Личные сообщения",
                                            count = dmChats.size
                                        )
                                    }
                                    items(dmChats, key = { it.id }) { chat ->
                                        val isOnline = chat.otherUserId?.let { onlineStatuses[it] } ?: false
                                        val lastSeen = chat.otherUserId?.let { lastSeenMap[it] }
                                        ChatListItem(
                                            chat = chat,
                                            currentUserId = userId,
                                            isOtherOnline = isOnline,
                                            otherLastSeen = lastSeen,
                                            onClick = {
                                                val title = chatTitle(chat)
                                                onOpenChat(chat.id, title, chat.type, chat.otherUserId)
                                            },
                                            onLongClick = {
                                                deleteChatTarget = chat
                                            }
                                        )
                                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                                    }
                                }
                                
                                // Секция групповых чатов
                                if (groupChats.isNotEmpty()) {
                                    item {
                                        SectionHeader(
                                            title = "Группы",
                                            count = groupChats.size
                                        )
                                    }
                                    items(groupChats, key = { it.id }) { chat ->
                                        ChatListItem(
                                            chat = chat,
                                            currentUserId = userId,
                                            isOtherOnline = false,
                                            otherLastSeen = null,
                                            onClick = {
                                                val title = chatTitle(chat)
                                                onOpenChat(chat.id, title, chat.type, chat.otherUserId)
                                            },
                                            onLongClick = {
                                                deleteChatTarget = chat
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
    }

    // Delete chat dialog
    deleteChatTarget?.let { chat ->
        var forAll by remember { mutableStateOf(false) }
        if (chat.type == "dm") {
            AlertDialog(
                onDismissRequest = { deleteChatTarget = null },
                title = { Text("Удалить чат") },
                text = {
                    Column {
                        Text("Удалить этот чат?")
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                            Text("Также у собеседника",
                                modifier = Modifier.clickable { forAll = !forAll })
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteChat(chat.id, forAll)
                        deleteChatTarget = null
                    }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteChatTarget = null }) { Text("Отмена") }
                }
            )
        } else {
            // Group — just dismiss, actions are in the chat screen menu
            deleteChatTarget = null
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: ChatDto,
    currentUserId: Int,
    isOtherOnline: Boolean,
    otherLastSeen: Long? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val title = chatTitle(chat)
    val lastText = chat.lastMessage?.text ?: "Нет сообщений"
    val time = chat.lastMessage?.timestamp?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    } ?: ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
                    MessageStatusIcon(
                        status = chat.lastMessage.status,
                        size = 14.dp,
                        tint = if (chat.lastMessage.status == "read")
                            Color(0xFF64B5F6) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
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
            // Last seen for DM chats (when offline)
            if (chat.type == "dm" && !isOtherOnline && otherLastSeen != null) {
                val statusText = formatLastSeen(otherLastSeen)
                if (statusText != "в сети") {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
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

@Composable
private fun SectionHeader(
    title: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Displays Material icons for message status */
@Composable
fun MessageStatusIcon(
    status: String,
    tint: Color,
    size: Dp = 16.dp
) {
    val icon = when (status) {
        "sent" -> Icons.Default.Done
        "delivered" -> Icons.Default.DoneAll
        "read" -> Icons.Default.DoneAll
        else -> Icons.Default.Done
    }
    Icon(
        imageVector = icon,
        contentDescription = when (status) {
            "sent" -> "Отправлено"
            "delivered" -> "Доставлено"
            "read" -> "Прочитано"
            else -> "Статус"
        },
        modifier = Modifier.size(size),
        tint = tint
    )
}
