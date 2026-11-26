package com.antigravity.triggeredactions.engine

import android.content.Context
import android.util.Log
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.BatteryThresholdOperator
import com.antigravity.triggeredactions.model.CallEventDirection
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import com.antigravity.triggeredactions.model.GeofenceTransitionMode
import com.antigravity.triggeredactions.model.MissedCallFilter
import com.antigravity.triggeredactions.model.NotificationDismissReason
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.TriggerEventContext
import com.antigravity.triggeredactions.service.TriggeredExecutionService
import java.util.concurrent.ConcurrentHashMap

/**
 * Central event router matching incoming system events against enabled [ActionSet] triggers,
 * evaluating `AND`/`OR` conditions, enforcing set cooldowns, and dispatching sequences to [TriggeredExecutionService].
 */
object TriggerDispatcher {

	private const val TAG = "TriggerDispatcher"
	private val mapLastTriggeredAtMs = ConcurrentHashMap<String, Long>()

	/**
	 * Executes a specific [ActionSet] immediately for manual user testing, bypassing trigger/condition checks.
	 *
	 * @param objContext Android context.
	 * @param objSet Target [ActionSet] to run.
	 */
	fun executeManualTestRun(objContext: Context, objSet: ActionSet) {
		val objState = DeviceStateCollector.refreshDeviceState(objContext)
		val objManualContext = TriggerEventContext(
			stringTriggerType = "manual_test",
			stringTriggerSummary = "Manual Test Run",
			stringWifiSsid = objState.stringWifiSsid,
			intBatteryPercent = objState.intBatteryPercent,
			stringForegroundPackage = objState.stringCurrentForegroundPackage
		)
		TriggeredExecutionService.enqueueActionSet(objContext, objSet, objManualContext)
	}


	// ---- Event Entrypoints ----

	/**
	 * Evaluates all enabled [ActionSet]s against a custom [triggerMatcher] predicate.
	 *
	 * @param objContext Android context.
	 * @param objBaseEvent Base event context from the receiver/listener.
	 * @param triggerMatcher Predicate returning non-null enriched [TriggerEventContext] when a [SetTrigger] matches.
	 */
	suspend fun dispatchEvent(
		objContext: Context,
		objBaseEvent: TriggerEventContext,
		triggerMatcher: (SetTrigger, TriggerEventContext) -> TriggerEventContext?
	) {
		try {
			val objRepo = ActionsRepository.getInstance(objContext)
			objRepo.ensureLoaded()
			val objSettings = objRepo.flowSettings.value
			if (!objSettings.boolMasterEngineEnabled) return

			val listSets = objRepo.flowActionSets.value.filter { it.boolIsEnabled }
			if (listSets.isEmpty()) return

			val listGeofences = objRepo.flowGeofences.value
			val objState = DeviceStateCollector.refreshDeviceState(objContext)
			val objEnrichedBase = objBaseEvent.copy(
				stringWifiSsid = objBaseEvent.stringWifiSsid.ifBlank { objState.stringWifiSsid },
				intBatteryPercent = if (objBaseEvent.intBatteryPercent >= 0) objBaseEvent.intBatteryPercent else objState.intBatteryPercent,
				stringForegroundPackage = objBaseEvent.stringForegroundPackage.ifBlank { objState.stringCurrentForegroundPackage }
			)

			val longNow = System.currentTimeMillis()
			for (objSet in listSets) {
				if (objSet.intCooldownSeconds > 0) {
					val longLast = mapLastTriggeredAtMs[objSet.stringId] ?: 0L
					if (longNow - longLast < objSet.intCooldownSeconds * 1000L) {
						continue
					}
				}

				var objMatchedContext: TriggerEventContext? = null
				for (objTrigger in objSet.listTriggers) {
					val objCandidate = triggerMatcher(objTrigger, objEnrichedBase)
					if (objCandidate != null) {
						val stringGeoResolved = if (objCandidate.stringGeofenceId.isNotBlank() && objCandidate.stringGeofenceName.isBlank()) {
							listGeofences.find { it.stringId == objCandidate.stringGeofenceId }?.stringName.orEmpty()
						} else {
							objCandidate.stringGeofenceName
						}
						objMatchedContext = objCandidate.copy(
							stringGeofenceName = stringGeoResolved,
							stringTriggerSummary = objCandidate.stringTriggerSummary.ifBlank {
								ContextTemplateEngine.getTriggerSummary(objTrigger, listGeofences)
							}
						)
						break
					}
				}

				if (objMatchedContext == null) continue

				val boolConditionsMet = ConditionEvaluator.evaluateSetConditions(
					objSet = objSet,
					objState = objState,
					listGeofences = listGeofences,
					objEventContext = objMatchedContext,
					boolIgnoreUnsupported = objSettings.boolIgnoreUnsupportedConditions
				)

				if (boolConditionsMet) {
					mapLastTriggeredAtMs[objSet.stringId] = longNow
					TriggeredExecutionService.enqueueActionSet(objContext, objSet, objMatchedContext)
				}
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggerDispatcher] Error dispatching event ${objBaseEvent.stringTriggerType} - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	/**
	 * Dispatches a Geofence enter/exit boundary event.
	 */
	suspend fun onGeofenceTransition(
		objContext: Context,
		listGeofenceIds: List<String>,
		boolEntered: Boolean
	) {
		val stringTransition = if (boolEntered) "Entered" else "Exited"
		for (stringGeoId in listGeofenceIds) {
			val objBase = TriggerEventContext(
				stringTriggerType = "geofence",
				stringGeofenceId = stringGeoId,
				stringGeofenceTransition = stringTransition
			)
			dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
				if (objTrigger is SetTrigger.GeofenceTrigger &&
					(objTrigger.stringGeofenceId.isBlank() || objTrigger.stringGeofenceId == stringGeoId)
				) {
					val boolDirMatch = when (objTrigger.transitionMode) {
						GeofenceTransitionMode.ENTER -> boolEntered
						GeofenceTransitionMode.EXIT -> !boolEntered
						GeofenceTransitionMode.BOTH -> true
					}
					if (boolDirMatch) objCtx else null
				} else null
			}
		}
	}

	/**
	 * Dispatches a scheduled exact time or interval alarm trigger.
	 */
	suspend fun onTimeAlarmFired(
		objContext: Context,
		stringTargetSetId: String,
		stringTargetTriggerId: String
	) {
		val objRepo = ActionsRepository.getInstance(objContext)
		objRepo.ensureLoaded()
		if (!objRepo.flowSettings.value.boolMasterEngineEnabled) return

		val objSet = objRepo.flowActionSets.value.find { it.stringId == stringTargetSetId && it.boolIsEnabled } ?: return
		val objTrigger = objSet.listTriggers.filterIsInstance<SetTrigger.TimeScheduleTrigger>()
			.find { it.stringId == stringTargetTriggerId } ?: return

		val listGeofences = objRepo.flowGeofences.value
		val objState = DeviceStateCollector.refreshDeviceState(objContext)
		val objCtx = TriggerEventContext(
			stringTriggerType = "time_schedule",
			stringTriggerSummary = ContextTemplateEngine.getTriggerSummary(objTrigger, listGeofences),
			stringWifiSsid = objState.stringWifiSsid,
			intBatteryPercent = objState.intBatteryPercent,
			stringForegroundPackage = objState.stringCurrentForegroundPackage
		)

		if (ConditionEvaluator.evaluateSetConditions(
				objSet,
				objState,
				listGeofences,
				objCtx,
				objRepo.flowSettings.value.boolIgnoreUnsupportedConditions
			)
		) {
			TriggeredExecutionService.enqueueActionSet(objContext, objSet, objCtx)
		}
	}

	/**
	 * Dispatches Screen On / Screen Off events.
	 */
	suspend fun onScreenStateChanged(objContext: Context, boolScreenOn: Boolean) {
		val objBase = TriggerEventContext(
			stringTriggerType = "screen_state",
			stringTriggerSummary = if (boolScreenOn) "Screen Turned On" else "Screen Turned Off"
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.ScreenStateTrigger && objTrigger.boolOnScreenOn == boolScreenOn) objCtx else null
		}
	}

