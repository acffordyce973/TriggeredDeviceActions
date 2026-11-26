package com.antigravity.triggeredactions.engine

import android.app.NotificationManager
import android.location.Location
import android.telephony.TelephonyManager
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.AppRunningCheckMode
import com.antigravity.triggeredactions.model.BatteryThresholdOperator
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import com.antigravity.triggeredactions.model.ConditionMatchMode
import com.antigravity.triggeredactions.model.DndConditionMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.LiveDeviceState
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.TextMatchMode
import com.antigravity.triggeredactions.model.TriggerEventContext

/**
 * Pure deterministic rule engine evaluating an [ActionSet]'s conditions (`ALL_AND` / `ANY_OR` + `boolInvert`)
 * against a [LiveDeviceState] snapshot.
 */
object ConditionEvaluator {

	/**
	 * Evaluates whether [objSet] satisfies its configured conditions.
	 *
	 * @param objSet Target [ActionSet] to evaluate.
	 * @param objState Snapshot of current device state.
	 * @param listGeofences Configured geofence definitions.
	 * @param objEventContext Optional trigger event payload (e.g. caller contact lookup).
	 * @param boolIgnoreUnsupported When true, conditions relying on absent hardware (telephony, GPS, BT) evaluate to true.
	 * @return True if the set is enabled and its condition group evaluates to true.
	 */
	fun evaluateSetConditions(
		objSet: ActionSet,
		objState: LiveDeviceState,
		listGeofences: List<GeofenceArea>,
		objEventContext: TriggerEventContext = TriggerEventContext(),
		boolIgnoreUnsupported: Boolean = true
	): Boolean {
		if (!objSet.boolIsEnabled) return false
		if (objSet.listConditions.isEmpty()) return true

		return when (objSet.conditionMatchMode) {
			ConditionMatchMode.ALL_AND -> objSet.listConditions.all { objCond ->
				evaluateSingleCondition(objCond, objState, listGeofences, objEventContext, boolIgnoreUnsupported)
			}
			ConditionMatchMode.ANY_OR -> objSet.listConditions.any { objCond ->
				evaluateSingleCondition(objCond, objState, listGeofences, objEventContext, boolIgnoreUnsupported)
			}
		}
	}

	/**
	 * Evaluates a single [SetCondition], applying `boolInvert` after computing the base predicate.
	 *
	 * @param objCondition Individual condition rule.
	 * @param objState Current device state.
	 * @param listGeofences Configured geofences.
	 * @param objEventContext Trigger context payload.
	 * @param boolIgnoreUnsupported Whether to skip checks for unsupported hardware.
	 * @return Final boolean truth value for this condition.
	 */
	fun evaluateSingleCondition(
		objCondition: SetCondition,
		objState: LiveDeviceState,
		listGeofences: List<GeofenceArea>,
		objEventContext: TriggerEventContext = TriggerEventContext(),
		boolIgnoreUnsupported: Boolean = true
	): Boolean {
		val boolRaw = when (objCondition) {
			is SetCondition.GeofenceCondition -> evaluateGeofence(objCondition, objState, listGeofences, boolIgnoreUnsupported)
			is SetCondition.TimeCondition -> evaluateTime(objCondition, objState)
			is SetCondition.DeviceUnlockedCondition -> objState.boolDeviceUnlocked == objCondition.boolRequireUnlocked
			is SetCondition.ScreenStateCondition -> objState.boolScreenOn == objCondition.boolRequireScreenOn
			is SetCondition.WifiCondition -> evaluateWifi(objCondition, objState)
			is SetCondition.MobileDataCondition -> evaluateMobileData(objCondition, objState, boolIgnoreUnsupported)
			is SetCondition.BluetoothCondition -> evaluateBluetooth(objCondition, objState, boolIgnoreUnsupported)
			is SetCondition.MediaPlayingCondition -> evaluateMedia(objCondition, objState)
			is SetCondition.OngoingCallCondition -> evaluateOngoingCall(objCondition, objState, objEventContext, boolIgnoreUnsupported)
			is SetCondition.DndStatusCondition -> evaluateDnd(objCondition, objState)
			is SetCondition.BatteryStatusCondition -> evaluateBattery(objCondition, objState)
			is SetCondition.ChargingCondition -> evaluateCharging(objCondition, objState)
			is SetCondition.AppRunningCondition -> evaluateAppRunning(objCondition, objState)
		}
		return if (objCondition.boolInvert) !boolRaw else boolRaw
	}

	private fun evaluateGeofence(
		objCondition: SetCondition.GeofenceCondition,
		objState: LiveDeviceState,
		listGeofences: List<GeofenceArea>,
		boolIgnoreUnsupported: Boolean
	): Boolean {
		if (!objState.boolSupportsLocation) {
			return boolIgnoreUnsupported
		}
		if (objCondition.stringGeofenceId.isBlank()) return false

		// Check active geofence transition set first, then fall back to spherical/WGS84 distance calculation
		val boolInsideByActiveSet = objState.setActiveGeofenceIds.contains(objCondition.stringGeofenceId)
		val objTargetArea = listGeofences.find { it.stringId == objCondition.stringGeofenceId }
		val boolInside = if (boolInsideByActiveSet) {
			true
		} else if (objTargetArea != null && objState.doubleLatitude != null && objState.doubleLongitude != null) {
			val doubleDistanceMeters = computeDistanceMeters(
				objState.doubleLatitude,
				objState.doubleLongitude,
				objTargetArea.doubleLatitude,
				objTargetArea.doubleLongitude
			)
			doubleDistanceMeters <= objTargetArea.floatRadiusMeters
		} else {
			return false
		}

		return boolInside == objCondition.boolInside
	}

	/**
	 * Computes Haversine great-circle distance in meters (safe in pure JVM unit tests as well as Android runtime).
	 */
	internal fun computeDistanceMeters(
		doubleLat1: Double,
		doubleLon1: Double,
		doubleLat2: Double,
		doubleLon2: Double
	): Double {
		return try {
			val arrayRes = FloatArray(1)
			Location.distanceBetween(doubleLat1, doubleLon1, doubleLat2, doubleLon2, arrayRes)
			if (arrayRes[0] > 0f || (doubleLat1 == doubleLat2 && doubleLon1 == doubleLon2)) {
				arrayRes[0].toDouble()
			} else {
				haversineMeters(doubleLat1, doubleLon1, doubleLat2, doubleLon2)
			}
		} catch (objException: Throwable) {
			haversineMeters(doubleLat1, doubleLon1, doubleLat2, doubleLon2)
		}
	}

	private fun haversineMeters(
		doubleLat1: Double,
		doubleLon1: Double,
		doubleLat2: Double,
		doubleLon2: Double
	): Double {
		val doubleEarthRadius = 6371000.0
		val doubleDLat = Math.toRadians(doubleLat2 - doubleLat1)
		val doubleDLon = Math.toRadians(doubleLon2 - doubleLon1)
		val doubleA = kotlin.math.sin(doubleDLat / 2) * kotlin.math.sin(doubleDLat / 2) +
			kotlin.math.cos(Math.toRadians(doubleLat1)) * kotlin.math.cos(Math.toRadians(doubleLat2)) *
			kotlin.math.sin(doubleDLon / 2) * kotlin.math.sin(doubleDLon / 2)
		val doubleC = 2 * kotlin.math.atan2(kotlin.math.sqrt(doubleA), kotlin.math.sqrt(1.0 - doubleA))
		return doubleEarthRadius * doubleC
	}

