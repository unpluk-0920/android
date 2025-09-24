# Unpluck App

Unpluck is an innovative Android application designed to help users manage their digital well-being by providing powerful focus tools, custom app spaces, and smart launcher proxying. It aims to reduce distractions and enhance productivity by giving users granular control over their app environment.

## Table of Contents

1.  [Features](#1-features)
2.  [Architecture](#2-architecture)
3.  [Getting Started](#3-getting-started)
  * [Prerequisites](#prerequisites)
  * [Installation](#installation)
  * [Build Instructions](#build-instructions)
1. [App Flow](#4-app-flow)
2. [Development Notes](#5-development-notes)
  * [`android:allowBackup="false"`](#androidallowbackupfalse)
  * [Testing Onboarding Flow](#testing-onboarding-flow)
  * [Onboarding Re-validation (Future Enhancement)](#onboarding-re-validation-future-enhancement)
1. [Contributing](#6-contributing)
2. [License](#7-license)

---

## 1. Features

* **Custom App Spaces:** Create dedicated "spaces" with specific sets of apps for different activities (e.g., "Work Space," "Focus Space").
* **Focus Mode:** An enhanced mode to minimize distractions and enhance productivity.
* **Launcher Proxying:** Seamlessly integrate with existing third-party launchers while still benefiting from Unpluck's features.
* **Onboarding Flow:** A guided setup for new users to configure permissions, connect devices, and personalize their initial experience.
* **BLE Device Integration (Planned/In Progress):** Connect with physical Unpluck devices for enhanced control.
* **Permission Management:** Centralized handling of critical Android permissions (BLE, Overlay, DND, Notifications, Location, Phone).

## 2. Architecture

Unpluck follows a modern Android architecture pattern, primarily leveraging:

* **Jetpack Compose:** For declarative UI development.
* **MVVM (Model-View-ViewModel):** For clear separation of concerns.
* **Kotlin Flow:** For asynchronous data streams.
* **Room Persistence Library:** For local data storage (SQLite).

## 3. Getting Started

### Prerequisites

* Android Studio Jellyfish | 2023.3.1 or newer
* Android SDK API Level 34
* Kotlin 1.9.0 or newer
* Gradle 8.4 or newer

### Installation

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/your-username/unpluck-app.git](https://github.com/your-username/unpluck-app.git)
    cd unpluck-app
    ```
2.  **Open in Android Studio:** Launch Android Studio and open the cloned project.
3.  **Sync Gradle:** Allow Android Studio to sync dependencies.

### Build Instructions

1.  **Clean and Rebuild:** `Build > Clean Project`, then `Build > Rebuild Project`.
2.  **Run:** Connect a device/emulator and click the "Run" button in Android Studio.

## 4. App Flow

Unpluck uses a `HomeRouterActivity` as its single entry point to manage initial navigation, ensuring a consistent user experience based on the app's current state (onboarding status, launcher selection).

```mermaid
graph TD
    A[App Icon Tapped] --> B{HomeRouterActivity<br>onCreate()};

    B --> C{Read SharedPreferences<br> (onboardingCompleted, launcherSelected, realLauncherPackage)};

    C --> D{IF NOT onboardingCompleted};
    D -- True --> E[Start OnboardingActivity];
    E --> F[HomeRouterActivity finish()];

    D --> G{ELSE IF NOT launcherSelected OR<br> realLauncherPackage IS NULL};
    G -- True --> H[Start OnboardingActivity<br> (with flag: start_on_launcher_selection=true)];
    H --> F;

    G -- False --> I{ELSE (onboardingCompleted TRUE AND<br> launcherSelected TRUE)};
    I --> J{Attempt to Launch<br> realLauncherPackage};
    J -- Success --> K[Start External Launcher App];
    J -- Failure (e.g., launcher uninstalled) --> L[Start MainActivity<br> (Fallback to Unpluck Main UI)];
    K --> F;
    L --> F;

    E --> M{OnboardingActivity<br>onCreate()};
    M --> N{IF start_on_launcher_selection flag};
    N -- True --> O[Set currentOnboardingStep = SET_LAUNCHER];
    N -- False --> P[Set currentOnboardingStep = INTRO];
    O --> Q[OnboardingFlow Composables];
    P --> Q;

    Q --> R{OnboardingFlow Steps};
    R -- Step 1: INTRO --> R1[IntroScreen];
    R1 -- onNext --> R2[viewModel.onIntroFinished()<br> (currentOnboardingStep = PERMISSIONS)];
    R -- Step 2: PERMISSIONS --> R3[PermissionsScreen];
    R3 -- onAllPermissionsGranted --> R4[viewModel.onPermissionsFinished()<br> (currentOnboardingStep = CONNECT_DEVICE)];
    R -- Step 3: CONNECT_DEVICE --> R5[ConnectDeviceScreen];
    R5 -- onDeviceConnected --> R6[viewModel.onDeviceConnected()<br> (currentOnboardingStep = CREATE_SPACE)];
    R -- Step 4: CREATE_SPACE --> R7[CreateSpaceScreen];
    R7 -- onSpaceCreated --> R8[viewModel.onSpaceCreated()<br> (currentOnboardingStep = SET_LAUNCHER)];
    R -- Step 5: SET_LAUNCHER --> R9[LauncherSelectionScreen];

    R9 -- onLauncherSet (user selects external or Unpluck)<br>(calls viewModel.onFinishOnboarding) --> S[OnboardingActivity: Finish()<br>AND Start MainActivity];
    S --> T[MainActivity<br>onCreate() (Displays MainAppUI)];
    T --> End(App Ready for Use);
```

## 5. Development Notes

### `android:allowBackup="false"`

* **In `AndroidManifest.xml`:** The attribute `android:allowBackup="false"` is set within the `<application>` tag.
* **Reason:** This prevents Android's automatic backup service from retaining app data (including `SharedPreferences`) across reinstalls, which can cause inconsistent behavior during development, especially on some physical devices.
* **Development Benefit:** Ensures a truly "fresh install" experience for reliable testing.
* **Production Note:** Setting `allowBackup="false"` means user data will **not** be automatically backed up by Google Drive.

### Testing Onboarding Flow

To reliably test the onboarding process from the start:

1.  **Ensure `android:allowBackup="false"` is set in your `AndroidManifest.xml`.**
2.  **Clear App Data:** Open a terminal in Android Studio and run `adb shell pm clear com.unpluck.app` (replace with your package name). This guarantees all app-private data is wiped.
3.  **Run:** Deploy the app from Android Studio. It should now begin with the `IntroScreen`.

### Onboarding Re-validation (Future Enhancement)

For production builds where user data backup might be desirable (`android:allowBackup="true"`), consider implementing an onboarding re-validation strategy. This involves:

* **Checking minimum functional requirements** (e.g., does the user have at least one `Space` created in the database?) instead of solely relying on the `onboardingCompleted` flag from `SharedPreferences`.
* If essential data is missing, the app can intelligently redirect the user back to the relevant onboarding step, even if the `onboardingCompleted` flag is `true` from a restored backup.

## 6. Contributing

We welcome contributions to Unpluck! Please refer to our [CONTRIBUTING.md](https://github.com/your-username/unpluck-app/blob/main/CONTRIBUTING.md) (if applicable) for guidelines.

## 7. License

This project is licensed under the MIT License - see the [LICENSE](https://github.com/your-username/unpluck-app/blob/main/LICENSE) file for details.