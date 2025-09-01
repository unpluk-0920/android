package com.unpluck.app.defs

import android.graphics.drawable.Drawable

data class LauncherInfo (
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)