package com.unpluck.app.defs

import kotlin.uuid.*

data class Space @OptIn(ExperimentalUuidApi::class) constructor(
    val id: String = Uuid.random().toString(),
    val name: String,
    // We will add app package names to this list later
    val appIds: List<String> = emptyList()
)