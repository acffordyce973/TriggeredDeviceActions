package com.antigravity.triggeredactions.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.data.ExecutionLogStore
import com.antigravity.triggeredactions.engine.ActionExecutor
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.ConcurrencyPolicy
import com.antigravity.triggeredactions.model.ExecutionLogEntry
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.TriggerEventContext
import com.antigravity.triggeredactions.notification.ActionNotificationHelper
import com.antigravity.triggeredactions.receiver.AlarmBroadcastReceiver
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Persisted continuation checkpoint for multi-minute [SetAction.WaitDelayAction] steps scheduled via AlarmManager.
 */
@Serializable
private data class SuspendedExecutionState(
	val stringToken: String,
	val objActionSet: ActionSet,
	val intNextActionIndex: Int,
	val objEventContext: TriggerEventContext,
	val listCompletedStepLogs: List<String>
)

/**
 * Foreground execution service running ordered [ActionSet] sequences, managing [ConcurrencyPolicy],
 * persisting long wait steps (`> 45s`) to exact alarms, and recording execution logs.
 */
class TriggeredExecutionService : Service() {

	private val objServiceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mapRunningJobs = ConcurrentHashMap<String, Job>()

	override fun onBind(objIntent: Intent?): IBinder? = null

	override fun onStartCommand(objIntent: Intent?, intFlags: Int, intStartId: Int): Int {
		promoteToForeground("Executing triggered actions...")

		val stringCommand = objIntent?.getStringExtra(EXTRA_COMMAND).orEmpty()
		when (stringCommand) {
			COMMAND_ENQUEUE_SET -> {
				val stringSetJson = objIntent?.getStringExtra(EXTRA_SET_JSON) ?: return checkIdleAndStop()
				val stringCtxJson = objIntent.getStringExtra(EXTRA_CONTEXT_JSON) ?: return checkIdleAndStop()
				try {
					val objSet = ActionsRepository.jsonSerializer.decodeFromString<ActionSet>(stringSetJson)
					val objCtx = ActionsRepository.jsonSerializer.decodeFromString<TriggerEventContext>(stringCtxJson)
					launchSequence(objSet, intStartIndex = 0, objInitialCtx = objCtx, listPriorLogs = emptyList())
				} catch (objException: Exception) {
					Log.e(
						TAG,
						"[TriggeredExecutionService] Error decoding enqueued set - ${objException.javaClass.simpleName}: ${objException.message}"
					)
					checkIdleAndStop()
				}
			}

			COMMAND_RESUME_WAIT -> {
				val stringToken = objIntent?.getStringExtra(EXTRA_RESUME_TOKEN) ?: return checkIdleAndStop()
				val objSuspended = popSuspendedCheckpoint(applicationContext, stringToken)
				if (objSuspended != null) {
					launchSequence(
						objSet = objSuspended.objActionSet,
						intStartIndex = objSuspended.intNextActionIndex,
						objInitialCtx = objSuspended.objEventContext,
						listPriorLogs = objSuspended.listCompletedStepLogs
					)
				} else {
					checkIdleAndStop()
				}
			}

			else -> checkIdleAndStop()
		}

		return START_NOT_STICKY
	}

