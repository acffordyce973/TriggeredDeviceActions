package com.antigravity.triggeredactions.engine

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import com.antigravity.triggeredactions.model.LiveDeviceState
import com.antigravity.triggeredactions.service.TriggeredNotificationListenerService
import com.google.android.gms.location.LocationServices
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Collects live hardware, network, Bluetooth, audio, telephony, notification, location, and foreground app states
 * without using `AccessibilityService`.
 */
object DeviceStateCollector {

	private const val TAG = "DeviceStateCollector"
	private const val LONG_USAGE_LOOKBACK_MS = 120_000L

	private val _flowLiveState = MutableStateFlow(LiveDeviceState())
	val flowLiveState: StateFlow<LiveDeviceState> = _flowLiveState.asStateFlow()

	private val setActiveGeofences = mutableSetOf<String>()
	private val setConnectedBtNames = mutableSetOf<String>()
	private val setConnectedBtMacs = mutableSetOf<String>()
	private val setPlayingMediaPkgs = mutableSetOf<String>()
	private val setActiveNotifPkgs = mutableSetOf<String>()

	@Volatile
	private var doubleCachedLat: Double? = null

	@Volatile
	private var doubleCachedLng: Double? = null

	@Volatile
	private var stringActiveMediaTitle: String = ""

	@Volatile
	private var stringActiveMediaArtist: String = ""

	@Volatile
	private var intLastCallState: Int = TelephonyManager.CALL_STATE_IDLE

	@Volatile
	private var stringActiveCallNumber: String = ""

	@Volatile
	private var boolSensitiveRedactionObserved: Boolean = false


	// ---- State Mutation Callbacks from Listeners & Receivers ----

	/**
	 * Updates cached device coordinates and refreshes the state flow.
	 *
	 * @param objContext Android context.
	 * @param doubleLatitude Latest latitude.
	 * @param doubleLongitude Latest longitude.
	 */
	fun updateLocation(objContext: Context, doubleLatitude: Double, doubleLongitude: Double) {
		doubleCachedLat = doubleLatitude
		doubleCachedLng = doubleLongitude
		refreshDeviceState(objContext)
	}

	/**
	 * Updates active geofence membership upon enter/exit transitions.
	 *
	 * @param objContext Android context.
	 * @param listGeofenceIds Geofence IDs involved in the transition.
	 * @param boolEntered True if entered, false if exited.
	 */
	@Synchronized
	fun updateGeofenceTransition(objContext: Context, listGeofenceIds: List<String>, boolEntered: Boolean) {
		if (boolEntered) {
			setActiveGeofences.addAll(listGeofenceIds)
		} else {
			setActiveGeofences.removeAll(listGeofenceIds.toSet())
		}
		refreshDeviceState(objContext)
	}

	/**
	 * Records a Bluetooth ACL connection or disconnection event.
	 *
	 * @param objContext Android context.
	 * @param stringName Device friendly name (may be empty if unnamed).
	 * @param stringMac Device hardware MAC address.
	 * @param boolConnected True on connect, false on disconnect.
	 */
	@Synchronized
	fun updateBluetoothDevice(objContext: Context, stringName: String, stringMac: String, boolConnected: Boolean) {
		if (boolConnected) {
			if (stringName.isNotBlank()) setConnectedBtNames.add(stringName)
			if (stringMac.isNotBlank()) setConnectedBtMacs.add(stringMac)
		} else {
			if (stringName.isNotBlank()) setConnectedBtNames.remove(stringName)
			if (stringMac.isNotBlank()) setConnectedBtMacs.remove(stringMac)
		}
		refreshDeviceState(objContext)
	}

	/**
	 * Updates active media playback sessions reported by [TriggeredNotificationListenerService].
	 *
	 * @param objContext Android context.
	 * @param setPackages Packages currently in `STATE_PLAYING`.
	 * @param stringTitle Primary track title.
	 * @param stringArtist Primary track artist.
	 */
	@Synchronized
	fun updateMediaSessions(
		objContext: Context,
		setPackages: Set<String>,
		stringTitle: String,
		stringArtist: String
	) {
		setPlayingMediaPkgs.clear()
		setPlayingMediaPkgs.addAll(setPackages)
		stringActiveMediaTitle = stringTitle
		stringActiveMediaArtist = stringArtist
		refreshDeviceState(objContext)
	}

