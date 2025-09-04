// In ui/OnboardingFlow.kt
package com.unpluck.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.unpluck.app.MainViewModel
import com.unpluck.app.OnboardingStep

@Composable
fun OnboardingFlow(
    viewModel: MainViewModel,
    onRequestBle: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestDnd: () -> Unit
) {
    val currentStep by viewModel.currentOnboardingStep
    val context = LocalContext.current

    val homeSettingsLauncher = rememberLauncherForActivityResult (
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // After they return from the settings screen, we finish onboarding
        viewModel.onFinishOnboarding(context)
    }
    when (currentStep) {
        OnboardingStep.INTRO -> {
            IntroPagerScreen(onGetStarted = { viewModel.onIntroFinished() })
        }
        OnboardingStep.PERMISSIONS -> {
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
                    viewModel.onPermissionsFinished()
                }
            )
        }
        OnboardingStep.CONNECT_DEVICE -> {
            val status by viewModel.connectionStatus
            val isConnected by viewModel.isDeviceConnected
            val context = LocalContext.current
            ConnectDeviceScreen(
                connectionStatus = status,
                isConnected = isConnected,
                onConnect = { viewModel.startDeviceConnection(context) },
                onNext = { viewModel.onDeviceConnected() }
            )
        }
        OnboardingStep.CREATE_SPACE -> {
            CreateSpaceScreen(viewModel = viewModel)
        }
        OnboardingStep.SET_LAUNCHER -> {
            SetLauncherScreen(
                onSetDefaultLauncher = {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    homeSettingsLauncher.launch(intent)
                }
            )
        }
    }
}