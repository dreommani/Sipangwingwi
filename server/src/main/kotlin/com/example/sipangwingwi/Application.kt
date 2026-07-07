package com.example.sipangwingwi

import com.example.sipangwingwi.inventory.BranchStock
import com.example.sipangwingwi.inventory.GoodsReceivingService
import com.example.sipangwingwi.inventory.InventoryProductDto
import com.example.sipangwingwi.inventory.InventorySnapshotDto
import com.example.sipangwingwi.inventory.Product
import com.example.sipangwingwi.inventory.ReceiveGoodsApiRequest
import com.example.sipangwingwi.inventory.ReceiveGoodsApiResponse
import com.example.sipangwingwi.inventory.ReceiveGoodsRequest
import com.example.sipangwingwi.inventory.StockMovement
import com.example.sipangwingwi.inventory.StockMovementType
import com.example.sipangwingwi.models.User
import com.example.sipangwingwi.organization.BranchDto
import com.example.sipangwingwi.organization.BusinessDto
import com.example.sipangwingwi.organization.CreateBranchRequest
import com.example.sipangwingwi.organization.CreateEmployeeRequest
import com.example.sipangwingwi.organization.EmployeeDto
import com.example.sipangwingwi.organization.InitialSetupRequest
import com.example.sipangwingwi.organization.LoginRequest
import com.example.sipangwingwi.organization.LoginResponse
import com.example.sipangwingwi.organization.SetupStatusDto
import com.example.sipangwingwi.organization.SwitchBranchRequest
import com.example.sipangwingwi.organization.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.util.UUID

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
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        )
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
        get("/inventory") {
            val businessId = call.request.queryParameters["businessId"] ?: "business-nairobi-001"
            val branchId = call.request.queryParameters["branchId"] ?: "branch-cbd-001"
            val snapshot = transaction {
                inventorySnapshot(businessId = businessId, branchId = branchId)
            }
            call.respond(snapshot)
        }
        post("/inventory/receive") {
            val request = call.receive<ReceiveGoodsApiRequest>()
            val response = try {
                transaction {
                    receiveGoods(request)
                }
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid goods receiving request.")
                return@post
            }
            call.respond(response)
        }
        get("/setup/status") {
            call.respond(transaction { setupStatus() })
        }
        post("/setup") {
            val request = call.receive<InitialSetupRequest>()
            val response = try {
                transaction { completeSetup(request) }
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid setup request.")
                return@post
            }
            call.respond(response)
        }
        post("/auth/login") {
            val request = call.receive<LoginRequest>()
            val employee = transaction {
                EmployeeTable
                    .selectAll()
                    .where {
                        (EmployeeTable.employeeId eq request.employeeId) and
                            (EmployeeTable.pinHash eq hashPin(request.pin)) and
                            (EmployeeTable.isActive eq true)
                    }
                    .firstOrNull()
                    ?.toEmployeeDto()
            }
            if (employee == null) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid employee or PIN.")
                return@post
            }
            call.respond(LoginResponse(employee))
        }
        post("/employees") {
            val request = call.receive<CreateEmployeeRequest>()
            val employee = try {
                transaction { createEmployee(request) }
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid employee request.")
                return@post
            }
            call.respond(employee)
        }
        post("/branches") {
            val request = call.receive<CreateBranchRequest>()
            val branch = try {
                transaction { createBranch(request) }
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid branch request.")
                return@post
            }
            call.respond(branch)
        }
        post("/branches/active") {
            val request = call.receive<SwitchBranchRequest>()
            val branch = try {
                transaction { switchActiveBranch(request.branchId) }
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, error.message ?: "Invalid branch request.")
                return@post
            }
            call.respond(branch)
        }
    }
}

private fun inventorySnapshot(
    businessId: String,
    branchId: String
): InventorySnapshotDto {
    val products = ProductTable
        .selectAll()
        .where { ProductTable.businessId eq businessId }
        .orderBy(ProductTable.name to SortOrder.ASC)
        .map { productRow ->
            val stock = BranchStockTable
                .selectAll()
                .where {
                    (BranchStockTable.productId eq productRow[ProductTable.id]) and
                        (BranchStockTable.businessId eq businessId) and
                        (BranchStockTable.branchId eq branchId)
                }
                .firstOrNull()
                ?.toBranchStock()
                ?: BranchStock(
                    productId = productRow[ProductTable.id],
                    businessId = businessId,
                    branchId = branchId,
                    quantityOnHand = 0,
                    updatedAtEpochMillis = 0
                )

            InventoryProductDto(
                product = productRow.toProduct(),
                stock = stock
            )
        }

    val movements = StockMovementTable
        .selectAll()
        .where {
            (StockMovementTable.businessId eq businessId) and
                (StockMovementTable.branchId eq branchId)
        }
        .orderBy(StockMovementTable.createdAtEpochMillis to SortOrder.DESC)
        .limit(30)
        .map { it.toStockMovement() }

    return InventorySnapshotDto(products = products, movements = movements)
}

