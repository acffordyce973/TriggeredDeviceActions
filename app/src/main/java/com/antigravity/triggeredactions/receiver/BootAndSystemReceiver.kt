package com.antigravity.triggeredactions.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.scheduler.ActionAlarmScheduler
import com.antigravity.triggeredactions.scheduler.ActionGeofenceScheduler
import com.antigravity.triggeredactions.service.TriggeredMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores geofences, exact alarms, and background monitoring after device boot, app updates, or clock changes,
 * and exposes an external broadcast intent (`ACTION_EXTERNAL_TRIGGER`) for ADB / third-party automation tools.
 */
class BootAndSystemReceiver : BroadcastReceiver() {

	override fun onReceive(objContext: Context, objIntent: Intent) {
		val stringAction = objIntent.action ?: return
		val objPending = goAsync()
		val objAppCtx = objContext.applicationContext

		CoroutineScope(Dispatchers.IO).launch {
			try {
				val objRepo = ActionsRepository.getInstance(objAppCtx)
				objRepo.ensureLoaded()
				val listSets = objRepo.flowActionSets.value
				val listGeos = objRepo.flowGeofences.value
				val objSettings = objRepo.flowSettings.value

				if (stringAction == ACTION_EXTERNAL_TRIGGER) {
					val stringSetId = objIntent.getStringExtra("set_id").orEmpty()
					val stringSetName = objIntent.getStringExtra("set_name").orEmpty()
					val objTarget = listSets.find {
						(stringSetId.isNotBlank() && it.stringId == stringSetId) ||
							(stringSetName.isNotBlank() && it.stringName.equals(stringSetName, ignoreCase = true))
					}
					if (objTarget != null) {
						TriggerDispatcher.executeManualTestRun(objAppCtx, objTarget)
					}
				} else {
					ActionGeofenceScheduler.synchronizeGeofences(
						objAppCtx,
						listGeos,
						listSets,
						objSettings.boolMasterEngineEnabled
					)
					ActionAlarmScheduler.synchronizeTimeTriggers(
						objAppCtx,
						listSets,
						objSettings.boolMasterEngineEnabled
					)
					TriggeredMonitorService.synchronizeMonitorService(objAppCtx, listSets, objSettings)
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[BootAndSystemReceiver] Error handling broadcast $stringAction - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				objPending.finish()
			}
		}
	}

	companion object {
		private const val TAG = "BootAndSystemReceiver"
		const val ACTION_EXTERNAL_TRIGGER = "com.antigravity.triggeredactions.ACTION_EXTERNAL_TRIGGER"
	}
}
