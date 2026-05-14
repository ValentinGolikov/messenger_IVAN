package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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

        /** Global search across users, chats and recent messages */
        get("/search/global") {
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            if (query.isBlank()) {
                return@get call.respond(mapOf("users" to emptyList<UserDto>(), "chats" to emptyList<ChatDto>(), "messages" to emptyList<MessageDto>()))
            }

            val q = query.lowercase()
            val result = DatabaseFactory.dbQuery {
                val users = Users.selectAll()
                    .where { (Users.displayName.lowerCase() like "%$q%") and (Users.id neq userId) }
                    .limit(10)
                    .map {
                        UserDto(
                            id = it[Users.id],
                            displayName = it[Users.displayName],
                            realName = it[Users.realName],
                            isMutualContact = false
                        )
                    }

                val myChats = getChatListForUser(userId)
                val chatHits = myChats.filter { chat ->
                    val name = (chat.title ?: chat.otherUserName ?: "").lowercase()
                    name.contains(q)
                }.take(10)

                val messageHits = mutableListOf<MessageDto>()
                myChats.forEach { chat ->
                    if (messageHits.size >= 20) return@forEach
                    val msgs = CassandraFactory.getMessages(chat.id, 200)
                    val matching = msgs.filter { !it.isDeleted && it.text.lowercase().contains(q) }.take(5)
                    if (matching.isEmpty()) return@forEach

                    // Batch-load sender names for matching messages
                    val senderIds = matching.map { it.senderId }.filter { it != 0 }.toSet()
                    val names = mutableMapOf<Int, String>()
                    if (senderIds.isNotEmpty()) {
                        Users.selectAll().where { Users.id inList senderIds }
                            .forEach { row -> names[row[Users.id]] = row[Users.displayName] }
                    }

                    matching.forEach { msg ->
                            val senderName = if (msg.senderId == 0) "Система"
                                             else names[msg.senderId] ?: "Unknown"
                            messageHits.add(
                                MessageDto(
                                    id = msg.id.toString(),
                                    chatId = msg.chatId,
                                    senderId = msg.senderId,
                                    senderName = senderName,
                                    text = msg.text,
                                    timestamp = msg.timestamp,
                                    status = msg.status,
                                    messageType = msg.messageType
                                )
                            )
                        }
                }

                mapOf("users" to users, "chats" to chatHits, "messages" to messageHits.take(20))
            }

            call.respond(result)
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
            val userId = call.request.queryParameters["userId"]?.toIntOrNull()

            // Per-user deleted message IDs
            val deletedIds = if (userId != null) {
                DatabaseFactory.dbQuery {
                    DeletedMessagesPerUser.selectAll()
                        .where { (DeletedMessagesPerUser.userId eq userId) and (DeletedMessagesPerUser.chatId eq chatId) }
                        .map { it[DeletedMessagesPerUser.messageId] }.toSet()
                }
            } else emptySet()

            // clearedAt for DM "delete for self"
            val clearedAt = if (userId != null) {
                DatabaseFactory.dbQuery {
                    ChatParticipants.selectAll()
                        .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                        .singleOrNull()?.get(ChatParticipants.clearedAt)
                }
            } else null

            val senderNames = mutableMapOf<Int, String>()

            val messages = CassandraFactory.getMessages(chatId, 50)
                .filter { !it.isDeleted }
                .filter { it.id.toString() !in deletedIds }
                .filter { clearedAt == null || it.timestamp > clearedAt }

            // Batch-load all sender names in ONE query instead of N+1
            val senderIds = messages.map { it.senderId }.filter { it != 0 }.toSet()
            if (senderIds.isNotEmpty()) {
                DatabaseFactory.dbQuery {
                    Users.selectAll()
                        .where { Users.id inList senderIds }
                        .forEach { row -> senderNames[row[Users.id]] = row[Users.displayName] }
                }
            }

            val messageDtos = messages.map { msg ->
                    val senderName = if (msg.senderId == 0) "Система"
                                     else senderNames[msg.senderId] ?: "Unknown"
                    MessageDto(
                        id = msg.id.toString(),
                        chatId = msg.chatId,
                        senderId = msg.senderId,
                        senderName = senderName,
                        text = msg.text,
                        timestamp = msg.timestamp,
                        status = msg.status,
                        messageType = msg.messageType
                    )
                }
            call.respond(messageDtos)
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

            // Verify inviter has permission (owner/admin)
            val canInvite = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where {
                        (ChatParticipants.chatId eq chatId) and
                                (ChatParticipants.userId eq inviterId)
                    }
                    .singleOrNull()
                    ?.get(ChatParticipants.role)
                    ?.let { it == "owner" || it == "admin" } == true
            }
            if (!canInvite) return@post call.respond(HttpStatusCode.Forbidden)

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
                    // Format: plain /join/TOKEN so the client InviteLinkBubble can detect it
                    CassandraFactory.insertMessage(
                        chatId = dmChatId,
                        senderId = inviterId,
                        text = "/join/$inviteToken",
                        timestamp = now
                    )

                    mapOf("method" to "invite_dm", "dmChatId" to dmChatId.toString(), "token" to inviteToken)
                }
            }

            call.respond(response)
        }

        // ── Pinned messages ──────────────────────────────────────────────────

        /** Pin a message in a chat (allows multiple pins) */
        post("/chats/{chatId}/pin") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val messageId = params["messageId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val isMember = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .count() > 0
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden)

            val now = System.currentTimeMillis()

            // Insert new pin (no longer deletes previous pins)
            DatabaseFactory.dbQuery {
                PinnedMessages.upsert(PinnedMessages.chatId, PinnedMessages.messageId) {
                    it[PinnedMessages.chatId] = chatId
                    it[PinnedMessages.messageId] = messageId
                    it[pinnedBy] = userId
                    it[pinnedAt] = now
                }
            }

            val pinnedDto = buildPinnedMessageDto(chatId, messageId, userId, now)
            if (pinnedDto != null) {
                val event = PinEvent(chatId = chatId, pinnedMessage = pinnedDto, action = "pin")
                val envelope = WsEnvelope(
                    type = "pin_update",
                    payload = Json.encodeToString(PinEvent.serializer(), event)
                )
                launch { ConnectionManager.broadcastToChat(chatId, envelope) }
            }

            call.respond(pinnedDto ?: HttpStatusCode.OK)
        }

        /** Unpin a specific message */
        delete("/chats/{chatId}/pin") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val messageId = params["messageId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            val isMember = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .count() > 0
            }
            if (!isMember) return@delete call.respond(HttpStatusCode.Forbidden)

            DatabaseFactory.dbQuery {
                PinnedMessages.deleteWhere {
                    (PinnedMessages.chatId eq chatId) and (PinnedMessages.messageId eq messageId)
                }
            }

            val unpinDto = buildPinnedMessageDto(chatId, messageId, userId, System.currentTimeMillis())
            val event = PinEvent(chatId = chatId, pinnedMessage = unpinDto, action = "unpin")
            val envelope = WsEnvelope(
                type = "pin_update",
                payload = Json.encodeToString(PinEvent.serializer(), event)
            )
            launch { ConnectionManager.broadcastToChat(chatId, envelope) }

            call.respond(HttpStatusCode.OK)
        }

        /** Get ALL pinned messages for a chat (list, sorted by pinnedAt DESC) */
        get("/chats/{chatId}/pins") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val pinRows = DatabaseFactory.dbQuery {
                PinnedMessages.selectAll()
                    .where { PinnedMessages.chatId eq chatId }
                    .orderBy(PinnedMessages.pinnedAt, SortOrder.DESC)
                    .toList()
            }

            val dtos = pinRows.mapNotNull { row ->
                buildPinnedMessageDto(
                    chatId,
                    row[PinnedMessages.messageId],
                    row[PinnedMessages.pinnedBy],
                    row[PinnedMessages.pinnedAt]
                )
            }
            call.respond(dtos)
        }

        // ── Message edit / deletion ─────────────────────────────────────────

        /** Edit own message text */
        post("/chats/{chatId}/messages/{messageId}/edit") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val messageId = call.parameters["messageId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val newText = params["text"]?.trim()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            if (newText.isBlank()) return@post call.respond(HttpStatusCode.BadRequest)

            val uuid = try { UUID.fromString(messageId) } catch (_: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }
            val msg = CassandraFactory.getMessageById(chatId, uuid) ?: return@post call.respond(HttpStatusCode.NotFound)
            if (msg.senderId != userId) return@post call.respond(HttpStatusCode.Forbidden)
            if (msg.messageType != "text" || msg.isDeleted) return@post call.respond(HttpStatusCode.BadRequest)

            CassandraFactory.editMessageText(chatId, uuid, newText)

            val event = MessageEditedEvent(chatId = chatId, messageId = messageId, text = newText)
            val envelope = WsEnvelope(
                type = "message_edited",
                payload = Json.encodeToString(MessageEditedEvent.serializer(), event)
            )
            launch { ConnectionManager.broadcastToChat(chatId, envelope) }

            call.respond(HttpStatusCode.OK)
        }

        /** Delete a message (for self or for all) */
        post("/chats/{chatId}/messages/{messageId}/delete") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val messageId = call.parameters["messageId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val forAll = params["forAll"]?.toBoolean() ?: false

            // Verify user is a participant
            val isMember = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .count() > 0
            }
            if (!isMember) return@post call.respond(HttpStatusCode.Forbidden)

            val uuid = try { UUID.fromString(messageId) }
                       catch (_: Exception) { return@post call.respond(HttpStatusCode.BadRequest) }

            if (forAll) {
                // In group chats, only own messages can be deleted for all
                val chatType = DatabaseFactory.dbQuery {
                    Chats.selectAll().where { Chats.id eq chatId }.singleOrNull()?.get(Chats.type)
                }
                if (chatType == "group") {
                    val msg = CassandraFactory.getMessageById(chatId, uuid)
                    if (msg != null && msg.senderId != userId) {
                        return@post call.respond(HttpStatusCode.Forbidden)
                    }
                }

                CassandraFactory.deleteMessageForAll(chatId, uuid)

                // Broadcast to all participants
                val event = MessageDeletedEvent(chatId = chatId, messageId = messageId)
                val envelope = WsEnvelope(
                    type = "message_deleted",
                    payload = Json.encodeToString(MessageDeletedEvent.serializer(), event)
                )
                launch { ConnectionManager.broadcastToChat(chatId, envelope) }
            } else {
                // Delete for self only
                DatabaseFactory.dbQuery {
                    DeletedMessagesPerUser.upsert(
                        DeletedMessagesPerUser.userId,
                        DeletedMessagesPerUser.chatId,
                        DeletedMessagesPerUser.messageId
                    ) {
                        it[DeletedMessagesPerUser.userId] = userId
                        it[DeletedMessagesPerUser.chatId] = chatId
                        it[DeletedMessagesPerUser.messageId] = messageId
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        // ── Chat deletion ────────────────────────────────────────────────────

        /** Delete a DM chat (for self or for both) */
        post("/chats/{chatId}/delete") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val forAll = params["forAll"]?.toBoolean() ?: false

            // Verify it's a DM and user is a participant
            val chatType = DatabaseFactory.dbQuery {
                Chats.selectAll().where { Chats.id eq chatId }.singleOrNull()?.get(Chats.type)
            }
            if (chatType != "dm") return@post call.respond(HttpStatusCode.BadRequest)

            if (forAll) {
                // Delete for both: remove chat, participants, messages
                CassandraFactory.deleteAllChatMessages(chatId)
                DatabaseFactory.dbQuery {
                    PinnedMessages.deleteWhere { PinnedMessages.chatId eq chatId }
                    ChatParticipants.deleteWhere { ChatParticipants.chatId eq chatId }
                    Chats.deleteWhere { Chats.id eq chatId }
                }
                // Notify the other user
                val event = ChatRemovedEvent(chatId = chatId, reason = "deleted")
                val envelope = WsEnvelope(
                    type = "chat_removed",
                    payload = Json.encodeToString(ChatRemovedEvent.serializer(), event)
                )
                launch { ConnectionManager.broadcastToChat(chatId, envelope) }
            } else {
                // Delete for self: set clearedAt
                val now = System.currentTimeMillis()
                DatabaseFactory.dbQuery {
                    ChatParticipants.update({
                        (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId)
                    }) {
                        it[clearedAt] = now
                    }
                }
            }
            call.respond(HttpStatusCode.OK)
        }

        // ── Leave / delete group ─────────────────────────────────────────────

        /** Leave a group chat */
        post("/chats/{chatId}/leave") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val newOwnerId = params["newOwnerId"]?.toIntOrNull()

            val participant = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .singleOrNull()
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val role = participant[ChatParticipants.role]
            val userName = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.id eq userId }.single()[Users.displayName]
            }

            // If owner, must provide newOwnerId
            if (role == "owner") {
                if (newOwnerId == null) return@post call.respond(HttpStatusCode.BadRequest, "Owner must specify newOwnerId")

                DatabaseFactory.dbQuery {
                    ChatParticipants.update({
                        (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq newOwnerId)
                    }) { it[ChatParticipants.role] = "owner" }
                }

                // Broadcast owner change
                val ownerEvent = OwnerChangedEvent(chatId = chatId, newOwnerId = newOwnerId)
                val ownerEnvelope = WsEnvelope(
                    type = "owner_changed",
                    payload = Json.encodeToString(OwnerChangedEvent.serializer(), ownerEvent)
                )
                launch { ConnectionManager.broadcastToChat(chatId, ownerEnvelope) }

                // System message about new owner
                val newOwnerName = DatabaseFactory.dbQuery {
                    Users.selectAll().where { Users.id eq newOwnerId }.single()[Users.displayName]
                }
                val now2 = System.currentTimeMillis()
                val sysId2 = CassandraFactory.insertSystemMessage(chatId, "$newOwnerName теперь администратор группы", now2)
                broadcastSystemMessage(chatId, sysId2, "$newOwnerName теперь администратор группы", now2)
            }

            // Remove participant
            DatabaseFactory.dbQuery {
                ChatParticipants.deleteWhere {
                    (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId)
                }
            }

            // System message
            val now = System.currentTimeMillis()
            val sysId = CassandraFactory.insertSystemMessage(chatId, "$userName покинул группу", now)
            broadcastSystemMessage(chatId, sysId, "$userName покинул группу", now)

            call.respond(HttpStatusCode.OK)
        }

        /** Delete a group (owner only) */
        delete("/chats/{chatId}") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@delete call.respond(HttpStatusCode.BadRequest)

            // Verify owner
            val isOwner = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) and (ChatParticipants.role eq "owner") }
                    .count() > 0
            }
            if (!isOwner) return@delete call.respond(HttpStatusCode.Forbidden)

            // Broadcast removal to all members before deleting
            val event = ChatRemovedEvent(chatId = chatId, reason = "group_deleted")
            val envelope = WsEnvelope(
                type = "chat_removed",
                payload = Json.encodeToString(ChatRemovedEvent.serializer(), event)
            )
            launch { ConnectionManager.broadcastToChat(chatId, envelope) }

            // Delete everything
            CassandraFactory.deleteAllChatMessages(chatId)
            DatabaseFactory.dbQuery {
                PinnedMessages.deleteWhere { PinnedMessages.chatId eq chatId }
                DeletedMessagesPerUser.deleteWhere { DeletedMessagesPerUser.chatId eq chatId }
                ChatInvites.deleteWhere { ChatInvites.chatId eq chatId }
                ChatParticipants.deleteWhere { ChatParticipants.chatId eq chatId }
                Chats.deleteWhere { Chats.id eq chatId }
            }

            call.respond(HttpStatusCode.OK)
        }

        // ── Group members ────────────────────────────────────────────────────

        /** Get members of a group chat */
        get("/chats/{chatId}/members") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val members = DatabaseFactory.dbQuery {
                val participants = ChatParticipants.selectAll()
                    .where { ChatParticipants.chatId eq chatId }
                    .toList()

                participants.map { p ->
                    val uid = p[ChatParticipants.userId]
                    val user = Users.selectAll().where { Users.id eq uid }.single()
                    val online = RedisFactory.isOnline(uid)
                    val lastSeen = if (!online) {
                        RedisFactory.getLastSeen(uid) ?: user[Users.lastSeenAt]
                    } else null

                    MemberDto(
                        id = uid,
                        displayName = user[Users.displayName],
                        role = p[ChatParticipants.role],
                        online = online,
                        lastSeen = lastSeen
                    )
                }
            }
            call.respond(members)
        }

        /** Kick a member from a group (owner/admin with role checks) */
        post("/chats/{chatId}/kick") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val targetId = params["targetId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val requesterRole = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) }
                    .singleOrNull()?.get(ChatParticipants.role)
            } ?: return@post call.respond(HttpStatusCode.Forbidden)

            val targetRole = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq targetId) }
                    .singleOrNull()?.get(ChatParticipants.role)
            } ?: return@post call.respond(HttpStatusCode.NotFound)

            val canKick = when (requesterRole) {
                "owner" -> targetRole != "owner"
                "admin" -> targetRole == "member"
                else -> false
            }
            if (!canKick) return@post call.respond(HttpStatusCode.Forbidden)

            val targetName = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.id eq targetId }.singleOrNull()?.get(Users.displayName) ?: "Unknown"
            }

            DatabaseFactory.dbQuery {
                ChatParticipants.deleteWhere {
                    (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq targetId)
                }
            }

            // System message
            val now = System.currentTimeMillis()
            val sysId = CassandraFactory.insertSystemMessage(chatId, "$targetName исключён из группы", now)
            broadcastSystemMessage(chatId, sysId, "$targetName исключён из группы", now)

            // Notify kicked user
            val kickEvent = ChatRemovedEvent(chatId = chatId, reason = "kicked")
            val kickEnvelope = WsEnvelope(
                type = "chat_removed",
                payload = Json.encodeToString(ChatRemovedEvent.serializer(), kickEvent)
            )
            ConnectionManager.connections[targetId]?.send(
                io.ktor.websocket.Frame.Text(Json.encodeToString(WsEnvelope.serializer(), kickEnvelope))
            )

            call.respond(HttpStatusCode.OK)
        }

        /** Set group role (owner only): member/admin */
        post("/chats/{chatId}/set-role") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val targetId = params["targetId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val role = params["role"]?.trim()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            if (role !in setOf("member", "admin")) return@post call.respond(HttpStatusCode.BadRequest)

            val isOwner = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) and (ChatParticipants.role eq "owner") }
                    .count() > 0
            }
            if (!isOwner) return@post call.respond(HttpStatusCode.Forbidden)

            val updated = DatabaseFactory.dbQuery {
                ChatParticipants.update({
                    (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq targetId) and (ChatParticipants.role neq "owner")
                }) { it[ChatParticipants.role] = role }
            }
            if (updated == 0) return@post call.respond(HttpStatusCode.NotFound)

            call.respond(HttpStatusCode.OK)
        }

        /** Transfer group ownership */
        post("/chats/{chatId}/transfer-owner") {
            val chatId = call.parameters["chatId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val params = call.receiveParameters()
            val userId = params["userId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val targetId = params["targetId"]?.toIntOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest)

            val isOwner = DatabaseFactory.dbQuery {
                ChatParticipants.selectAll()
                    .where { (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId) and (ChatParticipants.role eq "owner") }
                    .count() > 0
            }
            if (!isOwner) return@post call.respond(HttpStatusCode.Forbidden)

            DatabaseFactory.dbQuery {
                ChatParticipants.update({
                    (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq userId)
                }) { it[role] = "member" }

                ChatParticipants.update({
                    (ChatParticipants.chatId eq chatId) and (ChatParticipants.userId eq targetId)
                }) { it[role] = "owner" }
            }

            val ownerEvent = OwnerChangedEvent(chatId = chatId, newOwnerId = targetId)
            val envelope = WsEnvelope(
                type = "owner_changed",
                payload = Json.encodeToString(OwnerChangedEvent.serializer(), ownerEvent)
            )
            launch { ConnectionManager.broadcastToChat(chatId, envelope) }

            // System message
            val targetName = DatabaseFactory.dbQuery {
                Users.selectAll().where { Users.id eq targetId }.single()[Users.displayName]
            }
            val now = System.currentTimeMillis()
            val sysId = CassandraFactory.insertSystemMessage(chatId, "$targetName теперь администратор группы", now)
            broadcastSystemMessage(chatId, sysId, "$targetName теперь администратор группы", now)

            call.respond(HttpStatusCode.OK)
        }

        // ── User presence ────────────────────────────────────────────────────

        /** Get presence info (online + lastSeen) for a list of user IDs */
        get("/users/presence") {
            val idsParam = call.request.queryParameters["ids"] ?: ""
            val ids = idsParam.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (ids.isEmpty()) return@get call.respond(emptyMap<Int, UserPresenceDto>())

            val result = DatabaseFactory.dbQuery {
                ids.associateWith { uid ->
                    val online = RedisFactory.isOnline(uid)
                    val lastSeen = if (!online) {
                        RedisFactory.getLastSeen(uid)
                            ?: Users.selectAll().where { Users.id eq uid }
                                .singleOrNull()?.get(Users.lastSeenAt)
                    } else null
                    UserPresenceDto(online = online, lastSeen = lastSeen)
                }
            }
            call.respond(result)
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
    val myParticipations = ChatParticipants.selectAll()
        .where { ChatParticipants.userId eq userId }
        .map { it[ChatParticipants.chatId] to it[ChatParticipants.clearedAt] }

    if (myParticipations.isEmpty()) return emptyList()

    return myParticipations.mapNotNull { (cId, clearedAt) ->
        val chatRow = Chats.selectAll().where { Chats.id eq cId }.singleOrNull() ?: return@mapNotNull null
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
                    .singleOrNull()?.get(Users.displayName)
            }
        }

        // Last message from Cassandra
        val lastMsg = CassandraFactory.getLastMessage(cId)?.let { msg ->
            // If DM was cleared, skip messages before clearedAt
            if (clearedAt != null && msg.timestamp <= clearedAt) return@let null
            if (msg.isDeleted) return@let null
            val senderName = if (msg.senderId == 0) "Система" else Users.selectAll()
                .where { Users.id eq msg.senderId }
                .singleOrNull()?.get(Users.displayName) ?: "Unknown"
            MessageDto(
                id = msg.id.toString(),
                chatId = msg.chatId,
                senderId = msg.senderId,
                senderName = senderName,
                text = msg.text,
                timestamp = msg.timestamp,
                status = msg.status,
                messageType = msg.messageType
            )
        }

        // If DM was cleared and no messages after clearedAt, hide the chat
        if (clearedAt != null && lastMsg == null && chatType == "dm") return@mapNotNull null

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

/**
 * Build a PinnedMessageDto by fetching message data from Cassandra
 * and sender name from PostgreSQL.
 */
private suspend fun buildPinnedMessageDto(
    chatId: Int,
    messageId: String,
    pinnedBy: Int,
    pinnedAt: Long
): PinnedMessageDto? {
    val uuid = try { UUID.fromString(messageId) } catch (_: Exception) { return null }
    val msg = CassandraFactory.getMessageById(chatId, uuid) ?: return null
    val senderName = if (msg.senderId == 0) "Система" else DatabaseFactory.dbQuery {
        Users.selectAll()
            .where { Users.id eq msg.senderId }
            .singleOrNull()?.get(Users.displayName) ?: "Unknown"
    }
    return PinnedMessageDto(
        messageId = messageId,
        chatId = chatId,
        senderId = msg.senderId,
        senderName = senderName,
        text = msg.text,
        timestamp = msg.timestamp,
        pinnedBy = pinnedBy,
        pinnedAt = pinnedAt
    )
}

/**
 * Broadcast a system message to all connected participants of a chat.
 */
private suspend fun broadcastSystemMessage(chatId: Int, messageId: UUID, text: String, timestamp: Long) {
    val msgDto = MessageDto(
        id = messageId.toString(),
        chatId = chatId,
        senderId = 0,
        senderName = "Система",
        text = text,
        timestamp = timestamp,
        status = "sent",
        messageType = "system"
    )
    val envelope = WsEnvelope(
        type = "message",
        payload = Json.encodeToString(MessageDto.serializer(), msgDto)
    )
    ConnectionManager.broadcastToChat(chatId, envelope)
}
