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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.R
import com.example.ivan.main.ChatDto
import com.example.ivan.main.UserDto
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    userId: Int,
    onOpenChat: (chatId: Int, title: String) -> Unit
) {
    val vm: ChatsViewModel = viewModel(factory = ChatsViewModel.Factory(userId))
    val uiState by vm.uiState.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()

    // Обновляем список чатов каждый раз когда экран становится активным (возврат из диалога)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshSilent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showNewChatSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                vm.searchUsers(it)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                            placeholder = { Text("Найти пользователя...") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = ""; vm.clearSearch() }) {
                                        Icon(Icons.Default.Clear, contentDescription = null)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    } else {
                        Text("Сообщения", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = {
                            showSearch = false
                            searchQuery = ""
                            vm.clearSearch()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Закрыть поиск")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск")
                        }
                        IconButton(onClick = { showNewChatSheet = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Новый чат")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search results overlay
            if (searchResults.isNotEmpty() || (showSearch && searchQuery.isNotEmpty())) {
                SearchResultsList(
                    results = searchResults,
                    userId = userId,
                    vm = vm,
                    onOpenChat = onOpenChat
                )
            } else {
                when (val s = uiState) {
                    is ChatsUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is ChatsUiState.Error -> {
                        ErrorState(message = s.message, onRetry = { vm.loadChats() })
                    }
                    is ChatsUiState.Success -> {
                        if (s.chats.isEmpty()) {
                            EmptyChatsState(onNewChat = { showNewChatSheet = true })
                        } else {
                            ChatsList(
                                chats = s.chats,
                                userId = userId,
                                onOpenChat = { chatId, title ->
                                    vm.clearUnread(chatId)
                                    onOpenChat(chatId, title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatSheet(
            vm = vm,
            onOpenChat = onOpenChat,
            onDismiss = { showNewChatSheet = false }
        )
    }
}

// ── Chats list with sections ──────────────────────────────────────────────────

@Composable
private fun ChatsList(
    chats: List<ChatDto>,
    userId: Int,
    onOpenChat: (Int, String) -> Unit
) {
    // Sort by last message timestamp descending
    val sorted = remember(chats) {
        chats.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }
    val dms = sorted.filter { it.type == "dm" }
    val groups = sorted.filter { it.type == "group" }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (groups.isNotEmpty()) {
            item {
                SectionHeader(title = "Группы", icon = R.drawable.group)
            }
            items(groups, key = { it.id }) { chat ->
                ChatListItem(chat = chat, currentUserId = userId, onClick = {
                    onOpenChat(chat.id, chatTitle(chat))
                })
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }
        }
        if (dms.isNotEmpty()) {
            item {
                SectionHeader(title = "Личные сообщения", icon = R.drawable.message)
            }
            items(dms, key = { it.id }) { chat ->
                ChatListItem(chat = chat, currentUserId = userId, onClick = {
                    onOpenChat(chat.id, chatTitle(chat))
                })
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, icon: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ── Chat list item ────────────────────────────────────────────────────────────

@Composable
private fun ChatListItem(
    chat: ChatDto,
    currentUserId: Int,
    onClick: () -> Unit
) {
    val title = chatTitle(chat)
    val lastText = chat.lastMessage?.text ?: "Нет сообщений"
    val time = chat.lastMessage?.timestamp?.let { formatTime(it) } ?: ""
    val isOwn = chat.lastMessage?.senderId == currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
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
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = if (isOwn) "Вы: $lastText" else lastText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (chat.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(chat.unreadCount.toString(), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

// ── Search results ────────────────────────────────────────────────────────────

@Composable
private fun SearchResultsList(
    results: List<UserDto>,
    userId: Int,
    vm: ChatsViewModel,
    onOpenChat: (Int, String) -> Unit
) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Никого не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Пользователи",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(results) { user ->
            UserSearchItem(
                user = user,
                onOpenDm = {
                    vm.openDm(user.id) { chatId -> onOpenChat(chatId, user.displayName) }
                },
                onAddContact = { vm.addContact(user.id) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 72.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            )
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
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            if (user.isMutualContact) {
                Text(
                    "✓ Взаимный контакт",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                user.realName?.let {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onAddContact) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.ic_person),
                contentDescription = "Добавить в контакты",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onOpenDm) {
            Icon(
                imageVector = ImageVector.vectorResource(id = R.drawable.message),
                contentDescription = "Написать",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Empty / Error states ──────────────────────────────────────────────────────

@Composable
private fun EmptyChatsState(onNewChat: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.message),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Нет чатов",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Начните переписку или создайте группу",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNewChat) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Новый чат")
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

// ── New chat bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    vm: ChatsViewModel,
    onOpenChat: (chatId: Int, title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf<String?>(null) }
    var groupTitle by remember { mutableStateOf("") }
    val contacts by vm.contacts.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mode != null) {
                    IconButton(onClick = { mode = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
                Text(
                    text = when (mode) {
                        "dm" -> "Личное сообщение"
                        "group" -> "Новая группа"
                        else -> "Новый чат"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = if (mode != null) 0.dp else 4.dp, bottom = 4.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (mode) {
                null -> {
                    ListItem(
                        headlineContent = { Text("Личное сообщение") },
                        supportingContent = { Text("Написать конкретному человеку") },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        modifier = Modifier.clickable { mode = "dm" }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Создать группу") },
                        supportingContent = { Text("Групповой чат с несколькими людьми") },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.group),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { mode = "group" }
                    )
                }

                "dm" -> {
                    if (contacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Нет контактов",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Найдите людей через поиск ↑",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(contacts) { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.displayName) },
                                    supportingContent = if (contact.realName != null) {
                                        { Text(contact.realName) }
                                    } else null,
                                    leadingContent = {
                                        Surface(
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    contact.displayName.first().uppercaseChar().toString(),
                                                    fontWeight = FontWeight.Bold,
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
                                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                            }
                        }
                    }
                }

                "group" -> {
                    OutlinedTextField(
                        value = groupTitle,
                        onValueChange = { groupTitle = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название группы") },
                        placeholder = { Text("Введите название...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.group),
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (groupTitle.isNotBlank()) {
                                val title = groupTitle
                                vm.createGroup(title) { chatId, _ ->
                                    onOpenChat(chatId, title)
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
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun chatTitle(chat: ChatDto): String =
    if (chat.type == "dm") chat.otherUserName ?: "Личные сообщения"
    else chat.title ?: "Группа"

private fun formatTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return when {
        now.get(Calendar.DATE) == cal.get(Calendar.DATE) ->
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        now.get(Calendar.WEEK_OF_YEAR) == cal.get(Calendar.WEEK_OF_YEAR) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
        else ->
            SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(timestamp))
    }
}
