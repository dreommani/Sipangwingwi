package com.example.familytree

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform