package com.unpluck.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform