package com.example

import io.lettuce.core.RedisClient
import io.lettuce.core.api.sync.RedisCommands

object RedisFactory {

    private lateinit var client: RedisClient
    private lateinit var commands: RedisCommands<String, String>

    private const val ONLINE_TTL = 60L // seconds
    private const val ONLINE_PREFIX = "user:online:"

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

    fun shutdown() {
        if (::client.isInitialized) client.shutdown()
    }
}
