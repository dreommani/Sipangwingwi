package com.example.sipangwingwi

import com.example.sipangwingwi.inventory.InventorySnapshotDto
import com.example.sipangwingwi.inventory.ReceiveGoodsApiRequest
import com.example.sipangwingwi.inventory.ReceiveGoodsApiResponse
import com.example.sipangwingwi.models.User
import com.example.sipangwingwi.organization.BranchDto
import com.example.sipangwingwi.organization.CreateBranchRequest
import com.example.sipangwingwi.organization.CreateEmployeeRequest
import com.example.sipangwingwi.organization.EmployeeDto
import com.example.sipangwingwi.organization.InitialSetupRequest
import com.example.sipangwingwi.organization.LoginRequest
import com.example.sipangwingwi.organization.LoginResponse
import com.example.sipangwingwi.organization.SetupStatusDto
import com.example.sipangwingwi.organization.SwitchBranchRequest
import io.ktor.client.*
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

private val apiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

val client = HttpClient {
    install(ContentNegotiation) { json(apiJson) }
    defaultRequest {
        header(HttpHeaders.Accept, ContentType.Application.Json)
    }
}

private suspend inline fun <reified T> HttpResponse.decodeJson(): T =
    apiJson.decodeFromString(bodyAsText())

suspend fun fetchUsers(): List<User> =
    client.get("${defaultBackendBaseUrl()}/users").decodeJson()

suspend fun fetchInventory(
    businessId: String,
    branchId: String
): InventorySnapshotDto =
    client.get("${defaultBackendBaseUrl()}/inventory") {
        parameter("businessId", businessId)
        parameter("branchId", branchId)
    }.decodeJson()

suspend fun receiveGoods(request: ReceiveGoodsApiRequest): ReceiveGoodsApiResponse =
    client.post("${defaultBackendBaseUrl()}/inventory/receive") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun fetchSetupStatus(): SetupStatusDto =
    client.get("${defaultBackendBaseUrl()}/setup/status").decodeJson()

suspend fun submitInitialSetup(request: InitialSetupRequest): SetupStatusDto =
    client.post("${defaultBackendBaseUrl()}/setup") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun loginEmployeeOnBackend(request: LoginRequest): LoginResponse =
    client.post("${defaultBackendBaseUrl()}/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun createEmployeeOnBackend(request: CreateEmployeeRequest): EmployeeDto =
    client.post("${defaultBackendBaseUrl()}/employees") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun createBranchOnBackend(request: CreateBranchRequest): BranchDto =
    client.post("${defaultBackendBaseUrl()}/branches") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun switchActiveBranchOnBackend(request: SwitchBranchRequest): BranchDto =
    client.post("${defaultBackendBaseUrl()}/branches/active") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }.decodeJson()

suspend fun backendErrorBody(error: Throwable): String? =
    (error as? ResponseException)?.response?.runCatching { bodyAsText() }?.getOrNull()