private fun receiveGoods(request: ReceiveGoodsApiRequest): ReceiveGoodsApiResponse {
    val product = ProductTable
        .selectAll()
        .where {
            (ProductTable.id eq request.productId) and
                (ProductTable.businessId eq request.businessId)
        }
        .firstOrNull()
        ?: throw IllegalArgumentException("Product not found for this business.")

    val existingStock = BranchStockTable
        .selectAll()
        .where {
            (BranchStockTable.productId eq request.productId) and
                (BranchStockTable.businessId eq request.businessId) and
                (BranchStockTable.branchId eq request.branchId)
        }
        .firstOrNull()
        ?.toBranchStock()

    val result = GoodsReceivingService().receive(
        ReceiveGoodsRequest(
            movementId = UUID.randomUUID().toString(),
            businessId = request.businessId,
            branchId = request.branchId,
            productId = request.productId,
            quantityReceived = request.quantityReceived,
            supplierId = request.supplierId,
            receivedByEmployeeId = request.receivedByEmployeeId,
            receivedAtEpochMillis = System.currentTimeMillis(),
            referenceId = request.referenceId,
            existingStock = existingStock
        )
    )

    if (existingStock == null) {
        BranchStockTable.insert {
            it[productId] = result.stock.productId
            it[businessId] = result.stock.businessId
            it[branchId] = result.stock.branchId
            it[quantityOnHand] = result.stock.quantityOnHand
            it[updatedAtEpochMillis] = result.stock.updatedAtEpochMillis
        }
    } else {
        BranchStockTable.update(
            where = {
                (BranchStockTable.productId eq result.stock.productId) and
                    (BranchStockTable.businessId eq result.stock.businessId) and
                    (BranchStockTable.branchId eq result.stock.branchId)
            }
        ) {
            it[quantityOnHand] = result.stock.quantityOnHand
            it[updatedAtEpochMillis] = result.stock.updatedAtEpochMillis
        }
    }

    StockMovementTable.insert {
        it[id] = result.movement.id
        it[businessId] = result.movement.businessId
        it[branchId] = result.movement.branchId
        it[productId] = result.movement.productId
        it[quantityDelta] = result.movement.quantityDelta
        it[type] = result.movement.type.name
        it[reason] = result.movement.reason
        it[supplierId] = result.movement.supplierId
        it[referenceId] = result.movement.referenceId
        it[createdByEmployeeId] = result.movement.createdByEmployeeId
        it[createdAtEpochMillis] = result.movement.createdAtEpochMillis
    }

    return ReceiveGoodsApiResponse(
        product = InventoryProductDto(
            product = product.toProduct(),
            stock = result.stock
        ),
        movement = result.movement
    )
}

private fun ResultRow.toProduct(): Product =
    Product(
        id = this[ProductTable.id],
        businessId = this[ProductTable.businessId],
        name = this[ProductTable.name],
        barcode = this[ProductTable.barcode],
        sku = this[ProductTable.sku],
        unitOfMeasure = this[ProductTable.unitOfMeasure],
        isActive = this[ProductTable.isActive]
    )

private fun ResultRow.toBranchStock(): BranchStock =
    BranchStock(
        productId = this[BranchStockTable.productId],
        businessId = this[BranchStockTable.businessId],
        branchId = this[BranchStockTable.branchId],
        quantityOnHand = this[BranchStockTable.quantityOnHand],
        updatedAtEpochMillis = this[BranchStockTable.updatedAtEpochMillis]
    )

private fun ResultRow.toStockMovement(): StockMovement =
    StockMovement(
        id = this[StockMovementTable.id],
        businessId = this[StockMovementTable.businessId],
        branchId = this[StockMovementTable.branchId],
        productId = this[StockMovementTable.productId],
        quantityDelta = this[StockMovementTable.quantityDelta],
        type = StockMovementType.valueOf(this[StockMovementTable.type]),
        reason = this[StockMovementTable.reason],
        supplierId = this[StockMovementTable.supplierId],
        referenceId = this[StockMovementTable.referenceId],
        createdByEmployeeId = this[StockMovementTable.createdByEmployeeId],
        createdAtEpochMillis = this[StockMovementTable.createdAtEpochMillis]
    )

private fun setupStatus(): SetupStatusDto {
    val business = BusinessTable.selectAll().limit(1).firstOrNull()
    if (business == null) return SetupStatusDto(setupComplete = false)

    val businessDto = business.toBusinessDto()
    val branches = BranchTable
        .selectAll()
        .where { BranchTable.businessId eq businessDto.id }
        .orderBy(BranchTable.name to SortOrder.ASC)
        .map { it.toBranchDto() }
    val employees = EmployeeTable
        .selectAll()
        .where { EmployeeTable.businessId eq businessDto.id }
        .orderBy(EmployeeTable.name to SortOrder.ASC)
        .map { it.toEmployeeDto() }

    return SetupStatusDto(
        setupComplete = true,
        business = businessDto,
        branches = branches,
        activeBranchId = business[BusinessTable.activeBranchId],
        employees = employees
    )
}

