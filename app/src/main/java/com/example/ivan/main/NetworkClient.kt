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
import io.ktor.client.statement.*
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
    val timestamp: Long = System.currentTimeMillis(),
    val clientId: String? = null
)

@Serializable
data class MessageDto(
    val id: String = "",        // TimeUUID as string
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val status: String = "sent", // "sent" | "delivered" | "read"
    val messageType: String = "text" // "text" | "system"
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

/** Notification about message status change */
@Serializable
data class StatusUpdateEvent(
    val messageId: String,
    val chatId: Int,
    val status: String // "delivered" | "read"
)

/** User online/offline event */
@Serializable
data class PresenceEvent(
    val userId: Int,
    val online: Boolean,
    val lastSeen: Long? = null
)

/** Client → server: "I read messages up to this ID" */
@Serializable
data class ReadAckRequest(
    val chatId: Int,
    val lastMessageId: String
)

// ── Pinned messages ───────────────────────────────────────────────────────────

@Serializable
data class PinnedMessageDto(
    val messageId: String,
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val pinnedBy: Int,
    val pinnedAt: Long
)

@Serializable
data class PinEvent(
    val chatId: Int,
    val pinnedMessage: PinnedMessageDto?,
    val action: String = "pin" // "pin" | "unpin"
)

// ── Presence ──────────────────────────────────────────────────────────────────

@Serializable
data class UserPresenceDto(
    val online: Boolean,
    val lastSeen: Long? = null
)

// ── Group members ─────────────────────────────────────────────────────────────

@Serializable
data class MemberDto(
    val id: Int,
    val displayName: String,
    val role: String, // "owner" | "member"
    val online: Boolean,
    val lastSeen: Long? = null
)

// ── Deletion / leave events ───────────────────────────────────────────────────

@Serializable
data class MessageDeletedEvent(
    val chatId: Int,
    val messageId: String
)

@Serializable
data class ChatRemovedEvent(
    val chatId: Int,
    val reason: String // "deleted" | "kicked" | "group_deleted"
)

@Serializable
data class OwnerChangedEvent(
    val chatId: Int,
    val newOwnerId: Int
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

    suspend fun getChatMessages(chatId: Int, userId: Int? = null): List<MessageDto> =
        httpClient.get(buildUrl("/chats/$chatId/messages")) {
            userId?.let { parameter("userId", it) }
        }.body()

    // ── Read status ───────────────────────────────────────────────────────────

    suspend fun markAsRead(chatId: Int, userId: Int, lastMessageId: String) {
        httpClient.post(buildUrl("/chats/$chatId/read")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("lastMessageId", lastMessageId)
            }))
        }
    }

    // ── Online status ─────────────────────────────────────────────────────────

    suspend fun getOnlineStatus(userIds: List<Int>): Map<Int, Boolean> {
        if (userIds.isEmpty()) return emptyMap()
        return httpClient.get(buildUrl("/users/online")) {
            parameter("ids", userIds.joinToString(","))
        }.body()
    }

    // ── Presence (online + lastSeen) ──────────────────────────────────────────

    suspend fun getUserPresence(userIds: List<Int>): Map<Int, UserPresenceDto> {
        if (userIds.isEmpty()) return emptyMap()
        return httpClient.get(buildUrl("/users/presence")) {
            parameter("ids", userIds.joinToString(","))
        }.body()
    }

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

    // ── Pinned messages ───────────────────────────────────────────────────────

    suspend fun pinMessage(chatId: Int, userId: Int, messageId: String): PinnedMessageDto =
        httpClient.post(buildUrl("/chats/$chatId/pin")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("messageId", messageId)
            }))
        }.body()

    suspend fun unpinMessage(chatId: Int, userId: Int, messageId: String) {
        httpClient.delete(buildUrl("/chats/$chatId/pin")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("messageId", messageId)
            }))
        }
    }

    suspend fun getPinnedMessages(chatId: Int): List<PinnedMessageDto> =
        httpClient.get(buildUrl("/chats/$chatId/pins")).body()

    // ── Message deletion ──────────────────────────────────────────────────────

    suspend fun deleteMessage(chatId: Int, messageId: String, userId: Int, forAll: Boolean) {
        httpClient.post(buildUrl("/chats/$chatId/messages/$messageId/delete")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("forAll", forAll.toString())
            }))
        }
    }

    // ── Chat deletion ─────────────────────────────────────────────────────────

    suspend fun deleteChat(chatId: Int, userId: Int, forAll: Boolean) {
        httpClient.post(buildUrl("/chats/$chatId/delete")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("forAll", forAll.toString())
            }))
        }
    }

    // ── Leave / delete group ──────────────────────────────────────────────────

    suspend fun leaveGroup(chatId: Int, userId: Int, newOwnerId: Int? = null) {
        httpClient.post(buildUrl("/chats/$chatId/leave")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                newOwnerId?.let { append("newOwnerId", it.toString()) }
            }))
        }
    }

    suspend fun deleteGroup(chatId: Int, userId: Int) {
        httpClient.delete(buildUrl("/chats/$chatId")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
            }))
        }
    }

    // ── Group members ─────────────────────────────────────────────────────────

    suspend fun getMembers(chatId: Int): List<MemberDto> =
        httpClient.get(buildUrl("/chats/$chatId/members")).body()

    suspend fun kickMember(chatId: Int, userId: Int, targetId: Int) {
        httpClient.post(buildUrl("/chats/$chatId/kick")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("targetId", targetId.toString())
            }))
        }
    }

    suspend fun transferOwner(chatId: Int, userId: Int, targetId: Int) {
        httpClient.post(buildUrl("/chats/$chatId/transfer-owner")) {
            setBody(FormDataContent(Parameters.build {
                append("userId", userId.toString())
                append("targetId", targetId.toString())
            }))
        }
    }
}
