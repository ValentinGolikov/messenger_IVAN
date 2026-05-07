package com.example.ivan.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.example.ivan.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.main.MemberDto
import com.example.ivan.main.MessageDto
import com.example.ivan.main.PinnedMessageDto
import com.example.ivan.main.UserDto
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    userId: Int,
    chatId: Int,
    chatTitle: String,
    chatType: String = "dm",
    otherUserId: Int? = null,
    onBack: () -> Unit,
    onNavigateToJoin: ((token: String) -> Unit)? = null
) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory(userId, chatId))
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showPinnedListSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var deleteMessageTarget by remember { mutableStateOf<MessageDto?>(null) }
    val inviteToken by vm.inviteToken.collectAsState()
    val otherUserOnline by vm.otherUserOnline.collectAsState()
    val otherUserLastSeen by vm.otherUserLastSeen.collectAsState()
    val pinnedMessages by vm.pinnedMessages.collectAsState()
    val members by vm.members.collectAsState()
    val myRole by vm.myRole.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Context menu state for long-press on messages
    var contextMenuMessage by remember { mutableStateOf<MessageDto?>(null) }

    // The latest pinned message (for the banner)
    val latestPinned = pinnedMessages.firstOrNull()

    // Count unread messages (from other users)
    val unreadCount = messages.count { it.senderId != userId && it.status != "read" }
    
    // Find first unread message index (for "new messages" divider)
    val firstUnreadIndex = messages.indexOfFirst { it.senderId != userId && it.status != "read" }
    
    // Track if "new messages" divider has been scrolled past
    var newMessagesDividerScrolled by remember { mutableStateOf(false) }
    
    // Set other user for presence tracking in DM
    LaunchedEffect(otherUserId) {
        vm.setOtherUser(otherUserId)
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Check if "new messages" divider should be hidden (scrolled past)
    LaunchedEffect(listState, firstUnreadIndex) {
        if (firstUnreadIndex > 0 && !newMessagesDividerScrolled) {
            // Check if we've scrolled past the divider
            val firstVisibleIndex = listState.firstVisibleItemIndex
            if (firstVisibleIndex >= firstUnreadIndex) {
                newMessagesDividerScrolled = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatar with online indicator
                        Box {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = chatTitle.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            // Online indicator dot
                            if (chatType == "dm" && otherUserOnline) {
                                Surface(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.BottomEnd),
                                    shape = CircleShape,
                                    color = Color(0xFF4CAF50),
                                    shadowElevation = 2.dp
                                ) {}
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(chatTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (chatType == "dm") {
                                val statusText = if (otherUserOnline) "в сети"
                                    else otherUserLastSeen?.let { formatLastSeen(it) } ?: "не в сети"
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    color = if (otherUserOnline) Color(0xFF4CAF50)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (chatType == "group") {
                            DropdownMenuItem(
                                text = { Text("Участники") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    vm.loadMembers()
                                    showMembersSheet = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Пригласить ссылкой") },
                            leadingIcon = {
                                Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_link),
                                contentDescription = null
                                ) },
                            onClick = {
                                showMenu = false
                                vm.generateInviteLink()
                                showInviteSheet = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Добавить участника") },
                            leadingIcon = {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_person),
                                    contentDescription = null
                                ) },
                            onClick = {
                                showMenu = false
                                showAddMemberSheet = true
                            }
                        )
                        if (chatType == "group") {
                            HorizontalDivider()
                            if (myRole == "owner") {
                                DropdownMenuItem(
                                    text = { Text("Удалить группу", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMenu = false; showDeleteGroupDialog = true }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Покинуть группу", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showLeaveDialog = true }
                            )
                        }
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
            // ── Pinned message banner ─────────────────────────────────────────
            latestPinned?.let { pinned ->
                PinnedMessageBanner(
                    pinned = pinned,
                    totalPinned = pinnedMessages.size,
                    onClickScroll = {
                        if (pinnedMessages.size > 1) {
                            showPinnedListSheet = true
                        } else {
                            val idx = messages.indexOfFirst { it.id == pinned.messageId }
                            if (idx >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(idx)
                                }
                            }
                        }
                    },
                    onUnpin = { vm.unpinMessage(pinned.messageId) }
                )
            }

            // "New messages" divider - shown before first unread message
            if (firstUnreadIndex > 0 && !newMessagesDividerScrolled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Новые сообщения",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false  // Messages go top to bottom (old to new)
            ) {
                items(messages, key = { it.id }) { message ->
                    // System messages: centered gray text
                    if (message.messageType == "system") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = message.text,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        return@items
                    }

                    // Check if it's a join-link message
                    val inviteMsgToken = extractInviteToken(message.text)
                    if (inviteMsgToken != null) {
                        InviteLinkBubble(
                            message = message,
                            isOwn = message.senderId == userId,
                            onTap = { onNavigateToJoin?.invoke(inviteMsgToken) }
                        )
                    } else {
                        // Wrap in Box with long-press for context menu
                        Box {
                            MessageBubble(
                                message = message,
                                isOwn = message.senderId == userId,
                                isPinned = pinnedMessages.any { it.messageId == message.id },
                                onLongPress = { contextMenuMessage = message }
                            )
                            // Context menu dropdown
                            DropdownMenu(
                                expanded = contextMenuMessage?.id == message.id,
                                onDismissRequest = { contextMenuMessage = null },
                                offset = DpOffset(
                                    x = if (message.senderId == userId) 100.dp else 0.dp,
                                    y = 0.dp
                                )
                            ) {
                                val isPinned = pinnedMessages.any { it.messageId == message.id }
                                DropdownMenuItem(
                                    text = { Text(if (isPinned) "Открепить" else "Закрепить") },
                                    leadingIcon = {
                                        Icon(
                                            if (isPinned) Icons.Default.Close else Icons.Default.Lock,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        contextMenuMessage = null
                                        if (isPinned) vm.unpinMessage(message.id)
                                        else vm.pinMessage(message.id)
                                    }
                                )
                                // Delete option: in DM — any message; in group — only own
                                if (chatType == "dm" || message.senderId == userId) {
                                    DropdownMenuItem(
                                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            contextMenuMessage = null
                                            deleteMessageTarget = message
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Update newMessagesDividerScrolled when scrolling
            LaunchedEffect(listState, firstUnreadIndex) {
                if (firstUnreadIndex > 0 && !newMessagesDividerScrolled) {
                    val firstVisibleIndex = listState.firstVisibleItemIndex
                    if (firstVisibleIndex >= firstUnreadIndex) {
                        newMessagesDividerScrolled = true
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        vm.sendMessage(inputText)
                        inputText = ""
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // ── Invite link sheet ─────────────────────────────────────────────────────
    if (showInviteSheet) {
        ModalBottomSheet(onDismissRequest = { showInviteSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("Ссылка-приглашение", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                if (inviteToken == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    val inviteUrl = "ivan://join/$inviteToken"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                inviteUrl,
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("invite", inviteUrl))
                            }) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_link),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Поделитесь этой ссылкой, чтобы пригласить людей в чат.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── Add member sheet ──────────────────────────────────────────────────────
    if (showAddMemberSheet) {
        AddMemberSheet(
            vm = vm,
            onDismiss = { showAddMemberSheet = false }
        )
    }

    // ── Delete message dialog ─────────────────────────────────────────────────
    deleteMessageTarget?.let { msg ->
        var forAll by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { deleteMessageTarget = null },
            title = { Text("Удалить сообщение") },
            text = {
                Column {
                    Text("Удалить это сообщение?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = forAll, onCheckedChange = { forAll = it })
                        val label = if (chatType == "dm") "Также у собеседника" else "Удалить у всех"
                        Text(label, modifier = Modifier.clickable { forAll = !forAll })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMessage(msg.id, forAll)
                    deleteMessageTarget = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteMessageTarget = null }) { Text("Отмена") }
            }
        )
    }

    // ── Delete group dialog ───────────────────────────────────────────────────
    if (showDeleteGroupDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteGroupDialog = false },
            title = { Text("Удалить группу") },
            text = { Text("Группа будет удалена для всех участников. Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteGroupDialog = false
                    vm.deleteGroup { onBack() }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGroupDialog = false }) { Text("Отмена") }
            }
        )
    }

    // ── Leave group dialog ────────────────────────────────────────────────────
    if (showLeaveDialog) {
        if (myRole == "owner") {
            // Owner must pick a new admin
            var selectedNewOwner by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(Unit) { vm.loadMembers() }
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Покинуть группу") },
                text = {
                    Column {
                        Text("Вы администратор. Выберите нового администратора:")
                        Spacer(modifier = Modifier.height(8.dp))
                        val otherMembers = members.filter { it.id != userId }
                        otherMembers.forEach { member ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNewOwner = member.id }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedNewOwner == member.id,
                                    onClick = { selectedNewOwner = member.id }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(member.displayName)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLeaveDialog = false
                            vm.leaveGroup(newOwnerId = selectedNewOwner) { onBack() }
                        },
                        enabled = selectedNewOwner != null
                    ) { Text("Покинуть", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) { Text("Отмена") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Покинуть группу") },
                text = { Text("Вы уверены, что хотите покинуть группу?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLeaveDialog = false
                        vm.leaveGroup { onBack() }
                    }) { Text("Покинуть", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showLeaveDialog = false }) { Text("Отмена") }
                }
            )
        }
    }

    // ── Pinned messages list sheet ─────────────────────────────────────────────
    if (showPinnedListSheet) {
        PinnedListSheet(
            pinnedMessages = pinnedMessages,
            onDismiss = { showPinnedListSheet = false },
            onScrollTo = { msgId ->
                showPinnedListSheet = false
                val idx = messages.indexOfFirst { it.id == msgId }
                if (idx >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                }
            },
            onUnpin = { msgId -> vm.unpinMessage(msgId) }
        )
    }

    // ── Members list sheet ────────────────────────────────────────────────────
    if (showMembersSheet) {
        MemberListSheet(
            members = members,
            myRole = myRole,
            onDismiss = { showMembersSheet = false },
            onKick = { targetId -> vm.kickMember(targetId) },
            onTransferOwner = { targetId -> vm.transferOwner(targetId) }
        )
    }
}

// ── Pinned message banner ────────────────────────────────────────────────────

@Composable
private fun PinnedMessageBanner(
    pinned: PinnedMessageDto,
    totalPinned: Int = 1,
    onClickScroll: () -> Unit,
    onUnpin: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickScroll),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pin icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                val label = if (totalPinned > 1) "Закреплённые ($totalPinned)" else "Закреплённое сообщение"
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    pinned.text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            IconButton(
                onClick = onUnpin,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Открепить",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ── Add member sheet ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(
    vm: ChatViewModel,
    onDismiss: () -> Unit
) {
    val results by vm.memberSearchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Добавить участника",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.searchUsersToInvite(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Найти по имени...") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))

            snackMsg?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            }

            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(results) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName) },
                        supportingContent = {
                            if (user.isMutualContact) Text("✓ Взаимный контакт — добавится сразу")
                            else Text("Получит приглашение в ЛС")
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                vm.inviteUser(user.id) { method ->
                                    snackMsg = when (method) {
                                        "direct" -> "${user.displayName} добавлен в чат!"
                                        "invite_dm" -> "Приглашение отправлено в ЛС"
                                        else -> "Готово"
                                    }
                                }
                            }) {
                                Icon(
                                    if (user.isMutualContact) ImageVector.vectorResource(id = R.drawable.ic_person) else Icons.Default.Send,
                                    contentDescription = "Пригласить"
                                )
                            }
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Message bubble ───────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageDto,
    isOwn: Boolean,
    isPinned: Boolean = false,
    onLongPress: (() -> Unit)? = null
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (!isOwn) {
            Text(
                text = message.senderName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onLongPress?.invoke() }
                )
                .background(
                    color = if (isOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 16.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Column {
                // Pin indicator
                if (isPinned) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = if (isOwn) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Закреплено",
                            fontSize = 10.sp,
                            color = if (isOwn) Color.White.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = message.text,
                    color = if (isOwn) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = if (isOwn) Color.White.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    // Status indicator for own messages
                    if (isOwn) {
                        Spacer(modifier = Modifier.width(2.dp))
                        MessageStatusIcon(
                            status = message.status,
                            tint = when (message.status) {
                                "read" -> Color(0xFF64B5F6) // blue checkmarks
                                else -> Color.White.copy(alpha = 0.7f)
                            },
                            size = 14.dp
                        )
                    }
                }
            }
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

// ── Invite link bubble ───────────────────────────────────────────────────────

/**
 * Extract invite token from message text.
 * Matches "/join/{token}" anywhere in the text.
 */
private fun extractInviteToken(text: String): String? {
    val prefix = "/join/"
    val idx = text.indexOf(prefix)
    if (idx < 0) return null
    val afterPrefix = text.substring(idx + prefix.length)
    // Token is everything after /join/ up to the next space or end of string
    return afterPrefix.split(" ", "\n").firstOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * Special bubble for messages that contain an invite link (/join/<token>).
 * Shows a tappable card instead of raw text.
 */
@Composable
private fun InviteLinkBubble(
    message: MessageDto,
    isOwn: Boolean,
    onTap: () -> Unit
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (!isOwn) {
            Text(
                text = message.senderName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable(onClick = onTap)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_link),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Приглашение в группу",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Нажмите, чтобы присоединиться",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = time,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ── Pinned messages list sheet ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinnedListSheet(
    pinnedMessages: List<PinnedMessageDto>,
    onDismiss: () -> Unit,
    onScrollTo: (messageId: String) -> Unit,
    onUnpin: (messageId: String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Закреплённые сообщения",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            pinnedMessages.forEach { pin ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onScrollTo(pin.messageId) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pin.senderName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                pin.text,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onUnpin(pin.messageId) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Открепить",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Members list sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberListSheet(
    members: List<MemberDto>,
    myRole: String,
    onDismiss: () -> Unit,
    onKick: (targetId: Int) -> Unit,
    onTransferOwner: (targetId: Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Участники (${members.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            members.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar placeholder
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                member.displayName.firstOrNull()?.uppercase() ?: "?",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.displayName, fontWeight = FontWeight.Medium)
                            if (member.role == "owner") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "Админ",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        val status = if (member.online) "в сети"
                            else member.lastSeen?.let { formatLastSeen(it) } ?: "не в сети"
                        Text(
                            status,
                            fontSize = 12.sp,
                            color = if (member.online) Color(0xFF4CAF50)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Actions for owner (except on self)
                    if (myRole == "owner" && member.role != "owner") {
                        var showMemberMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showMemberMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                            }
                            DropdownMenu(
                                expanded = showMemberMenu,
                                onDismissRequest = { showMemberMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Передать права") },
                                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                                    onClick = { showMemberMenu = false; onTransferOwner(member.id) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Исключить", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { showMemberMenu = false; onKick(member.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Last seen formatter (Telegram-style) ───────────────────────────────

private fun pluralMinutes(n: Long): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod100 in 11..19 -> "минут"
        mod10 == 1L -> "минуту"
        mod10 in 2..4 -> "минуты"
        else -> "минут"
    }
}

fun formatLastSeen(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffSec = diffMs / 1000

    // <60 seconds — but this is lastSeen, so user is offline
    // Return "недавно" instead of "в сети"
    if (diffSec < 60) return "недавно"

    // 1-59 minutes (has priority over today/yesterday)
    val diffMin = diffSec / 60
    if (diffMin < 60) return "$diffMin ${pluralMinutes(diffMin)} назад"

    // Calendar-day checks
    val cal = Calendar.getInstance()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(timestamp))

    if (timestamp >= todayStart) return "сегодня в $timeStr"
    if (timestamp >= yesterdayStart) return "вчера в $timeStr"

    val diffDays = diffMs / (24 * 60 * 60 * 1000)
    if (diffDays <= 7) return "на этой неделе"
    if (diffDays <= 30) return "в этом месяце"
    return "давно"
}
