package com.antigravity.triggeredactions.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// ---- Core Enums & Filter Modes ----

/**
 * Determines whether all conditions in an [ActionSet] must pass (`ALL_AND`)
 * or at least one condition must pass (`ANY_OR`).
 */
@Serializable
enum class ConditionMatchMode {
	ALL_AND,
	ANY_OR
}

/**
 * Controls how an [ActionSet] behaves if triggered again while a sequence (such as a Wait step) is running.
 */
@Serializable
enum class ConcurrencyPolicy {
	SKIP_IF_RUNNING,
	RESTART_SEQUENCE,
	RUN_PARALLEL
}

/**
 * Text and pattern matching strategy used across triggers and conditions.
 */
@Serializable
enum class TextMatchMode {
	ANY,
	CONTAINS,
	EQUALS,
	WILDCARD,
	REGEX,
	OTP_PRESET
}

/**
 * Contact book filtering mode for call and SMS triggers/conditions.
 */
@Serializable
enum class ContactFilterMode {
	ANY,
	IN_CONTACTS,
	NOT_IN_CONTACTS,
	STARRED_CONTACTS,
	SPECIFIC_NUMBERS_OR_REGEX
}

/**
 * Geofence boundary crossing direction.
 */
@Serializable
enum class GeofenceTransitionMode {
	ENTER,
	EXIT,
	BOTH
}

/**
 * Hardware charging power source filter.
 */
@Serializable
enum class ChargingSourceFilter {
	ANY,
	AC,
	USB,
	WIRELESS,
	DOCK
}

/**
 * Numeric comparison operator for battery percentage triggers and conditions.
 */
@Serializable
enum class BatteryThresholdOperator {
	DROPS_AT_OR_BELOW,
	RISES_AT_OR_ABOVE,
	EQUALS,
	LESS_THAN_OR_EQUAL,
	GREATER_THAN_OR_EQUAL
}

/**
 * Target Do Not Disturb interruption filter applied by [SetAction.SetDndAction].
 */
@Serializable
enum class DndTargetMode {
	OFF_ALLOW_ALL,
	PRIORITY_ONLY,
	ALARMS_ONLY,
	TOTAL_SILENCE
}

/**
 * Do Not Disturb state evaluated by [SetCondition.DndStatusCondition].
 */
@Serializable
enum class DndConditionMode {
	ANY_DND_ON,
	DND_OFF,
	PRIORITY_ONLY,
	ALARMS_ONLY,
	TOTAL_SILENCE
}

/**
 * Call transition event direction for [SetTrigger.OngoingCallTrigger].
 */
@Serializable
enum class CallEventDirection {
	INCOMING_ANSWERED,
	OUTGOING_STARTED,
	ANY_ONGOING,
	CALL_ENDED
}

/**
 * Sub-filter distinguishing unanswered missed calls from user-declined calls.
 */
@Serializable
enum class MissedCallFilter {
	ANY_UNANSWERED,
	MISSED_RANG_OUT_ONLY,
	REJECTED_BY_USER_ONLY
}

/**
 * Reason why a notification was removed from the status bar.
 */
@Serializable
enum class NotificationDismissReason {
	ANY,
	USER_DISMISSED,
	USER_CLICKED,
	APP_CANCELLED
}

/**
 * Audio stream routing target for [SetAction.PlaySoundAction].
 */
@Serializable
enum class AudioStreamTarget {
	ALARM,
	NOTIFICATION,
	MEDIA,
	RING
}

/**
 * Recipient resolution mode for [SetAction.SendSmsAction].
 */
@Serializable
enum class SmsRecipientMode {
	TRIGGER_CALLER_OR_SENDER,
	CUSTOM_NUMBER
}

/**
 * Detection strategy for [SetCondition.AppRunningCondition] (without AccessibilityService).
 */
@Serializable
enum class AppRunningCheckMode {
	FOREGROUND_NOW,
	RECENT_FOREGROUND,
	FOREGROUND_OR_ACTIVE_MEDIA_NOTIF
}

/**
 * Built-in Android OS Settings / System activity presets for [SetAction.StartAppActivityAction].
 */
