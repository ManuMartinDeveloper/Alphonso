# Alphonso Security App

Alphonso is an Android application designed for policy enforcement and monitoring using Device Admin and Accessibility Services.

## Features
- **Policy Enforcement:** Uses Device Policy Manager (DPM) to lock down the device, restrict certain actions, and act as a Device Owner.
- **On-Device Machine Learning:** Uses ONNX Runtime for real-time inference on screen contents, monitoring applications to maintain policies.
- **Persistent Locking:** Enforces lockdowns for applications that violate policies. These lockouts are resilient and persist across app reboots via RoomDB.
- **Local & Remote Configuration:** Relies on Firebase Realtime Database for managing remote configurations (e.g., threshold limits, blocklists) and logs incidents securely. Includes local RoomDB for offline auditing and AI retraining tasks.

## Setup Instructions

1. **Clone the Repository:** Download the project source code.
2. **Open in Android Studio:** Import the project into Android Studio (built using Gradle).
3. **Configure Firebase:**
   - Add your `google-services.json` file inside the `app/` directory.
   - Make sure you enable Firebase Authentication and Realtime Database in your Firebase console.
4. **Build and Run:** Run `./gradlew assembleDebug` or build directly from Android Studio.

## Architecture

- **`MainActivity`:** Entry point of the app handling basic permissions and starting the Monitor Service.
- **`ConsciousnessAccessibilityService`:** Core AI scanning logic. Analyzes screen text and uses ONNX for content inspection. It executes lockouts via DPM.
- **`AppMonitorService`:** A foreground service that maintains the application's lifecycle, ensuring that the watchdog is always active.
- **`PolicyManager`:** Manages global policies applied to the Android device using `DevicePolicyManager`.
- **`DebugActivity` / `SettingsActivity`:** Provides insights into the event logs, AI confidence thresholds, and system statuses.

## Permissions Required
The application uses highly sensitive permissions:
- `SYSTEM_ALERT_WINDOW`: For the censorship overlay.
- `BIND_ACCESSIBILITY_SERVICE`: To read screen contents.
- `BIND_DEVICE_ADMIN`: For policy enforcement.

> **Note:** For the device admin and policy enforcement to work as intended, the application must be set as a Device Owner via ADB after installation:
> `adb shell dpm set-device-owner com.alphonso/.ConsciousnessDeviceAdminReceiver`
