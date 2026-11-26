package com.antigravity.triggeredactions.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives hardware Bluetooth ACL connect and disconnect broadcasts, updating [DeviceStateCollector]
 * and evaluating [com.antigravity.triggeredactions.model.SetTrigger.BluetoothTrigger] rules.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

	@SuppressLint("MissingPermission")
	override fun onReceive(objContext: Context, objIntent: Intent) {
		val stringAction = objIntent.action ?: return
		val boolConnected = when (stringAction) {
			BluetoothDevice.ACTION_ACL_CONNECTED -> true
			BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
			else -> return
		}

		val objDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			objIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
		} else {
			@Suppress("DEPRECATION")
			objIntent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
		}

		val boolHasBtPerm = DeviceStateCollector.hasBluetoothConnectPermission(objContext)
		val stringName = if (boolHasBtPerm) runCatching { objDevice?.name.orEmpty() }.getOrDefault("") else ""
		val stringMac = runCatching { objDevice?.address.orEmpty() }.getOrDefault("")

		val objAppCtx = objContext.applicationContext
		DeviceStateCollector.updateBluetoothDevice(objAppCtx, stringName, stringMac, boolConnected)

		val objPending = goAsync()
		CoroutineScope(Dispatchers.IO).launch {
			try {
				TriggerDispatcher.onBluetoothChanged(objAppCtx, boolConnected, stringName, stringMac)
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[BluetoothConnectionReceiver] Error handling BT broadcast $stringAction - ${objException.javaClass.simpleName}: ${objException.message}"
				)
			} finally {
				objPending.finish()
			}
		}
	}

	companion object {
		private const val TAG = "BluetoothReceiver"
	}
}
