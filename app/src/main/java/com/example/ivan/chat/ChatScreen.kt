package com.example.ivan.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ivan.main.MessageDto
import com.example.ivan.main.UserDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    userId: Int,
    chatId: Int,
    chatTitle: String,
    chatType: String = "dm",
    otherUserId: Int? = null,
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
    val otherUserOnline by vm.otherUserOnline.collectAsState()
    val context = LocalContext.current

    // Set other user for presence tracking in DM
    LaunchedEffect(otherUserId) {
        vm.setOtherUser(otherUserId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
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
                                Text(
                                    text = if (otherUserOnline) "в сети" else "не в сети",
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
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // Check if it's a join-link message
                    val isInviteMsg = message.text.startsWith("/join/")
                    if (isInviteMsg) {
                        InviteLinkBubble(
                            message = message,
                            isOwn = message.senderId == userId
                        )
                    } else {
                        MessageBubble(message = message, isOwn = message.senderId == userId)
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
}

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

@Composable
fun MessageBubble(message: MessageDto, isOwn: Boolean) {
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
                        MessageStatusIcon(
                            status = message.status,
                            tint = when (message.status) {
                                "read" -> Color(0xFF64B5F6) // blue checkmarks
                                else -> Color.White.copy(alpha = 0.7f)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** Displays ✓ for sent, ✓✓ for delivered/read */
@Composable
fun MessageStatusIcon(status: String, tint: Color) {
    val text = when (status) {
        "sent" -> "✓"
        "delivered" -> "✓✓"
        "read" -> "✓✓"
        else -> ""
    }
    Text(
        text = text,
        fontSize = 11.sp,
        color = tint,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Special bubble for messages that contain an invite link (/join/<token>).
 * Shows a tappable card instead of raw text.
 */
@Composable
private fun InviteLinkBubble(message: MessageDto, isOwn: Boolean) {
    val token = message.text.removePrefix("/join/")
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
            modifier = Modifier.widthIn(max = 280.dp)
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
