package com.example.sipangwingwi

import com.example.sipangwingwi.models.User
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    connectToDatabase()
    configureSerialization()
    configureRouting()
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
        get("/health") {
            call.respondText("OK")
        }
        get("/users") {
            val users = transaction {
                UserTable.selectAll().map {
                    User(
                        id = it[UserTable.id],
                        name = it[UserTable.name],
                        email = it[UserTable.email]
                    )
                }
            }
            call.respond(users)
        }
    }
}
