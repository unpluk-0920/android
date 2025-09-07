// In ui/ConnectDeviceScreen.kt
package com.unpluck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unpluck.app.defs.BleDevice
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart

@Composable
fun ConnectDeviceScreen(
    connectionStatus: String,
    isConnected: Boolean,
    isScanning: Boolean,
    foundDevices: List<BleDevice>,
    onScan: () -> Unit,
    onConnectToDevice: (String) -> Unit,
    onNext: () -> Unit
) {
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)

    Scaffold(
        modifier = Modifier.background(
            brush = Brush.linearGradient(colors = gradientColors)
        ),
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        )
        {
            // ... Your Icon and Title Text ...

            Spacer(modifier = Modifier.height(32.dp))

            if (isConnected) {
                Text("Successfully connected!", color = MaterialTheme.colorScheme.primary)
                Button(onClick = onNext, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Next")
                }
            } else {
                // Show the scan button if we're not already scanning
                if (!isScanning) {
                    Button(onClick = onScan) { Text("Scan for Devices") }
                } else {
                    CircularProgressIndicator()
                    Text("Scanning...", modifier = Modifier.padding(top = 8.dp))
                }

                // List of found devices
                LazyColumn (modifier = Modifier.padding(top = 16.dp).heightIn(max = 200.dp)) {
                    items(foundDevices) { device ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onConnectToDevice(device.address) }) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(device.name, fontWeight = FontWeight.Bold)
                                Text(device.address, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text(connectionStatus, modifier = Modifier.padding(top = 16.dp))
        }

    }

}