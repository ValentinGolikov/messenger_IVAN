package com.example

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val yandexId = varchar("yandex_id", 30).uniqueIndex()
    val displayName = varchar("display_name", 50)
    val realName = varchar("real_name", 100).nullable()
    val email = varchar("email", 100).nullable()
    override val primaryKey = PrimaryKey(id)
}

/**
 * Contacts — mutual friendship.
 * A contact relationship is directional: (userId → contactId) means userId added contactId.
 * Mutual = both directions exist.
 */
object Contacts : Table("contacts") {
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val contactId = integer("contact_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(userId, contactId)
}

/**
 * Chats — both DMs and groups.
 * type: "dm" | "group"
 * For DMs, title/avatarUrl are null; name is derived from the other participant.
 */
object Chats : Table("chats") {
    val id = integer("id").autoIncrement()
    val type = varchar("type", 10) // "dm" | "group"
    val title = varchar("title", 200).nullable()
    val avatarUrl = text("avatar_url").nullable()
    val createdBy = integer("created_by").references(Users.id)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * ChatParticipants — who is in which chat.
 * role: "owner" | "member"
 */
object ChatParticipants : Table("chat_participants") {
    val chatId = integer("chat_id").references(Chats.id, onDelete = ReferenceOption.CASCADE)
    val userId = integer("user_id").references(Users.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 10).default("member") // "owner" | "member"
    val joinedAt = long("joined_at")
    override val primaryKey = PrimaryKey(chatId, userId)
}

/**
 * Messages — belong to a chat.
 */
object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val chatId = integer("chat_id").references(Chats.id, onDelete = ReferenceOption.CASCADE)
    val senderId = integer("sender_id").references(Users.id)
    val text = text("text")
    val timestamp = long("timestamp")
    val isDeleted = bool("is_deleted").default(false)
    override val primaryKey = PrimaryKey(id)
}

/**
 * ChatInvites — shareable invite links for group chats.
 * token is a random UUID used in the invite URL.
 * maxUses: null = unlimited
 * expiresAt: null = never expires
 */
object ChatInvites : Table("chat_invites") {
    val id = integer("id").autoIncrement()
    val chatId = integer("chat_id").references(Chats.id, onDelete = ReferenceOption.CASCADE)
    val createdBy = integer("created_by").references(Users.id)
    val token = varchar("token", 64).uniqueIndex()
    val maxUses = integer("max_uses").nullable()
    val currentUses = integer("current_uses").default(0)
    val expiresAt = long("expires_at").nullable()
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}
