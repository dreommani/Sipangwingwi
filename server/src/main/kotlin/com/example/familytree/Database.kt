package com.example.familytree

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
            
            SchemaUtils.create(UserTable)
            
            val count = UserTable.selectAll().count()
            println("Database check: Found $count users in 'users' table.")
        }
        println("Database initialization complete.")
    } catch (e: Exception) {
        println("DATABASE ERROR: ${e.message}")
        e.printStackTrace()
    }
}
