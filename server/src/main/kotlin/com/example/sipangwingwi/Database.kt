package com.example.sipangwingwi

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object UserTable : Table("users") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val email = varchar("email", 255)
    override val primaryKey = PrimaryKey(id)
}

object ProductTable : Table("products") {
    val id = varchar("id", 64)
    val businessId = varchar("business_id", 64)
    val name = varchar("name", 255)
    val barcode = varchar("barcode", 64).nullable()
    val sku = varchar("sku", 64).nullable()
    val unitOfMeasure = varchar("unit_of_measure", 32)
    val isActive = bool("is_active")
    override val primaryKey = PrimaryKey(id)
}

object BranchStockTable : Table("branch_stock") {
    val productId = varchar("product_id", 64).references(ProductTable.id)
    val businessId = varchar("business_id", 64)
    val branchId = varchar("branch_id", 64)
    val quantityOnHand = long("quantity_on_hand")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    override val primaryKey = PrimaryKey(productId, businessId, branchId)
}

object StockMovementTable : Table("stock_movements") {
    val id = varchar("id", 64)
    val businessId = varchar("business_id", 64)
    val branchId = varchar("branch_id", 64)
    val productId = varchar("product_id", 64).references(ProductTable.id)
    val quantityDelta = long("quantity_delta")
    val type = varchar("type", 64)
    val reason = varchar("reason", 255)
    val supplierId = varchar("supplier_id", 64).nullable()
    val referenceId = varchar("reference_id", 128).nullable()
    val createdByEmployeeId = varchar("created_by_employee_id", 64)
    val createdAtEpochMillis = long("created_at_epoch_millis")
    override val primaryKey = PrimaryKey(id)
}

fun connectToDatabase() {
    println("Initializing database connection...")
    try {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/family_tree"
            driverClassName = "org.postgresql.Driver"
            username = "postgres"
            password = "postgres"
            maximumPoolSize = 10
            connectionTimeout = 5000
        }
        val ds = HikariDataSource(config)
        val db = Database.connect(ds)
        
        println("Connecting and creating schema if needed...")
        transaction(db) {
            // Log SQL statements to console
            addLogger(StdOutSqlLogger)
            
            SchemaUtils.create(UserTable, ProductTable, BranchStockTable, StockMovementTable)
            seedInventoryIfNeeded()
            
            val count = UserTable.selectAll().count()
            println("Database check: Found $count users in 'users' table.")
        }
        println("Database initialization complete.")
    } catch (e: Exception) {
        println("DATABASE ERROR: ${e.message}")
        e.printStackTrace()
    }
}

private fun seedInventoryIfNeeded() {
    if (ProductTable.selectAll().count() > 0) return

    val businessId = "business-nairobi-001"
    val branchId = "branch-cbd-001"
    val now = System.currentTimeMillis()

    val products = listOf(
        SeedProduct("prod-maize-flour-2kg", "Maize flour 2kg", "6161101001012", 18),
        SeedProduct("prod-cooking-oil-1l", "Cooking oil 1L", "6161101001029", 9),
        SeedProduct("prod-soda-500ml", "Soda 500ml", "6161101001036", 42),
        SeedProduct("prod-rice-pishori-1kg", "Pishori rice 1kg", "6161101001043", 15)
    )

    products.forEach { product ->
        ProductTable.insert {
            it[id] = product.id
            it[ProductTable.businessId] = businessId
            it[name] = product.name
            it[barcode] = product.barcode
            it[sku] = null
            it[unitOfMeasure] = "each"
            it[isActive] = true
        }
        BranchStockTable.insert {
            it[productId] = product.id
            it[BranchStockTable.businessId] = businessId
            it[BranchStockTable.branchId] = branchId
            it[quantityOnHand] = product.quantityOnHand
            it[updatedAtEpochMillis] = now
        }
    }
}

private data class SeedProduct(
    val id: String,
    val name: String,
    val barcode: String,
    val quantityOnHand: Long
)
