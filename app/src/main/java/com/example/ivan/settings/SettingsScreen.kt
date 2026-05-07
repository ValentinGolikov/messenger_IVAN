package com.example.ivan.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onThemeChanged: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersion = remember { getAppVersion(context) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            // Секция внешнего вида
            Text(
                text = "Внешний вид",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // Переключатель темы
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChanged(!isDarkTheme) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isDarkTheme) "Тёмная тема" else "Светлая тема",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (isDarkTheme) "Используется тёмная тема" else "Используется светлая тема",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeChanged
                        )
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Секция информации
            Text(
                text = "О приложении",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Версия") },
                        supportingContent = { Text(appVersion) },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Разработчик") },
                        supportingContent = { Text("Circus_Shapeto_Dev") },
                        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Version parsing ───────────────────────────────────────────────────────────

@Serializable
data class VersionInfo(
    val version: String,
    val build: String,
    val uiVersion: String
)

private fun getAppVersion(context: android.content.Context): String {
    return try {
        val inputStream = context.resources.openRawResource(com.example.ivan.R.raw.version)
        val reader = BufferedReader(java.io.InputStreamReader(inputStream))
        val jsonText = reader.use { it.readText() }
        val versionInfo = Json.decodeFromString(VersionInfo.serializer(), jsonText)
        versionInfo.uiVersion
    } catch (e: Exception) {
        e.printStackTrace()
        "0.1.0"
    }
}