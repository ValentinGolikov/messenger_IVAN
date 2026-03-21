package com.example

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*

fun Application.configureRouting(authService: AuthService) {
    routing {
        post("/login") {
            val token = call.receiveParameters()["token"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val yandexUser = authService.verifyToken(token) ?: return@post call.respond(HttpStatusCode.Unauthorized)

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
    }
}
