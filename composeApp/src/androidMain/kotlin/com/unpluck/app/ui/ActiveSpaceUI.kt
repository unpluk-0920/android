// In ui/ActiveSpaceUI.kt
package com.unpluck.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unpluck.app.defs.Space

@Composable
fun ActiveSpaceUI(
    space: Space,
    onBlockNotifications: () -> Unit,
    onAllowNotifications: () -> Unit,
    onForceExit: () -> Unit
) {
    Scaffold(
        bottomBar = {
            Button(
                onClick = onForceExit,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Force Exit Focus Mode")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Rounded.ShieldMoon,
                contentDescription = "Focus Mode Active",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Focus Mode: ${space.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text("Allowed Apps", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            // TODO: Display the list of allowed app icons here from space.appIds
            Text("(App list will be shown here)")

            Spacer(modifier = Modifier.weight(1f))

            Row {
                Button(onClick = onBlockNotifications) {
                    Text("Block Notifications")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(onClick = onAllowNotifications) {
                    Text("Allow Notifications")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}