	/**
	 * Updates the set of packages with active notifications in the status bar.
	 *
	 * @param setPackages Active notification package names.
	 * @param boolRedactionDetected True if Android 15+ sensitive OTP redaction placeholder text was observed.
	 */
	@Synchronized
	fun updateActiveNotifications(setPackages: Set<String>, boolRedactionDetected: Boolean = false) {
		setActiveNotifPkgs.clear()
		setActiveNotifPkgs.addAll(setPackages)
		if (boolRedactionDetected) {
			boolSensitiveRedactionObserved = true
		}
	}

	/**
	 * Updates cached telephony call state and phone number.
	 *
	 * @param objContext Android context.
	 * @param intState TelephonyManager call state constant.
	 * @param stringNumber Associated phone number (if available).
	 */
	fun updateCallState(objContext: Context, intState: Int, stringNumber: String) {
		intLastCallState = intState
		stringActiveCallNumber = if (intState == TelephonyManager.CALL_STATE_IDLE) "" else stringNumber
		refreshDeviceState(objContext)
	}

	/**
	 * Requests a passive/cached last location fix from FusedLocationProviderClient when location permission is held.
	 *
	 * @param objContext Android context.
	 */
	@SuppressLint("MissingPermission")
	fun requestCachedLocationFix(objContext: Context) {
		if (!hasForegroundLocationPermission(objContext)) return
		try {
			LocationServices.getFusedLocationProviderClient(objContext).lastLocation
				.addOnSuccessListener { objLoc ->
					if (objLoc != null) {
						doubleCachedLat = objLoc.latitude
						doubleCachedLng = objLoc.longitude
						refreshDeviceState(objContext)
					}
				}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[DeviceStateCollector] Error reading cached location - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}


	// ---- Snapshot Collection ----

	/**
	 * Collects a fresh [LiveDeviceState] snapshot and publishes it to [flowLiveState].
	 *
	 * @param objContext Android context.
	 * @return Fresh [LiveDeviceState] snapshot.
	 */
	fun refreshDeviceState(objContext: Context): LiveDeviceState {
		val objSnapshot = collectSnapshot(objContext)
		_flowLiveState.value = objSnapshot
		return objSnapshot
	}

	@Suppress("DEPRECATION")
	@SuppressLint("MissingPermission")
	private fun collectSnapshot(objContext: Context): LiveDeviceState {
		val objPm = objContext.packageManager
		val boolSupportsTelephony = objPm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
		val boolSupportsBluetooth = objPm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
		val boolSupportsLocation = objPm.hasSystemFeature(PackageManager.FEATURE_LOCATION)

		val objPowerMgr = objContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
		val boolScreenOn = objPowerMgr?.isInteractive ?: true

		val objKeyguardMgr = objContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
		val boolDeviceUnlocked = !(objKeyguardMgr?.isDeviceLocked ?: false)

		// Battery & Charging
		val intentBattery = objContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
		val intLevel = intentBattery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
		val intScale = intentBattery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
		val intBatteryPct = if (intLevel >= 0 && intScale > 0) (intLevel * 100) / intScale else 100
		val intPlugged = intentBattery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
		val boolIsCharging = intPlugged != 0
		val chargingSource = when (intPlugged) {
			BatteryManager.BATTERY_PLUGGED_AC -> ChargingSourceFilter.AC
			BatteryManager.BATTERY_PLUGGED_USB -> ChargingSourceFilter.USB
			BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargingSourceFilter.WIRELESS
			BatteryManager.BATTERY_PLUGGED_DOCK -> ChargingSourceFilter.DOCK
			else -> ChargingSourceFilter.ANY
		}

		// Wi-Fi & Mobile Data
		val objWifiMgr = objContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
		val boolWifiEnabled = objWifiMgr?.isWifiEnabled ?: false
		val objConnMgr = objContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
		val objActiveNet = objConnMgr?.activeNetwork
		val objNetCaps = if (objActiveNet != null) objConnMgr.getNetworkCapabilities(objActiveNet) else null
		val boolWifiConnected = objNetCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
		val boolCellularConnected = objNetCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
		val boolRoaming = objNetCaps != null && !objNetCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

		var stringSsid = ""
		if (boolWifiConnected) {
			val objTransportWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				objNetCaps.transportInfo as? WifiInfo
			} else null
			val stringRawSsid = objTransportWifi?.ssid ?: runCatching { objWifiMgr?.connectionInfo?.ssid }.getOrNull().orEmpty()
			stringSsid = cleanWifiSsid(stringRawSsid)
		}

		// Bluetooth
		val objBtMgr = objContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
		val objBtAdapter = objBtMgr?.adapter
		val boolBtEnabled = objBtAdapter?.isEnabled == true
		val setBtNamesSnapshot: Set<String>
		val setBtMacsSnapshot: Set<String>
		synchronized(this) {
			if (boolBtEnabled && objBtMgr != null && hasBluetoothConnectPermission(objContext)) {
				runCatching {
					val listGatt = objBtMgr.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
					listGatt.forEach { objDev ->
						objDev.name?.takeIf { it.isNotBlank() }?.let { setConnectedBtNames.add(it) }
						objDev.address?.takeIf { it.isNotBlank() }?.let { setConnectedBtMacs.add(it) }
					}
				}
			} else if (!boolBtEnabled) {
				setConnectedBtNames.clear()
				setConnectedBtMacs.clear()
			}
			setBtNamesSnapshot = setConnectedBtNames.toSet()
			setBtMacsSnapshot = setConnectedBtMacs.toSet()
		}

		// Audio / Media
		val objAudioMgr = objContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
		val setMediaSnapshot: Set<String>
		val setNotifSnapshot: Set<String>
		synchronized(this) {
			setMediaSnapshot = setPlayingMediaPkgs.toSet()
			setNotifSnapshot = setActiveNotifPkgs.toSet()
		}
		val boolMediaPlaying = setMediaSnapshot.isNotEmpty() || (objAudioMgr?.isMusicActive == true)

		// Do Not Disturb
		val objNotifMgr = objContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
		val intDndFilter = objNotifMgr?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL

		// Telephony Call State
		val intResolvedCallState = if (boolSupportsTelephony && ContextCompat.checkSelfPermission(
				objContext,
				Manifest.permission.READ_PHONE_STATE
			) == PackageManager.PERMISSION_GRANTED
		) {
			runCatching {
				val objTel = objContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
				objTel?.callState ?: intLastCallState
			}.getOrDefault(intLastCallState)
		} else {
			intLastCallState
		}

		// Calendar
		val objCal = Calendar.getInstance()
		val intHour = objCal.get(Calendar.HOUR_OF_DAY)
		val intMinute = objCal.get(Calendar.MINUTE)
		val intCalDow = objCal.get(Calendar.DAY_OF_WEEK)
		val intIsoDow = if (intCalDow == Calendar.SUNDAY) 7 else intCalDow - 1
		val intMonth = objCal.get(Calendar.MONTH) + 1

		// Foreground App via UsageStatsManager (Banking-App Safe)
		val pairUsage = queryForegroundUsageInfo(objContext)

		val setGeoSnapshot = synchronized(this) { setActiveGeofences.toSet() }

		return LiveDeviceState(
			boolScreenOn = boolScreenOn,
			boolDeviceUnlocked = boolDeviceUnlocked,
			boolWifiEnabled = boolWifiEnabled,
			boolWifiConnected = boolWifiConnected,
			stringWifiSsid = stringSsid,
			boolMobileDataConnected = boolCellularConnected,
			boolIsRoaming = boolRoaming,
			boolBluetoothEnabled = boolBtEnabled,
			setConnectedBluetoothNames = setBtNamesSnapshot,
			setConnectedBluetoothMacs = setBtMacsSnapshot,
			boolMediaPlaying = boolMediaPlaying,
			setPlayingMediaPackages = setMediaSnapshot,
			stringActiveMediaTitle = stringActiveMediaTitle,
			stringActiveMediaArtist = stringActiveMediaArtist,
			intCallState = intResolvedCallState,
			stringActiveCallNumber = stringActiveCallNumber,
			intDndFilter = intDndFilter,
			intBatteryPercent = intBatteryPct,
			boolIsCharging = boolIsCharging,
			chargingSource = chargingSource,
			intHour = intHour,
			intMinute = intMinute,
			intDayOfWeek = intIsoDow,
			intMonth = intMonth,
			doubleLatitude = doubleCachedLat,
			doubleLongitude = doubleCachedLng,
			setActiveGeofenceIds = setGeoSnapshot,
			stringCurrentForegroundPackage = pairUsage.first,
			mapRecentForegroundTimestamps = pairUsage.second,
			setActiveNotificationPackages = setNotifSnapshot,
			boolSupportsLocation = boolSupportsLocation,
			boolSupportsTelephony = boolSupportsTelephony,
			boolSupportsBluetooth = boolSupportsBluetooth,
			boolSensitiveNotificationsRedacted = boolSensitiveRedactionObserved || !isSensitiveNotificationsAllowed(objContext)
		)
	}

