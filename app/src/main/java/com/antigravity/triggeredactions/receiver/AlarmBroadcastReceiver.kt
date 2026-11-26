package com.antigravity.triggeredactions.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.scheduler.ActionAlarmScheduler
import com.antigravity.triggeredactions.service.TriggeredExecutionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles wakeful exact alarms for [SetTrigger.TimeScheduleTrigger] and delayed sequence continuations.
 */
class AlarmBroadcastReceiver : BroadcastReceiver() {

	override fun onReceive(objContext: Context, objIntent: Intent) {
		val stringAction = objIntent.action ?: return
		val objAppCtx = objContext.applicationContext

		if (stringAction == ACTION_RESUME_WAIT) {
			val stringExecutionToken = objIntent.getStringExtra(EXTRA_EXECUTION_TOKEN) ?: return
			TriggeredExecutionService.resumeSuspendedWait(objAppCtx, stringExecutionToken)
			return
		}

		if (stringAction == ACTION_TIME_TRIGGER) {
			val stringSetId = objIntent.getStringExtra(EXTRA_SET_ID) ?: return
			val stringTriggerId = objIntent.getStringExtra(EXTRA_TRIGGER_ID) ?: return
			val objPending = goAsync()

			CoroutineScope(Dispatchers.IO).launch {
				try {
					val objRepo = ActionsRepository.getInstance(objAppCtx)
					objRepo.ensureLoaded()
					val objSet = objRepo.flowActionSets.value.find { it.stringId == stringSetId }
					val objTrigger = objSet?.listTriggers
						?.filterIsInstance<SetTrigger.TimeScheduleTrigger>()
						?.find { it.stringId == stringTriggerId }

					if (objSet != null && objSet.boolIsEnabled && objTrigger != null &&
						objRepo.flowSettings.value.boolMasterEngineEnabled
					) {
						ActionAlarmScheduler.scheduleSingleNextOccurrence(objAppCtx, stringSetId, objTrigger)
						TriggerDispatcher.onTimeAlarmFired(objAppCtx, stringSetId, stringTriggerId)
					}
				} catch (objException: Exception) {
					Log.e(
						TAG,
						"[AlarmBroadcastReceiver] Error firing time trigger $stringSetId - ${objException.javaClass.simpleName}: ${objException.message}"
					)
				} finally {
					objPending.finish()
				}
			}
		}
	}

	companion object {
		private const val TAG = "AlarmBroadcastReceiver"
		const val ACTION_TIME_TRIGGER = "com.antigravity.triggeredactions.ACTION_TIME_TRIGGER"
		const val ACTION_RESUME_WAIT = "com.antigravity.triggeredactions.ACTION_RESUME_WAIT"
		const val EXTRA_SET_ID = "extra_set_id"
		const val EXTRA_TRIGGER_ID = "extra_trigger_id"
		const val EXTRA_EXECUTION_TOKEN = "extra_execution_token"
	}
}
