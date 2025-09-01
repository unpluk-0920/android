package com.unpluck.app.defs

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform