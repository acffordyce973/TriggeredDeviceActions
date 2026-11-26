package com.antigravity.triggeredactions.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.model.AudioStreamTarget
import com.antigravity.triggeredactions.model.DndTargetMode
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SmsRecipientMode
import com.antigravity.triggeredactions.model.SystemActivityPreset
import com.antigravity.triggeredactions.model.TriggerEventContext
import com.antigravity.triggeredactions.network.ShareXClient
import com.antigravity.triggeredactions.network.WebhookClient
import com.antigravity.triggeredactions.notification.ActionNotificationHelper
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

/**
 * Result of executing an individual [SetAction] within an [com.antigravity.triggeredactions.model.ActionSet].
 */
data class ActionStepOutcome(
	val boolSuccess: Boolean,
	val stringStepSummary: String,
	val objUpdatedContext: TriggerEventContext
)

/**
 * Executes individual [SetAction] steps (Sound, Notification, DnD, End Call, SMS, Webhook, ShareX Upload,
 * Start App / Shortcut / Activity, and Wait / Wait-For-Call-End).
 */
object ActionExecutor {

	private const val TAG = "ActionExecutor"
	private val mapRecentSmsDispatches = ConcurrentHashMap<String, Long>()
	private val objMainHandler = Handler(Looper.getMainLooper())

