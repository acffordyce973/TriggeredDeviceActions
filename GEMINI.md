# Gemini Agent Guide - Triggered Device Actions

## 📌 Project Overview
**Triggered Device Actions** (launcher label: **Triggered Actions**) is a battery-efficient, non-Play-Store Android device automation application built with **Kotlin**, **Jetpack Compose (Material 3)**, and event-driven Android platform services. It evaluates user-defined **Action Sets** consisting of one or more **Triggers** (OR entrypoints), zero or more **Conditions** (`ALL_AND` / `ANY_OR` with per-condition `NOT` inversion), and sequential **Actions** (with dynamic `{context}` variable substitution).

- **Package Name / Namespace:** `com.antigravity.triggeredactions`
- **Minimum SDK:** `29` (Android 10)
- **Target SDK:** `34` (Android 14 — intentionally retained for sideloaded Global Do Not Disturb `setInterruptionFilter` control on Android 15/16)
- **Compile SDK:** `36` (Android 16)
- **Banking-App Compatibility:** `AccessibilityService` is strictly omitted from `AndroidManifest.xml` and source code so banking and financial apps never flag this package. Foreground app checks use `UsageStatsManager`, and background activity launches use a transient `1x1` `SYSTEM_ALERT_WINDOW` bridge.

---

## 🏗️ Directory Structure
- `app/src/main/java/com/antigravity/triggeredactions/`
	- `model/`: Polymorphic `@Serializable` models (`ActionModels.kt`) for `ActionSet`, all 16 `SetTrigger`s, all 13 `SetCondition`s, all 11 `SetAction`s, `GeofenceArea`, `WebhookProfile`, `ShareXProfile`, `TriggerEventContext`, `LiveDeviceState`, `ExecutionLogEntry`, and `AppSettings`.
	- `data/`: `ActionsRepository.kt` (JSON config persistence, backup import/export, Triggered Live Wallpaper geofence import, starter templates), `ExecutionLogStore.kt`, `ContactLookupHelper.kt`, and `InstalledAppsRepository.kt`.
	- `engine/`: `TriggerDispatcher.kt`, `ConditionEvaluator.kt`, `ContextTemplateEngine.kt` (OTP & ReDoS-safe regex extractor + variable substitution), `ActionExecutor.kt`, `BackgroundLaunchBridge.kt`, `DeviceStateCollector.kt`, and `ConflictAndAuditDetector.kt`.
	- `network/`: `WebhookClient.kt` (Discord rich embeds + generic HTTP REST) and `ShareXClient.kt` (`.sxcu` parser/importer + text/file multipart uploader compatible with `WebServer/Shared`).
	- `service/`: `TriggeredNotificationListenerService.kt` (notifications + `MediaSessionManager`), `TriggeredMonitorService.kt`, `DynamicEventMonitor.kt`, `TriggeredExecutionService.kt`, and `TriggeredQuickTileService.kt`.
	- `receiver/`: `BootAndSystemReceiver.kt`, `AlarmBroadcastReceiver.kt`, `GeofenceBroadcastReceiver.kt`, `TelephonyCallReceiver.kt`, `SmsBroadcastReceiver.kt`, `BluetoothConnectionReceiver.kt`, and `NotificationActionReceiver.kt`.
	- `scheduler/`: `ActionGeofenceScheduler.kt` and `ActionAlarmScheduler.kt`.
	- `notification/`: `ActionNotificationHelper.kt`.
	- `ui/`: Material 3 Compose screens (`MainScreen.kt`, `MainViewModel.kt`, `StatusTab.kt`, `ActionSetsTab.kt`, `RulesAuditTab.kt`, `SettingsTab.kt`), `InteractiveGeofenceMap.kt`, and editor/picker dialogs.
- `.github/workflows/github-actions.yml`: Automated signed APK release workflow triggered on git tag push.
- `BuildDebug.bat`: One-click debug APK build wrapper (`./gradlew assembleDebug`).

---

## 📋 MANDATORY DOCUMENTATION RULES FOR AI AGENTS

Whenever you make changes to this codebase, you **MUST** uphold the following documentation requirements:

1. **Permission Changes (`AndroidManifest.xml`):**
	- If ANY permission is added, modified, or removed, you **MUST immediately update the Permissions table in [`README.md`](file:///D:/Personal/Projects/TriggeredActions/README.md)**.
	- Never add `BIND_ACCESSIBILITY_SERVICE` unless explicitly requested by the repository owner.

2. **Library / Dependency Changes (`gradle/libs.versions.toml` or `app/build.gradle.kts`):**
	- If ANY new library, SDK, or third-party dependency is introduced, you **MUST credit it in [`README.md`](file:///D:/Personal/Projects/TriggeredActions/README.md)** under **Credits & Acknowledgments**.

3. **Feature / Trigger / Condition / Action Changes:**
	- Any newly added or modified features, triggers, conditions, or actions **MUST be synchronized in [`README.md`](file:///D:/Personal/Projects/TriggeredActions/README.md)**.

---

## 📐 Coding Conventions

1. **Function Naming:** `<purpose><Name>` (e.g., `evaluateSetConditions`, `executeSingleAction`, `parseSxcuJson`, `formatBackupFilename`). Compose UI `@Composable` components use standard PascalCase (`MainScreen`, `StatusTab`).
2. **Variable Naming:** `<typePrefix><Name>` (`bool`, `int`, `long`, `float`, `double`, `string`, `list`, `set`, `map`, `file`, `dir`, `uri`, `intent`, `obj`).
3. **Indentation:** Use **tabs** across all files. Never spaces.
4. **Spacing & Documentation:** One blank line between functions; two blank lines before `// ---- Sectional Comments ----` and one blank line after; comprehensive KDoc on functions/classes; structured `[ScriptName] Action Target - ErrorType: Message` error logging.
5. **Versioning:** `<major>.<minor>.<revision>` in `app/build.gradle.kts` (`versionName` + `versionCode`).

---

## 🔐 Keystore and Secrets Safety
- **NEVER** commit `keystore.properties`, `*.keystore`, `*.jks`, or `local.properties` to git.
