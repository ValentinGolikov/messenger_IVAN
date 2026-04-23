package com.example.ivan.main

import com.example.ivan.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.*
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.Parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── DTOs ──────────────────────────────────────────────────────────────────────

@Serializable
data class LoginResponse(
    val userId: Int,
    val yandexData: YandexUserDto
)

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
    val chatId: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class MessageDto(
    val id: Int = 0,
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long
)

@Serializable
data class ChatDto(
    val id: Int,
    val type: String,           // "dm" | "group"
    val title: String?,
    val avatarUrl: String?,
    val otherUserId: Int?,
    val otherUserName: String?,
    val lastMessage: MessageDto?,
    val unreadCount: Int
)

@Serializable
data class UserDto(
    val id: Int,
    val displayName: String,
    val realName: String? = null,
    val isMutualContact: Boolean = false
)

@Serializable
data class CreateGroupResponse(
    val chatId: Int,
    val inviteToken: String
)

@Serializable
data class InviteDto(
    val token: String,
    val chatId: Int,
    val chatTitle: String?,
    val chatType: String,
    val createdBy: Int,
    val currentUses: Int,
    val maxUses: Int?,
    val expiresAt: Long?
)

@Serializable
data class JoinByInviteResponse(
    val chatId: Int,
    val chatTitle: String?,
    val chatType: String,
    val alreadyMember: Boolean
)

/** Generic WebSocket envelope */
@Serializable
data class WsEnvelope(
    val type: String,
    val payload: String
)

// ── HTTP client singleton ─────────────────────────────────────────────────────

object NetworkClient {
    private val BASE_URL = "http://${BuildConfig.SERVER_URL}"

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
        install(Logging) { level = LogLevel.BODY }
        install(WebSockets)
    }

    fun buildUrl(path: String): String = "$BASE_URL$path"

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun login(token: String): LoginResponse =
        httpClient.post(buildUrl("/login")) {
            setBody(FormDataContent(Parameters.build { append("token", token) }))
        }.body()

    // ── Users / Contacts ──────────────────────────────────────────────────────

    suspend fun searchUsers(query: String, selfId: Int): List<UserDto> =
        httpClient.get(buildUrl("/users/search")) {
            parameter("q", query)
            parameter("selfId", selfId)
        }.body()

    suspend fun addContact(userId: Int, contactId: Int) {
        httpClient.post(buildUrl("/contacts/add")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("contactId", contactId.toString())
            }))
        }
    }

    suspend fun getContacts(userId: Int): List<UserDto> =
        httpClient.get(buildUrl("/contacts/$userId")).body()

    // ── Chats ─────────────────────────────────────────────────────────────────

    suspend fun getChats(userId: Int): List<ChatDto> =
        httpClient.get(buildUrl("/chats/$userId")).body()

    suspend fun createGroup(userId: Int, title: String): CreateGroupResponse =
        httpClient.post(buildUrl("/chats/group")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("title", title)
            }))
        }.body()

    suspend fun openDm(userId: Int, otherUserId: Int): Int {
        val resp = httpClient.post(buildUrl("/chats/dm")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("otherUserId", otherUserId.toString())
            }))
        }.body<Map<String, Int>>()
        return resp["chatId"]!!
    }

    suspend fun getChatMessages(chatId: Int): List<MessageDto> =
        httpClient.get(buildUrl("/chats/$chatId/messages")).body()

    // ── Invites ───────────────────────────────────────────────────────────────

    suspend fun createInvite(chatId: Int, userId: Int): String {
        val resp = httpClient.post(buildUrl("/chats/$chatId/invites")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
            }))
        }.body<Map<String, String>>()
        return resp["token"]!!
    }

    suspend fun getInviteInfo(token: String): InviteDto =
        httpClient.get(buildUrl("/invites/$token")).body()

    suspend fun joinByInvite(token: String, userId: Int): JoinByInviteResponse =
        httpClient.post(buildUrl("/invites/$token/join")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
            }))
        }.body()

    suspend fun inviteUserToChat(chatId: Int, inviterId: Int, targetId: Int): Map<String, String> =
        httpClient.post(buildUrl("/chats/$chatId/invite-user")) {
            setBody(FormDataContent(Parameters.build {
                append("inviterId", inviterId.toString())
                append("targetId", targetId.toString())
            }))
        }.body()
}
