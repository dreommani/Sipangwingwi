package com.example.sipangwingwi

import com.example.sipangwingwi.inventory.InventorySnapshotDto
import com.example.sipangwingwi.inventory.ReceiveGoodsApiRequest
import com.example.sipangwingwi.inventory.ReceiveGoodsApiResponse
import com.example.sipangwingwi.models.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*

val client = HttpClient {
    install(ContentNegotiation) { json() }
}

suspend fun fetchUsers(): List<User> =
    client.get("${defaultBackendBaseUrl()}/users").body()

suspend fun fetchInventory(
    businessId: String,
    branchId: String
): InventorySnapshotDto =
    client.get("${defaultBackendBaseUrl()}/inventory") {
        parameter("businessId", businessId)
        parameter("branchId", branchId)
    }.body()

suspend fun receiveGoods(request: ReceiveGoodsApiRequest): ReceiveGoodsApiResponse =
    client.post("${defaultBackendBaseUrl()}/inventory/receive") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.body()
