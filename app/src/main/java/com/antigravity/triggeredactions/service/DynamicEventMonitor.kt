package com.antigravity.triggeredactions.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Singleton runtime registrar for dynamic-only Android broadcasts (`SCREEN_ON/OFF`, `USER_PRESENT`,
 * `POWER_CONNECTED/DISCONNECTED`, `BATTERY_CHANGED`) and [ConnectivityManager.NetworkCallback] events.
 * Reference-guarded so hosting inside both NotificationListenerService and TriggeredMonitorService never duplicates events.
 */
object DynamicEventMonitor {

	private const val TAG = "DynamicEventMonitor"

	private val objScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	@Volatile
	private var boolIsRegistered = false

	private var intLastBatteryPct: Int = -1
	private var boolLastWifiConnected: Boolean? = null
	private var stringLastWifiSsid: String = ""
	private var boolLastCellularConnected: Boolean? = null
	private var boolLastDeviceUnlocked: Boolean? = null

	private val objBroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(objContext: Context, objIntent: Intent) {
			val stringAction = objIntent.action ?: return
			val objAppCtx = objContext.applicationContext
			when (stringAction) {
				Intent.ACTION_SCREEN_ON -> {
					DeviceStateCollector.refreshDeviceState(objAppCtx)
					objScope.launch {
						TriggerDispatcher.onScreenStateChanged(objAppCtx, boolScreenOn = true)
					}
				}
				Intent.ACTION_SCREEN_OFF -> {
					DeviceStateCollector.refreshDeviceState(objAppCtx)
					objScope.launch {
						TriggerDispatcher.onScreenStateChanged(objAppCtx, boolScreenOn = false)
						// Check immediate or delayed keyguard lock after screen turns off
						delay(1200L)
						val objKeyguard = objAppCtx.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
						val boolLockedNow = objKeyguard?.isDeviceLocked == true
						if (boolLockedNow && boolLastDeviceUnlocked != false) {
							boolLastDeviceUnlocked = false
							DeviceStateCollector.refreshDeviceState(objAppCtx)
							TriggerDispatcher.onDeviceLockChanged(objAppCtx, boolUnlocked = false)
						}
					}
				}
				Intent.ACTION_USER_PRESENT -> {
					boolLastDeviceUnlocked = true
					DeviceStateCollector.refreshDeviceState(objAppCtx)
					objScope.launch {
						TriggerDispatcher.onDeviceLockChanged(objAppCtx, boolUnlocked = true)
					}
				}
				Intent.ACTION_POWER_CONNECTED -> {
					val objState = DeviceStateCollector.refreshDeviceState(objAppCtx)
					objScope.launch {
						TriggerDispatcher.onChargingStateChanged(objAppCtx, boolConnected = true, objState.chargingSource)
					}
				}
				Intent.ACTION_POWER_DISCONNECTED -> {
					DeviceStateCollector.refreshDeviceState(objAppCtx)
					objScope.launch {
						TriggerDispatcher.onChargingStateChanged(objAppCtx, boolConnected = false, ChargingSourceFilter.ANY)
					}
				}
				Intent.ACTION_BATTERY_CHANGED -> {
					val intLevel = objIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
					val intScale = objIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
					if (intLevel >= 0 && intScale > 0) {
						val intPct = (intLevel * 100) / intScale
						val intPrev = intLastBatteryPct
						intLastBatteryPct = intPct
						if (intPrev in 0..100 && intPrev != intPct) {
							objScope.launch {
								TriggerDispatcher.onBatteryLevelChanged(objAppCtx, intPrev, intPct)
							}
						}
					}
				}
			}
		}
	}

	private var objNetworkCallback: ConnectivityManager.NetworkCallback? = null

	/**
	 * Ensures dynamic broadcast receivers and network callbacks are actively registered on the application context.
	 *
	 * @param objContext Android context.
	 */
	@Synchronized
	fun ensureRegistered(objContext: Context) {
		if (boolIsRegistered) return
		val objAppCtx = objContext.applicationContext

		try {
			val objInitialState = DeviceStateCollector.refreshDeviceState(objAppCtx)
			intLastBatteryPct = objInitialState.intBatteryPercent
			boolLastWifiConnected = objInitialState.boolWifiConnected
			stringLastWifiSsid = objInitialState.stringWifiSsid
			boolLastCellularConnected = objInitialState.boolMobileDataConnected
			boolLastDeviceUnlocked = objInitialState.boolDeviceUnlocked

			val objFilter = IntentFilter().apply {
				addAction(Intent.ACTION_SCREEN_ON)
				addAction(Intent.ACTION_SCREEN_OFF)
				addAction(Intent.ACTION_USER_PRESENT)
				addAction(Intent.ACTION_POWER_CONNECTED)
				addAction(Intent.ACTION_POWER_DISCONNECTED)
				addAction(Intent.ACTION_BATTERY_CHANGED)
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				objAppCtx.registerReceiver(objBroadcastReceiver, objFilter, Context.RECEIVER_EXPORTED)
			} else {
				objAppCtx.registerReceiver(objBroadcastReceiver, objFilter)
			}

			registerConnectivityCallback(objAppCtx)
			boolIsRegistered = true
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[DynamicEventMonitor] Error registering dynamic monitors - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun registerConnectivityCallback(objAppCtx: Context) {
		val objConnMgr = objAppCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
		val intFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO
		} else 0

		val objCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && intFlags != 0) {
			object : ConnectivityManager.NetworkCallback(intFlags) {
				override fun onCapabilitiesChanged(objNetwork: Network, objCaps: NetworkCapabilities) {
					handleConnectivityEvaluation(objAppCtx, objCaps)
				}
				override fun onLost(objNetwork: Network) {
					handleConnectivityEvaluation(objAppCtx, null)
				}
			}
		} else {
			object : ConnectivityManager.NetworkCallback() {
				override fun onCapabilitiesChanged(objNetwork: Network, objCaps: NetworkCapabilities) {
					handleConnectivityEvaluation(objAppCtx, objCaps)
				}
				override fun onLost(objNetwork: Network) {
					handleConnectivityEvaluation(objAppCtx, null)
				}
			}
		}

		val objReq = NetworkRequest.Builder()
			.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
			.build()
		objConnMgr.registerNetworkCallback(objReq, objCallback)
		objNetworkCallback = objCallback
	}

	private fun handleConnectivityEvaluation(objAppCtx: Context, objIncomingCaps: NetworkCapabilities?) {
		val objState = DeviceStateCollector.refreshDeviceState(objAppCtx)
		val stringCallbackSsid = (objIncomingCaps?.transportInfo as? WifiInfo)?.ssid
			?.let { DeviceStateCollector.cleanWifiSsid(it) }
			.orEmpty()
		val stringEffectiveSsid = stringCallbackSsid.ifBlank { objState.stringWifiSsid }

		val boolWifiNow = objState.boolWifiConnected
		if (boolLastWifiConnected != null && (boolLastWifiConnected != boolWifiNow ||
				(boolWifiNow && stringEffectiveSsid.isNotBlank() && stringEffectiveSsid != stringLastWifiSsid))
		) {
			boolLastWifiConnected = boolWifiNow
			stringLastWifiSsid = if (boolWifiNow) stringEffectiveSsid else ""
			objScope.launch {
				TriggerDispatcher.onWifiChanged(objAppCtx, boolWifiNow, stringEffectiveSsid)
			}
		} else {
			boolLastWifiConnected = boolWifiNow
			stringLastWifiSsid = if (boolWifiNow) stringEffectiveSsid else ""
		}

		val boolCellNow = objState.boolMobileDataConnected
		if (boolLastCellularConnected != null && boolLastCellularConnected != boolCellNow) {
			boolLastCellularConnected = boolCellNow
			objScope.launch {
				TriggerDispatcher.onMobileDataChanged(objAppCtx, boolCellNow, objState.boolIsRoaming)
			}
		} else {
			boolLastCellularConnected = boolCellNow
		}
	}

	/**
	 * Unregisters dynamic receivers and network callbacks when monitoring is completely shut down.
	 *
	 * @param objContext Android context.
	 */
	@Synchronized
	fun unregisterAll(objContext: Context) {
		if (!boolIsRegistered) return
		val objAppCtx = objContext.applicationContext
		runCatching { objAppCtx.unregisterReceiver(objBroadcastReceiver) }
		objNetworkCallback?.let { objCb ->
			val objConnMgr = objAppCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			runCatching { objConnMgr?.unregisterNetworkCallback(objCb) }
		}
		objNetworkCallback = null
		boolIsRegistered = false
	}
}
