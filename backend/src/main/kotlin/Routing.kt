package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

fun Application.configureRouting(authService: AuthService) {
    routing {

        // ── Auth ─────────────────────────────────────────────────────────────

        post("/login") {
            val token = call.receiveParameters()["token"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val yandexUser = authService.verifyToken(token)
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = DatabaseFactory.dbQuery {
                Users.upsert(Users.yandexId) {
                    it[yandexId] = yandexUser.id
                    it[displayName] = yandexUser.displayName
                    it[realName] = yandexUser.realName
                    it[email] = yandexUser.email
                }[Users.id]
            }

            call.respond(AuthResponse(userId = userId, yandexData = yandexUser))
        }

        post("/auth/password/register") {
            val params = call.receiveParameters()
            val login = params["login"]?.normalizeLogin()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Login is required")
            val password = params["password"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Password is required")
            val displayName = params["displayName"]?.trim()?.takeIf { it.isNotBlank() } ?: login

            if (!isValidLogin(login) || password.length < 6) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid login or password")
            }

            val existing = DatabaseFactory.dbQuery {
                PasswordAccounts.selectAll().where { PasswordAccounts.login eq login }.count() > 0
            }
            if (existing) return@post call.respond(HttpStatusCode.Conflict, "Login already exists")

            val salt = newSalt()
            val passwordHash = hashPassword(password, salt)
            val now = System.currentTimeMillis()

            val userId = DatabaseFactory.dbQuery {
                val id = Users.insert {
                    it[yandexId] = passwordUserExternalId(login)
                    it[Users.displayName] = displayName.take(50)
                    it[realName] = null
                    it[email] = null
                }[Users.id]

                PasswordAccounts.insert {
                    it[PasswordAccounts.login] = login
                    it[PasswordAccounts.userId] = id
                    it[PasswordAccounts.passwordHash] = passwordHash
                    it[PasswordAccounts.salt] = salt
                    it[createdAt] = now
                }

                id
            }

            call.respond(passwordAuthResponse(userId, displayName, login))
        }

        post("/auth/password/login") {
            val params = call.receiveParameters()
            val login = params["login"]?.normalizeLogin()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Login is required")
            val password = params["password"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Password is required")

            val row = DatabaseFactory.dbQuery {
                (PasswordAccounts innerJoin Users)
                    .select(
                        PasswordAccounts.login,
                        PasswordAccounts.userId,
                        PasswordAccounts.passwordHash,
                        PasswordAccounts.salt,
                        Users.displayName
                    )
                    .where { PasswordAccounts.login eq login }
                    .singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid login or password")

            val expectedHash = row[PasswordAccounts.passwordHash]
            val actualHash = hashPassword(password, row[PasswordAccounts.salt])
            if (actualHash != expectedHash) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Invalid login or password")
            }

            call.respond(passwordAuthResponse(row[PasswordAccounts.userId], row[Users.displayName], login))
        }

        // ── Users / Contacts ─────────────────────────────────────────────────

        /** Search users by display name (excluding self) */
        get("/users/search") {
            val query = call.request.queryParameters["q"] ?: ""
            val selfId = call.request.queryParameters["selfId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val users = DatabaseFactory.dbQuery {
                val mutualContactIds = getMutualContactIds(selfId)
                Users.selectAll()
                    .where {
                        (Users.displayName.lowerCase() like "%${query.lowercase()}%") and
                                (Users.id neq selfId)
                    }
                    .limit(20)
                    .map { row ->
                        UserDto(
                            id = row[Users.id],
                            displayName = row[Users.displayName],
                            realName = row[Users.realName],
                            isMutualContact = row[Users.id] in mutualContactIds
                        )
                    }
            }
            call.respond(users)
        }

        /** Add a contact (one-directional) */
        post("/contacts/add") {
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val contactId = params["contactId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            if (userId == contactId) return@post call.respond(HttpStatusCode.BadRequest)

            DatabaseFactory.dbQuery {
                Contacts.upsert(Contacts.userId, Contacts.contactId) {
                    it[Contacts.userId] = userId
                    it[Contacts.contactId] = contactId
                    it[createdAt] = System.currentTimeMillis()
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        /** Get contacts for a user */
        get("/contacts/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val contacts = DatabaseFactory.dbQuery {
                val mutualIds = getMutualContactIds(userId)
                // All contacts this user added
                val addedIds = Contacts.selectAll()
                    .where { Contacts.userId eq userId }
                    .map { it[Contacts.contactId] }

                if (addedIds.isEmpty()) return@dbQuery emptyList()

                Users.selectAll()
                    .where { Users.id inList addedIds }
                    .map { row ->
                        UserDto(
                            id = row[Users.id],
                            displayName = row[Users.displayName],
                            realName = row[Users.realName],
                            isMutualContact = row[Users.id] in mutualIds
                        )
                    }
            }
            call.respond(contacts)
        }

        // ── Chats ─────────────────────────────────────────────────────────────

        /** Get all chats for a user */
        get("/chats/{userId}") {
            val userId = call.parameters["userId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val chats = DatabaseFactory.dbQuery {
                getChatListForUser(userId)
            }
            call.respond(chats)
        }

        /** Create a group chat */
        post("/chats/group") {
            val params = call.receiveParameters()
            val creatorId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val title = params["title"]?.takeIf { it.isNotBlank() }
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val now = System.currentTimeMillis()
            val inviteToken = UUID.randomUUID().toString()

            val chatId = DatabaseFactory.dbQuery {
                val cId = Chats.insert {
                    it[type] = "group"
                    it[Chats.title] = title
                    it[createdBy] = creatorId
                    it[createdAt] = now
                }[Chats.id]

                ChatParticipants.insert {
                    it[chatId] = cId
                    it[userId] = creatorId
                    it[role] = "owner"
                    it[joinedAt] = now
                }

                ChatInvites.insert {
                    it[ChatInvites.chatId] = cId
                    it[ChatInvites.createdBy] = creatorId
                    it[token] = inviteToken
                    it[createdAt] = now
                }

                cId
            }

            call.respond(CreateGroupResponse(chatId = chatId, inviteToken = inviteToken))
        }

        /**
         * Open or get an existing DM chat between two users.
         * Creates one if it doesn't exist.
         */
        post("/chats/dm") {
            val params = call.receiveParameters()
            val userA = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val userB = params["otherUserId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            if (userA == userB) return@post call.respond(HttpStatusCode.BadRequest)

            val chatId = DatabaseFactory.dbQuery {
                findOrCreateDm(userA, userB)
            }
            call.respond(mapOf("chatId" to chatId))
        }

        /** Get messages for a chat (last 50, oldest first) — from Cassandra */
        get("/chats/{chatId}/messages") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val senderNames = mutableMapOf<Int, String>()

            val messages = CassandraFactory.getMessages(chatId, 50)
                .filter { !it.isDeleted }
                .map { msg ->
                    val senderName = senderNames.getOrPut(msg.senderId) {
                        DatabaseFactory.dbQuery {
                            Users.selectAll()
                                .where { Users.id eq msg.senderId }
                                .singleOrNull()?.get(Users.displayName) ?: "Unknown"
                        }
                    }
                    MessageDto(
                        id = msg.id.toString(),
                        chatId = msg.chatId,
                        senderId = msg.senderId,
                        senderName = senderName,
                        text = msg.text,
                        timestamp = msg.timestamp,
                        status = msg.status
                    )
                }
            call.respond(messages)
        }

        /** Mark messages as read */
        post("/chats/{chatId}/read") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val lastMessageId = params["lastMessageId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val uuid = try { UUID.fromString(lastMessageId) }
                       catch (e: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }

            CassandraFactory.markMessagesAsRead(chatId, uuid, userId)
            call.respond(HttpStatusCode.OK)
        }

        /** Check online status for a list of user IDs */
        get("/users/online") {
            val idsParam = call.request.queryParameters["ids"] ?: ""
            val ids = idsParam.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (ids.isEmpty()) return@get call.respond(emptyMap<Int, Boolean>())
            call.respond(RedisFactory.getOnlineUsers(ids))
        }

        // ── Invite links ──────────────────────────────────────────────────────

        /** Create a new invite link for a group chat */
        post("/chats/{chatId}/invites") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            // Verify user is a participant
            val isMember = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .count() > 0
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden)

            val token = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()

            DatabaseFactory.dbQuery {
                ChatInvites.insert {
                    it[ChatInvites.chatId] = chatId
                    it[ChatInvites.createdBy] = userId
                    it[ChatInvites.token] = token
                    it[createdAt] = now
                }
            }
            call.respond(mapOf("token" to token))
        }

        /** Resolve invite token info (before joining) */
        get("/invites/{token}") {
            val token = call.parameters["token"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val invite = DatabaseFactory.dbQuery {
                val row = (ChatInvites innerJoin Chats)
                    .select(
                        ChatInvites.token,
                        ChatInvites.chatId,
                        ChatInvites.createdBy,
                        ChatInvites.currentUses,
                        ChatInvites.maxUses,
                        ChatInvites.expiresAt,
                        Chats.title,
                        Chats.type
                    )
                    .where { ChatInvites.token eq token }
                    .singleOrNull() ?: return@dbQuery null

                val now = System.currentTimeMillis()
                val expired = row[ChatInvites.expiresAt]?.let { it < now } ?: false
                val exhausted = row[ChatInvites.maxUses]?.let { row[ChatInvites.currentUses] >= it } ?: false
                if (expired || exhausted) return@dbQuery null

                InviteDto(
                    token = row[ChatInvites.token],
                    chatId = row[ChatInvites.chatId],
                    chatTitle = row[Chats.title],
                    chatType = row[Chats.type],
                    createdBy = row[ChatInvites.createdBy],
                    currentUses = row[ChatInvites.currentUses],
                    maxUses = row[ChatInvites.maxUses],
                    expiresAt = row[ChatInvites.expiresAt]
                )
            } ?: return@get call.respond(HttpStatusCode.NotFound)

            call.respond(invite)
        }

        /** Join a chat using an invite token */
        post("/invites/{token}/join") {
            val token = call.parameters["token"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val result = DatabaseFactory.dbQuery {
                val inviteRow = (ChatInvites innerJoin Chats)
                    .select(
                        ChatInvites.id,
                        ChatInvites.chatId,
                        ChatInvites.currentUses,
                        ChatInvites.maxUses,
                        ChatInvites.expiresAt,
                        Chats.title,
                        Chats.type
                    )
                    .where { ChatInvites.token eq token }
                    .singleOrNull() ?: return@dbQuery null

                val now = System.currentTimeMillis()
                val expired = inviteRow[ChatInvites.expiresAt]?.let { it < now } ?: false
                val exhausted = inviteRow[ChatInvites.maxUses]?.let {
                    inviteRow[ChatInvites.currentUses] >= it
                } ?: false
                if (expired || exhausted) return@dbQuery null

                val chatId = inviteRow[ChatInvites.chatId]
                val chatType = inviteRow[Chats.type]

                // DM invites cannot be joined this way
                if (chatType == "dm") return@dbQuery null

                val alreadyMember = ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .count() > 0

                if (!alreadyMember) {
                    ChatParticipants.insert {
                        it[ChatParticipants.chatId] = chatId
                        it[ChatParticipants.userId] = userId
                        it[role] = "member"
                        it[joinedAt] = now
                    }
                    // Increment use counter
                    ChatInvites.update({ ChatInvites.id eq inviteRow[ChatInvites.id] }) {
                        it[currentUses] = inviteRow[ChatInvites.currentUses] + 1
                    }
                }

                JoinByInviteResponse(
                    chatId = chatId,
                    chatTitle = inviteRow[Chats.title],
                    chatType = chatType,
                    alreadyMember = alreadyMember
                )
            } ?: return@post call.respond(HttpStatusCode.Gone) // expired/invalid

            call.respond(result)
        }

        /**
         * Add a user to a group chat.
         * - If both are mutual contacts → join immediately.
         * - Otherwise → send a DM invite link to the target user.
         */
        post("/chats/{chatId}/invite-user") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val inviterId = params["inviterId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val targetId = params["targetId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            // Verify inviter is a participant
            val isMember = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where {
                        (ChatParticipants.chatId eq chatId) and
                                (ChatParticipants.userId eq inviterId)
                    }.count() > 0
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden)

            val now = System.currentTimeMillis()

            val response = DatabaseFactory.dbQuery {
                val mutualContactIds = getMutualContactIds(inviterId)
                val isMutual = targetId in mutualContactIds

                if (isMutual) {
                    // Add directly
                    val alreadyIn = ChatParticipants.selectAll()
                        .where {
                            (ChatParticipants.chatId eq chatId) and
                                    (ChatParticipants.userId eq targetId)
                        }.count() > 0
                    if (!alreadyIn) {
                        ChatParticipants.insert {
                            it[ChatParticipants.chatId] = chatId
                            it[ChatParticipants.userId] = targetId
                            it[role] = "member"
                            it[joinedAt] = now
                        }
                    }
                    mapOf("method" to "direct", "alreadyMember" to alreadyIn.toString())
                } else {
                    // Create invite token and send as DM message
                    val inviteToken = UUID.randomUUID().toString()
                    ChatInvites.insert {
                        it[ChatInvites.chatId] = chatId
                        it[ChatInvites.createdBy] = inviterId
                        it[token] = inviteToken
                        it[ChatInvites.createdAt] = now
                    }

                    // Find or create DM between inviter and target
                    val dmChatId = findOrCreateDm(inviterId, targetId)

                    val chatTitle = Chats.selectAll()
                        .where { Chats.id eq chatId }
                        .single()[Chats.title] ?: "группу"

                    val inviterName = Users.selectAll()
                        .where { Users.id eq inviterId }
                        .single()[Users.displayName]

                    // Send the invite link as a system message in the DM (via Cassandra)
                    CassandraFactory.insertMessage(
                        chatId = dmChatId,
                        senderId = inviterId,
                        text = "$inviterName приглашает вас в «$chatTitle»: /join/$inviteToken",
                        timestamp = now
                    )

                    mapOf("method" to "invite_dm", "dmChatId" to dmChatId.toString(), "token" to inviteToken)
                }
            }

            call.respond(response)
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Returns IDs of users who are mutual contacts with [userId]. */
private fun getMutualContactIds(userId: Int): Set<Int> {
    val added = Contacts.selectAll()
        .where { Contacts.userId eq userId }
        .map { it[Contacts.contactId] }.toSet()

    if (added.isEmpty()) return emptySet()

    val addedMe = Contacts.selectAll()
        .where { (Contacts.userId inList added) and (Contacts.contactId eq userId) }
        .map { it[Contacts.userId] }.toSet()

    return added intersect addedMe
}

private fun String.normalizeLogin(): String? {
    val normalized = trim().lowercase()
    return normalized.takeIf { it.isNotBlank() }
}

private fun isValidLogin(login: String): Boolean {
    return login.length in 3..50 && login.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
}

private fun newSalt(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun hashPassword(password: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest("$salt:$password".toByteArray(Charsets.UTF_8))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun passwordAuthResponse(userId: Int, displayName: String, login: String): PasswordAuthResponse {
    return PasswordAuthResponse(
        token = "password:${UUID.randomUUID()}",
        user = PasswordUserDto(
            id = userId,
            name = displayName,
            username = login,
        )
    )
}

private fun passwordUserExternalId(login: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(digest.digest(login.toByteArray(Charsets.UTF_8)))
    return "pwd:${hash.take(24)}"
}

/** Finds existing DM between two users or creates a new one. Returns chatId. */
private fun findOrCreateDm(userA: Int, userB: Int): Int {
    // Find a chat where both users are participants and type = "dm"
    val userAChats = ChatParticipants.selectAll()
        .where { ChatParticipants.userId eq userA }
        .map { it[ChatParticipants.chatId] }.toSet()

    val userBChats = ChatParticipants.selectAll()
        .where { ChatParticipants.userId eq userB }
        .map { it[ChatParticipants.chatId] }.toSet()

    val common = userAChats intersect userBChats

    if (common.isNotEmpty()) {
        val existing = Chats.selectAll()
            .where { (Chats.id inList common) and (Chats.type eq "dm") }
            .firstOrNull()
        if (existing != null) return existing[Chats.id]
    }

    // Create DM
    val now = System.currentTimeMillis()
    val newChatId = Chats.insert {
        it[type] = "dm"
        it[title] = null
        it[createdBy] = userA
        it[createdAt] = now
    }[Chats.id]

    ChatParticipants.insert {
        it[ChatParticipants.chatId] = newChatId
        it[ChatParticipants.userId] = userA
        it[role] = "member"
        it[joinedAt] = now
    }
    ChatParticipants.insert {
        it[ChatParticipants.chatId] = newChatId
        it[ChatParticipants.userId] = userB
        it[role] = "member"
        it[joinedAt] = now
    }
    return newChatId
}

private fun getChatListForUser(userId: Int): List<ChatDto> {
    val myChatIds = ChatParticipants.selectAll()
        .where { ChatParticipants.userId eq userId }
        .map { it[ChatParticipants.chatId] }

    if (myChatIds.isEmpty()) return emptyList()

    return myChatIds.map { cId ->
        val chatRow = Chats.selectAll().where { Chats.id eq cId }.single()
        val chatType = chatRow[Chats.type]

        var otherUserId: Int? = null
        var otherUserName: String? = null

        if (chatType == "dm") {
            val other = ChatParticipants.selectAll()
                .where { (ChatParticipants.chatId eq cId) and (ChatParticipants.userId neq userId) }
                .firstOrNull()
            if (other != null) {
                otherUserId = other[ChatParticipants.userId]
                otherUserName = Users.selectAll()
                    .where { Users.id eq otherUserId!! }
                    .single()[Users.displayName]
            }
        }

        // Last message from Cassandra
        val lastMsg = CassandraFactory.getLastMessage(cId)?.let { msg ->
            val senderName = Users.selectAll()
                .where { Users.id eq msg.senderId }
                .singleOrNull()?.get(Users.displayName) ?: "Unknown"
            MessageDto(
                id = msg.id.toString(),
                chatId = msg.chatId,
                senderId = msg.senderId,
                senderName = senderName,
                text = msg.text,
                timestamp = msg.timestamp,
                status = msg.status
            )
        }

        ChatDto(
            id = cId,
            type = chatType,
            title = chatRow[Chats.title],
            avatarUrl = chatRow[Chats.avatarUrl],
            otherUserId = otherUserId,
            otherUserName = otherUserName,
            lastMessage = lastMsg,
            unreadCount = 0 // TODO: implement unread count via Cassandra
        )
    }
}
