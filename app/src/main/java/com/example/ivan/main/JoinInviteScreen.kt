package com.example.ivan.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ivan.R

/**
 * Shown when the user opens an invite deep-link: ivan://join/{token}
 * Fetches invite info and lets the user confirm joining.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinInviteScreen(
    userId: Int,
    token: String,
    onJoined: (chatId: Int, title: String) -> Unit,
    onBack: () -> Unit
) {
    var inviteInfo by remember { mutableStateOf<InviteDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var joining by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        loading = true
        try {
            inviteInfo = NetworkClient.getInviteInfo(token)
            error = null
        } catch (e: Exception) {
            error = "Ссылка недействительна или истекла."
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приглашение") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                loading -> CircularProgressIndicator()

                error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("Назад") }
                }

                inviteInfo != null -> {
                    val info = inviteInfo!!
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(id = R.drawable.group),
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                info.chatTitle ?: "Группа",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Вас пригласили в группу",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            info.maxUses?.let { max ->
                                Text(
                                    "Использований: ${info.currentUses} / $max",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    joining = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !joining
                            ) {
                                if (joining) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text("Присоединиться")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                }
            }
        }
    }

    // Trigger join when button clicked
    LaunchedEffect(joining) {
        if (!joining) return@LaunchedEffect
        try {
            val result = NetworkClient.joinByInvite(token, userId)
            onJoined(result.chatId, result.chatTitle ?: "Группа")
        } catch (e: Exception) {
            error = "Не удалось присоединиться: ${e.message}"
            joining = false
        }
    }
}