@Serializable
enum class SystemActivityPreset {
	NONE_CUSTOM_COMPONENT,
	APP_DETAILS_SETTINGS,
	WIFI_SETTINGS,
	BLUETOOTH_SETTINGS,
	DND_SETTINGS,
	NOTIFICATION_ACCESS_SETTINGS,
	BATTERY_SAVER_SETTINGS,
	TETHER_HOTSPOT_SETTINGS,
	CUSTOM_URI_DEEPLINK
}


// ---- Shared Geographic & Integration Profiles ----

/**
 * Geographic circular boundary definition (schema-compatible with Triggered Live Wallpaper).
 */
@Serializable
data class GeofenceArea(
	val stringId: String = UUID.randomUUID().toString(),
	val stringName: String,
	val doubleLatitude: Double,
	val doubleLongitude: Double,
	val floatRadiusMeters: Float = 150f
)

/**
 * Reusable HTTP / Discord webhook endpoint profile stored in Settings.
 */
@Serializable
data class WebhookProfile(
	val stringId: String = UUID.randomUUID().toString(),
	val stringName: String,
	val stringUrl: String,
	val boolIsDiscord: Boolean = true,
	val stringDefaultMethod: String = "POST",
	val mapHeaders: Map<String, String> = emptyMap(),
	val stringDiscordUsername: String = ""
)

/**
 * Reusable ShareX Custom Uploader (.sxcu) profile compatible with ShareX v15+ and WebServer/Shared.
 */
@Serializable
data class ShareXProfile(
	val stringId: String = UUID.randomUUID().toString(),
	val stringName: String,
	val stringRequestMethod: String = "POST",
	val stringRequestUrl: String,
	val mapHeaders: Map<String, String> = emptyMap(),
	val mapParameters: Map<String, String> = emptyMap(),
	val stringBodyType: String = "MultipartFormData",
	val stringFileFormName: String = "uploaded_file",
	val mapArguments: Map<String, String> = emptyMap(),
	val stringUrlTemplate: String = "{response}",
	val stringErrorTemplate: String = "{response}"
)


// ---- Polymorphic Triggers (16 Supported Types) ----

/**
 * Base sealed interface for all event triggers that initiate evaluation of an [ActionSet].
 */
@Serializable
sealed interface SetTrigger {
	val stringId: String

	@Serializable
	@SerialName("geofence")
	data class GeofenceTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringGeofenceId: String = "",
		val transitionMode: GeofenceTransitionMode = GeofenceTransitionMode.BOTH
	) : SetTrigger

	@Serializable
	@SerialName("time_schedule")
	data class TimeScheduleTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolIsInterval: Boolean = false,
		val intHour: Int = 8,
		val intMinute: Int = 0,
		val listDaysOfWeek: List<Int> = emptyList(),
		val intIntervalMinutes: Int = 60
	) : SetTrigger

	@Serializable
	@SerialName("device_lock")
	data class DeviceLockTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnUnlocked: Boolean = true
	) : SetTrigger

	@Serializable
	@SerialName("screen_state")
	data class ScreenStateTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnScreenOn: Boolean = true
	) : SetTrigger

	@Serializable
	@SerialName("wifi")
	data class WifiTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnConnected: Boolean = true,
		val ssidMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringSsidFilter: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("mobile_data")
	data class MobileDataTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnConnected: Boolean = true,
		val boolRequireNotRoaming: Boolean = false
	) : SetTrigger

	@Serializable
	@SerialName("bluetooth")
	data class BluetoothTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnConnected: Boolean = true,
		val deviceMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringDeviceNameOrMac: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("media_playback")
	data class MediaPlaybackTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnPlaybackStarted: Boolean = true,
		val boolOnTrackChanged: Boolean = false,
		val stringAppPackage: String = "",
		val textMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringTrackOrArtistFilter: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("incoming_call")
	data class IncomingCallTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val contactFilterMode: ContactFilterMode = ContactFilterMode.ANY,
		val stringNumberOrRegex: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("missed_call")
	data class MissedCallTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val contactFilterMode: ContactFilterMode = ContactFilterMode.ANY,
		val missedCallFilter: MissedCallFilter = MissedCallFilter.ANY_UNANSWERED,
		val stringNumberOrRegex: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("ongoing_call")
	data class OngoingCallTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val callDirection: CallEventDirection = CallEventDirection.INCOMING_ANSWERED,
		val contactFilterMode: ContactFilterMode = ContactFilterMode.ANY,
		val stringNumberOrRegex: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("sms_received")
	data class SmsReceivedTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val contactFilterMode: ContactFilterMode = ContactFilterMode.ANY,
		val stringSenderFilter: String = "",
		val bodyMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringBodyPattern: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("battery_status")
	data class BatteryStatusTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val thresholdOperator: BatteryThresholdOperator = BatteryThresholdOperator.DROPS_AT_OR_BELOW,
		val intBatteryPercent: Int = 20
	) : SetTrigger

	@Serializable
	@SerialName("charging_state")
	data class ChargingStateTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolOnChargingConnected: Boolean = true,
		val sourceFilter: ChargingSourceFilter = ChargingSourceFilter.ANY
	) : SetTrigger

	@Serializable
	@SerialName("notification_received")
	data class NotificationReceivedTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringPackageFilter: String = "",
		val stringExcludedPackages: String = "",
		val titleMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringTitlePattern: String = "",
		val bodyMatchMode: TextMatchMode = TextMatchMode.OTP_PRESET,
		val stringBodyPattern: String = "",
		val boolIgnoreOngoing: Boolean = true,
		val boolIgnoreGroupSummary: Boolean = true
	) : SetTrigger

	@Serializable
	@SerialName("notification_dismissed")
	data class NotificationDismissedTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringPackageFilter: String = "",
		val stringExcludedPackages: String = "",
		val dismissReason: NotificationDismissReason = NotificationDismissReason.ANY,
		val titleMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringTitlePattern: String = "",
		val bodyMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringBodyPattern: String = ""
	) : SetTrigger

	@Serializable
	@SerialName("device_booted")
	data class DeviceBootedTrigger(
		override val stringId: String = UUID.randomUUID().toString(),
		val intBootDelaySeconds: Int = 0
	) : SetTrigger
}


// ---- Polymorphic Conditions (13 Supported Types) ----

/**
 * Base sealed interface for contextual state conditions evaluated when an [ActionSet] is triggered.
 */
@Serializable
sealed interface SetCondition {
	val stringId: String
	val boolInvert: Boolean

	@Serializable
	@SerialName("geofence")
	data class GeofenceCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringGeofenceId: String = "",
		val boolInside: Boolean = true,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("time_window")
	data class TimeCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolUseTimeWindow: Boolean = true,
		val intStartHour: Int = 8,
		val intStartMinute: Int = 0,
		val intEndHour: Int = 22,
		val intEndMinute: Int = 0,
		val listDaysOfWeek: List<Int> = emptyList(),
		val listMonths: List<Int> = emptyList(),
		override val boolInvert: Boolean = false
	) : SetCondition {
		/**
		 * Checks whether the provided hour and minute fall inside the configured daily window.
		 *
		 * @param intCurrentHour Current hour in 24-hour format (0..23).
		 * @param intCurrentMinute Current minute (0..59).
		 * @return True if within the configured window (supporting midnight wrap-around).
		 */
		fun isWithinTimeWindow(intCurrentHour: Int, intCurrentMinute: Int): Boolean {
			if (!boolUseTimeWindow) return true
			val intCurrentTotal = intCurrentHour * 60 + intCurrentMinute
			val intStartTotal = intStartHour * 60 + intStartMinute
			val intEndTotal = intEndHour * 60 + intEndMinute
			return if (intStartTotal <= intEndTotal) {
				intCurrentTotal in intStartTotal..intEndTotal
			} else {
				intCurrentTotal >= intStartTotal || intCurrentTotal <= intEndTotal
			}
		}
	}

	@Serializable
	@SerialName("device_unlocked")
	data class DeviceUnlockedCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireUnlocked: Boolean = true,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("screen_state")
	data class ScreenStateCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireScreenOn: Boolean = true,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("wifi_state")
	data class WifiCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireConnected: Boolean = true,
		val ssidMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringSsidFilter: String = "",
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("mobile_data_state")
	data class MobileDataCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireConnected: Boolean = true,
		val boolRequireNotRoaming: Boolean = false,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("bluetooth_state")
	data class BluetoothCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireConnected: Boolean = true,
		val deviceMatchMode: TextMatchMode = TextMatchMode.ANY,
		val stringDeviceNameOrMac: String = "",
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("media_playing")
	data class MediaPlayingCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequirePlaying: Boolean = true,
		val stringAppPackage: String = "",
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("ongoing_call")
	data class OngoingCallCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireInCall: Boolean = true,
		val contactFilterMode: ContactFilterMode = ContactFilterMode.ANY,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("dnd_status")
	data class DndStatusCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val dndConditionMode: DndConditionMode = DndConditionMode.ANY_DND_ON,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("battery_status")
	data class BatteryStatusCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val thresholdOperator: BatteryThresholdOperator = BatteryThresholdOperator.LESS_THAN_OR_EQUAL,
		val intBatteryPercent: Int = 50,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("charging_state")
	data class ChargingCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolRequireCharging: Boolean = true,
		val sourceFilter: ChargingSourceFilter = ChargingSourceFilter.ANY,
		override val boolInvert: Boolean = false
	) : SetCondition

	@Serializable
	@SerialName("app_running")
	data class AppRunningCondition(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringPackageName: String = "",
		val checkMode: AppRunningCheckMode = AppRunningCheckMode.RECENT_FOREGROUND,
		val intRecentWindowSeconds: Int = 15,
		override val boolInvert: Boolean = false
	) : SetCondition
}


// ---- Polymorphic Actions (11 Supported Types) ----

/**
 * Base sealed interface for ordered actions executed sequentially when an [ActionSet] matches.
 */
@Serializable
sealed interface SetAction {
	val stringId: String

	@Serializable
	@SerialName("play_sound")
	data class PlaySoundAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringRingtoneUri: String = "",
		val stringCustomFilePath: String = "",
		val audioStream: AudioStreamTarget = AudioStreamTarget.ALARM,
		val intVolumeOverridePercent: Int = -1,
		val boolVibrate: Boolean = true,
		val intMaxDurationSeconds: Int = 10
	) : SetAction

	@Serializable
	@SerialName("send_notification")
	data class SendNotificationAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringTitleTemplate: String = "Triggered Actions",
		val stringMessageTemplate: String = "Rule {set_name} executed.",
		val boolHighPriority: Boolean = true,
		val boolAddCopyMatchButton: Boolean = false,
		val stringTapOpenUrlOrPackage: String = ""
	) : SetAction

	@Serializable
	@SerialName("set_dnd")
	data class SetDndAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val targetMode: DndTargetMode = DndTargetMode.OFF_ALLOW_ALL,
		val boolSetRingerLoudWhenOff: Boolean = false
	) : SetAction

	@Serializable
	@SerialName("set_brightness")
	data class SetBrightnessAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val boolAutoBrightness: Boolean = false,
		val intBrightnessPercent: Int = 50
	) : SetAction

	@Serializable
	@SerialName("end_call")
	data class EndCallAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val longDelayBeforeEndMs: Long = 250L
	) : SetAction

	@Serializable
	@SerialName("send_sms")
	data class SendSmsAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val recipientMode: SmsRecipientMode = SmsRecipientMode.TRIGGER_CALLER_OR_SENDER,
		val stringCustomPhoneNumber: String = "",
		val stringMessageTemplate: String = "",
		val intSubscriptionId: Int = -1,
		val intRecipientCooldownSeconds: Int = 60
	) : SetAction

	@Serializable
	@SerialName("send_webhook")
	data class SendWebhookAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringProfileId: String = "",
		val stringCustomUrl: String = "",
		val boolIsDiscordFormat: Boolean = true,
		val stringHttpMethod: String = "POST",
		val mapCustomHeaders: Map<String, String> = emptyMap(),
		val stringBodyOrMessageTemplate: String = "{match}",
		val stringDiscordEmbedTitle: String = "",
		val stringDiscordUsername: String = ""
	) : SetAction

	@Serializable
	@SerialName("sharex_upload")
	data class ShareXUploadAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringShareXProfileId: String = "",
		val boolUploadFile: Boolean = false,
		val stringTextContentTemplate: String = "{notif_text}",
		val stringTextFileNameTemplate: String = "snippet_{datetime}.txt",
		val stringFilePathOrDirectory: String = "",
		val boolPickLatestFileInDir: Boolean = false,
		val boolCopyUrlToClipboard: Boolean = true,
		val boolNotifyOnComplete: Boolean = true
	) : SetAction

	@Serializable
	@SerialName("start_app")
	data class StartAppAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringPackageName: String = "",
		val boolClearTask: Boolean = false
	) : SetAction

	@Serializable
	@SerialName("start_app_shortcut")
	data class StartAppShortcutAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val stringPackageName: String = "",
		val stringShortcutLabel: String = "",
		val stringIntentUri: String = ""
	) : SetAction

	@Serializable
	@SerialName("start_app_activity")
	data class StartAppActivityAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val systemPreset: SystemActivityPreset = SystemActivityPreset.APP_DETAILS_SETTINGS,
		val stringTargetPackage: String = "",
		val stringActivityClassName: String = "",
		val stringCustomDataUri: String = "",
		val mapExtras: Map<String, String> = emptyMap()
	) : SetAction

	@Serializable
	@SerialName("wait_delay")
	data class WaitDelayAction(
		override val stringId: String = UUID.randomUUID().toString(),
		val intDelaySeconds: Int = 20,
		val boolWaitUntilCallEndsFirst: Boolean = false,
		val intMaxCallWaitSeconds: Int = 120
	) : SetAction
}


