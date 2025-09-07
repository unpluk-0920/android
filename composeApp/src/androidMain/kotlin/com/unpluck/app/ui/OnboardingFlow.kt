package com.unpluck.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.unpluck.app.MainViewModel
import com.unpluck.app.OnboardingStep

@Composable
fun OnboardingFlow(
    viewModel: MainViewModel,
    onRequestBle: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestLocation: () -> Unit,
    onRequestPhone: () -> Unit
) {
    val currentStep by viewModel.currentOnboardingStep
    val context = LocalContext.current

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
                notificationGranted = viewModel.notificationPermissionGranted.value,
                locationGranted = viewModel.locationPermissionGranted.value,
                phoneGranted = viewModel.phonePermissionsGranted.value,
                onRequestNotification = onRequestNotification,
                onRequestLocation = onRequestLocation,
                onRequestPhone = onRequestPhone,
                onFinish = {
                    viewModel.onPermissionsFinished()
                }
            )
        }
        OnboardingStep.CONNECT_DEVICE -> {
            val status by viewModel.connectionStatus
            val isConnected by viewModel.isDeviceConnected
            val isScanning by viewModel.isScanning
            val devices by viewModel.foundDevices
            val context = LocalContext.current

            ConnectDeviceScreen(
                connectionStatus = status,
                isConnected = isConnected,
                isScanning = isScanning,
                foundDevices = devices,
                onScan = { viewModel.startScan(context) },
                onConnectToDevice = { address -> viewModel.connectToDevice(context, address) },
                onNext = { viewModel.onDeviceConnected() }
            )
        }
        OnboardingStep.CREATE_SPACE -> {
            CreateSpaceScreen(
                onCreate = {
                    name -> viewModel.createNewSpace(context, name)
                    viewModel.onSpaceCreationDone()
                },
                onClose = {
                    viewModel.navigateBack()
                }
            )
        }
        OnboardingStep.SET_LAUNCHER -> {
            SetLauncherScreen(
                onSetDefaultLauncher = {
                    // 1. Finish onboarding and save the flag FIRST.
                    viewModel.onFinishOnboarding(context)

                    // 2. THEN, send the user to the system settings.
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }
    }
}