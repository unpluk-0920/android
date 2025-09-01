package com.unpluck.app.defs

data class Space(
    val id: Int,
    val name: String,
    val appIds: List<String> // For now, this will be empty
)