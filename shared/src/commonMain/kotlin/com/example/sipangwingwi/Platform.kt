package com.example.sipangwingwi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform