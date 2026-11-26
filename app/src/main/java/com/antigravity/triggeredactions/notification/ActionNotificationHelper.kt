package com.antigravity.triggeredactions.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.MainActivity
import com.antigravity.triggeredactions.R
import com.antigravity.triggeredactions.receiver.NotificationActionReceiver

/**
 * Manages notification channels, foreground service notifications, and user-triggered notifications.
 */
object ActionNotificationHelper {

	const val CHANNEL_MONITOR_ID = "triggered_actions_monitor"
	const val CHANNEL_EXECUTION_ID = "triggered_actions_execution"
	const val CHANNEL_USER_DEFAULT_ID = "triggered_actions_alerts_default"
	const val CHANNEL_USER_HIGH_ID = "triggered_actions_alerts_high"

	const val NOTIFICATION_ID_MONITOR = 4101
	const val NOTIFICATION_ID_EXECUTION = 4102

	/**
	 * Ensures all system and user alert notification channels exist.
	 *
	 * @param objContext Android context.
	 */
	fun ensureChannelsCreated(objContext: Context) {
		val objNotifMgr = objContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

		val listChannels = listOf(
			NotificationChannel(
				CHANNEL_MONITOR_ID,
				"Background Event Monitor",
				NotificationManager.IMPORTANCE_MIN
			).apply {
				description = "Silent ongoing indicator while dynamic device triggers are monitored."
				setShowBadge(false)
			},
			NotificationChannel(
				CHANNEL_EXECUTION_ID,
				"Active Sequence Execution",
				NotificationManager.IMPORTANCE_LOW
			).apply {
				description = "Displayed briefly while delayed or network Action Sets are executing."
				setShowBadge(false)
			},
			NotificationChannel(
				CHANNEL_USER_DEFAULT_ID,
				"Action Alerts (Standard)",
				NotificationManager.IMPORTANCE_DEFAULT
			).apply {
				description = "Standard notifications sent by Action Sets."
			},
			NotificationChannel(
				CHANNEL_USER_HIGH_ID,
				"Action Alerts (High Priority)",
				NotificationManager.IMPORTANCE_HIGH
			).apply {
				description = "Heads-up high priority notifications sent by Action Sets."
			}
		)
		objNotifMgr.createNotificationChannels(listChannels)
	}

	/**
	 * Builds the low-priority foreground notification for [com.antigravity.triggeredactions.service.TriggeredMonitorService].
	 *
	 * @param objContext Android context.
	 * @param intEnabledSetCount Number of currently enabled Action Sets.
	 * @return Configured [Notification].
	 */
	fun buildMonitorNotification(objContext: Context, intEnabledSetCount: Int): Notification {
		ensureChannelsCreated(objContext)
		val intentOpen = Intent(objContext, MainActivity::class.java).apply {
			flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		val piOpen = PendingIntent.getActivity(
			objContext,
			100,
			intentOpen,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val intentPause = Intent(objContext, NotificationActionReceiver::class.java).apply {
			action = NotificationActionReceiver.ACTION_TOGGLE_ENGINE
		}
		val piPause = PendingIntent.getBroadcast(
			objContext,
			101,
			intentPause,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		return NotificationCompat.Builder(objContext, CHANNEL_MONITOR_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle("Triggered Device Actions Active")
			.setContentText("Monitoring $intEnabledSetCount enabled set(s)")
			.setContentIntent(piOpen)
			.setOngoing(true)
			.setSilent(true)
			.setPriority(NotificationCompat.PRIORITY_MIN)
			.addAction(0, "Pause Engine", piPause)
			.build()
	}

	/**
	 * Builds the foreground notification shown while an Action Set sequence with delays or network calls runs.
	 *
	 * @param objContext Android context.
	 * @param stringStatusSummary Description of the currently executing set/step.
	 * @return Configured [Notification].
	 */
	fun buildExecutionNotification(objContext: Context, stringStatusSummary: String): Notification {
		ensureChannelsCreated(objContext)
		val intentOpen = Intent(objContext, MainActivity::class.java)
		val piOpen = PendingIntent.getActivity(
			objContext,
			102,
			intentOpen,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		return NotificationCompat.Builder(objContext, CHANNEL_EXECUTION_ID)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle("Running Action Set")
			.setContentText(stringStatusSummary)
			.setContentIntent(piOpen)
			.setOngoing(true)
			.setSilent(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.build()
	}

	/**
	 * Posts a user-configured notification from [com.antigravity.triggeredactions.model.SetAction.SendNotificationAction].
	 *
	 * @param objContext Android context.
	 * @param stringTitle Rendered title.
	 * @param stringMessage Rendered message body.
	 * @param boolHighPriority True for heads-up high importance channel.
	 * @param stringCopyValue Optional extracted `{match}` / URL text to attach as a one-tap Copy action button.
	 * @param stringTapTarget Optional URL or package name to open when tapped.
	 */
	@SuppressLint("MissingPermission")
	fun postUserActionNotification(
		objContext: Context,
		stringTitle: String,
		stringMessage: String,
		boolHighPriority: Boolean,
		stringCopyValue: String = "",
		stringTapTarget: String = ""
	) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			ContextCompat.checkSelfPermission(objContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
		) {
			return
		}

		ensureChannelsCreated(objContext)
		val stringChannelId = if (boolHighPriority) CHANNEL_USER_HIGH_ID else CHANNEL_USER_DEFAULT_ID
		val intNotifId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt().coerceAtLeast(5000)

		val intentTap = when {
			stringTapTarget.startsWith("http://", ignoreCase = true) ||
				stringTapTarget.startsWith("https://", ignoreCase = true) -> {
				Intent(Intent.ACTION_VIEW, Uri.parse(stringTapTarget)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			}
			stringTapTarget.isNotBlank() -> {
				objContext.packageManager.getLaunchIntentForPackage(stringTapTarget)
					?: Intent(objContext, MainActivity::class.java)
			}
			else -> Intent(objContext, MainActivity::class.java)
		}

		val piTap = PendingIntent.getActivity(
			objContext,
			intNotifId,
			intentTap,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val objBuilder = NotificationCompat.Builder(objContext, stringChannelId)
			.setSmallIcon(R.drawable.ic_notification)
			.setContentTitle(stringTitle.ifBlank { "Triggered Actions" })
			.setContentText(stringMessage)
			.setStyle(NotificationCompat.BigTextStyle().bigText(stringMessage))
			.setContentIntent(piTap)
			.setAutoCancel(true)
			.setPriority(if (boolHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

		if (stringCopyValue.isNotBlank()) {
			val intentCopy = Intent(objContext, NotificationActionReceiver::class.java).apply {
				action = NotificationActionReceiver.ACTION_COPY_TEXT
				putExtra(NotificationActionReceiver.EXTRA_COPY_TEXT, stringCopyValue)
			}
			val piCopy = PendingIntent.getBroadcast(
				objContext,
				intNotifId + 1,
				intentCopy,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
			objBuilder.addAction(0, "Copy", piCopy)
		}

		NotificationManagerCompat.from(objContext).notify(intNotifId, objBuilder.build())
	}
}
