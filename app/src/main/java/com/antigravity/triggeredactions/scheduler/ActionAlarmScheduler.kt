package com.antigravity.triggeredactions.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.receiver.AlarmBroadcastReceiver
import java.util.Calendar

/**
 * Schedules exact wakeful alarms for [SetTrigger.TimeScheduleTrigger] rules (both fixed clock times and intervals).
 */
object ActionAlarmScheduler {

	private const val TAG = "ActionAlarmScheduler"

	/**
	 * Synchronizes all [SetTrigger.TimeScheduleTrigger] alarms for enabled [ActionSet]s.
	 *
	 * @param objContext Android context.
	 * @param listActionSets All configured Action Sets.
	 * @param boolMasterEnabled Whether the master automation engine is enabled.
	 */
	fun synchronizeTimeTriggers(
		objContext: Context,
		listActionSets: List<ActionSet>,
		boolMasterEnabled: Boolean = true
	) {
		val objAlarmMgr = objContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

		for (objSet in listActionSets) {
			for (objTrigger in objSet.listTriggers.filterIsInstance<SetTrigger.TimeScheduleTrigger>()) {
				val objPi = buildTimeTriggerPendingIntent(objContext, objSet.stringId, objTrigger.stringId)
				objAlarmMgr.cancel(objPi)

				if (boolMasterEnabled && objSet.boolIsEnabled) {
					val longNextTriggerMs = computeNextFireTimeMs(objTrigger, System.currentTimeMillis())
					if (longNextTriggerMs > 0L) {
						scheduleExactSafe(objAlarmMgr, longNextTriggerMs, objPi)
					}
				}
			}
		}
	}

	/**
	 * Schedules the next occurrence of a single [SetTrigger.TimeScheduleTrigger] after it fires.
	 *
	 * @param objContext Android context.
	 * @param stringSetId Parent ActionSet ID.
	 * @param objTrigger Time trigger configuration.
	 */
	fun scheduleSingleNextOccurrence(
		objContext: Context,
		stringSetId: String,
		objTrigger: SetTrigger.TimeScheduleTrigger
	) {
		val objAlarmMgr = objContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
		val longNextMs = computeNextFireTimeMs(objTrigger, System.currentTimeMillis() + 1000L)
		if (longNextMs > 0L) {
			val objPi = buildTimeTriggerPendingIntent(objContext, stringSetId, objTrigger.stringId)
			scheduleExactSafe(objAlarmMgr, longNextMs, objPi)
		}
	}

	/**
	 * Computes the next epoch millisecond timestamp when [objTrigger] should fire after [longNowMs].
	 *
	 * @param objTrigger Time schedule trigger configuration.
	 * @param longNowMs Current epoch time in milliseconds.
	 * @return Next trigger epoch timestamp in milliseconds, or `-1L` if disabled.
	 */
	fun computeNextFireTimeMs(
		objTrigger: SetTrigger.TimeScheduleTrigger,
		longNowMs: Long
	): Long {
		if (objTrigger.boolIsInterval) {
			val intMins = objTrigger.intIntervalMinutes.coerceAtLeast(1)
			return longNowMs + (intMins * 60_000L)
		}

		val objBaseCal = Calendar.getInstance().apply { timeInMillis = longNowMs }
		for (intDayOffset in 0..7) {
			val objCandidate = (objBaseCal.clone() as Calendar).apply {
				add(Calendar.DAY_OF_YEAR, intDayOffset)
				set(Calendar.HOUR_OF_DAY, objTrigger.intHour.coerceIn(0, 23))
				set(Calendar.MINUTE, objTrigger.intMinute.coerceIn(0, 59))
				set(Calendar.SECOND, 0)
				set(Calendar.MILLISECOND, 0)
			}

			if (objCandidate.timeInMillis <= longNowMs) continue

			val intCalDow = objCandidate.get(Calendar.DAY_OF_WEEK)
			val intIsoDow = if (intCalDow == Calendar.SUNDAY) 7 else intCalDow - 1
			if (objTrigger.listDaysOfWeek.isEmpty() || objTrigger.listDaysOfWeek.contains(intIsoDow)) {
				return objCandidate.timeInMillis
			}
		}
		return -1L
	}

	private fun buildTimeTriggerPendingIntent(
		objContext: Context,
		stringSetId: String,
		stringTriggerId: String
	): PendingIntent {
		val intentAlarm = Intent(objContext, AlarmBroadcastReceiver::class.java).apply {
			action = AlarmBroadcastReceiver.ACTION_TIME_TRIGGER
			putExtra(AlarmBroadcastReceiver.EXTRA_SET_ID, stringSetId)
			putExtra(AlarmBroadcastReceiver.EXTRA_TRIGGER_ID, stringTriggerId)
		}
		val intReqCode = "$stringSetId:$stringTriggerId".hashCode()
		val intFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		return PendingIntent.getBroadcast(objContext, intReqCode, intentAlarm, intFlags)
	}

	private fun scheduleExactSafe(
		objAlarmMgr: AlarmManager,
		longTriggerAtMs: Long,
		objPendingIntent: PendingIntent
	) {
		try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !objAlarmMgr.canScheduleExactAlarms()) {
				objAlarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longTriggerAtMs, objPendingIntent)
			} else {
				objAlarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longTriggerAtMs, objPendingIntent)
			}
		} catch (objException: SecurityException) {
			Log.e(
				TAG,
				"[ActionAlarmScheduler] SecurityException scheduling exact alarm - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			objAlarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, longTriggerAtMs, objPendingIntent)
		}
	}
}
