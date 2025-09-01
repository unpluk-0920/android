package com.unpluck.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unpluck.app.defs.Space

@Composable
fun AppsGridScreen(
    space: Space,
    onBlockNotifications: () -> Unit,
    onAllowNotifications: () -> Unit,
    onEnableCallBlocking: () -> Unit,
    onCheckSettings: () -> Unit,
    onForceExit: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // We'll add a real clock later
        Text(
            "12:26 AM",
            color = Color.White,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null // no ripple effect
            ) {
                tapCount++
                if (tapCount >= 5) {
                    showExitDialog = true
                }
            }
        )
        Spacer(Modifier.height(48.dp))
        Text(
            "Apps for '${space.name}' will be shown here",
            color = Color.Gray,
            fontSize = 18.sp
        )
        Spacer(Modifier.height(48.dp))

        // --- Add these buttons ---
        Text("Notifications", color = Color.White, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBlockNotifications) {
                Text("Block All")
            }
            Button(onClick = onAllowNotifications) {
                Text("Allow All")
            }
        }

        Spacer(Modifier.height(24.dp))
        // Add this new button
        Button(onClick = onEnableCallBlocking) {
            Text("Enable Call Blocking")
        }
        Button(onClick = onCheckSettings) {
            Text("Force Check Settings")
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = {
                showExitDialog = false
                tapCount = 0
            },
            title = { Text("Emergency Exit") },
            text = { Text("Do you want to exit the focus space?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onForceExit() // Call the exit function
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        tapCount = 0
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}