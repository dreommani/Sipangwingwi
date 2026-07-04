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
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
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