	/**
	 * Dispatches Device Unlocked / Device Locked events.
	 */
	suspend fun onDeviceLockChanged(objContext: Context, boolUnlocked: Boolean) {
		val objBase = TriggerEventContext(
			stringTriggerType = "device_lock",
			stringTriggerSummary = if (boolUnlocked) "Device Unlocked" else "Device Locked"
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.DeviceLockTrigger && objTrigger.boolOnUnlocked == boolUnlocked) objCtx else null
		}
	}

	/**
	 * Dispatches Charger Connected / Disconnected events.
	 */
	suspend fun onChargingStateChanged(
		objContext: Context,
		boolConnected: Boolean,
		chargingSource: ChargingSourceFilter
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "charging_state",
			stringTriggerSummary = if (boolConnected) "Charger Connected (${chargingSource.name})" else "Charger Disconnected"
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.ChargingStateTrigger && objTrigger.boolOnChargingConnected == boolConnected) {
				val boolSourceOk = !boolConnected ||
					objTrigger.sourceFilter == ChargingSourceFilter.ANY ||
					objTrigger.sourceFilter == chargingSource
				if (boolSourceOk) objCtx else null
			} else null
		}
	}

	/**
	 * Dispatches Battery Level edge crossings (`intPreviousPct` -> `intCurrentPct`).
	 */
	suspend fun onBatteryLevelChanged(objContext: Context, intPreviousPct: Int, intCurrentPct: Int) {
		if (intPreviousPct == intCurrentPct) return
		val objBase = TriggerEventContext(
			stringTriggerType = "battery_status",
			stringTriggerSummary = "Battery at $intCurrentPct%",
			intBatteryPercent = intCurrentPct
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.BatteryStatusTrigger) {
				val intTarget = objTrigger.intBatteryPercent
				val boolEdgeHit = when (objTrigger.thresholdOperator) {
					BatteryThresholdOperator.DROPS_AT_OR_BELOW,
					BatteryThresholdOperator.LESS_THAN_OR_EQUAL -> intPreviousPct > intTarget && intCurrentPct <= intTarget
					BatteryThresholdOperator.RISES_AT_OR_ABOVE,
					BatteryThresholdOperator.GREATER_THAN_OR_EQUAL -> intPreviousPct < intTarget && intCurrentPct >= intTarget
					BatteryThresholdOperator.EQUALS -> intCurrentPct == intTarget
				}
				if (boolEdgeHit) objCtx else null
			} else null
		}
	}

	/**
	 * Dispatches Wi-Fi connection / disconnection transitions.
	 */
	suspend fun onWifiChanged(objContext: Context, boolConnected: Boolean, stringSsid: String) {
		val objBase = TriggerEventContext(
			stringTriggerType = "wifi",
			stringTriggerSummary = if (boolConnected) "Wi-Fi Connected ($stringSsid)" else "Wi-Fi Disconnected",
			stringWifiSsid = stringSsid
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.WifiTrigger && objTrigger.boolOnConnected == boolConnected) {
				val objMatch = ContextTemplateEngine.evaluateTextMatch(
					stringSsid,
					objTrigger.ssidMatchMode,
					objTrigger.stringSsidFilter
				)
				if (objMatch.boolMatched) objCtx.copy(stringMatchedValue = objMatch.stringPrimaryMatch) else null
			} else null
		}
	}

	/**
	 * Dispatches Mobile Data connection / disconnection transitions.
	 */
	suspend fun onMobileDataChanged(objContext: Context, boolConnected: Boolean, boolIsRoaming: Boolean) {
		val objBase = TriggerEventContext(
			stringTriggerType = "mobile_data",
			stringTriggerSummary = if (boolConnected) "Mobile Data Connected" else "Mobile Data Disconnected"
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.MobileDataTrigger && objTrigger.boolOnConnected == boolConnected) {
				if (boolConnected && objTrigger.boolRequireNotRoaming && boolIsRoaming) null else objCtx
			} else null
		}
	}

	/**
	 * Dispatches Bluetooth ACL device connection / disconnection transitions.
	 */
	suspend fun onBluetoothChanged(
		objContext: Context,
		boolConnected: Boolean,
		stringDeviceName: String,
		stringDeviceMac: String
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "bluetooth",
			stringTriggerSummary = if (boolConnected) "BT Connected: ${stringDeviceName.ifBlank { stringDeviceMac }}" else "BT Disconnected: ${stringDeviceName.ifBlank { stringDeviceMac }}",
			stringBluetoothDeviceName = stringDeviceName,
			stringBluetoothDeviceMac = stringDeviceMac
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.BluetoothTrigger && objTrigger.boolOnConnected == boolConnected) {
				val objNameMatch = ContextTemplateEngine.evaluateTextMatch(
					stringDeviceName,
					objTrigger.deviceMatchMode,
					objTrigger.stringDeviceNameOrMac
				)
				val objMacMatch = ContextTemplateEngine.evaluateTextMatch(
					stringDeviceMac,
					objTrigger.deviceMatchMode,
					objTrigger.stringDeviceNameOrMac
				)
				if (objNameMatch.boolMatched || objMacMatch.boolMatched) objCtx else null
			} else null
		}
	}

	/**
	 * Dispatches Media Session playback start/stop/track-change events.
	 */
	suspend fun onMediaPlaybackEvent(
		objContext: Context,
		boolPlaybackActive: Boolean,
		boolIsTrackChangeOnly: Boolean,
		stringPackage: String,
		stringTitle: String,
		stringArtist: String
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "media_playback",
			stringTriggerSummary = if (boolPlaybackActive) "Media Playing ($stringTitle)" else "Media Stopped",
			stringMediaAppPackage = stringPackage,
			stringMediaTitle = stringTitle,
			stringMediaArtist = stringArtist
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.MediaPlaybackTrigger) {
				val boolStateOk = if (boolIsTrackChangeOnly) {
					objTrigger.boolOnTrackChanged && boolPlaybackActive
				} else {
					objTrigger.boolOnPlaybackStarted == boolPlaybackActive
				}
				if (!boolStateOk) return@dispatchEvent null
				if (objTrigger.stringAppPackage.isNotBlank() && objTrigger.stringAppPackage != stringPackage) {
					return@dispatchEvent null
				}
				val stringCombinedMeta = "$stringTitle $stringArtist".trim()
				val objMatch = ContextTemplateEngine.evaluateTextMatch(
					stringCombinedMeta,
					objTrigger.textMatchMode,
					objTrigger.stringTrackOrArtistFilter
				)
				if (objMatch.boolMatched) {
					objCtx.copy(
						stringMatchedValue = objMatch.stringPrimaryMatch,
						listRegexGroups = objMatch.listCaptureGroups
					)
				} else null
			} else null
		}
	}

	/**
	 * Dispatches Incoming Call ringing events.
	 */
	suspend fun onIncomingCall(
		objContext: Context,
		stringPhoneNumber: String,
		stringContactName: String,
		boolInContacts: Boolean,
		boolIsStarred: Boolean
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "incoming_call",
			stringTriggerSummary = "Incoming Call from ${stringContactName.ifBlank { stringPhoneNumber.ifBlank { "Unknown" } }}",
			stringCallerOrSenderNumber = stringPhoneNumber,
			stringContactName = stringContactName,
			boolIsInContacts = boolInContacts,
			boolIsStarredContact = boolIsStarred
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.IncomingCallTrigger &&
				ContextTemplateEngine.matchesContactFilter(
					objTrigger.contactFilterMode,
					stringPhoneNumber,
					boolInContacts,
					boolIsStarred,
					objTrigger.stringNumberOrRegex
				)
			) objCtx else null
		}
	}

	/**
	 * Dispatches Missed / Declined Call events.
	 */
	suspend fun onMissedCall(
		objContext: Context,
		stringPhoneNumber: String,
		stringContactName: String,
		boolInContacts: Boolean,
		boolIsStarred: Boolean,
		boolRejectedByUser: Boolean
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "missed_call",
			stringTriggerSummary = "Missed Call from ${stringContactName.ifBlank { stringPhoneNumber.ifBlank { "Unknown" } }}",
			stringCallerOrSenderNumber = stringPhoneNumber,
			stringContactName = stringContactName,
			boolIsInContacts = boolInContacts,
			boolIsStarredContact = boolIsStarred
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.MissedCallTrigger) {
				val boolSubTypeOk = when (objTrigger.missedCallFilter) {
					MissedCallFilter.ANY_UNANSWERED -> true
					MissedCallFilter.MISSED_RANG_OUT_ONLY -> !boolRejectedByUser
					MissedCallFilter.REJECTED_BY_USER_ONLY -> boolRejectedByUser
				}
				if (boolSubTypeOk && ContextTemplateEngine.matchesContactFilter(
						objTrigger.contactFilterMode,
						stringPhoneNumber,
						boolInContacts,
						boolIsStarred,
						objTrigger.stringNumberOrRegex
					)
				) objCtx else null
			} else null
		}
	}

	/**
	 * Dispatches Ongoing / Answered / Outgoing / Ended Call state transitions.
	 */
	suspend fun onOngoingOrEndedCall(
		objContext: Context,
		direction: CallEventDirection,
		stringPhoneNumber: String,
		stringContactName: String,
		boolInContacts: Boolean,
		boolIsStarred: Boolean
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "ongoing_call",
			stringTriggerSummary = "Call ${direction.name}",
			stringCallerOrSenderNumber = stringPhoneNumber,
			stringContactName = stringContactName,
			boolIsInContacts = boolInContacts,
			boolIsStarredContact = boolIsStarred
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.OngoingCallTrigger) {
				val boolDirMatch = when (objTrigger.callDirection) {
					CallEventDirection.ANY_ONGOING -> direction == CallEventDirection.INCOMING_ANSWERED ||
						direction == CallEventDirection.OUTGOING_STARTED
					else -> objTrigger.callDirection == direction
				}
				if (boolDirMatch && ContextTemplateEngine.matchesContactFilter(
						objTrigger.contactFilterMode,
						stringPhoneNumber,
						boolInContacts,
						boolIsStarred,
						objTrigger.stringNumberOrRegex
					)
				) objCtx else null
			} else null
		}
	}

	/**
	 * Dispatches incoming SMS message triggers.
	 */
	suspend fun onSmsReceived(
		objContext: Context,
		stringSender: String,
		stringBody: String,
		stringContactName: String,
		boolInContacts: Boolean,
		boolIsStarred: Boolean
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "sms_received",
			stringTriggerSummary = "SMS from ${stringContactName.ifBlank { stringSender }}",
			stringCallerOrSenderNumber = stringSender,
			stringContactName = stringContactName,
			boolIsInContacts = boolInContacts,
			boolIsStarredContact = boolIsStarred,
			stringSmsBody = stringBody
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.SmsReceivedTrigger) {
				val boolSenderOk = ContextTemplateEngine.matchesContactFilter(
					objTrigger.contactFilterMode,
					stringSender,
					boolInContacts,
					boolIsStarred,
					objTrigger.stringSenderFilter
				)
				if (!boolSenderOk) return@dispatchEvent null
				val objBodyMatch = ContextTemplateEngine.evaluateTextMatch(
					stringBody,
					objTrigger.bodyMatchMode,
					objTrigger.stringBodyPattern
				)
				if (objBodyMatch.boolMatched) {
					objCtx.copy(
						stringMatchedValue = objBodyMatch.stringPrimaryMatch,
						listRegexGroups = objBodyMatch.listCaptureGroups
					)
				} else null
			} else null
		}
	}

	/**
	 * Dispatches Notification Posted events.
	 */
	suspend fun onNotificationPosted(
		objContext: Context,
		stringPackage: String,
		stringAppName: String,
		stringTitle: String,
		stringBody: String,
		boolIsOngoing: Boolean,
		boolIsGroupSummary: Boolean
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "notification_received",
			stringTriggerSummary = "Notification from $stringAppName",
			stringNotificationPackage = stringPackage,
			stringNotificationAppName = stringAppName,
			stringNotificationTitle = stringTitle,
			stringNotificationText = stringBody
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.NotificationReceivedTrigger) {
				if (objTrigger.boolIgnoreOngoing && boolIsOngoing) return@dispatchEvent null
				if (objTrigger.boolIgnoreGroupSummary && boolIsGroupSummary) return@dispatchEvent null
				if (objTrigger.stringPackageFilter.isNotBlank()) {
					val listAllowed = objTrigger.stringPackageFilter.split(',').map { it.trim() }.filter { it.isNotEmpty() }
					if (listAllowed.isNotEmpty() && !listAllowed.any { it.equals(stringPackage, ignoreCase = true) }) {
						return@dispatchEvent null
					}
				}
				val objTitleMatch = ContextTemplateEngine.evaluateTextMatch(
					stringTitle,
					objTrigger.titleMatchMode,
					objTrigger.stringTitlePattern
				)
				if (!objTitleMatch.boolMatched) return@dispatchEvent null

				val stringCombinedBody = if (objTrigger.bodyMatchMode == com.antigravity.triggeredactions.model.TextMatchMode.OTP_PRESET) {
					"$stringTitle $stringBody".trim()
				} else {
					stringBody
				}
				val objBodyMatch = ContextTemplateEngine.evaluateTextMatch(
					stringCombinedBody,
					objTrigger.bodyMatchMode,
					objTrigger.stringBodyPattern
				)
				if (objBodyMatch.boolMatched) {
					val stringPrimary = objBodyMatch.stringPrimaryMatch.ifBlank { objTitleMatch.stringPrimaryMatch }
					objCtx.copy(
						stringMatchedValue = stringPrimary,
						listRegexGroups = objBodyMatch.listCaptureGroups.ifEmpty { objTitleMatch.listCaptureGroups }
					)
				} else null
			} else null
		}
	}

	/**
	 * Dispatches Notification Removed / Dismissed events.
	 */
	suspend fun onNotificationDismissed(
		objContext: Context,
		stringPackage: String,
		stringAppName: String,
		stringTitle: String,
		stringBody: String,
		dismissReason: NotificationDismissReason
	) {
		val objBase = TriggerEventContext(
			stringTriggerType = "notification_dismissed",
			stringTriggerSummary = "Notification Dismissed ($stringAppName)",
			stringNotificationPackage = stringPackage,
			stringNotificationAppName = stringAppName,
			stringNotificationTitle = stringTitle,
			stringNotificationText = stringBody
		)
		dispatchEvent(objContext, objBase) { objTrigger, objCtx ->
			if (objTrigger is SetTrigger.NotificationDismissedTrigger) {
				if (objTrigger.dismissReason != NotificationDismissReason.ANY && objTrigger.dismissReason != dismissReason) {
					return@dispatchEvent null
				}
				if (objTrigger.stringPackageFilter.isNotBlank()) {
					val listAllowed = objTrigger.stringPackageFilter.split(',').map { it.trim() }.filter { it.isNotEmpty() }
					if (listAllowed.isNotEmpty() && !listAllowed.any { it.equals(stringPackage, ignoreCase = true) }) {
						return@dispatchEvent null
					}
				}
				val objTitleMatch = ContextTemplateEngine.evaluateTextMatch(
					stringTitle,
					objTrigger.titleMatchMode,
					objTrigger.stringTitlePattern
				)
				if (!objTitleMatch.boolMatched) return@dispatchEvent null

				val objBodyMatch = ContextTemplateEngine.evaluateTextMatch(
					stringBody,
					objTrigger.bodyMatchMode,
					objTrigger.stringBodyPattern
				)
				if (objBodyMatch.boolMatched) {
					objCtx.copy(
						stringMatchedValue = objBodyMatch.stringPrimaryMatch,
						listRegexGroups = objBodyMatch.listCaptureGroups
					)
				} else null
			} else null
		}
	}
}
