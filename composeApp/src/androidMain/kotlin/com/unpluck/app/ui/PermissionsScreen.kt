package com.unpluck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.DoNotDisturb
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    bleGranted: Boolean,
    overlayGranted: Boolean,
    dndGranted: Boolean,
    onRequestBle: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestDnd: () -> Unit,
    notificationGranted: Boolean,
    locationGranted: Boolean,
    phoneGranted: Boolean,
    onRequestNotification: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestPhone: () -> Unit,
    onFinish: () -> Unit
) {
    val allPermissionsGranted = bleGranted && overlayGranted && dndGranted && notificationGranted && locationGranted && phoneGranted

    Scaffold(
        bottomBar = {
            // The finish button is only visible when all permissions are granted
            AnimatedVisibility(
                visible = allPermissionsGranted,
                enter = fadeIn(animationSpec = tween(500)),
                exit = fadeOut(animationSpec = tween(500))
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                ) {
                    Text("Let's Connect to SmartCover")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                "One Last Step",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Unpluk needs these permissions to function correctly.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))

            // The list of permissions
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PermissionRow(
                    icon = Icons.Rounded.Bluetooth,
                    title = "Bluetooth",
                    rationale = "To connect with your Unpluk smart case.",
                    isGranted = bleGranted,
                    onRequest = onRequestBle
                )
                PermissionRow(
                    icon = Icons.Rounded.Layers,
                    title = "Display Over Other Apps",
                    rationale = "Allows Focus Mode to appear instantly.",
                    isGranted = overlayGranted,
                    onRequest = onRequestOverlay
                )
                PermissionRow(
                    icon = Icons.Rounded.DoNotDisturb,
                    title = "Do Not Disturb Access",
                    rationale = "To block notifications in Focus Mode.",
                    isGranted = dndGranted,
                    onRequest = onRequestDnd
                )
                PermissionRow(
                    icon = Icons.Rounded.Notifications,
                    title = "Notifications",
                    rationale = "Required to show the persistent service notification.",
                    isGranted = notificationGranted,
                    onRequest = onRequestNotification
                )
                PermissionRow(
                    icon = Icons.Rounded.LocationOn,
                    title = "Precise Location",
                    rationale = "Required by Android to scan for nearby Bluetooth devices.",
                    isGranted = locationGranted,
                    onRequest = onRequestLocation
                )
                PermissionRow(
                    icon = Icons.Rounded.Call,
                    title = "Phone & Contacts",
                    rationale = "To identify and block unwanted calls during Focus Mode.",
                    isGranted = phoneGranted,
                    onRequest = onRequestPhone
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    rationale: String,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The green checkmark
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Status",
                modifier = Modifier.size(32.dp),
                tint = if (isGranted) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )

            // Title and rationale
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(rationale, style = MaterialTheme.typography.bodySmall)
            }

            // Grant button
            if (!isGranted) {
                Button(onClick = onRequest) {
                    Text("Grant")
                }
            }
        }
    }
}