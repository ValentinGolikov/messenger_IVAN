package com.example.ivan.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ivan.chat.ChatScreen
import com.example.ivan.chats.ChatsScreen
import com.example.ivan.ui.theme.IvanTheme
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var sdk: YandexAuthSdk
    private lateinit var launcher: ActivityResultLauncher<YandexAuthLoginOptions>

    /**
     * Holds the authenticated user ID.
     * IMPORTANT: accessed only from the main thread after login completes.
     * We use a StateFlow so the Compose navigation can react when it changes.
     */
    private val authState = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = YandexAuthSdk.create(YandexAuthOptions(this))
        launcher = registerForActivityResult(sdk.contract) { result -> handleResult(result) }

        setContent {
            IvanTheme {
            val navController = rememberNavController()
            val userId = authState.collectAsState().value

            NavHost(navController = navController, startDestination = "splash") {

                composable("splash") {
                    SplashScreen() // shown while login is in progress
                }

                composable("chats") {
                    // Only reachable after userId is set
                    ChatsScreen(
                        userId = userId ?: return@composable,
                        onOpenChat = { chatId, title ->
                            navController.navigate("chat/$chatId?title=${Uri.encode(title)}")
                        }
                    )
                }

                composable(
                    route = "chat/{chatId}?title={title}",
                    arguments = listOf(
                        navArgument("chatId") { type = NavType.IntType },
                        navArgument("title") {
                            type = NavType.StringType
                            defaultValue = ""
                        }
                    )
                ) { backStack ->
                    val chatId = backStack.arguments?.getInt("chatId") ?: return@composable
                    val title = backStack.arguments?.getString("title") ?: ""
                    ChatScreen(
                        userId = userId ?: return@composable,
                        chatId = chatId,
                        chatTitle = title,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "join/{token}",
                    arguments = listOf(navArgument("token") { type = NavType.StringType })
                ) { backStack ->
                    val token = backStack.arguments?.getString("token") ?: return@composable
                    JoinInviteScreen(
                        userId = userId ?: return@composable,
                        token = token,
                        onJoined = { chatId, title ->
                            navController.navigate("chat/$chatId?title=${Uri.encode(title)}") {
                                popUpTo("chats") { inclusive = false }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // Drive navigation from auth state
            androidx.compose.runtime.LaunchedEffect(userId) {
                if (userId != null) {
                    // Check if there's a deep-link invite to handle
                    val inviteToken = extractInviteToken(intent)
                    if (inviteToken != null) {
                        navController.navigate("join/$inviteToken") {
                            popUpTo("splash") { inclusive = true }
                        }
                    } else {
                        navController.navigate("chats") {
                            popUpTo("splash") { inclusive = true }
                        }
                    }
                }
            }
            } // IvanTheme
        }

        // Start login flow
        lifecycleScope.launch {
            val savedToken = TokenStorage.getToken(this@MainActivity)
            Log.d("IVAN", "Saved token: $savedToken")
            if (savedToken != null) {
                val success = tryLoginWithToken(savedToken)
                if (!success) {
                    TokenStorage.clearToken(this@MainActivity)
                    launcher.launch(YandexAuthLoginOptions())
                }
            } else {
                launcher.launch(YandexAuthLoginOptions())
            }
        }
    }

    /**
     * Attempts login with [token].
     * On success, saves userId into [authState] so navigation triggers.
     * Returns true on success.
     */
    private suspend fun tryLoginWithToken(token: String): Boolean {
        return try {
            val loginResponse = NetworkClient.login(token)
            // ✅ FIX: authState is set BEFORE navigation happens (which reacts to authState).
            authState.value = loginResponse.userId
            Log.d("IVAN", "Logged in: ${loginResponse.yandexData.displayName}, id=${loginResponse.userId}")
            true
        } catch (e: Exception) {
            Log.e("IVAN", "Login failed", e)
            false
        }
    }

    private fun handleResult(result: YandexAuthResult) {
        when (result) {
            is YandexAuthResult.Success -> onSuccessAuth(result.token)
            is YandexAuthResult.Failure -> onProcessError(result.exception)
            YandexAuthResult.Cancelled -> onCancelled()
        }
    }

    private fun onSuccessAuth(token: YandexAuthToken) {
        lifecycleScope.launch {
            TokenStorage.saveToken(this@MainActivity, token.value)
            tryLoginWithToken(token.value)
        }
    }

    private fun onProcessError(exception: Exception) {
        Log.e("IVAN", "Auth error", exception)
        // TODO: show error UI
    }

    private fun onCancelled() {
        Log.d("IVAN", "Auth cancelled")
        // Re-launch auth so the user can retry
        launcher.launch(YandexAuthLoginOptions())
    }

    // Deep link: ivan://join/{token}  or  https://ivan.example.com/join/{token}
    private fun extractInviteToken(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        val path = uri.path ?: return null
        return if (path.startsWith("/join/")) path.removePrefix("/join/") else null
    }
}