	/**
	 * Executes a single [SetAction] and returns its [ActionStepOutcome] along with any updated context tokens
	 * (such as `{last_upload_url}` from ShareX uploads).
	 *
	 * @param objContext Android context.
	 * @param objAction Action step to run.
	 * @param objEventContext Current event context variables.
	 * @param stringSetId Parent Action Set ID.
	 * @param stringSetName Parent Action Set Name.
	 * @return [ActionStepOutcome] with step status and log line.
	 */
	suspend fun executeSingleAction(
		objContext: Context,
		objAction: SetAction,
		objEventContext: TriggerEventContext,
		stringSetId: String,
		stringSetName: String
	): ActionStepOutcome {
		return try {
			when (objAction) {
				is SetAction.PlaySoundAction -> executePlaySound(objContext, objAction, objEventContext)
				is SetAction.SendNotificationAction -> executeSendNotification(objContext, objAction, objEventContext, stringSetName)
				is SetAction.SetDndAction -> executeSetDnd(objContext, objAction, objEventContext)
				is SetAction.EndCallAction -> executeEndCall(objContext, objAction, objEventContext)
				is SetAction.SendSmsAction -> executeSendSms(objContext, objAction, objEventContext, stringSetId, stringSetName)
				is SetAction.SendWebhookAction -> executeSendWebhook(objContext, objAction, objEventContext, stringSetName)
				is SetAction.ShareXUploadAction -> executeShareXUpload(objContext, objAction, objEventContext, stringSetName)
				is SetAction.StartAppAction -> executeStartApp(objContext, objAction, objEventContext)
				is SetAction.StartAppShortcutAction -> executeStartShortcut(objContext, objAction, objEventContext)
				is SetAction.StartAppActivityAction -> executeStartActivity(objContext, objAction, objEventContext, stringSetName)
				is SetAction.WaitDelayAction -> executeInlineWait(objContext, objAction, objEventContext)
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ActionExecutor] Error running action ${objAction::class.simpleName} in $stringSetName - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "Error (${objAction::class.simpleName}): ${objException.message}",
				objUpdatedContext = objEventContext
			)
		}
	}


	// ---- Individual Action Implementations ----

	private suspend fun executePlaySound(
		objContext: Context,
		objAction: SetAction.PlaySoundAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		val objAudioMgr = objContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
		val intStreamType = when (objAction.audioStream) {
			AudioStreamTarget.ALARM -> AudioManager.STREAM_ALARM
			AudioStreamTarget.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
			AudioStreamTarget.MEDIA -> AudioManager.STREAM_MUSIC
			AudioStreamTarget.RING -> AudioManager.STREAM_RING
		}
		val intUsage = when (objAction.audioStream) {
			AudioStreamTarget.ALARM -> AudioAttributes.USAGE_ALARM
			AudioStreamTarget.NOTIFICATION -> AudioAttributes.USAGE_NOTIFICATION
			AudioStreamTarget.MEDIA -> AudioAttributes.USAGE_MEDIA
			AudioStreamTarget.RING -> AudioAttributes.USAGE_NOTIFICATION_RINGTONE
		}

		var intSavedVolume = -1
		if (objAudioMgr != null && objAction.intVolumeOverridePercent in 0..100) {
			intSavedVolume = objAudioMgr.getStreamVolume(intStreamType)
			val intMaxVol = objAudioMgr.getStreamMaxVolume(intStreamType)
			val intTargetVol = ((intMaxVol * objAction.intVolumeOverridePercent) / 100).coerceIn(0, intMaxVol)
			runCatching { objAudioMgr.setStreamVolume(intStreamType, intTargetVol, 0) }
		}

		if (objAction.boolVibrate) {
			triggerVibration(objContext)
		}

		val uriSound = when {
			objAction.stringCustomFilePath.isNotBlank() && File(objAction.stringCustomFilePath).exists() -> {
				Uri.fromFile(File(objAction.stringCustomFilePath))
			}
			objAction.stringRingtoneUri.isNotBlank() -> Uri.parse(objAction.stringRingtoneUri)
			objAction.audioStream == AudioStreamTarget.ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
				?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
			else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
		}

		val objPlayer = MediaPlayer()
		try {
			objPlayer.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(intUsage)
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build()
			)
			if (uriSound != null) {
				objPlayer.setDataSource(objContext, uriSound)
				objPlayer.isLooping = false
				objPlayer.prepare()
				objPlayer.start()
				val longMaxWaitMs = (objAction.intMaxDurationSeconds.coerceIn(1, 60) * 1000L)
				val longDurationMs = objPlayer.duration.takeIf { it > 0 }?.toLong()?.coerceAtMost(longMaxWaitMs) ?: 2500L
				delay(longDurationMs)
			}
		} finally {
			runCatching {
				if (objPlayer.isPlaying) objPlayer.stop()
				objPlayer.release()
			}
			if (objAudioMgr != null && intSavedVolume >= 0) {
				runCatching { objAudioMgr.setStreamVolume(intStreamType, intSavedVolume, 0) }
			}
		}

		return ActionStepOutcome(
			boolSuccess = true,
			stringStepSummary = "Played sound on ${objAction.audioStream.name} stream",
			objUpdatedContext = objEventContext
		)
	}

	@Suppress("DEPRECATION")
	private fun triggerVibration(objContext: Context) {
		runCatching {
			val objVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				val objVm = objContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
				objVm?.defaultVibrator
			} else {
				objContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
			}
			objVibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 250L, 120L, 250L), -1))
		}
	}

	private fun executeSendNotification(
		objContext: Context,
		objAction: SetAction.SendNotificationAction,
		objEventContext: TriggerEventContext,
		stringSetName: String
	): ActionStepOutcome {
		val stringTitle = ContextTemplateEngine.renderTemplate(objAction.stringTitleTemplate, objEventContext, stringSetName)
		val stringBody = ContextTemplateEngine.renderTemplate(objAction.stringMessageTemplate, objEventContext, stringSetName)
		val stringCopyCandidate = objEventContext.stringMatchedValue.ifBlank { objEventContext.stringLastUploadUrl }
		val stringTapTarget = ContextTemplateEngine.renderTemplate(objAction.stringTapOpenUrlOrPackage, objEventContext, stringSetName)

		ActionNotificationHelper.postUserActionNotification(
			objContext = objContext,
			stringTitle = stringTitle,
			stringMessage = stringBody,
			boolHighPriority = objAction.boolHighPriority,
			stringCopyValue = if (objAction.boolAddCopyMatchButton) stringCopyCandidate else "",
			stringTapTarget = stringTapTarget
		)
		return ActionStepOutcome(
			boolSuccess = true,
			stringStepSummary = "Sent notification: \"$stringTitle\"",
			objUpdatedContext = objEventContext
		)
	}

	private fun executeSetDnd(
		objContext: Context,
		objAction: SetAction.SetDndAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		val objNotifMgr = objContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
			?: return ActionStepOutcome(false, "NotificationManager unavailable", objEventContext)

		val intFilter = when (objAction.targetMode) {
			DndTargetMode.OFF_ALLOW_ALL -> NotificationManager.INTERRUPTION_FILTER_ALL
			DndTargetMode.PRIORITY_ONLY -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
			DndTargetMode.ALARMS_ONLY -> NotificationManager.INTERRUPTION_FILTER_ALARMS
			DndTargetMode.TOTAL_SILENCE -> NotificationManager.INTERRUPTION_FILTER_NONE
		}

		// Optional WRITE_SECURE_SETTINGS direct zen_mode write if granted via ADB
		val intZenConstant = when (objAction.targetMode) {
			DndTargetMode.OFF_ALLOW_ALL -> 0
			DndTargetMode.PRIORITY_ONLY -> 1
			DndTargetMode.TOTAL_SILENCE -> 2
			DndTargetMode.ALARMS_ONLY -> 3
		}
		if (ContextCompat.checkSelfPermission(objContext, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
			runCatching {
				Settings.Global.putInt(objContext.contentResolver, "zen_mode", intZenConstant)
			}
		}

		if (!objNotifMgr.isNotificationPolicyAccessGranted) {
			return ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "Set DnD failed: Do Not Disturb Access not granted",
				objUpdatedContext = objEventContext
			)
		}

		objNotifMgr.setInterruptionFilter(intFilter)
		DeviceStateCollector.refreshDeviceState(objContext)
		return ActionStepOutcome(
			boolSuccess = true,
			stringStepSummary = "Set Do Not Disturb -> ${objAction.targetMode.name}",
			objUpdatedContext = objEventContext
		)
	}

	@Suppress("DEPRECATION")
	@SuppressLint("MissingPermission")
	private suspend fun executeEndCall(
		objContext: Context,
		objAction: SetAction.EndCallAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		if (ContextCompat.checkSelfPermission(objContext, Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) {
			return ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "End Call failed: ANSWER_PHONE_CALLS permission not granted",
				objUpdatedContext = objEventContext
			)
		}

		if (objAction.longDelayBeforeEndMs > 0L) {
			delay(objAction.longDelayBeforeEndMs.coerceAtMost(5000L))
		}

		val objTelecom = objContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
			?: return ActionStepOutcome(false, "TelecomManager unavailable", objEventContext)

		val boolEnded = objTelecom.endCall()
		return ActionStepOutcome(
			boolSuccess = boolEnded,
			stringStepSummary = if (boolEnded) "Ended active/ringing phone call" else "No active call to end",
			objUpdatedContext = objEventContext
		)
	}

	@Suppress("DEPRECATION")
	private fun executeSendSms(
		objContext: Context,
		objAction: SetAction.SendSmsAction,
		objEventContext: TriggerEventContext,
		stringSetId: String,
		stringSetName: String
	): ActionStepOutcome {
		if (ContextCompat.checkSelfPermission(objContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
			return ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "Send SMS failed: SEND_SMS permission not granted",
				objUpdatedContext = objEventContext
			)
		}

		val stringRawRecipient = when (objAction.recipientMode) {
			SmsRecipientMode.TRIGGER_CALLER_OR_SENDER -> objEventContext.stringCallerOrSenderNumber
			SmsRecipientMode.CUSTOM_NUMBER -> ContextTemplateEngine.renderTemplate(
				objAction.stringCustomPhoneNumber,
				objEventContext,
				stringSetName
			)
		}.trim()

		if (stringRawRecipient.isEmpty() || !stringRawRecipient.any { it.isDigit() }) {
			return ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "Send SMS skipped: No valid phone number available (${stringRawRecipient.ifBlank { "empty" }})",
				objUpdatedContext = objEventContext
			)
		}

		// Anti-loop per-recipient rate limiter
		val stringCooldownKey = "$stringSetId:${stringRawRecipient.filter { it.isDigit() }}"
		val longNow = System.currentTimeMillis()
		if (objAction.intRecipientCooldownSeconds > 0) {
			val longPrev = mapRecentSmsDispatches[stringCooldownKey] ?: 0L
			if (longNow - longPrev < objAction.intRecipientCooldownSeconds * 1000L) {
				return ActionStepOutcome(
					boolSuccess = true,
					stringStepSummary = "Send SMS skipped (recipient cooldown active for $stringRawRecipient)",
					objUpdatedContext = objEventContext
				)
			}
		}

		val stringRenderedBody = ContextTemplateEngine.renderTemplate(
			objAction.stringMessageTemplate,
			objEventContext,
			stringSetName
		)
		if (stringRenderedBody.isBlank()) {
			return ActionStepOutcome(false, "Send SMS failed: Message body is empty", objEventContext)
		}

		val objSmsMgr = if (objAction.intSubscriptionId >= 0) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				objContext.getSystemService(SmsManager::class.java)?.createForSubscriptionId(objAction.intSubscriptionId)
			} else {
				SmsManager.getSmsManagerForSubscriptionId(objAction.intSubscriptionId)
			}
		} else {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
				objContext.getSystemService(SmsManager::class.java)
			} else {
				SmsManager.getDefault()
			}
		} ?: return ActionStepOutcome(false, "SmsManager unavailable on this device", objEventContext)

		val listParts = objSmsMgr.divideMessage(stringRenderedBody)
		objSmsMgr.sendMultipartTextMessage(stringRawRecipient, null, listParts, null, null)
		mapRecentSmsDispatches[stringCooldownKey] = longNow

		return ActionStepOutcome(
			boolSuccess = true,
			stringStepSummary = "Sent SMS to $stringRawRecipient (${listParts.size} part(s))",
			objUpdatedContext = objEventContext
		)
	}

	private suspend fun executeSendWebhook(
		objContext: Context,
		objAction: SetAction.SendWebhookAction,
		objEventContext: TriggerEventContext,
		stringSetName: String
	): ActionStepOutcome {
		val objRepo = ActionsRepository.getInstance(objContext)
		objRepo.ensureLoaded()
		val objProfile = objRepo.flowWebhookProfiles.value.find { it.stringId == objAction.stringProfileId }

		val stringTargetUrl = objProfile?.stringUrl?.takeIf { it.isNotBlank() } ?: objAction.stringCustomUrl
		val boolIsDiscord = objProfile?.boolIsDiscord ?: objAction.boolIsDiscordFormat
		val mapCombinedHeaders = (objProfile?.mapHeaders.orEmpty() + objAction.mapCustomHeaders).mapValues { (_, v) ->
			ContextTemplateEngine.renderTemplate(v, objEventContext, stringSetName)
		}

		val objResult = if (boolIsDiscord) {
			val stringMsg = ContextTemplateEngine.renderTemplate(
				objAction.stringBodyOrMessageTemplate,
				objEventContext,
				stringSetName
			)
			val stringEmbedTitle = ContextTemplateEngine.renderTemplate(
				objAction.stringDiscordEmbedTitle,
				objEventContext,
				stringSetName
			)
			val stringUser = objAction.stringDiscordUsername.ifBlank { objProfile?.stringDiscordUsername.orEmpty() }
			WebhookClient.sendDiscordWebhook(
				stringWebhookUrl = stringTargetUrl,
				stringMessageContent = stringMsg,
				stringEmbedTitle = stringEmbedTitle,
				stringUsername = stringUser,
				mapHeaders = mapCombinedHeaders
			)
		} else {
			val boolIsJsonTemplate = objAction.stringBodyOrMessageTemplate.trimStart().startsWith("{")
			val stringRenderedBody = ContextTemplateEngine.renderTemplate(
				objAction.stringBodyOrMessageTemplate,
				objEventContext,
				stringSetName,
				boolJsonEscapeValues = boolIsJsonTemplate
			)
			val stringVerb = objAction.stringHttpMethod.ifBlank { objProfile?.stringDefaultMethod ?: "POST" }
			WebhookClient.executeGenericWebhook(
				stringUrl = stringTargetUrl,
				stringMethod = stringVerb,
				mapHeaders = mapCombinedHeaders,
				stringBody = stringRenderedBody
			)
		}

		return ActionStepOutcome(
			boolSuccess = objResult.boolSuccess,
			stringStepSummary = "Webhook: ${objResult.stringDetail}",
			objUpdatedContext = objEventContext
		)
	}

	private suspend fun executeShareXUpload(
		objContext: Context,
		objAction: SetAction.ShareXUploadAction,
		objEventContext: TriggerEventContext,
		stringSetName: String
	): ActionStepOutcome {
		val objRepo = ActionsRepository.getInstance(objContext)
		objRepo.ensureLoaded()
		val objProfile = objRepo.flowShareXProfiles.value.find { it.stringId == objAction.stringShareXProfileId }
			?: objRepo.flowShareXProfiles.value.firstOrNull()
			?: return ActionStepOutcome(false, "ShareX Upload failed: No ShareX profile configured", objEventContext)

		val objUploadRes = if (objAction.boolUploadFile) {
			val stringResolvedPath = ContextTemplateEngine.renderTemplate(
				objAction.stringFilePathOrDirectory,
				objEventContext,
				stringSetName
			).trim()
			val fileBase = File(stringResolvedPath)
			val fileTarget = if (objAction.boolPickLatestFileInDir && fileBase.isDirectory) {
				fileBase.listFiles()?.filter { it.isFile }?.maxByOrNull { it.lastModified() }
			} else {
				fileBase
			}
			if (fileTarget == null) {
				return ActionStepOutcome(false, "ShareX Upload failed: No file found in $stringResolvedPath", objEventContext)
			}
			ShareXClient.uploadFile(objProfile, fileTarget)
		} else {
			val stringText = ContextTemplateEngine.renderTemplate(
				objAction.stringTextContentTemplate,
				objEventContext,
				stringSetName
			)
			val stringFileName = ContextTemplateEngine.renderTemplate(
				objAction.stringTextFileNameTemplate,
				objEventContext,
				stringSetName
			)
			ShareXClient.uploadText(objProfile, stringText, stringFileName)
		}

		if (!objUploadRes.boolSuccess) {
			return ActionStepOutcome(
				boolSuccess = false,
				stringStepSummary = "ShareX Upload failed: ${objUploadRes.stringErrorDetail}",
				objUpdatedContext = objEventContext
			)
		}

		val stringUrl = objUploadRes.stringResultUrl
		if (objAction.boolCopyUrlToClipboard && stringUrl.isNotBlank()) {
			objMainHandler.post {
				val objClip = objContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
				objClip?.setPrimaryClip(ClipData.newPlainText("ShareX URL", stringUrl))
			}
		}

		if (objAction.boolNotifyOnComplete && stringUrl.isNotBlank()) {
			ActionNotificationHelper.postUserActionNotification(
				objContext = objContext,
				stringTitle = "Uploaded to ${objProfile.stringName}",
				stringMessage = stringUrl,
				boolHighPriority = false,
				stringCopyValue = stringUrl,
				stringTapTarget = stringUrl
			)
		}

		return ActionStepOutcome(
			boolSuccess = true,
			stringStepSummary = "ShareX Uploaded -> $stringUrl",
			objUpdatedContext = objEventContext.copy(stringLastUploadUrl = stringUrl)
		)
	}

	private suspend fun executeStartApp(
		objContext: Context,
		objAction: SetAction.StartAppAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		val stringPkg = objAction.stringPackageName.trim()
		if (stringPkg.isEmpty()) {
			return ActionStepOutcome(false, "Start App failed: Package name empty", objEventContext)
		}
		val intentLaunch = objContext.packageManager.getLaunchIntentForPackage(stringPkg)
			?: return ActionStepOutcome(false, "Start App failed: Launch intent not found for $stringPkg", objEventContext)

		if (objAction.boolClearTask) {
			intentLaunch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
		}
		val boolLaunched = BackgroundLaunchBridge.launchActivityFromBackground(
			objContext,
			intentLaunch,
			"Open $stringPkg"
		)
		return ActionStepOutcome(
			boolSuccess = boolLaunched,
			stringStepSummary = if (boolLaunched) "Launched app $stringPkg" else "Posted launch notification for $stringPkg",
			objUpdatedContext = objEventContext
		)
	}

	private suspend fun executeStartShortcut(
		objContext: Context,
		objAction: SetAction.StartAppShortcutAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		if (objAction.stringIntentUri.isBlank()) {
			return ActionStepOutcome(false, "Start Shortcut failed: Shortcut intent URI is empty", objEventContext)
		}
		val intentShortcut = Intent.parseUri(objAction.stringIntentUri, Intent.URI_INTENT_SCHEME)
		val boolLaunched = BackgroundLaunchBridge.launchActivityFromBackground(
			objContext,
			intentShortcut,
			"Run Shortcut: ${objAction.stringShortcutLabel}"
		)
		return ActionStepOutcome(
			boolSuccess = boolLaunched,
			stringStepSummary = if (boolLaunched) "Launched shortcut ${objAction.stringShortcutLabel}" else "Posted fallback notification for shortcut",
			objUpdatedContext = objEventContext
		)
	}

	private suspend fun executeStartActivity(
		objContext: Context,
		objAction: SetAction.StartAppActivityAction,
		objEventContext: TriggerEventContext,
		stringSetName: String
	): ActionStepOutcome {
		val stringResolvedPkg = ContextTemplateEngine.renderTemplate(
			objAction.stringTargetPackage,
			objEventContext,
			stringSetName
		).trim().ifBlank { objEventContext.stringForegroundPackage }

		val intentTarget: Intent = when (objAction.systemPreset) {
			SystemActivityPreset.APP_DETAILS_SETTINGS -> {
				if (stringResolvedPkg.isBlank()) {
					return ActionStepOutcome(false, "OS App Info failed: No target package specified", objEventContext)
				}
				Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
					data = Uri.fromParts("package", stringResolvedPkg, null)
				}
			}
			SystemActivityPreset.WIFI_SETTINGS -> Intent(Settings.ACTION_WIFI_SETTINGS)
			SystemActivityPreset.BLUETOOTH_SETTINGS -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
			SystemActivityPreset.DND_SETTINGS -> Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
			SystemActivityPreset.NOTIFICATION_ACCESS_SETTINGS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
			SystemActivityPreset.BATTERY_SAVER_SETTINGS -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
			SystemActivityPreset.TETHER_HOTSPOT_SETTINGS -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
			SystemActivityPreset.CUSTOM_URI_DEEPLINK -> {
				val stringUri = ContextTemplateEngine.renderTemplate(objAction.stringCustomDataUri, objEventContext, stringSetName)
				Intent(Intent.ACTION_VIEW, Uri.parse(stringUri)).apply {
					if (stringResolvedPkg.isNotBlank()) setPackage(stringResolvedPkg)
				}
			}
			SystemActivityPreset.NONE_CUSTOM_COMPONENT -> {
				if (stringResolvedPkg.isBlank() || objAction.stringActivityClassName.isBlank()) {
					return ActionStepOutcome(false, "Custom Activity failed: Package or Activity class missing", objEventContext)
				}
				Intent().apply {
					component = ComponentName(stringResolvedPkg, objAction.stringActivityClassName.trim())
					if (objAction.stringCustomDataUri.isNotBlank()) {
						data = Uri.parse(
							ContextTemplateEngine.renderTemplate(objAction.stringCustomDataUri, objEventContext, stringSetName)
						)
					}
				}
			}
		}

		for ((stringExtraK, stringExtraV) in objAction.mapExtras) {
			if (stringExtraK.isNotBlank()) {
				intentTarget.putExtra(
					stringExtraK,
					ContextTemplateEngine.renderTemplate(stringExtraV, objEventContext, stringSetName)
				)
			}
		}

		val boolLaunched = BackgroundLaunchBridge.launchActivityFromBackground(
			objContext,
			intentTarget,
			"Open ${objAction.systemPreset.name}"
		)
		return ActionStepOutcome(
			boolSuccess = boolLaunched,
			stringStepSummary = if (boolLaunched) {
				"Launched activity ${objAction.systemPreset.name} ($stringResolvedPkg)"
			} else {
				"Posted launch notification for ${objAction.systemPreset.name}"
			},
			objUpdatedContext = objEventContext
		)
	}

	/**
	 * Performs inline coroutine suspension for waits (`<= 45s`) and optional `boolWaitUntilCallEndsFirst` polling.
	 *
	 * @param objContext Android context.
	 * @param objAction Wait action configuration.
	 * @param objEventContext Current event context.
	 * @return [ActionStepOutcome] once wait finishes.
	 */
	suspend fun executeInlineWait(
		objContext: Context,
		objAction: SetAction.WaitDelayAction,
		objEventContext: TriggerEventContext
	): ActionStepOutcome {
		if (objAction.boolWaitUntilCallEndsFirst) {
			waitForPhoneCallIdle(objContext, objAction.intMaxCallWaitSeconds)
		}
		val intSecs = objAction.intDelaySeconds.coerceAtLeast(0)
		if (intSecs > 0) {
			delay(intSecs * 1000L)
		}
		val stringSummary = if (objAction.boolWaitUntilCallEndsFirst) {
			"Waited for call end + ${intSecs}s"
		} else {
			"Waited ${intSecs}s"
		}
		return ActionStepOutcome(true, stringSummary, objEventContext)
	}

	/**
	 * Suspends until `TelephonyManager.callState == CALL_STATE_IDLE` (or until [intMaxSeconds] elapses).
	 *
	 * @param objContext Android context.
	 * @param intMaxSeconds Safety timeout in seconds.
	 */
	@Suppress("DEPRECATION")
	suspend fun waitForPhoneCallIdle(objContext: Context, intMaxSeconds: Int) {
		val longDeadline = System.currentTimeMillis() + (intMaxSeconds.coerceIn(5, 3600) * 1000L)
		while (System.currentTimeMillis() < longDeadline) {
			val objState = DeviceStateCollector.refreshDeviceState(objContext)
			if (objState.intCallState == TelephonyManager.CALL_STATE_IDLE) {
				return
			}
			delay(500L)
		}
	}
}