	private fun evaluateTime(
		objCondition: SetCondition.TimeCondition,
		objState: LiveDeviceState
	): Boolean {
		if (!objCondition.isWithinTimeWindow(objState.intHour, objState.intMinute)) {
			return false
		}
		if (objCondition.listDaysOfWeek.isNotEmpty() && !objCondition.listDaysOfWeek.contains(objState.intDayOfWeek)) {
			return false
		}
		if (objCondition.listMonths.isNotEmpty() && !objCondition.listMonths.contains(objState.intMonth)) {
			return false
		}
		return true
	}

	private fun evaluateWifi(
		objCondition: SetCondition.WifiCondition,
		objState: LiveDeviceState
	): Boolean {
		if (!objCondition.boolRequireConnected) {
			return !objState.boolWifiConnected
		}
		if (!objState.boolWifiConnected) return false
		if (objCondition.ssidMatchMode == TextMatchMode.ANY || objCondition.stringSsidFilter.isBlank()) {
			return true
		}
		return ContextTemplateEngine.evaluateTextMatch(
			objState.stringWifiSsid,
			objCondition.ssidMatchMode,
			objCondition.stringSsidFilter
		).boolMatched
	}

	private fun evaluateMobileData(
		objCondition: SetCondition.MobileDataCondition,
		objState: LiveDeviceState,
		boolIgnoreUnsupported: Boolean
	): Boolean {
		if (!objState.boolSupportsTelephony) return boolIgnoreUnsupported
		if (objState.boolMobileDataConnected != objCondition.boolRequireConnected) return false
		if (objCondition.boolRequireConnected && objCondition.boolRequireNotRoaming && objState.boolIsRoaming) {
			return false
		}
		return true
	}

	private fun evaluateBluetooth(
		objCondition: SetCondition.BluetoothCondition,
		objState: LiveDeviceState,
		boolIgnoreUnsupported: Boolean
	): Boolean {
		if (!objState.boolSupportsBluetooth) return boolIgnoreUnsupported
		val boolAnyConnected = objState.setConnectedBluetoothNames.isNotEmpty() || objState.setConnectedBluetoothMacs.isNotEmpty()
		if (!objCondition.boolRequireConnected) {
			if (objCondition.deviceMatchMode == TextMatchMode.ANY || objCondition.stringDeviceNameOrMac.isBlank()) {
				return !boolAnyConnected
			}
			return !matchesAnyConnectedBluetooth(objState, objCondition.deviceMatchMode, objCondition.stringDeviceNameOrMac)
		}

		if (!boolAnyConnected) return false
		if (objCondition.deviceMatchMode == TextMatchMode.ANY || objCondition.stringDeviceNameOrMac.isBlank()) {
			return true
		}
		return matchesAnyConnectedBluetooth(objState, objCondition.deviceMatchMode, objCondition.stringDeviceNameOrMac)
	}

	private fun matchesAnyConnectedBluetooth(
		objState: LiveDeviceState,
		matchMode: TextMatchMode,
		stringFilter: String
	): Boolean {
		val setCandidates = objState.setConnectedBluetoothNames + objState.setConnectedBluetoothMacs
		return setCandidates.any { stringCandidate ->
			ContextTemplateEngine.evaluateTextMatch(stringCandidate, matchMode, stringFilter).boolMatched
		}
	}

	private fun evaluateMedia(
		objCondition: SetCondition.MediaPlayingCondition,
		objState: LiveDeviceState
	): Boolean {
		if (!objCondition.boolRequirePlaying) {
			if (objCondition.stringAppPackage.isBlank()) return !objState.boolMediaPlaying
			return !objState.setPlayingMediaPackages.contains(objCondition.stringAppPackage)
		}
		if (!objState.boolMediaPlaying) return false
		if (objCondition.stringAppPackage.isBlank()) return true
		return objState.setPlayingMediaPackages.contains(objCondition.stringAppPackage)
	}

