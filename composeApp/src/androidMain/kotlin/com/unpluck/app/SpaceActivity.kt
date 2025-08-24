package com.unpluck.app

import SpaceScreen
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class SpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content to our shared Composable
        setContent {
            SpaceScreen()
        }
    }
}