package com.antigravity.triggeredactions.receiver

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.antigravity.triggeredactions.data.ActionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles interactive buttons inside notifications posted by Triggered Actions (e.g., copying an extracted
 * OTP/URL to clipboard or pausing the automation engine).
 */
class NotificationActionReceiver : BroadcastReceiver() {

	override fun onReceive(objContext: Context, objIntent: Intent) {
		when (objIntent.action) {
			ACTION_COPY_TEXT -> {
				val stringText = objIntent.getStringExtra(EXTRA_COPY_TEXT).orEmpty()
				if (stringText.isNotEmpty()) {
					val objClipMgr = objContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
					objClipMgr?.setPrimaryClip(ClipData.newPlainText("Triggered Actions", stringText))
					Toast.makeText(objContext, "Copied to clipboard", Toast.LENGTH_SHORT).show()
				}
			}
			ACTION_TOGGLE_ENGINE -> {
				val objPending = goAsync()
				val objAppCtx = objContext.applicationContext
				CoroutineScope(Dispatchers.IO).launch {
					try {
						val objRepo = ActionsRepository.getInstance(objAppCtx)
						objRepo.ensureLoaded()
						val boolNext = !objRepo.flowSettings.value.boolMasterEngineEnabled
						objRepo.updateAppSettings { it.copy(boolMasterEngineEnabled = boolNext) }
					} finally {
						objPending.finish()
					}
				}
			}
		}
	}

	companion object {
		const val ACTION_COPY_TEXT = "com.antigravity.triggeredactions.ACTION_COPY_TEXT"
		const val ACTION_TOGGLE_ENGINE = "com.antigravity.triggeredactions.ACTION_TOGGLE_ENGINE"
		const val EXTRA_COPY_TEXT = "extra_copy_text"
	}
}
