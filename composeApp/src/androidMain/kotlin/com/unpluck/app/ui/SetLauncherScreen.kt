// In ui/SetLauncherScreen.kt
package com.unpluck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart

@Composable
fun SetLauncherScreen(onSetDefaultLauncher: () -> Unit) {
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)
    Scaffold(
            modifier = Modifier.background(
                brush = Brush.linearGradient(colors = gradientColors)
            ),
            containerColor = Color.Transparent,
        ) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        )
        {
            Icon(
                Icons.Rounded.Home,
                contentDescription = "Set Default Launcher",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Final Step: Set as Default",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "To enable Focus Mode, Unpluk needs to be your default home app. When you're not in Focus Mode, we'll send you right to your original launcher.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onSetDefaultLauncher,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Settings to Set Default")
            }
        }
    }

}