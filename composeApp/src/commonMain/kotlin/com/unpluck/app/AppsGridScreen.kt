package com.unpluck.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppsGridScreen(
    space: Space,
    onBlockNotifications: () -> Unit,
    onAllowNotifications: () -> Unit,
    onEnableCallBlocking: () -> Unit,
    onCheckSettings: () -> Unit
) {
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
            fontWeight = FontWeight.Bold
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
}