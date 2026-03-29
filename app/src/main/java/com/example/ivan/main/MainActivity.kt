package com.example.ivan.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ivan.chat.ChatScreen
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import com.yandex.authsdk.YandexAuthToken
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Parameters
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var sdk: YandexAuthSdk
    private lateinit var launcher: ActivityResultLauncher<YandexAuthLoginOptions>
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = YandexAuthSdk.create(YandexAuthOptions(this))
        launcher = registerForActivityResult(sdk.contract) { result -> handleResult(result) }

        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(onNavigateToChat = { navController.navigate("chat") })
                }
                composable("chat") {
                    ChatScreen(userId = currentUserId)
                }
            }
        }

        lifecycleScope.launch {
            val savedToken = TokenStorage.getToken(this@MainActivity)
            Log.d("test", "Сохранённый токен: $savedToken")
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

    private suspend fun tryLoginWithToken(token: String): Boolean {
        return try {
            val response: HttpResponse = NetworkClient.httpClient.post(
                NetworkClient.buildUrl("/login")
            ) {
                setBody(FormDataContent(Parameters.build {
                    append("token", token)
                }))
            }

            if (response.status == HttpStatusCode.Unauthorized) {
                return false
            }

            val loginResponse: LoginResponse = response.body()
            currentUserId = loginResponse.userId
            Log.d("test", "Автовход: ${loginResponse.yandexData.displayName}. ID: $currentUserId")
            true
        } catch (e: Exception) {
            Log.e("test", "Ошибка при входе", e)
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
            Log.d("test", "Сохраняем токен: ${token.value}")
            TokenStorage.saveToken(this@MainActivity, token.value)
            val saved = TokenStorage.getToken(this@MainActivity)
            Log.d("test", "Токен после сохранения: $saved")
            tryLoginWithToken(token.value)
        }
    }

    private fun onProcessError(exception: Exception) {
        TODO()
    }

    private fun onCancelled() {
        TODO()
    }
}