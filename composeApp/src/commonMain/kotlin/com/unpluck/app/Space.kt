package com.unpluck.app

data class Space(
    val id: Int,
    val name: String,
    val appIds: List<String> // For now, this will be empty
)