package com.unpluck.app

import androidx.compose.runtime.*

@Composable
fun UnpluckApp(
    spaces: List<Space>,
    onBlockNotifications: () -> Unit,
    onAllowNotifications: () -> Unit,
    onEnableCallBlocking: () -> Unit,
    onCheckSettings: () -> Unit
) {
    // State to keep track of the selected space. Null means no space is selected.
    var selectedSpace by remember { mutableStateOf<Space?>(null) }

    // Decide which screen to show based on the state
    val currentSpace = selectedSpace
    if (currentSpace == null) {
        // If no space is selected, show the list of spaces
        SpaceSelectionScreen(
            spaces = spaces,
            onSpaceSelected = { space ->
                selectedSpace = space
            }
        )
    } else {
        // If a space is selected, show the apps grid for that space
        AppsGridScreen(
            space = currentSpace,
            onBlockNotifications = onBlockNotifications,
            onAllowNotifications = onAllowNotifications,
            onEnableCallBlocking = onEnableCallBlocking,
            onCheckSettings = onCheckSettings,
        )
    }
}