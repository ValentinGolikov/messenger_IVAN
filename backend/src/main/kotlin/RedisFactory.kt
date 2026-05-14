package com.example

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands

object RedisFactory {

    private lateinit var client: RedisClient
    private lateinit var commands: RedisCommands<String, String>

    private const val ONLINE_TTL = 60L // seconds
    private const val ONLINE_PREFIX = "user:online:"
    private const val LAST_SEEN_PREFIX = "user:last_seen:"
    private const val CHAT_LIST_PREFIX = "cache:chats:"
    private const val CHAT_LIST_TTL = 10L // seconds — short TTL, chat list changes often

    fun init() {
        val host = System.getenv("REDIS_HOST") ?: "localhost"
        val port = System.getenv("REDIS_PORT") ?: "6379"
        client = RedisClient.create("redis://$host:$port")
        val connection = client.connect()
        commands = connection.sync()
    }

    /** Mark user as online with TTL */
    fun setOnline(userId: Int) {
        commands.setex("$ONLINE_PREFIX$userId", ONLINE_TTL, "1")
    }

    /** Remove online status */
    fun setOffline(userId: Int) {
        commands.del("$ONLINE_PREFIX$userId")
    }

    /** Check if user is online */
    fun isOnline(userId: Int): Boolean {
        return commands.exists("$ONLINE_PREFIX$userId") > 0
    }

    /** Refresh TTL (heartbeat) */
    fun heartbeat(userId: Int) {
        commands.expire("$ONLINE_PREFIX$userId", ONLINE_TTL)
    }

    /** Batch check online status for multiple users */
    fun getOnlineUsers(userIds: List<Int>): Map<Int, Boolean> {
        if (userIds.isEmpty()) return emptyMap()
        val pipeline = client.connect().sync()
        return userIds.associateWith { id ->
            pipeline.exists("$ONLINE_PREFIX$id") > 0
        }
    }

    /** Store last-seen timestamp (persistent, no TTL) */
    fun setLastSeen(userId: Int, timestamp: Long) {
        commands.set("$LAST_SEEN_PREFIX$userId", timestamp.toString())
    }

    /** Get last-seen timestamp for a single user */
    fun getLastSeen(userId: Int): Long? {
        return commands.get("$LAST_SEEN_PREFIX$userId")?.toLongOrNull()
    }

    /** Batch get last-seen timestamps */
    fun getLastSeenBatch(userIds: List<Int>): Map<Int, Long?> {
        if (userIds.isEmpty()) return emptyMap()
        return userIds.associateWith { id ->
            commands.get("$LAST_SEEN_PREFIX$id")?.toLongOrNull()
        }
    }

    fun shutdown() {
        if (::client.isInitialized) client.shutdown()
    }

    // ── Chat list cache ───────────────────────────────────────────────────────

    /** Cache serialized chat list JSON for a user */
    fun setChatListCache(userId: Int, json: String) {
        commands.setex("$CHAT_LIST_PREFIX$userId", CHAT_LIST_TTL, json)
    }

    /** Get cached chat list JSON for a user, null if not cached */
    fun getChatListCache(userId: Int): String? {
        return commands.get("$CHAT_LIST_PREFIX$userId")
    }

    /** Invalidate chat list cache for a user (call when new message arrives) */
    fun invalidateChatListCache(userId: Int) {
        commands.del("$CHAT_LIST_PREFIX$userId")
    }

    /** Invalidate chat list cache for multiple users at once */
    fun invalidateChatListCacheForUsers(userIds: List<Int>) {
        if (userIds.isEmpty()) return
        val keys = userIds.map { "$CHAT_LIST_PREFIX$it" }.toTypedArray()
        commands.del(*keys)
    }
}
