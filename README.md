# Triggered Device Actions

> [!IMPORTANT]
> ### 🤖 AI Disclaimer
> This application—including event-driven background services, rule evaluation engine, ShareX/Webhook networking, and Jetpack Compose Material 3 UI—was architected and implemented by AI based on specifications from the repository owner.

---

[![Download Release](https://img.shields.io/github/v/release/acffordyce973/TriggeredDeviceActions?label=Latest%20Release&color=success)](https://github.com/acffordyce973/TriggeredDeviceActions/releases)
[![Add to Obtainium](https://img.shields.io/badge/Obtainium-Add_App-blue?logo=android)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/acffordyce973/TriggeredDeviceActions)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Triggered Device Actions** (*Triggered Actions* in the app launcher) is a battery-efficient, sideload-first Android automation engine sharing the clean 4-tab design language of **Triggered Live Wallpaper**. Define **Action Sets** combining multiple **Triggers**, **`AND` / `OR` Conditions** (with `NOT` inversion), and ordered **Sequential Actions** with live `{context}` variable extraction.

---

## 📥 Download & Installation

- **Direct Download:** Download the latest signed APK from the [Releases](https://github.com/acffordyce973/TriggeredDeviceActions/releases) page.
- **Obtainium:** Click below to add the app directly to [Obtainium](https://github.com/ImranR98/Obtainium) for automatic update tracking:
	- [**Add to Obtainium (App Link)**](obtainium://add/https://github.com/acffordyce973/TriggeredDeviceActions)
	- [**Add to Obtainium (Browser Redirect)**](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/acffordyce973/TriggeredDeviceActions)

---

## ✨ Highlights & Design Principles

- **Sets System with `AND` / `OR` / `NOT` Logic:** Combine one or more event triggers, zero or more contextual conditions (`ALL_AND` or `ANY_OR` plus per-condition `NOT`), and multi-step ordered actions.
- **Banking-App Safe (Zero AccessibilityService):** Deliberately omits `AccessibilityService` so banking and payment apps never flag or block the device. Foreground app detection uses `UsageStatsManager`, and background app/activity launching uses a transient `1x1` `SYSTEM_ALERT_WINDOW` launch bridge.
- **Direct Global Do Not Disturb Control:** Targets API 34 (compiled against SDK 36) so `NotificationManager.setInterruptionFilter()` directly toggles global Do Not Disturb on Android 10–16—including temporary repeat-caller breakthrough windows.
- **ShareX (`.sxcu`) & Discord Webhook Native:** Import standard `.sxcu` uploader files (including `WebServer/Shared`) to upload text snippets or latest screenshots/files, and forward extracted OTPs or event payloads to Discord or REST webhooks.
- **Triggered Live Wallpaper Ecosystem Parity:** Identical OpenStreetMap + Leaflet interactive Geofence picker, timestamped JSON config backups, and direct one-tap import of `listGeofences` from existing Triggered Live Wallpaper backup files.

---

## ⚡ Supported Triggers (16)

| Trigger | Refinements & Data Filters |
| :--- | :--- |
| **Geofence Location** | Specific Geofence Area; `Enter`, `Exit`, or `Both` transitions |
| **Time** | Exact daily `HH:mm` clock time (with Days of Week filter) or periodic *N*-minute interval |
| **Device Unlocked / Locked** | `Device Unlocked` (`USER_PRESENT`) or `Device Locked` |
| **Screen On / Off** | `Screen Turned On` or `Screen Turned Off` |
| **Wi-Fi Connected** | `Connected` / `Disconnected`; SSID match (`Any`, `Contains`, `Equals`, `Wildcard`, `Regex`) |
| **Mobile Data Connected** | `Connected` / `Disconnected`; optional `Require Not Roaming` |
| **Bluetooth Connected** | `Connected` / `Disconnected`; Paired BT Device Name or MAC (`Any`, `Equals`, `Contains`, `Wildcard`, `Regex`) |
| **Media Playing** | `Playback Started`, `Playback Stopped`, or `Track Changed`; Media App filter + Track/Artist filter |
| **Incoming Phone Call** | Caller filter (`Any`, `In Contacts`, `Not in Contacts`, `Starred Contacts`, `Specific Numbers / Regex`) |
| **Missed Call** | `Any Unanswered`, `Missed (Rang Out Only)`, or `Rejected by User`; Caller Contact/Number filter |
| **Ongoing / Answered Call** | `Incoming Answered`, `Outgoing Started`, `Any Ongoing`, or `Call Ended`; Contact/Number filter |
| **SMS Received** | Sender Contact/Number filter + Body match (`Any`, `Contains`, `Equals`, `Wildcard`, `Regex`, `OTP_PRESET`) |
| **Battery Status** | Edge-triggered threshold (`Drops <= X%`, `Rises >= X%`, `Equals X%`) |
| **Charging / Discharging** | `Plugged In` or `Unplugged`; Power source filter (`Any`, `AC`, `USB`, `Wireless`, `Dock`) |
| **Notification Received** | Source App package(s), Title & Body match (`OTP_PRESET`, `Regex`, `Wildcard`, `Contains`), Ignore Ongoing/Summary |
| **Notification Dismissed** | Source App package(s), Dismissal Reason (`Any`, `User Dismissed`, `User Clicked`, `App Cancelled`), Title/Body filter |

---

## 🔍 Supported Conditions (13 + `AND` / `OR` / `NOT`)

Every condition supports **`NOT` (Invert)** and participates in the Set's **`ALL_AND`** or **`ANY_OR`** evaluation mode:

| Condition | Options & Refinements |
| :--- | :--- |
| **Geofence** | `Inside` or `Outside` a configured Geofence Area |
| **Time** | Start–End Time Window (supports overnight wrap like `22:00`–`06:00`) + Days of Week |
| **Device Unlocked** | `Unlocked` or `Locked` |
| **Screen State** | `Screen On` (`PowerManager.isInteractive`) or `Screen Off` |
| **Wi-Fi State** | `Connected` (with optional SSID filter) or `Disconnected` |
| **Mobile Data State** | `Connected` (with optional roaming check) or `Disconnected` |
| **Bluetooth State** | `Connected` (any or specific paired device Name/MAC) or `Disconnected` |
| **Media Playing** | `Playing` or `Not Playing` (any app or specific media package) |
| **Ongoing Call** | `In Active Call` or `Idle (No Call)` |
| **Do Not Disturb (DnD)** | `Any DnD On`, `DnD Off`, `Priority Only`, `Alarms Only`, or `Total Silence` |
| **Battery Status** | Battery percentage `<=`, `>=`, or `==` target % |
| **Charging / Discharging** | `Charging` (`Any`, `AC`, `USB`, `Wireless`, `Dock`) or `Discharging` |
| **App Running / Open** | `Foreground Now`, `Recent Foreground` (last *N* seconds), or `Foreground / Active Media / Notification` |

---

## 🚀 Supported Sequential Actions (11)

| Action | Capabilities & Refinements |
| :--- | :--- |
| **Make a Sound** | System Ringtone/Alarm picker or direct audio file path; stream (`Alarm`, `Notification`, `Media`, `Ring`); volume override & vibrate |
| **Send Notification** | Custom Title & Body with `{variables}`, High/Standard priority, optional **Copy `{match}`** button & tap target |
| **Set Do Not Disturb** | `OFF_ALLOW_ALL`, `PRIORITY_ONLY`, `ALARMS_ONLY`, or `TOTAL_SILENCE` |
| **End / Reject Call** | Immediately ends/declines an active or ringing call via `TelecomManager.endCall()` |
| **Send SMS** | Send to dynamic `{caller_number}` / `{sms_sender}` or custom number; multipart support; anti-loop per-recipient cooldown |
| **Send Webhook** | Saved Discord / HTTP profiles or custom URL; Discord rich embeds or raw REST (`GET`/`POST`/`PUT`/`PATCH`) |
| **ShareX Upload** | Import `.sxcu` files; upload rendered text or file / latest file in folder; stores `{last_upload_url}` & copies URL |
| **Start App** | Launch any installed app from background triggers |
| **Start App Shortcut** | Pick via Android `ACTION_CREATE_SHORTCUT` or discovered static `<shortcuts>` XML definitions |
| **Start App Activity** | OS Presets (`APP_DETAILS_SETTINGS` for Force Stop / Clear Cache / Permissions, Wi-Fi, BT, DnD, etc.) or any exported Activity |
| **Wait / Delay** | Fixed delay in seconds (wakeful `AlarmManager` offload for `>45s`) + optional **Wait Until Phone Call Ends First** |

### Dynamic `{context}` Placeholders
Use these tokens inside Notification, SMS, Webhook, ShareX, and Activity fields:
`{match}`, `{otp}`, `{match_1}`..`{match_5}`, `{caller_number}`, `{caller_name}`, `{sms_sender}`, `{sms_body}`, `{notif_app}`, `{notif_pkg}`, `{notif_title}`, `{notif_text}`, `{geofence_name}`, `{geofence_transition}`, `{bt_device_name}`, `{bt_device_mac}`, `{wifi_ssid}`, `{battery_level}`, `{media_title}`, `{media_artist}`, `{foreground_pkg}`, `{last_upload_url}`, `{date}`, `{time}`, `{datetime}`.

> [!TIP]
> **Android 15+ Non-SMS Push OTP Notifications:** SMS OTPs via the `SMS Received` trigger are never redacted. On Android 15+, Android System Intelligence hides OTP text inside `NotificationListenerService` for third-party apps until unlocked once via ADB:
> ```bash
> adb shell cmd appops set --user 0 com.antigravity.triggeredactions RECEIVE_SENSITIVE_NOTIFICATIONS allow
> ```
> A one-tap copy button for this command appears automatically in the **Settings** tab on Android 15+ devices.

---

## 🔒 Permissions & Privacy

All rules, logs, and coordinates remain locally on your device; network connections are made exclusively to OpenStreetMap tile servers (when viewing the Geofence map) and user-configured Webhook / ShareX endpoints.

| Permission | Purpose & Justification |
| :--- | :--- |
| `android.permission.INTERNET` | Sends user-configured Discord/HTTP webhooks, ShareX uploads, and downloads OpenStreetMap tiles in the Geofence editor. |
| `android.permission.ACCESS_NETWORK_STATE` | Monitors Wi-Fi and Mobile Data connectivity transitions and capabilities. |
| `android.permission.ACCESS_WIFI_STATE` | Reads Wi-Fi adapter state and connected network SSID metadata. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restores geofences, scheduled time alarms, and trigger monitors automatically after reboot or app update. |
| `android.permission.WAKE_LOCK` | Ensures short-lived alarm receivers complete condition checks reliably during Doze sleep. |
| `android.permission.FOREGROUND_SERVICE` | Runs the event monitor and multi-step delayed action execution services without OS termination. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Required on Android 14+ for continuous background device automation monitoring and delayed action sequences. |
| `android.permission.SCHEDULE_EXACT_ALARM` | Schedules exact daily time triggers and wakeful multi-minute `Wait` continuations. |
| `android.permission.USE_EXACT_ALARM` | Guarantees exact alarm delivery for user-configured time schedules on Android 13+. |
| `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompts to exempt the automation engine from aggressive OEM background battery killers. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Powers the transient `1x1` non-focusable overlay launch bridge so background triggers can open apps and OS Settings activities on Android 10–16. |
| `android.permission.USE_FULL_SCREEN_INTENT` | Fallback heads-up launch notification if overlay permission has not been granted. |
| `android.permission.POST_NOTIFICATIONS` | Displays the optional monitor status notification and user-configured `Send Notification` alerts on Android 13+. |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | Reads and modifies Do Not Disturb (`NotificationManager.setInterruptionFilter`) states. |
| `android.permission.VIBRATE` | Triggers haptic vibration feedback when configured in `Make a Sound` actions. |
| `android.permission.ACCESS_FINE_LOCATION` | Evaluates precise GPS coordinates for Geofence triggers/conditions and Wi-Fi SSID resolution. |
| `android.permission.ACCESS_COARSE_LOCATION` | Base cell/network location permission paired with fine location. |
| `android.permission.ACCESS_BACKGROUND_LOCATION` | Enables hardware Geofence transitions and SSID checks while the screen is off or the app is in the background. |
| `android.permission.BLUETOOTH` / `BLUETOOTH_ADMIN` | Legacy Bluetooth adapter state and bonded device queries on Android 10–11 (API ≤ 30). |
| `android.permission.BLUETOOTH_CONNECT` | Reads connected and paired Bluetooth device names and MAC addresses on Android 12+ (API 31+). |
| `android.permission.READ_PHONE_STATE` | Detects incoming, answered, ongoing, and ended phone call state transitions and Dual-SIM state. |
| `android.permission.READ_CALL_LOG` | Required by Android 9+ alongside `READ_PHONE_STATE` to receive `EXTRA_INCOMING_NUMBER` and distinguish Missed vs Rejected calls. |
| `android.permission.READ_CONTACTS` | Checks whether callers or SMS senders belong to saved or starred Contacts. |
| `android.permission.ANSWER_PHONE_CALLS` | Executes the `End / Reject Call` action via `TelecomManager.endCall()`. |
| `android.permission.RECEIVE_SMS` | Receives incoming SMS PDUs for `SMS Received` triggers (including unredacted SMS OTPs). |
| `android.permission.SEND_SMS` | Dispatches automated SMS replies for the `Send SMS` action. |
| `android.permission.QUERY_ALL_PACKAGES` | Lists installed applications, exported activities, and static `<shortcuts>` XML definitions in the Set Editor pickers. |
| `android.permission.PACKAGE_USAGE_STATS` | Detects foreground/open applications for the `App Running / Open` condition without using `AccessibilityService`. |
| `android.permission.MANAGE_EXTERNAL_STORAGE` | Enables direct file path access (e.g. `/sdcard/Pictures/Screenshots`) for ShareX file uploads and custom audio files. |
| `android.permission.READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` | Standard media/storage read compatibility for sound files and file uploads. |
| `android.permission.WRITE_SECURE_SETTINGS` | Optional power-user permission (if granted via ADB) for direct `zen_mode` synchronization. |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | System service binding for Notification Received/Dismissed triggers and `MediaSessionManager` playback tracking. |

---

## 🛠️ Building & Testing

- **Debug APK:** Run `BuildDebug.bat` or `./gradlew assembleDebug`
- **Unit Tests:** Run `./gradlew testDebugUnitTest`
- **CI/CD Releases:** Pushing a semantic version tag (`git tag 1.0.0 && git push origin 1.0.0`) builds, signs, and publishes the release APK via GitHub Actions.

---

## 👥 Credits & Acknowledgments

- **Google AndroidX & Jetpack Compose (Material 3):** Declarative UI, Activity Compose, Lifecycle ViewModel, DocumentFile, and Core KTX.
- **AndroidX WorkManager:** Background work infrastructure.
- **AndroidX WebKit:** Secure `WebViewAssetLoader` hosting for local map assets.
- **Google Play Services Location:** Low-power hardware geofencing and fused location provider.
- **Square OkHttp & MockWebServer:** Reliable HTTP/Webhook dispatch, multipart ShareX uploads, and HTTP test verification.
- **Kotlinx Serialization & Coroutines:** Polymorphic JSON rule persistence and structured concurrency.
- **Leaflet & OpenStreetMap:** Interactive map rendering in the Geofence picker.
- **Obtainium:** Direct decentralized APK updates from GitHub Releases.

---

## 📄 License

Licensed under the **Apache License 2.0** with commercial app store redistribution restrictions. See [LICENSE](LICENSE) for full details.
