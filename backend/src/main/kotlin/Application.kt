package com.example

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

fun main(args: Array<String>): Unit = EngineMain.main(args)

fun Application.module() {
    DatabaseFactory.init()
    CassandraFactory.init()
    RedisFactory.init()

    val httpClient = HttpClient(CIO) {
        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val authService = AuthService(httpClient)

    install(ServerContentNegotiation) { json() }
    install(WebSockets)

    configureRouting(authService)
    configureSockets()
    configureTestRoutes()

    // Graceful shutdown
    monitor.subscribe(ApplicationStopped) {
        CassandraFactory.shutdown()
        RedisFactory.shutdown()
    }
}
