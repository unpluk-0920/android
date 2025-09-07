// In ui/SpaceSettingScreen.kt
package com.unpluck.app.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unpluck.app.MainViewModel
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSettingScreen(viewModel: MainViewModel) {
    val space = viewModel.spaceToEdit.value ?: return
    val context = LocalContext.current
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)

    var currentName by remember { mutableStateOf(space.name) }

    Scaffold(
        modifier = Modifier.background(
            brush = Brush.linearGradient(colors = gradientColors)
        ),
        containerColor = Color.Transparent,
        // 1. The topBar parameter has been completely removed.
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 2. A new Row is added as the first item to act as a custom header.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }

                    Text(
                        "Edit Space",
                        color = Color.Black,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f) // This makes the title center itself
                    )

                    TextButton(
                        onClick = { viewModel.updateSpaceName(context, currentName) },
                        enabled = currentName.isNotBlank() && currentName != space.name,
                    ) {
                        Text("Save")
                    }
                }
            }

            // 3. The rest of your content remains the same.
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                OutlinedTextField(
                    value = currentName,
                    onValueChange = { currentName = it },
                    label = { Text("Space Name") },
                    modifier = Modifier.fillMaxWidth(),
//                    colors = TextFieldDefaults.outlinedTextFieldColors(
//                        // ... your text field colors ...
//                    )
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                SettingsRow(
                    icon = Icons.Rounded.Contacts,
                    title = "Allowed Contacts",
                    onClick = { Log.d("Settings", "Navigate to Allowed Contacts screen") }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = "Block Notifications",
                    onClick = { Log.d("Settings", "Navigate to Notification settings screen") }
                )
            }
            item {
                SettingsRow(
                    icon = Icons.Rounded.PhoneDisabled,
                    title = "Block Calls",
                    onClick = { Log.d("Settings", "Navigate to Block Calls screen") }
                )
            }
            item {
                val context = LocalContext.current
                SettingsRow(
                    icon = Icons.Rounded.Apps,
                    title = "Apps Allowed",
                    onClick = {
                        viewModel.loadAllInstalledApps(context)
                        viewModel.navigateToAppSelection()
                    }
                )
            }
        }
    }
}