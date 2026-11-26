package com.antigravity.triggeredactions.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives hardware-assisted geofence boundary transitions from Google Play Services Location.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

	override fun onReceive(objContext: Context, objIntent: Intent) {
		if (objIntent.action != ACTION_GEOFENCE_TRANSITION) return
		val objEvent = GeofencingEvent.fromIntent(objIntent) ?: return
		if (objEvent.hasError()) {
			Log.e(
				TAG,
				"[GeofenceBroadcastReceiver] Geofencing error code ${objEvent.errorCode} - GeofenceError: Transition dropped"
			)
			return
		}

		val intTransition = objEvent.geofenceTransition
		val boolEntered = when (intTransition) {
			Geofence.GEOFENCE_TRANSITION_ENTER -> true
			Geofence.GEOFENCE_TRANSITION_EXIT -> false
			else -> return
		}

		val listIds = objEvent.triggeringGeofences?.mapNotNull { it.requestId }.orEmpty()
		if (listIds.isEmpty()) return

		objEvent.triggeringLocation?.let { objLoc ->
			DeviceStateCollector.updateLocation(objContext.applicationContext, objLoc.latitude, objLoc.longitude)
		}
		DeviceStateCollector.updateGeofenceTransition(objContext.applicationContext, listIds, boolEntered)

		val objPending = goAsync()
		val objAppCtx = objContext.applicationContext
		CoroutineScope(Dispatchers.IO).launch {
			try {
				TriggerDispatcher.onGeofenceTransition(objAppCtx, listIds, boolEntered)
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[GeofenceBroadcastReceiver] Error dispatching geofence transition - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				objPending.finish()
			}
		}
	}

	companion object {
		private const val TAG = "GeofenceReceiver"
		const val ACTION_GEOFENCE_TRANSITION = "com.antigravity.triggeredactions.ACTION_GEOFENCE_TRANSITION"
	}
}
