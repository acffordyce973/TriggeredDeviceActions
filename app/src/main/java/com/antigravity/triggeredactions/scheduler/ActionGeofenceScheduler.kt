package com.antigravity.triggeredactions.scheduler

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.receiver.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Synchronizes active geofences referenced by enabled Action Sets with Google Play Services GeofencingClient.
 */
object ActionGeofenceScheduler {

	private const val TAG = "ActionGeofenceScheduler"
	private const val INT_REQUEST_CODE_GEOFENCE = 6060

	private fun getGeofencePendingIntent(objContext: Context): PendingIntent {
		val intentGeo = Intent(objContext, GeofenceBroadcastReceiver::class.java).apply {
			action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_TRANSITION
		}
		val intFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		} else {
			PendingIntent.FLAG_UPDATE_CURRENT
		}
		return PendingIntent.getBroadcast(objContext, INT_REQUEST_CODE_GEOFENCE, intentGeo, intFlags)
	}

	/**
	 * Registers only the [GeofenceArea] items actively referenced by enabled [ActionSet] triggers or conditions.
	 * Automatically removes registered geofences if master engine is paused or no enabled sets reference geofences.
	 *
	 * @param objContext Android context.
	 * @param listGeofences All defined geofences.
	 * @param listActionSets All defined action sets.
	 * @param boolMasterEnabled Global engine toggle.
	 */
	@SuppressLint("MissingPermission")
	fun synchronizeGeofences(
		objContext: Context,
		listGeofences: List<GeofenceArea>,
		listActionSets: List<ActionSet>,
		boolMasterEnabled: Boolean = true
	) {
		if (!boolMasterEnabled || !DeviceStateCollector.hasForegroundLocationPermission(objContext)) {
			removeAllGeofences(objContext)
			return
		}

		val setReferencedIds = mutableSetOf<String>()
		for (objSet in listActionSets) {
			if (!objSet.boolIsEnabled) continue
			objSet.listTriggers.filterIsInstance<SetTrigger.GeofenceTrigger>().forEach {
				if (it.stringGeofenceId.isNotBlank()) setReferencedIds.add(it.stringGeofenceId)
			}
			objSet.listConditions.filterIsInstance<SetCondition.GeofenceCondition>().forEach {
				if (it.stringGeofenceId.isNotBlank()) setReferencedIds.add(it.stringGeofenceId)
			}
		}

		val listActiveAreas = listGeofences.filter { setReferencedIds.contains(it.stringId) }
		if (listActiveAreas.isEmpty()) {
			removeAllGeofences(objContext)
			return
		}

		try {
			val objClient = LocationServices.getGeofencingClient(objContext)
			val objPendingIntent = getGeofencePendingIntent(objContext)

			val listPlayGeofences = listActiveAreas.map { objGeo ->
				Geofence.Builder()
					.setRequestId(objGeo.stringId)
					.setCircularRegion(
						objGeo.doubleLatitude,
						objGeo.doubleLongitude,
						objGeo.floatRadiusMeters.coerceAtLeast(50f)
					)
					.setExpirationDuration(Geofence.NEVER_EXPIRE)
					.setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
					.build()
			}

			val objRequest = GeofencingRequest.Builder()
				.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
				.addGeofences(listPlayGeofences)
				.build()

			objClient.removeGeofences(objPendingIntent).addOnCompleteListener {
				try {
					objClient.addGeofences(objRequest, objPendingIntent)
						.addOnFailureListener { objErr ->
							Log.e(
								TAG,
								"[ActionGeofenceScheduler] Failed registering geofences - ${objErr.javaClass.simpleName}: ${objErr.message}"
							)
						}
				} catch (objException: SecurityException) {
					Log.e(
						TAG,
						"[ActionGeofenceScheduler] SecurityException registering geofences - ${objException.javaClass.simpleName}: ${objException.message}"
					)
				}
			}
		} catch (objException: Throwable) {
			Log.e(
				TAG,
				"[ActionGeofenceScheduler] Error accessing LocationServices - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	/**
	 * Unregisters all geofences managed by this application.
	 *
	 * @param objContext Android context.
	 */
	fun removeAllGeofences(objContext: Context) {
		try {
			val objClient = LocationServices.getGeofencingClient(objContext)
			objClient.removeGeofences(getGeofencePendingIntent(objContext))
		} catch (objException: Throwable) {
			Log.e(
				TAG,
				"[ActionGeofenceScheduler] Error removing geofences - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}
}
