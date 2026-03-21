package com.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class YandexUserDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("real_name") val realName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val login: String? = null,
    val sex: String? = null,
    @SerialName("default_email") val email: String? = null,
    @SerialName("default_phone") val defaultPhone: YandexPhoneDto? = null,
    val psuid: String? = null
)

@Serializable
data class YandexPhoneDto(
    val id: Long,
    val number: String
)

@Serializable
data class ChatMessage(
    val senderId: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AuthResponse(
    val userId: Int,
    val yandexData: YandexUserDto
)