private fun completeSetup(request: InitialSetupRequest): SetupStatusDto {
    require(BusinessTable.selectAll().count() == 0L) { "Setup already exists." }
    require(request.ownerName.isNotBlank()) { "Owner name is required." }
    require(request.businessName.isNotBlank()) { "Business name is required." }
    require(request.branchName.isNotBlank()) { "Branch name is required." }
    require(request.adminPin.length >= 4) { "Admin PIN must be at least 4 digits." }

    val businessId = stableId("business", request.businessName)
    val branchId = stableId("branch", request.branchName)

    BusinessTable.insert {
        it[id] = businessId
        it[name] = request.businessName.trim()
        it[ownerName] = request.ownerName.trim()
        it[phone] = request.phone.trim()
        it[activeBranchId] = branchId
    }
    BranchTable.insert {
        it[id] = branchId
        it[BranchTable.businessId] = businessId
        it[name] = request.branchName.trim()
        it[isActive] = true
    }
    EmployeeTable.insert {
        it[employeeId] = "employee-admin-001"
        it[EmployeeTable.businessId] = businessId
        it[EmployeeTable.branchId] = branchId
        it[name] = request.ownerName.trim()
        it[role] = UserRole.Admin.name
        it[pinHash] = hashPin(request.adminPin)
        it[isActive] = true
    }
    EmployeeTable.insert {
        it[employeeId] = "employee-manager-001"
        it[EmployeeTable.businessId] = businessId
        it[EmployeeTable.branchId] = branchId
        it[name] = "Branch Manager"
        it[role] = UserRole.Manager.name
        it[pinHash] = hashPin("2222")
        it[isActive] = true
    }
    EmployeeTable.insert {
        it[employeeId] = "employee-worker-001"
        it[EmployeeTable.businessId] = businessId
        it[EmployeeTable.branchId] = branchId
        it[name] = "Worker"
        it[role] = UserRole.Worker.name
        it[pinHash] = hashPin("1234")
        it[isActive] = true
    }

    return setupStatus()
}

private fun createEmployee(request: CreateEmployeeRequest): EmployeeDto {
    require(request.name.isNotBlank()) { "Employee name is required." }
    require(request.pin.length >= 4) { "PIN must be at least 4 digits." }
    require(
        EmployeeTable.selectAll().where { EmployeeTable.pinHash eq hashPin(request.pin) }.count() == 0L
    ) { "PIN is already in use." }

    val employeeId = "employee-${UUID.randomUUID()}"
    EmployeeTable.insert {
        it[EmployeeTable.employeeId] = employeeId
        it[EmployeeTable.businessId] = request.businessId
        it[EmployeeTable.branchId] = request.branchId
        it[name] = request.name.trim()
        it[role] = request.role.name
        it[pinHash] = hashPin(request.pin)
        it[isActive] = true
    }
    return EmployeeTable.selectAll().where { EmployeeTable.employeeId eq employeeId }.first().toEmployeeDto()
}

private fun createBranch(request: CreateBranchRequest): BranchDto {
    require(request.name.isNotBlank()) { "Branch name is required." }
    val branchId = "branch-${UUID.randomUUID()}"
    BranchTable.insert {
        it[id] = branchId
        it[BranchTable.businessId] = request.businessId
        it[name] = request.name.trim()
        it[isActive] = true
    }
    if (BusinessTable.selectAll().where { BusinessTable.id eq request.businessId }.first()[BusinessTable.activeBranchId] == null) {
        BusinessTable.update({ BusinessTable.id eq request.businessId }) {
            it[activeBranchId] = branchId
        }
    }
    return BranchTable.selectAll().where { BranchTable.id eq branchId }.first().toBranchDto()
}

private fun switchActiveBranch(branchId: String): BranchDto {
    val branch = BranchTable.selectAll().where { BranchTable.id eq branchId }.firstOrNull()
        ?: throw IllegalArgumentException("Branch not found.")
    BusinessTable.update({ BusinessTable.id eq branch[BranchTable.businessId] }) {
        it[activeBranchId] = branchId
    }
    return branch.toBranchDto()
}

private fun ResultRow.toBusinessDto(): BusinessDto =
    BusinessDto(
        id = this[BusinessTable.id],
        name = this[BusinessTable.name],
        ownerName = this[BusinessTable.ownerName],
        phone = this[BusinessTable.phone]
    )

private fun ResultRow.toBranchDto(): BranchDto =
    BranchDto(
        id = this[BranchTable.id],
        businessId = this[BranchTable.businessId],
        name = this[BranchTable.name],
        isActive = this[BranchTable.isActive]
    )

private fun ResultRow.toEmployeeDto(): EmployeeDto =
    EmployeeDto(
        employeeId = this[EmployeeTable.employeeId],
        businessId = this[EmployeeTable.businessId],
        branchId = this[EmployeeTable.branchId],
        name = this[EmployeeTable.name],
        role = UserRole.valueOf(this[EmployeeTable.role]),
        pin = "****",
        isActive = this[EmployeeTable.isActive]
    )

private fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}

private fun stableId(prefix: String, value: String): String {
    val suffix = value.lowercase().filter { it.isLetterOrDigit() }.ifBlank { UUID.randomUUID().toString() }
    return "$prefix-$suffix"
}
