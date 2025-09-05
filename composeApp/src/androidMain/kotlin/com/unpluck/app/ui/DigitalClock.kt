// In ui/DigitalClock.kt
package com.unpluck.app.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DigitalClock() {
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }

    // This effect will run once and update the time every second
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1000) // Wait for 1 second
        }
    }

    // Define the formatters for time and date
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM dd")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = currentTime.format(timeFormatter),
            fontSize = 64.sp,
            fontWeight = FontWeight.Light,
            color = Color.DarkGray
        )
        Text(
            text = currentTime.format(dateFormatter),
            fontSize = 20.sp,
            fontWeight = FontWeight.Normal,
            color = Color.DarkGray.copy(alpha = 0.8f)
        )
    }
}