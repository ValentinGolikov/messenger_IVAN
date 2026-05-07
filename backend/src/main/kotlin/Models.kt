package com.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Yandex OAuth ──────────────────────────────────────────────────────────────

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

// ── Auth ──────────────────────────────────────────────────────────────────────

@Serializable
data class AuthResponse(
    val userId: Int,
    val yandexData: YandexUserDto
)

// ── Chats ─────────────────────────────────────────────────────────────────────

@Serializable
data class ChatDto(
    val id: Int,
    val type: String,           // "dm" | "group"
    val title: String?,         // null for DMs — client derives from other participant
    val avatarUrl: String?,
    val otherUserId: Int?,      // populated for DMs
    val otherUserName: String?, // populated for DMs
    val lastMessage: MessageDto?,
    val unreadCount: Int
)

@Serializable
data class CreateGroupRequest(
    val title: String,
    val memberIds: List<Int> = emptyList()
)

@Serializable
data class CreateGroupResponse(
    val chatId: Int,
    val inviteToken: String
)

// ── Messages ──────────────────────────────────────────────────────────────────

/** Sent from client → server over WebSocket */
@Serializable
data class ChatMessage(
    val chatId: Int,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val clientId: String? = null  // optional idempotency key from client
)

/** Broadcast to clients over WebSocket / returned in history */
@Serializable
data class MessageDto(
    val id: String = "",        // TimeUUID as string
    val chatId: Int,
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long,
    val status: String = "sent" // "sent" | "delivered" | "read"
)

// ── Contacts ──────────────────────────────────────────────────────────────────

@Serializable
data class UserDto(
    val id: Int,
    val displayName: String,
    val realName: String? = null,
    val isMutualContact: Boolean = false
)

// ── Invites ───────────────────────────────────────────────────────────────────

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

// ── WebSocket events ──────────────────────────────────────────────────────────

/**
 * Generic envelope sent over the WebSocket so the client can distinguish
 * message types without a separate connection per feature.
 *
 * type: "message" | "chat_created" | "user_added" | "status_update" | "presence" | "read_ack" | "pin_update"
 */
@Serializable
data class WsEnvelope(
    val type: String,
    val payload: String // JSON-encoded payload
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
    val online: Boolean
)

/** Client → server: "I read messages up to this ID" */
@Serializable
data class ReadAckRequest(
    val chatId: Int,
    val lastMessageId: String
)

// ── Pinned messages ───────────────────────────────────────────────────────────

/** Returned by GET /chats/{chatId}/pin and included in pin_update WS event */
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

/** WebSocket event: a message was pinned or unpinned */
@Serializable
data class PinEvent(
    val chatId: Int,
    val pinnedMessage: PinnedMessageDto? // null = unpinned
)
