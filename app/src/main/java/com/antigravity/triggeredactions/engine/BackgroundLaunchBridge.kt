package com.antigravity.triggeredactions.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.antigravity.triggeredactions.R
import com.antigravity.triggeredactions.notification.ActionNotificationHelper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Launches target Activities, Apps, and Shortcuts reliably from background triggers on Android 10–16
 * without using `AccessibilityService` (keeping banking apps unflagged).
 *
 * When `SYSTEM_ALERT_WINDOW` is granted, briefly attaches a 1x1 non-focusable translucent overlay window
 * on the Main thread right before invoking `startActivity` to satisfy Android 15+ `BackgroundActivityStartController`
 * visible-window checks, then detaches the view 350ms later.
 */
object BackgroundLaunchBridge {

	private const val TAG = "BackgroundLaunchBridge"
	private val objMainHandler = Handler(Looper.getMainLooper())

	/**
	 * Launches [objIntent] from a background coroutine, returning true if the launch succeeded.
	 *
	 * @param objContext Android context.
	 * @param objIntent Configured Activity intent.
	 * @param stringFallbackTitle Title used if falling back to a full-screen heads-up notification.
	 * @return True if `startActivity` succeeded without throwing an exception.
	 */
	suspend fun launchActivityFromBackground(
		objContext: Context,
		objIntent: Intent,
		stringFallbackTitle: String = "Launch Triggered Activity"
	): Boolean {
		val objAppCtx = objContext.applicationContext
		objIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

		return suspendCancellableCoroutine { objCont ->
			objMainHandler.post {
				var objTempOverlay: View? = null
				val objWindowMgr = objAppCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

				try {
					if (Settings.canDrawOverlays(objAppCtx) && objWindowMgr != null) {
						val objView = View(objAppCtx)
						val objParams = WindowManager.LayoutParams(
							1,
							1,
							WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
							WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
								WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
								WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
							PixelFormat.TRANSLUCENT
						).apply {
							gravity = Gravity.TOP or Gravity.START
							x = 0
							y = 0
						}
						runCatching {
							objWindowMgr.addView(objView, objParams)
							objTempOverlay = objView
						}
					}

					objAppCtx.startActivity(objIntent)

					if (objTempOverlay != null && objWindowMgr != null) {
						val objToRemove = objTempOverlay
						objMainHandler.postDelayed({
							runCatching { objWindowMgr.removeViewImmediate(objToRemove) }
						}, 350L)
					}

					if (objCont.isActive) objCont.resume(true)
				} catch (objException: Exception) {
					objTempOverlay?.let { objView ->
						runCatching { objWindowMgr?.removeViewImmediate(objView) }
					}
					Log.e(
						TAG,
						"[BackgroundLaunchBridge] Error launching activity ${objIntent.component ?: objIntent.action} - ${objException.javaClass.simpleName}: ${objException.message}"
					)
					postFullScreenFallbackNotification(objAppCtx, objIntent, stringFallbackTitle)
					if (objCont.isActive) objCont.resume(false)
				}
			}
		}
	}

	@Suppress("MissingPermission")
	private fun postFullScreenFallbackNotification(
		objContext: Context,
		objIntent: Intent,
		stringTitle: String
	) {
		try {
			ActionNotificationHelper.ensureChannelsCreated(objContext)
			val intId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt().coerceAtLeast(7000)
			val piLaunch = PendingIntent.getActivity(
				objContext,
				intId,
				objIntent,
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
			val objNotif = NotificationCompat.Builder(objContext, ActionNotificationHelper.CHANNEL_USER_HIGH_ID)
				.setSmallIcon(R.drawable.ic_notification)
				.setContentTitle(stringTitle)
				.setContentText("Tap to open (grant 'Display over other apps' in Settings for instant launch)")
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setCategory(NotificationCompat.CATEGORY_CALL)
				.setAutoCancel(true)
				.setContentIntent(piLaunch)
				.setFullScreenIntent(piLaunch, true)
				.build()
			NotificationManagerCompat.from(objContext).notify(intId, objNotif)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[BackgroundLaunchBridge] Error posting fallback notification - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}
}
