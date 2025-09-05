// In ui/ActiveSpaceUI.kt
package com.unpluck.app.ui

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    onSettingsClicked: () -> Unit,
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

            Box(modifier = Modifier.clickable() { onSettingsClicked() }) {
                SpinningStarText(text = space.name)
            }

            Spacer(modifier = Modifier.weight(1f))

            LazyColumn (
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp), // Set a max height for the list
                contentPadding = PaddingValues(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Use .take(5) to ensure we only show at most 5 apps
                items(space.appIds.take(5)) { packageName ->
                    AppListItem(packageName = packageName)
                }
            }

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
private fun SpinningStarText(text: String) {
    // 1. Use rememberInfiniteTransition to create a looping animation.
    val infiniteTransition = rememberInfiniteTransition(label = "infinite rotation")

    // 2. Animate a float value from 0 to 360 degrees, repeating forever.
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween (durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = "Spinning Star",
            tint = Color.Black.copy(alpha = 0.7f),
            // 3. Apply the rotation to the Icon's modifier.
            modifier = Modifier.rotate(rotationAngle)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun AppListItem(packageName: String) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val appLabel = remember(packageName) {
        try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    if (appLabel != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // 2. Get the specific launch intent for this app's package name.
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

                    // 3. Check if the intent is valid and then launch the activity.
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    } else {
                        // Optionally, handle the case where the app can't be launched
                        Toast.makeText(context, "$appLabel can't be opened", Toast.LENGTH_SHORT).show()
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = appLabel,
                color = Color.Black,
                fontWeight = FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}