	private fun promoteToForeground(stringSummary: String) {
		try {
			val objNotif = ActionNotificationHelper.buildExecutionNotification(this, stringSummary)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				startForeground(
					ActionNotificationHelper.NOTIFICATION_ID_EXECUTION,
					objNotif,
					ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
				)
			} else {
				startForeground(ActionNotificationHelper.NOTIFICATION_ID_EXECUTION, objNotif)
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggeredExecutionService] Error starting foreground execution - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun launchSequence(
		objSet: ActionSet,
		intStartIndex: Int,
		objInitialCtx: TriggerEventContext,
		listPriorLogs: List<String>
	) {
		val stringJobKey = when (objSet.concurrencyPolicy) {
			ConcurrencyPolicy.SKIP_IF_RUNNING -> {
				val objExisting = mapRunningJobs[objSet.stringId]
				if (objExisting != null && objExisting.isActive && intStartIndex == 0) {
					checkIdleAndStop()
					return
				}
				objSet.stringId
			}
			ConcurrencyPolicy.RESTART_SEQUENCE -> {
				mapRunningJobs.remove(objSet.stringId)?.cancel()
				objSet.stringId
			}
			ConcurrencyPolicy.RUN_PARALLEL -> "${objSet.stringId}:${UUID.randomUUID()}"
		}

		val objJob = objServiceScope.launch {
			val listStepLogs = listPriorLogs.toMutableList()
			var objRunningCtx = objInitialCtx
			var boolAllOk = true
			var boolSuspendedToAlarm = false

			try {
				for (intIdx in intStartIndex until objSet.listActions.size) {
					val objAction = objSet.listActions[intIdx]
					promoteToForeground("${objSet.stringName} (${intIdx + 1}/${objSet.listActions.size})")

					// Offload waits longer than 45 seconds to exact AlarmManager so deep Doze is respected cleanly
					if (objAction is SetAction.WaitDelayAction && objAction.intDelaySeconds > 45) {
						if (objAction.boolWaitUntilCallEndsFirst) {
							ActionExecutor.waitForPhoneCallIdle(applicationContext, objAction.intMaxCallWaitSeconds)
						}
						listStepLogs.add("Scheduled wakeful wait (${objAction.intDelaySeconds}s)")
						scheduleAlarmContinuation(
							objContext = applicationContext,
							objSet = objSet,
							intNextIndex = intIdx + 1,
							intDelaySeconds = objAction.intDelaySeconds,
							objCtx = objRunningCtx,
							listLogs = listStepLogs
						)
						boolSuspendedToAlarm = true
						break
					}

					val objOutcome = ActionExecutor.executeSingleAction(
						objContext = applicationContext,
						objAction = objAction,
						objEventContext = objRunningCtx,
						stringSetId = objSet.stringId,
						stringSetName = objSet.stringName
					)
					objRunningCtx = objOutcome.objUpdatedContext
					listStepLogs.add(objOutcome.stringStepSummary)
					if (!objOutcome.boolSuccess) {
						boolAllOk = false
					}
				}

				if (!boolSuspendedToAlarm) {
					val objRepo = ActionsRepository.getInstance(applicationContext)
					objRepo.ensureLoaded()
					ExecutionLogStore.getInstance(applicationContext).appendLogEntry(
						ExecutionLogEntry(
							stringSetId = objSet.stringId,
							stringSetName = objSet.stringName,
							stringTriggerSummary = objInitialCtx.stringTriggerSummary,
							boolSuccess = boolAllOk,
							listStepLogs = listStepLogs
						),
						intMaxEntries = objRepo.flowSettings.value.intMaxLogEntries
					)
				}
			} catch (objCancel: CancellationException) {
				throw objCancel
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[TriggeredExecutionService] Unexpected error in sequence ${objSet.stringName} - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				mapRunningJobs.remove(stringJobKey)
				checkIdleAndStop()
			}
		}

		mapRunningJobs[stringJobKey] = objJob
	}

	private fun checkIdleAndStop(): Int {
		if (mapRunningJobs.values.none { it.isActive }) {
			stopForeground(STOP_FOREGROUND_REMOVE)
			stopSelf()
		}
		return START_NOT_STICKY
	}

	companion object {
		private const val TAG = "TriggeredExecService"
		private const val EXTRA_COMMAND = "extra_command"
		private const val COMMAND_ENQUEUE_SET = "cmd_enqueue_set"
		private const val COMMAND_RESUME_WAIT = "cmd_resume_wait"
		private const val EXTRA_SET_JSON = "extra_set_json"
		private const val EXTRA_CONTEXT_JSON = "extra_context_json"
		private const val EXTRA_RESUME_TOKEN = "extra_resume_token"
		private val objCheckpointLock = Any()

		/**
		 * Starts [TriggeredExecutionService] to execute [objSet] with [objEventContext].
		 *
		 * @param objContext Android context.
		 * @param objSet Matched [ActionSet].
		 * @param objEventContext Trigger context payload.
		 */
		fun enqueueActionSet(
			objContext: Context,
			objSet: ActionSet,
			objEventContext: TriggerEventContext
		) {
			try {
				val intentExec = Intent(objContext, TriggeredExecutionService::class.java).apply {
					putExtra(EXTRA_COMMAND, COMMAND_ENQUEUE_SET)
					putExtra(EXTRA_SET_JSON, ActionsRepository.jsonSerializer.encodeToString(objSet))
					putExtra(EXTRA_CONTEXT_JSON, ActionsRepository.jsonSerializer.encodeToString(objEventContext))
				}
				objContext.startForegroundService(intentExec)
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[TriggeredExecutionService] Error starting execution service for ${objSet.stringName} - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}

		/**
		 * Resumes a previously suspended long-wait sequence upon exact alarm wakeup.
		 *
		 * @param objContext Android context.
		 * @param stringToken Checkpoint token.
		 */
		fun resumeSuspendedWait(objContext: Context, stringToken: String) {
			try {
				val intentResume = Intent(objContext, TriggeredExecutionService::class.java).apply {
					putExtra(EXTRA_COMMAND, COMMAND_RESUME_WAIT)
					putExtra(EXTRA_RESUME_TOKEN, stringToken)
				}
				objContext.startForegroundService(intentResume)
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[TriggeredExecutionService] Error resuming suspended wait $stringToken - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			}
		}

		private fun scheduleAlarmContinuation(
			objContext: Context,
			objSet: ActionSet,
			intNextIndex: Int,
			intDelaySeconds: Int,
			objCtx: TriggerEventContext,
			listLogs: List<String>
		) {
			val stringToken = UUID.randomUUID().toString()
			val objCheckpoint = SuspendedExecutionState(
				stringToken = stringToken,
				objActionSet = objSet,
				intNextActionIndex = intNextIndex,
				objEventContext = objCtx,
				listCompletedStepLogs = listLogs
			)
			synchronized(objCheckpointLock) {
				val dirWaits = File(objContext.filesDir, "pending_waits").apply { mkdirs() }
				File(dirWaits, "$stringToken.json").writeText(
					ActionsRepository.jsonSerializer.encodeToString(objCheckpoint)
				)
			}

			val objAlarmMgr = objContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
			val intentResume = Intent(objContext, AlarmBroadcastReceiver::class.java).apply {
				action = AlarmBroadcastReceiver.ACTION_RESUME_WAIT
				putExtra(AlarmBroadcastReceiver.EXTRA_EXECUTION_TOKEN, stringToken)
			}
			val piResume = PendingIntent.getBroadcast(
				objContext,
				stringToken.hashCode(),
				intentResume,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
			val longWakeAtMs = System.currentTimeMillis() + (intDelaySeconds * 1000L)
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !objAlarmMgr.canScheduleExactAlarms()) {
					objAlarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longWakeAtMs, piResume)
				} else {
					objAlarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longWakeAtMs, piResume)
				}
			} catch (objException: SecurityException) {
				objAlarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longWakeAtMs, piResume)
			}
		}

		private fun popSuspendedCheckpoint(objContext: Context, stringToken: String): SuspendedExecutionState? {
			synchronized(objCheckpointLock) {
				val fileTarget = File(File(objContext.filesDir, "pending_waits"), "$stringToken.json")
				if (!fileTarget.exists()) return null
				return try {
					val stringJson = fileTarget.readText()
					fileTarget.delete()
					ActionsRepository.jsonSerializer.decodeFromString<SuspendedExecutionState>(stringJson)
				} catch (objException: Exception) {
					fileTarget.delete()
					null
				}
			}
		}
	}
}
