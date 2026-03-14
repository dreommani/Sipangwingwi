package com.example.familytree

import com.example.familytree.models.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*

val client = HttpClient {
    install(ContentNegotiation) { json() }
}

suspend fun fetchUsers(): List<User> =
    client.get("http://localhost:8080/users").body()