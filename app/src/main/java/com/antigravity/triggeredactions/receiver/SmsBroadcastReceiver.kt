package com.antigravity.triggeredactions.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.antigravity.triggeredactions.data.ContactLookupHelper
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS PDUs directly from the Android telephony stack (`SMS_RECEIVED_ACTION`),
 * reassembling multi-part messages per sender and dispatching unredacted text to [TriggerDispatcher].
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

	override fun onReceive(objContext: Context, objIntent: Intent) {
		if (objIntent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
		val arrayMessages = Telephony.Sms.Intents.getMessagesFromIntent(objIntent)
		if (arrayMessages.isNullOrEmpty()) return

		val mapSenderBodies = linkedMapOf<String, StringBuilder>()
		for (objSms in arrayMessages) {
			val stringOrigin = objSms.displayOriginatingAddress ?: objSms.originatingAddress ?: "Unknown"
			val stringPart = objSms.displayMessageBody ?: objSms.messageBody.orEmpty()
			mapSenderBodies.getOrPut(stringOrigin) { StringBuilder() }.append(stringPart)
		}

		val objPending = goAsync()
		val objAppCtx = objContext.applicationContext
		CoroutineScope(Dispatchers.IO).launch {
			try {
				for ((stringSender, objBodyBuilder) in mapSenderBodies) {
					val objContact = ContactLookupHelper.lookupPhoneNumber(objAppCtx, stringSender)
					TriggerDispatcher.onSmsReceived(
						objContext = objAppCtx,
						stringSender = stringSender,
						stringBody = objBodyBuilder.toString(),
						stringContactName = objContact.stringDisplayName,
						boolInContacts = objContact.boolIsInContacts,
						boolIsStarred = objContact.boolIsStarred
					)
				}
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[SmsBroadcastReceiver] Error processing incoming SMS - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				objPending.finish()
			}
		}
	}

	companion object {
		private const val TAG = "SmsBroadcastReceiver"
	}
}
