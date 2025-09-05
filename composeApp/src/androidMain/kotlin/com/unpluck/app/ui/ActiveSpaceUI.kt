// In ui/ActiveSpaceUI.kt
package com.unpluck.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.unpluck.app.defs.Space
import com.unpluck.app.ui.theme.GradientEnd
import com.unpluck.app.ui.theme.GradientMid
import com.unpluck.app.ui.theme.GradientMid2
import com.unpluck.app.ui.theme.GradientStart

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ActiveSpaceUI(
    space: Space,
    onForceExit: () -> Unit
) {
    val gradientColors = listOf(GradientStart, GradientMid, GradientMid2, GradientEnd)
    Scaffold(
        modifier = Modifier.background(
            brush = Brush.linearGradient(colors = gradientColors)
        ),
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Column(
            // 2. Make the Column fill the screen and center its children horizontally
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(top = 64.dp), // 3. Add padding to push content down from the top bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 4. Place the DigitalClock first
            DigitalClock()

            // 5. Add a Spacer for vertical distance
            Spacer(modifier = Modifier.height(24.dp))

            // 6. Add the new Text composable
            Text(
                text = space.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black.copy(alpha = 0.9f)
            )
        }
    }
}