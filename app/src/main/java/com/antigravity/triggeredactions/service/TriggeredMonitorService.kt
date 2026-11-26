package com.antigravity.triggeredactions.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.AppSettings
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.notification.ActionNotificationHelper

/**
 * Lightweight event-driven Foreground Service hosting [DynamicEventMonitor] whenever dynamic broadcast
 * or network triggers are enabled and [TriggeredNotificationListenerService] is not bound (or when the user
 * opts into the persistent status notification).
 */
class TriggeredMonitorService : Service() {

	override fun onBind(objIntent: Intent?): IBinder? = null

	override fun onStartCommand(objIntent: Intent?, intFlags: Int, intStartId: Int): Int {
		val intEnabledCount = objIntent?.getIntExtra(EXTRA_ENABLED_SET_COUNT, 0) ?: 0
		try {
			val objNotif = ActionNotificationHelper.buildMonitorNotification(this, intEnabledCount)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				startForeground(
					ActionNotificationHelper.NOTIFICATION_ID_MONITOR,
					objNotif,
					ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
				)
			} else {
				startForeground(ActionNotificationHelper.NOTIFICATION_ID_MONITOR, objNotif)
			}
			DynamicEventMonitor.ensureRegistered(applicationContext)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggeredMonitorService] Error starting foreground monitor - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
		return START_STICKY
	}

	override fun onDestroy() {
		if (!TriggeredNotificationListenerService.boolIsConnected) {
			DynamicEventMonitor.unregisterAll(applicationContext)
		}
		super.onDestroy()
	}

	companion object {
		private const val TAG = "TriggeredMonitorService"
		private const val EXTRA_ENABLED_SET_COUNT = "extra_enabled_set_count"

		/**
		 * Starts or stops [TriggeredMonitorService] based on active dynamic triggers and master settings.
		 *
		 * @param objContext Android context.
		 * @param listSets Current Action Sets.
		 * @param objSettings Current AppSettings.
		 */
		fun synchronizeMonitorService(
			objContext: Context,
			listSets: List<ActionSet>,
			objSettings: AppSettings
		) {
			val listEnabled = listSets.filter { it.boolIsEnabled }
			val boolNeedsDynamicMonitor = listEnabled.any { objSet ->
				objSet.listTriggers.any { objTrig ->
					objTrig is SetTrigger.ScreenStateTrigger ||
						objTrig is SetTrigger.DeviceLockTrigger ||
						objTrig is SetTrigger.ChargingStateTrigger ||
						objTrig is SetTrigger.BatteryStatusTrigger ||
						objTrig is SetTrigger.WifiTrigger ||
						objTrig is SetTrigger.MobileDataTrigger
				}
			}

			val boolShouldRunForeground = objSettings.boolMasterEngineEnabled &&
				listEnabled.isNotEmpty() &&
				(objSettings.boolShowPersistentMonitorNotification ||
					(boolNeedsDynamicMonitor && !TriggeredNotificationListenerService.boolIsConnected))

			val intentService = Intent(objContext, TriggeredMonitorService::class.java).apply {
				putExtra(EXTRA_ENABLED_SET_COUNT, listEnabled.size)
			}

			try {
				if (boolShouldRunForeground) {
					objContext.startForegroundService(intentService)
				} else {
					objContext.stopService(intentService)
					if (objSettings.boolMasterEngineEnabled && TriggeredNotificationListenerService.boolIsConnected) {
						DynamicEventMonitor.ensureRegistered(objContext)
					} else if (!objSettings.boolMasterEngineEnabled) {
						DynamicEventMonitor.unregisterAll(objContext)
					}
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[TriggeredMonitorService] Error synchronizing service state - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}
	}
}
