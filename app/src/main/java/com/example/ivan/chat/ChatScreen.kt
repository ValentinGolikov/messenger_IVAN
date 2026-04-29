package com.example.ivan.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.R
import com.example.ivan.main.MessageDto
import com.example.ivan.main.UserDto
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: Int,
    chatId: Int,
    chatTitle: String,
    onBack: () -> Unit
) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.Factory(userId, chatId))
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showInviteSheet by remember { mutableStateOf(false) }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    val inviteToken by vm.inviteToken.collectAsState()
    val context = LocalContext.current

    // Scroll to bottom when new message arrives (reverseLayout = true → index 0 = bottom)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(chatTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
                        DropdownMenuItem(
                            text = { Text("Пригласить ссылкой") },
                            leadingIcon = {
                                Icon(
                                    ImageVector.vectorResource(id = R.drawable.ic_link),
                                    contentDescription = null
                                )
                            },
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
                                    ImageVector.vectorResource(id = R.drawable.ic_person),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showMenu = false
                                showAddMemberSheet = true
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages list — reverseLayout so newest is at bottom
            val reversedMessages = remember(messages) { messages.reversed() }
            val grouped = remember(reversedMessages) { groupMessagesByDate(reversedMessages) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // grouped is ordered newest-first for reverseLayout
                grouped.forEach { (dateLabel, msgs) ->
                    items(msgs, key = { it.id }) { message ->
                        val isInviteMsg = message.text.startsWith("/join/")
                        if (isInviteMsg) {
                            InviteLinkBubble(message = message, isOwn = message.senderId == userId)
                        } else {
                            MessageBubble(
                                message = message,
                                isOwn = message.senderId == userId,
                                onCopy = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("msg", message.text))
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    item(key = "date_$dateLabel") {
                        DateDivider(label = dateLabel)
                    }
                }
            }

            // Input bar
            MessageInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        vm.sendMessage(inputText)
                        inputText = ""
                    }
                }
            )
        }
    }

    // Invite link sheet
    if (showInviteSheet) {
        InviteSheet(
            inviteToken = inviteToken,
            context = context,
            onDismiss = { showInviteSheet = false }
        )
    }

    // Add member sheet
    if (showAddMemberSheet) {
        AddMemberSheet(vm = vm, onDismiss = { showAddMemberSheet = false })
    }
}

// ── Message input bar ─────────────────────────────────────────────────────────

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Отправить",
                    tint = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Date divider ──────────────────────────────────────────────────────────────

@Composable
private fun DateDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Message bubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageDto,
    isOwn: Boolean,
    onCopy: () -> Unit = {}
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    var showCopyToast by remember { mutableStateOf(false) }

    if (showCopyToast) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showCopyToast = false
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (!isOwn) {
            Text(
                text = message.senderName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isOwn) 18.dp else 4.dp,
                        bottomEnd = if (isOwn) 4.dp else 18.dp
                    )
                )
                .background(
                    if (isOwn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = { onCopy(); showCopyToast = true }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .widthIn(max = 280.dp)
        ) {
            Column {
                Text(
                    text = message.text,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = time,
                    fontSize = 10.sp,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        if (showCopyToast) {
            Text(
                "Скопировано",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Invite link bubble ────────────────────────────────────────────────────────

@Composable
private fun InviteLinkBubble(message: MessageDto, isOwn: Boolean) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        if (!isOwn) {
            Text(
                text = message.senderName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }
        Card(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (isOwn) 18.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 18.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_link),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Приглашение в группу",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Нажмите, чтобы присоединиться",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
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

// ── Invite sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteSheet(
    inviteToken: String?,
    context: Context,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Ссылка-приглашение",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (inviteToken == null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val inviteUrl = "ivan://join/$inviteToken"
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            ImageVector.vectorResource(id = R.drawable.ic_link),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            inviteUrl,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("invite", inviteUrl))
                        }) {
                            Icon(
                                ImageVector.vectorResource(id = R.drawable.content_copy),
                                contentDescription = "Копировать"
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

// ── Add member sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberSheet(vm: ChatViewModel, onDismiss: () -> Unit) {
    val results by vm.memberSearchResults.collectAsState()
    var query by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

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
                onValueChange = { query = it; vm.searchUsersToInvite(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Найти по имени...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            feedback?.let {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                items(results) { user ->
                    ListItem(
                        headlineContent = { Text(user.displayName) },
                        supportingContent = {
                            Text(
                                if (user.isMutualContact) "✓ Взаимный контакт — добавится сразу"
                                else "Получит приглашение в ЛС",
                                fontSize = 12.sp
                            )
                        },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        user.displayName.first().uppercaseChar().toString(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            FilledTonalIconButton(onClick = {
                                vm.inviteUser(user.id) { method ->
                                    feedback = when (method) {
                                        "direct" -> "✓ ${user.displayName} добавлен в чат"
                                        "invite_dm" -> "Приглашение отправлено в ЛС"
                                        else -> "Готово"
                                    }
                                }
                            }) {
                                Icon(
                                    if (user.isMutualContact) Icons.Default.Person else Icons.Default.Send,
                                    contentDescription = "Пригласить",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Groups messages by date. Returns list ordered newest-first (for reverseLayout).
 * Each entry: dateLabel → list of messages in that day (also newest-first).
 */
private fun groupMessagesByDate(
    messagesNewestFirst: List<MessageDto>
): List<Pair<String, List<MessageDto>>> {
    if (messagesNewestFirst.isEmpty()) return emptyList()

    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }

    fun label(ts: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = ts }
        return when {
            isSameDay(cal, today) -> "Сегодня"
            isSameDay(cal, yesterday) -> "Вчера"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ts))
        }
    }

    return messagesNewestFirst
        .groupBy { label(it.timestamp) }
        .entries
        .map { it.key to it.value }
}

private fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
