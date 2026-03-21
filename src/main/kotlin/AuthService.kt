package com.example

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class AuthService(private val httpClient: HttpClient) {
    suspend fun verifyToken(token: String): YandexUserDto? = try {
        httpClient.get("https://login.yandex.ru/info") {
            header("Authorization", "OAuth $token")
        }.body()
    } catch (e: Exception) {
        println("Auth error: ${e.message}")
        null
    }
}