// ---- Action Set, Settings & Runtime Models ----

/**
 * Primary automation rule unit combining Triggers (OR), Conditions (AND/OR), and sequential Actions.
 */
@Serializable
data class ActionSet(
	val stringId: String = UUID.randomUUID().toString(),
	val stringName: String,
	val stringDescription: String = "",
	val boolIsEnabled: Boolean = true,
	val listTriggers: List<SetTrigger> = emptyList(),
	val conditionMatchMode: ConditionMatchMode = ConditionMatchMode.ALL_AND,
	val listConditions: List<SetCondition> = emptyList(),
	val listActions: List<SetAction> = emptyList(),
	val concurrencyPolicy: ConcurrencyPolicy = ConcurrencyPolicy.SKIP_IF_RUNNING,
	val intCooldownSeconds: Int = 0
)

/**
 * Global application settings persisted alongside Action Sets and Geofences.
 */
@Serializable
data class AppSettings(
	val boolMasterEngineEnabled: Boolean = true,
	val boolShowPersistentMonitorNotification: Boolean = true,
	val boolIgnoreUnsupportedConditions: Boolean = true,
	val boolIncludeSecretsInBackup: Boolean = true,
	val boolHasDismissedPermissionsOnboarding: Boolean = false,
	val intMaxLogEntries: Int = 100,
	val stringBackupFilenameTemplate: String = "TriggeredActions_Config_{yyyyMMdd_HHmmss}"
)

/**
 * Complete JSON backup and configuration envelope.
 */
@Serializable
data class ActionsConfigData(
	val listActionSets: List<ActionSet> = emptyList(),
	val listGeofences: List<GeofenceArea> = emptyList(),
	val listWebhookProfiles: List<WebhookProfile> = emptyList(),
	val listShareXProfiles: List<ShareXProfile> = emptyList(),
	val objSettings: AppSettings = AppSettings()
)

/**
 * Contextual payload captured by the firing trigger and threaded through actions for placeholder substitution.
 */
@Serializable
data class TriggerEventContext(
	val stringTriggerType: String = "manual",
	val stringTriggerSummary: String = "Manual Run",
	val stringCallerOrSenderNumber: String = "",
	val stringContactName: String = "",
	val boolIsInContacts: Boolean = false,
	val boolIsStarredContact: Boolean = false,
	val stringSmsBody: String = "",
	val stringNotificationPackage: String = "",
	val stringNotificationAppName: String = "",
	val stringNotificationTitle: String = "",
	val stringNotificationText: String = "",
	val stringMatchedValue: String = "",
	val listRegexGroups: List<String> = emptyList(),
	val stringGeofenceId: String = "",
	val stringGeofenceName: String = "",
	val stringGeofenceTransition: String = "",
	val stringBluetoothDeviceName: String = "",
	val stringBluetoothDeviceMac: String = "",
	val stringWifiSsid: String = "",
	val intBatteryPercent: Int = -1,
	val stringMediaAppPackage: String = "",
	val stringMediaTitle: String = "",
	val stringMediaArtist: String = "",
	val stringForegroundPackage: String = "",
	val stringLastUploadUrl: String = "",
	val longTimestampMs: Long = System.currentTimeMillis()
)

/**
 * Snapshot of live hardware, telephony, network, audio, and foreground state.
 */
