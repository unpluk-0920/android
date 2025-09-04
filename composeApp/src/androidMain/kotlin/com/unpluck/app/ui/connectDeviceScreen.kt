// In ui/ConnectDeviceScreen.kt
package com.unpluck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ConnectDeviceScreen(
    connectionStatus: String,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isConnected) {
            Icon(Icons.Rounded.BluetoothConnected, contentDescription = "Connected", modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        } else {
            Icon(Icons.Rounded.BluetoothSearching, contentDescription = "Searching", modifier = Modifier.size(80.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Connect Your SmartCase", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Press the button below and make sure your Unpluk case is nearby.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))

        if (!isConnected) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Find My Case")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(connectionStatus, style = MaterialTheme.typography.labelLarge)

        AnimatedVisibility(visible = isConnected) {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                Text("Next")
            }
        }
    }
}