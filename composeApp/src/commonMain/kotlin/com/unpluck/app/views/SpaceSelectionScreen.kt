// In composeApp/src/commonMain/kotlin/com/unpluck/app/SpaceSelectionScreen.kt
package com.unpluck.app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unpluck.app.defs.Space


@Composable
fun LauncherSelectionScreen(
    // This will be a list of LauncherInfo objects, but we can't use that type in commonMain.
    // So we'll pass the data as simple lists.
    labels: List<String>,
    onLauncherSelected: (Int) -> Unit,
) {
    // We need to add implementations for Image composable to render the icon
    // For now, let's just show the names.
    Column (
        modifier = Modifier.fillMaxSize().background(Color.DarkGray).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Select Your Preferred Launcher", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn {
            items(labels.size) { index ->
                Button(
                    onClick = { onLauncherSelected(index) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(labels[index], fontSize = 18.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SpaceSelectionScreen(
    spaces: List<Space>,
    onSpaceSelected: (Space) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select a Space",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 24.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(spaces) { space ->
                Button(
                    onClick = { onSpaceSelected(space) }, // Call the callback on click
                    modifier = Modifier.fillMaxWidth().height(60.dp)
                ) {
                    Text(text = space.name, fontSize = 20.sp)
                }
            }
        }
    }
}