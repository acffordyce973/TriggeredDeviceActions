package com.antigravity.triggeredactions.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.data.ContactLookupHelper
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.model.CallEventDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tracks Telephony call state transitions (`RINGING`, `OFFHOOK`, `IDLE`) to dispatch Incoming Call,
 * Ongoing/Answered/Outgoing/Ended Call, and Missed/Declined Call triggers with caller contact resolution.
 *
 * Note: On Android 9+, Android delivers two `PHONE_STATE` broadcasts when `READ_CALL_LOG` is granted—one
 * without `EXTRA_INCOMING_NUMBER` and one with `EXTRA_INCOMING_NUMBER`. This state machine deduplicates
 * them cleanly so triggers receive the resolved caller phone number exactly once per transition.
 */
class TelephonyCallReceiver : BroadcastReceiver() {

	@Suppress("DEPRECATION")
	override fun onReceive(objContext: Context, objIntent: Intent) {
		if (objIntent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
		val stringStateExtra = objIntent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
		val boolHasCallLogPerm = ContextCompat.checkSelfPermission(
			objContext,
			Manifest.permission.READ_CALL_LOG
		) == PackageManager.PERMISSION_GRANTED
		val boolHasIncomingNumberExtra = objIntent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

		// When READ_CALL_LOG is granted, Android fires an unnumbered broadcast and a numbered broadcast; skip the unnumbered duplicate.
		if (boolHasCallLogPerm && !boolHasIncomingNumberExtra) {
			return
		}

		val stringRawNumber = objIntent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER).orEmpty().trim()
		val intNewState = when (stringStateExtra) {
			TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
			TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
			else -> TelephonyManager.CALL_STATE_IDLE
		}

		val intPrevState: Int
		val stringEffectiveNumber: String
		synchronized(objStateLock) {
			if (intNewState == intCurrentCallState && (stringRawNumber.isEmpty() || stringRawNumber == stringCurrentNumber)) {
				return
			}
			intPrevState = intCurrentCallState
			intCurrentCallState = intNewState
			if (stringRawNumber.isNotEmpty()) {
				stringCurrentNumber = stringRawNumber
			}
			stringEffectiveNumber = stringCurrentNumber
			if (intNewState == TelephonyManager.CALL_STATE_IDLE) {
				stringCurrentNumber = ""
			}
		}

		val objAppCtx = objContext.applicationContext
		DeviceStateCollector.updateCallState(objAppCtx, intNewState, stringEffectiveNumber)

		val objPending = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				val objContact = ContactLookupHelper.lookupPhoneNumber(objAppCtx, stringEffectiveNumber)
				when {
					intNewState == TelephonyManager.CALL_STATE_RINGING -> {
						TriggerDispatcher.onIncomingCall(
							objContext = objAppCtx,
							stringPhoneNumber = stringEffectiveNumber,
							stringContactName = objContact.stringDisplayName,
							boolInContacts = objContact.boolIsInContacts,
							boolIsStarred = objContact.boolIsStarred
						)
					}

					intPrevState == TelephonyManager.CALL_STATE_RINGING && intNewState == TelephonyManager.CALL_STATE_OFFHOOK -> {
						TriggerDispatcher.onOngoingOrEndedCall(
							objContext = objAppCtx,
							direction = CallEventDirection.INCOMING_ANSWERED,
							stringPhoneNumber = stringEffectiveNumber,
							stringContactName = objContact.stringDisplayName,
							boolInContacts = objContact.boolIsInContacts,
							boolIsStarred = objContact.boolIsStarred
						)
					}

					intPrevState == TelephonyManager.CALL_STATE_IDLE && intNewState == TelephonyManager.CALL_STATE_OFFHOOK -> {
						TriggerDispatcher.onOngoingOrEndedCall(
							objContext = objAppCtx,
							direction = CallEventDirection.OUTGOING_STARTED,
							stringPhoneNumber = stringEffectiveNumber,
							stringContactName = objContact.stringDisplayName,
							boolInContacts = objContact.boolIsInContacts,
							boolIsStarred = objContact.boolIsStarred
						)
					}

					intPrevState == TelephonyManager.CALL_STATE_RINGING && intNewState == TelephonyManager.CALL_STATE_IDLE -> {
						// Unanswered incoming call -> Wait briefly for CallLog commit to distinguish Missed vs Rejected
						delay(850L)
						val boolWasRejected = checkLatestCallWasRejected(objAppCtx, boolHasCallLogPerm)
						TriggerDispatcher.onMissedCall(
							objContext = objAppCtx,
							stringPhoneNumber = stringEffectiveNumber,
							stringContactName = objContact.stringDisplayName,
							boolInContacts = objContact.boolIsInContacts,
							boolIsStarred = objContact.boolIsStarred,
							boolRejectedByUser = boolWasRejected
						)
					}

					intPrevState == TelephonyManager.CALL_STATE_OFFHOOK && intNewState == TelephonyManager.CALL_STATE_IDLE -> {
						TriggerDispatcher.onOngoingOrEndedCall(
							objContext = objAppCtx,
							direction = CallEventDirection.CALL_ENDED,
							stringPhoneNumber = stringEffectiveNumber,
							stringContactName = objContact.stringDisplayName,
							boolInContacts = objContact.boolIsInContacts,
							boolIsStarred = objContact.boolIsStarred
						)
					}
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[TelephonyCallReceiver] Error processing phone state $stringStateExtra - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				objPending.finish()
			}
		}
	}

	private fun checkLatestCallWasRejected(objContext: Context, boolHasCallLogPerm: Boolean): Boolean {
		if (!boolHasCallLogPerm) return false
		return try {
			objContext.contentResolver.query(
				CallLog.Calls.CONTENT_URI,
				arrayOf(CallLog.Calls.TYPE),
				null,
				null,
				"${CallLog.Calls.DATE} DESC"
			)?.use { objCursor ->
				if (objCursor.moveToFirst()) {
					val intType = objCursor.getInt(0)
					intType == CallLog.Calls.REJECTED_TYPE
				} else false
			} ?: false
		} catch (objException: Exception) {
			false
		}
	}

	companion object {
		private const val TAG = "TelephonyCallReceiver"
		private val objStateLock = Any()
		private var intCurrentCallState: Int = TelephonyManager.CALL_STATE_IDLE
		private var stringCurrentNumber: String = ""
	}
}