data class LiveDeviceState(
	val boolScreenOn: Boolean = true,
	val boolDeviceUnlocked: Boolean = true,
	val boolWifiEnabled: Boolean = false,
	val boolWifiConnected: Boolean = false,
	val stringWifiSsid: String = "",
	val boolMobileDataConnected: Boolean = false,
	val boolIsRoaming: Boolean = false,
	val boolBluetoothEnabled: Boolean = false,
	val setConnectedBluetoothNames: Set<String> = emptySet(),
	val setConnectedBluetoothMacs: Set<String> = emptySet(),
	val boolMediaPlaying: Boolean = false,
	val setPlayingMediaPackages: Set<String> = emptySet(),
	val stringActiveMediaTitle: String = "",
	val stringActiveMediaArtist: String = "",
	val intCallState: Int = 0,
	val stringActiveCallNumber: String = "",
	val intDndFilter: Int = 1,
	val intBatteryPercent: Int = 100,
	val boolIsCharging: Boolean = false,
	val chargingSource: ChargingSourceFilter = ChargingSourceFilter.ANY,
	val intHour: Int = 12,
	val intMinute: Int = 0,
	val intDayOfWeek: Int = 1,
	val intMonth: Int = 1,
	val doubleLatitude: Double? = null,
	val doubleLongitude: Double? = null,
	val setActiveGeofenceIds: Set<String> = emptySet(),
	val stringCurrentForegroundPackage: String = "",
	val mapRecentForegroundTimestamps: Map<String, Long> = emptyMap(),
	val setActiveNotificationPackages: Set<String> = emptySet(),
	val boolSupportsLocation: Boolean = true,
	val boolSupportsTelephony: Boolean = true,
	val boolSupportsBluetooth: Boolean = true,
	val boolSensitiveNotificationsRedacted: Boolean = false
)

/**
 * Audit / conflict warning surfaced in the Rules tab.
 */
data class RuleAuditIssue(
	val stringIssueId: String,
	val stringTitle: String,
	val stringDescription: String,
	val stringRecommendation: String,
	val boolIsError: Boolean = true
)

/**
 * Persistent execution trace record displayed in the Status tab.
 */
@Serializable
data class ExecutionLogEntry(
	val stringId: String = UUID.randomUUID().toString(),
	val longTimestampMs: Long = System.currentTimeMillis(),
	val stringSetId: String,
	val stringSetName: String,
	val stringTriggerSummary: String,
	val boolSuccess: Boolean,
	val listStepLogs: List<String> = emptyList()
)


// ---- Backup Filename Formatter ----

/**
 * Formats a user backup filename template by expanding `{date}`, `{time}`, `{datetime}`,
 * and arbitrary Java [SimpleDateFormat] `{pattern}` tokens into a clean `.json` filename.
 *
 * @param stringTemplate User-specified backup filename template.
 * @param dateReference Reference timestamp for formatting (defaults to current system time).
 * @return Sanitized filename ending with `.json`.
 */
fun formatBackupFilename(stringTemplate: String, dateReference: Date = Date()): String {
	var stringClean = stringTemplate.trim()
	while (stringClean.endsWith(".json", ignoreCase = true)) {
		stringClean = stringClean.substring(0, stringClean.length - 5).trim()
	}
	if (stringClean.isEmpty()) {
		stringClean = "TriggeredActions"
	}

	stringClean = stringClean
		.replace("{datetime}", SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(dateReference))
		.replace("{date}", SimpleDateFormat("yyyyMMdd", Locale.US).format(dateReference))
		.replace("{time}", SimpleDateFormat("HHmmss", Locale.US).format(dateReference))

	val objRegex = Pattern.compile("\\{([^}]+)\\}")
	val objMatcher = objRegex.matcher(stringClean)
	val objBuffer = StringBuffer()
	while (objMatcher.find()) {
		val stringToken = objMatcher.group(1) ?: ""
		val stringReplaced = try {
			SimpleDateFormat(stringToken, Locale.US).format(dateReference)
		} catch (objException: Exception) {
			stringToken
		}
		objMatcher.appendReplacement(objBuffer, Matcher.quoteReplacement(stringReplaced))
	}
	objMatcher.appendTail(objBuffer)
	stringClean = objBuffer.toString().trim()

	while (stringClean.endsWith(".json", ignoreCase = true)) {
		stringClean = stringClean.substring(0, stringClean.length - 5).trim()
	}
	if (stringClean.isEmpty()) {
		stringClean = "TriggeredActions"
	}
	return "$stringClean.json"
}
