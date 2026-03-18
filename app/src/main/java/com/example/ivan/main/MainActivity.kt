package com.example.ivan.main

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch
import io.ktor.http.Parameters
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.client.plugins.websocket.*
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sdk = YandexAuthSdk.create(YandexAuthOptions(this))
        val launcher = registerForActivityResult(sdk.contract) { result -> handleResult(result) }
        val loginOptions = YandexAuthLoginOptions()
        launcher.launch(loginOptions)
        setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "main"
            ) {
                composable("main") {
                    MainScreen(
                        onNavigateToChat = {
                            navController.navigate("chat")
                        }
                    )
                }
                composable("chat") {
                    ChatScreen()
                }
            }

        }
    }
    private fun handleResult(result: YandexAuthResult) {
        when (result) {
            is YandexAuthResult.Success -> onSuccessAuth(result.token)
            is YandexAuthResult.Failure -> onProccessError(result.exception)
            YandexAuthResult.Cancelled -> onCancelled()
        }
    }

    private fun onSuccessAuth(token: YandexAuthToken) {
        lifecycleScope.launch {
            try {
                val response: LoginResponse = NetworkClient.httpClient.post(NetworkClient.buildUrl("/login")) {
                    setBody(FormDataContent(Parameters.build {
                        append("token", token.value)
                    }))
                }.body()

                val internalId = response.userId
                val name = response.yandexData.displayName
                val email = response.yandexData.email
                val realName = response.yandexData.realName

                Log.d("test", "Вход выполнен: $name $realName ($email). Наш ID: $internalId")
                // TODO() - Сделать профиль в котором отображается информация о пользователе
                connectToChat(internalId)

            } catch (e: Exception) {
                Log.e("test", "Ошибка при получении данных", e)
            }
        }
    }

    private fun onProccessError(exception: Exception) {
        TODO()
    }

    private fun onCancelled() {
        TODO()
    }

    private fun connectToChat(userId: Int) {
        lifecycleScope.launch {
            try {
                NetworkClient.httpClient.webSocket("ws://192.168.0.105:8080/chat/$userId") {

                    val testMessage = ChatMessage(senderId = userId, text = "Привет из Android!")
                    send(Frame.Text(Json.encodeToString(ChatMessage.serializer(), testMessage)))

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val receivedMessage = Json.decodeFromString<ChatMessage>(frame.readText())
                            Log.d("test", "Новое сообщение: ${receivedMessage.text}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("test", "Ошибка WebSocket", e)
            }
        }
    }
}