	private fun evaluateOngoingCall(
		objCondition: SetCondition.OngoingCallCondition,
		objState: LiveDeviceState,
		objEventContext: TriggerEventContext,
		boolIgnoreUnsupported: Boolean
	): Boolean {
		if (!objState.boolSupportsTelephony) return boolIgnoreUnsupported
		val boolInCall = objState.intCallState == TelephonyManager.CALL_STATE_OFFHOOK
		if (boolInCall != objCondition.boolRequireInCall) return false
		if (boolInCall && objCondition.contactFilterMode != com.antigravity.triggeredactions.model.ContactFilterMode.ANY) {
			val stringNum = objState.stringActiveCallNumber.ifBlank { objEventContext.stringCallerOrSenderNumber }
			return ContextTemplateEngine.matchesContactFilter(
				objCondition.contactFilterMode,
				stringNum,
				objEventContext.boolIsInContacts,
				objEventContext.boolIsStarredContact
			)
		}
		return true
	}

	private fun evaluateDnd(
		objCondition: SetCondition.DndStatusCondition,
		objState: LiveDeviceState
	): Boolean {
		val intFilter = objState.intDndFilter
		return when (objCondition.dndConditionMode) {
			DndConditionMode.DND_OFF -> intFilter == NotificationManager.INTERRUPTION_FILTER_ALL
			DndConditionMode.ANY_DND_ON -> intFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
				intFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
			DndConditionMode.PRIORITY_ONLY -> intFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
			DndConditionMode.ALARMS_ONLY -> intFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
			DndConditionMode.TOTAL_SILENCE -> intFilter == NotificationManager.INTERRUPTION_FILTER_NONE
		}
	}

	private fun evaluateBattery(
		objCondition: SetCondition.BatteryStatusCondition,
		objState: LiveDeviceState
	): Boolean {
		val intCurrent = objState.intBatteryPercent
		val intTarget = objCondition.intBatteryPercent
		return when (objCondition.thresholdOperator) {
			BatteryThresholdOperator.DROPS_AT_OR_BELOW,
			BatteryThresholdOperator.LESS_THAN_OR_EQUAL -> intCurrent <= intTarget
			BatteryThresholdOperator.RISES_AT_OR_ABOVE,
			BatteryThresholdOperator.GREATER_THAN_OR_EQUAL -> intCurrent >= intTarget
			BatteryThresholdOperator.EQUALS -> intCurrent == intTarget
		}
	}

	private fun evaluateCharging(
		objCondition: SetCondition.ChargingCondition,
		objState: LiveDeviceState
	): Boolean {
		if (!objCondition.boolRequireCharging) {
			return !objState.boolIsCharging
		}
		if (!objState.boolIsCharging) return false
		if (objCondition.sourceFilter == ChargingSourceFilter.ANY) return true
		return objState.chargingSource == objCondition.sourceFilter
	}

	private fun evaluateAppRunning(
		objCondition: SetCondition.AppRunningCondition,
		objState: LiveDeviceState
	): Boolean {
		val stringTargetPkg = objCondition.stringPackageName.trim()
		if (stringTargetPkg.isEmpty()) return false

		return when (objCondition.checkMode) {
			AppRunningCheckMode.FOREGROUND_NOW -> {
				objState.stringCurrentForegroundPackage == stringTargetPkg
			}
			AppRunningCheckMode.RECENT_FOREGROUND -> {
				if (objState.stringCurrentForegroundPackage == stringTargetPkg) {
					true
				} else {
					val longLastSeen = objState.mapRecentForegroundTimestamps[stringTargetPkg] ?: return false
					val longElapsedMs = System.currentTimeMillis() - longLastSeen
					longElapsedMs <= (objCondition.intRecentWindowSeconds.coerceAtLeast(1) * 1000L)
				}
			}
			AppRunningCheckMode.FOREGROUND_OR_ACTIVE_MEDIA_NOTIF -> {
				objState.stringCurrentForegroundPackage == stringTargetPkg ||
					objState.setPlayingMediaPackages.contains(stringTargetPkg) ||
					objState.setActiveNotificationPackages.contains(stringTargetPkg)
			}
		}
	}
}