	private fun queryForegroundUsageInfo(objContext: Context): Pair<String, Map<String, Long>> {
		if (!hasUsageStatsPermission(objContext)) {
			return "" to emptyMap()
		}
		return try {
			val objUsageMgr = objContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
				?: return "" to emptyMap()
			val longNow = System.currentTimeMillis()
			val objEvents = objUsageMgr.queryEvents(longNow - LONG_USAGE_LOOKBACK_MS, longNow)
			val objEvent = UsageEvents.Event()
			var stringCurrentFg = ""
			var longLatestResume = 0L
			val mapRecent = mutableMapOf<String, Long>()

			while (objEvents.hasNextEvent()) {
				objEvents.getNextEvent(objEvent)
				val stringPkg = objEvent.packageName ?: continue
				if (objEvent.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
					mapRecent[stringPkg] = objEvent.timeStamp
					if (objEvent.timeStamp >= longLatestResume) {
						longLatestResume = objEvent.timeStamp
						stringCurrentFg = stringPkg
					}
				} else if (objEvent.eventType == UsageEvents.Event.ACTIVITY_PAUSED) {
					val longPrev = mapRecent[stringPkg] ?: 0L
					if (objEvent.timeStamp > longPrev) {
						mapRecent[stringPkg] = objEvent.timeStamp
					}
				}
			}
			stringCurrentFg to mapRecent
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[DeviceStateCollector] Error querying UsageStatsManager - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			"" to emptyMap()
		}
	}

	/**
	 * Strips surrounding quotes and `<unknown ssid>` sentinel strings returned by Android Wi-Fi APIs.
	 *
	 * @param stringRawSsid Raw SSID from [WifiInfo].
	 * @return Clean SSID or empty string if unknown.
	 */
	fun cleanWifiSsid(stringRawSsid: String): String {
		val stringUnquoted = stringRawSsid.trim().removeSurrounding("\"")
		return if (stringUnquoted.equals("<unknown ssid>", ignoreCase = true)) "" else stringUnquoted
	}


	// ---- Permission Inspection Helpers ----

	/**
	 * Checks whether foreground location (`ACCESS_FINE_LOCATION` or `ACCESS_COARSE_LOCATION`) is granted.
	 *
	 * @param objContext Android context.
	 * @return True if granted.
	 */
	fun hasForegroundLocationPermission(objContext: Context): Boolean {
		return ContextCompat.checkSelfPermission(objContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
			ContextCompat.checkSelfPermission(objContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
	}

	/**
	 * Checks whether background location (`ACCESS_BACKGROUND_LOCATION`) is granted.
	 *
	 * @param objContext Android context.
	 * @return True if granted.
	 */
	fun hasBackgroundLocationPermission(objContext: Context): Boolean {
		return ContextCompat.checkSelfPermission(
			objContext,
			Manifest.permission.ACCESS_BACKGROUND_LOCATION
		) == PackageManager.PERMISSION_GRANTED
	}

	/**
	 * Checks whether `BLUETOOTH_CONNECT` is granted (required on Android 12+ / API 31+).
	 *
	 * @param objContext Android context.
	 * @return True if granted or API <= 30.
	 */
	fun hasBluetoothConnectPermission(objContext: Context): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			ContextCompat.checkSelfPermission(objContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
	}

	/**
	 * Checks whether Usage Access (`PACKAGE_USAGE_STATS`) is granted in system Special App Access.
	 *
	 * @param objContext Android context.
	 * @return True if Usage Access is enabled.
	 */
	fun hasUsageStatsPermission(objContext: Context): Boolean {
		return try {
			val objAppOps = objContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
			val intMode = objAppOps.unsafeCheckOpNoThrow(
				AppOpsManager.OPSTR_GET_USAGE_STATS,
				Process.myUid(),
				objContext.packageName
			)
			intMode == AppOpsManager.MODE_ALLOWED
		} catch (objException: Exception) {
			false
		}
	}

	/**
	 * Checks whether [TriggeredNotificationListenerService] is enabled in Notification Access settings.
	 *
	 * @param objContext Android context.
	 * @return True if Notification Listener access is active.
	 */
	fun hasNotificationListenerPermission(objContext: Context): Boolean {
		return try {
			val stringFlat = Settings.Secure.getString(objContext.contentResolver, "enabled_notification_listeners").orEmpty()
			val objComponent = ComponentName(objContext, TriggeredNotificationListenerService::class.java)
			stringFlat.contains(objComponent.flattenToString())
		} catch (objException: Exception) {
			false
		}
	}

	/**
	 * Checks whether Display Over Other Apps (`SYSTEM_ALERT_WINDOW`) is granted for background activity launch bridging.
	 *
	 * @param objContext Android context.
	 * @return True if overlay permission is granted.
	 */
	fun hasOverlayPermission(objContext: Context): Boolean {
		return Settings.canDrawOverlays(objContext)
	}

	/**
	 * Checks whether Do Not Disturb Policy Access (`ACCESS_NOTIFICATION_POLICY`) is granted.
	 *
	 * @param objContext Android context.
	 * @return True if granted.
	 */
	fun hasDndPolicyPermission(objContext: Context): Boolean {
		val objNotifMgr = objContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
		return objNotifMgr?.isNotificationPolicyAccessGranted == true
	}

	/**
	 * Checks whether Modify System Settings (`WRITE_SETTINGS`) is granted for changing screen brightness.
	 *
	 * @param objContext Android context.
	 * @return True if `Settings.System.canWrite` is allowed.
	 */
	fun hasWriteSettingsPermission(objContext: Context): Boolean {
		return Settings.System.canWrite(objContext)
	}

	/**
	 * Checks whether All Files Access (`MANAGE_EXTERNAL_STORAGE`) is granted on Android 11+.
	 *
	 * @return True if granted or running on Android 10 (API 29).
	 */
	fun hasAllFilesPermission(): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			Environment.isExternalStorageManager()
		} else {
			true
		}
	}

	/**
	 * Checks whether exact alarm scheduling is allowed on Android 12+.
	 *
	 * @param objContext Android context.
	 * @return True if exact alarms can be scheduled.
	 */
	fun hasExactAlarmPermission(objContext: Context): Boolean {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			val objAlarmMgr = objContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
			objAlarmMgr?.canScheduleExactAlarms() ?: true
		} else {
			true
		}
	}

	/**
	 * Checks whether the app is exempted from Doze battery optimizations.
	 *
	 * @param objContext Android context.
	 * @return True if ignoring battery optimizations.
	 */
	fun isIgnoringBatteryOptimizations(objContext: Context): Boolean {
		val objPowerMgr = objContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
		return objPowerMgr?.isIgnoringBatteryOptimizations(objContext.packageName) == true
	}

	/**
	 * Counts how many core automation permissions on the Settings screen are currently ungranted.
	 *
	 * @param objContext Android context.
	 * @return Number of ungranted core permission groups.
	 */
	fun countMissingCorePermissions(objContext: Context): Int {
		var intMissing = 0
		if (!hasDndPolicyPermission(objContext)) intMissing++
		if (!hasNotificationListenerPermission(objContext)) intMissing++
		if (!hasOverlayPermission(objContext)) intMissing++
		if (!hasUsageStatsPermission(objContext)) intMissing++
		if (!hasForegroundLocationPermission(objContext) || !hasBackgroundLocationPermission(objContext)) intMissing++
		if (!isIgnoringBatteryOptimizations(objContext)) intMissing++
		return intMissing
	}

	/**
	 * Checks whether Android 15+ (API 35+) allows unredacted sensitive (OTP) notifications via AppOps.
	 * Always returns true on Android 14 and below where OTP notification redaction does not exist.
	 *
	 * @param objContext Android context.
	 * @return True if sensitive notifications are unredacted, false if blocked on Android 15+.
	 */
	fun isSensitiveNotificationsAllowed(objContext: Context): Boolean {
		if (Build.VERSION.SDK_INT < 35) return true
		return try {
			val objAppOps = objContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return true
			val intMode = objAppOps.unsafeCheckOpNoThrow(
				"android:receive_sensitive_notifications",
				Process.myUid(),
				objContext.packageName
			)
			intMode == AppOpsManager.MODE_ALLOWED
		} catch (objException: Throwable) {
			!boolSensitiveRedactionObserved
		}
	}
}
