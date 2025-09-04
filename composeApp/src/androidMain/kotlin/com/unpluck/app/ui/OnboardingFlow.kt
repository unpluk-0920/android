// In ui/OnboardingFlow.kt
package com.unpluck.app.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.unpluck.app.MainViewModel

@Composable
fun OnboardingFlow(
    viewModel: MainViewModel,
    onRequestBle: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestDnd: () -> Unit
) {
    // This state manages which step we are on (0 = Welcome, 1 = Permissions)
    var currentStep by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    when (currentStep) {
        0 -> {
            // Step 1: Show the Welcome Screen
            WelcomeScreen(onNext = { currentStep = 1 })
        }
        1 -> {
            // Step 2: Show the Permissions Screen
            val bleGranted by viewModel.blePermissionsGranted
            val overlayGranted by viewModel.overlayPermissionGranted
            val dndGranted by viewModel.dndPermissionGranted

            PermissionsScreen(
                bleGranted = bleGranted,
                overlayGranted = overlayGranted,
                dndGranted = dndGranted,
                onRequestBle = onRequestBle,
                onRequestOverlay = onRequestOverlay,
                onRequestDnd = onRequestDnd,
                onFinish = {
                    // When finished, call the ViewModel function
                    viewModel.onFinishOnboarding(context)
                }
            )
        }
    }
}