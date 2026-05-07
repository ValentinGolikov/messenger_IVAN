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
    val status: String = "sent", // "sent" | "delivered" | "read"
    val messageType: String = "text" // "text" | "system"
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
 * type: "message" | "chat_created" | "user_added" | "status_update"
 *     | "presence" | "read_ack" | "pin_update"
 *     | "message_deleted" | "chat_removed" | "owner_changed"
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
    val senderId: Int,
    val status: String // "delivered" | "read"
)

/** User online/offline event */
@Serializable
data class PresenceEvent(
    val userId: Int,
    val online: Boolean,
    val lastSeen: Long? = null // timestamp when user went offline, null if online
)

/** Client → server: "I read messages up to this ID" */
@Serializable
data class ReadAckRequest(
    val chatId: Int,
    val lastMessageId: String
)

// ── Pinned messages ───────────────────────────────────────────────────────────

/** Returned by GET /chats/{chatId}/pins and included in pin_update WS event */
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
    val pinnedMessage: PinnedMessageDto?, // null when action = "unpin"
    val action: String = "pin" // "pin" | "unpin"
)

// ── Presence ──────────────────────────────────────────────────────────────────

/** Returned by GET /users/presence */
@Serializable
data class UserPresenceDto(
    val online: Boolean,
    val lastSeen: Long? = null
)

// ── Group members ─────────────────────────────────────────────────────────────

/** Returned by GET /chats/{chatId}/members */
@Serializable
data class MemberDto(
    val id: Int,
    val displayName: String,
    val role: String, // "owner" | "member"
    val online: Boolean,
    val lastSeen: Long? = null
)

// ── Deletion / leave events ───────────────────────────────────────────────────

/** WS event: a message was deleted for all participants */
@Serializable
data class MessageDeletedEvent(
    val chatId: Int,
    val messageId: String
)

/** WS event: chat removed (DM deleted for both / group deleted / user kicked) */
@Serializable
data class ChatRemovedEvent(
    val chatId: Int,
    val reason: String // "deleted" | "kicked" | "group_deleted"
)

/** WS event: group ownership transferred */
@Serializable
data class OwnerChangedEvent(
    val chatId: Int,
    val newOwnerId: Int
)
