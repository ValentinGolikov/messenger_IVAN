package com.example

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.BoundStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.core.uuid.Uuids
import java.net.InetSocketAddress
import java.util.UUID

object CassandraFactory {

    private lateinit var session: CqlSession
    private lateinit var keyspace: String

    fun init() {
        val host = System.getenv("CASSANDRA_HOST") ?: "localhost"
        val port = (System.getenv("CASSANDRA_PORT") ?: "9042").toInt()
        keyspace = System.getenv("CASSANDRA_KEYSPACE") ?: "messenger"

        session = CqlSession.builder()
            .addContactPoint(InetSocketAddress(host, port))
            .withLocalDatacenter("datacenter1")
            .build()

        // Create keyspace
        session.execute("""
            CREATE KEYSPACE IF NOT EXISTS $keyspace
            WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
        """.trimIndent())

        session.execute("USE $keyspace")

        // Create messages table
        session.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                chat_id    int,
                id         timeuuid,
                sender_id  int,
                text       text,
                timestamp  bigint,
                status     text,
                is_deleted boolean,
                message_type text,
                PRIMARY KEY (chat_id, id)
            ) WITH CLUSTERING ORDER BY (id ASC)
        """.trimIndent())

        // Idempotent migration: add message_type column if table existed before
        try {
            session.execute("ALTER TABLE messages ADD message_type text")
        } catch (_: Exception) {
            // Column already exists — ignore
        }
    }

    /** Generate a new TimeUUID (v1) for a message */
    fun newTimeUuid(): UUID = Uuids.timeBased()

    /** Insert a new message, returns the generated TimeUUID */
    fun insertMessage(chatId: Int, senderId: Int, text: String, timestamp: Long, status: String = "sent"): UUID {
        val id = newTimeUuid()
        session.execute(
            "INSERT INTO messages (chat_id, id, sender_id, text, timestamp, status, is_deleted, message_type) VALUES (?, ?, ?, ?, ?, ?, false, 'text')",
            chatId, id, senderId, text, timestamp, status
        )
        return id
    }

    /** Insert a system message (e.g. "User left the group"), returns the generated TimeUUID */
    fun insertSystemMessage(chatId: Int, text: String, timestamp: Long): UUID {
        val id = newTimeUuid()
        session.execute(
            "INSERT INTO messages (chat_id, id, sender_id, text, timestamp, status, is_deleted, message_type) VALUES (?, ?, 0, ?, ?, 'sent', false, 'system')",
            chatId, id, text, timestamp
        )
        return id
    }

    /** Get last N messages for a chat (oldest first) */
    fun getMessages(chatId: Int, limit: Int = 50): List<CassandraMessage> {
        // We want the last N messages sorted oldest-first.
        // Cassandra sorts ASC by default. To get the "last N" we reverse and re-reverse:
        val rs = session.execute(
            "SELECT id, chat_id, sender_id, text, timestamp, status, is_deleted, message_type FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT ?",
            chatId, limit
        )
        return rs.map { row -> row.toCassandraMessage() }.reversed()
    }

    /** Get the last message for a chat (for chat list preview) */
    fun getLastMessage(chatId: Int): CassandraMessage? {
        val rs = session.execute(
            "SELECT id, chat_id, sender_id, text, timestamp, status, is_deleted, message_type FROM messages WHERE chat_id = ? ORDER BY id DESC LIMIT 1",
            chatId
        )
        return rs.firstOrNull()?.toCassandraMessage()
    }

    /** Update message status */
    fun updateMessageStatus(chatId: Int, messageId: UUID, newStatus: String) {
        session.execute(
            "UPDATE messages SET status = ? WHERE chat_id = ? AND id = ?",
            newStatus, chatId, messageId
        )
    }

    /** Get a single message by chatId and messageId */
    fun getMessageById(chatId: Int, messageId: UUID): CassandraMessage? {
        val rs = session.execute(
            "SELECT id, chat_id, sender_id, text, timestamp, status, is_deleted, message_type FROM messages WHERE chat_id = ? AND id = ?",
            chatId, messageId
        )
        return rs.firstOrNull()?.toCassandraMessage()
    }

    /** Delete a message for all: set is_deleted=true and clear the text */
    fun deleteMessageForAll(chatId: Int, messageId: UUID) {
        session.execute(
            "UPDATE messages SET is_deleted = true, text = '' WHERE chat_id = ? AND id = ?",
            chatId, messageId
        )
    }

    /** Delete all messages in a chat (physically removes rows) */
    fun deleteAllChatMessages(chatId: Int) {
        session.execute(
            "DELETE FROM messages WHERE chat_id = ?",
            chatId
        )
    }

    /** Mark all messages up to (and including) lastMessageId as read, for a specific sender */
    fun markMessagesAsRead(chatId: Int, lastMessageId: UUID, readerUserId: Int): List<Pair<UUID, Int>> {
        // Fetch messages up to lastMessageId that are not from the reader and not yet 'read'
        val rs = session.execute(
            "SELECT id, sender_id, status FROM messages WHERE chat_id = ? AND id <= ?",
            chatId, lastMessageId
        )
        val updated = mutableListOf<Pair<UUID, Int>>() // (messageId, senderId)
        for (row in rs) {
            val senderId = row.getInt("sender_id")
            val status = row.getString("status")
            if (senderId != readerUserId && status != "read") {
                val msgId = row.getUuid("id")!!
                session.execute(
                    "UPDATE messages SET status = 'read' WHERE chat_id = ? AND id = ?",
                    chatId, msgId
                )
                updated.add(msgId to senderId)
            }
        }
        return updated
    }

    fun shutdown() {
        if (::session.isInitialized) session.close()
    }

    private fun Row.toCassandraMessage(): CassandraMessage {
        return CassandraMessage(
            id = getUuid("id")!!,
            chatId = getInt("chat_id"),
            senderId = getInt("sender_id"),
            text = getString("text") ?: "",
            timestamp = getLong("timestamp"),
            status = getString("status") ?: "sent",
            isDeleted = getBoolean("is_deleted"),
            messageType = getString("message_type") ?: "text"
        )
    }
}

data class CassandraMessage(
    val id: UUID,
    val chatId: Int,
    val senderId: Int,
    val text: String,
    val timestamp: Long,
    val status: String,
    val isDeleted: Boolean,
    val messageType: String = "text" // "text" | "system"
)
