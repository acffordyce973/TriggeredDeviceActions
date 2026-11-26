package com.antigravity.triggeredactions.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.triggeredactions.data.InstalledAppsRepository
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.model.NotificationDismissReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * System-bound NotificationListenerService handling:
 * 1. Notification Received & Notification Dismissed triggers (with Android 15 OTP redaction detection).
 * 2. Rich MediaSessionManager playback & track metadata tracking.
 * 3. Hosting [DynamicEventMonitor] with zero extra ForegroundService notification overhead when bound.
 */
class TriggeredNotificationListenerService : NotificationListenerService() {

	private val objScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val mapRegisteredControllers = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()
	private var boolLastAnyMediaPlaying: Boolean = false
	private var stringLastTrackSignature: String = ""

	private val objSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { listControllers ->
		attachMediaControllers(listControllers.orEmpty())
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		boolIsConnected = true
		DynamicEventMonitor.ensureRegistered(applicationContext)
		refreshActiveNotificationPackages()
		registerMediaSessionTracking()
	}

	override fun onListenerDisconnected() {
		super.onListenerDisconnected()
		boolIsConnected = false
		unregisterMediaSessionTracking()
	}

	override fun onNotificationPosted(objSbn: StatusBarNotification?) {
		val sbn = objSbn ?: return
		val stringPkg = sbn.packageName ?: return
		if (stringPkg == packageName) return

		val objNotif = sbn.notification ?: return
		val objExtras = objNotif.extras
		val stringTitle = objExtras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
		val stringBigText = objExtras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
		val stringShortText = objExtras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
		val stringBody = stringBigText.ifBlank { stringShortText }

		val boolRedacted = isSystemRedactedPlaceholder(stringBody) || isSystemRedactedPlaceholder(stringTitle)
		refreshActiveNotificationPackages(boolRedacted)

		val boolOngoing = sbn.isOngoing || (objNotif.flags and Notification.FLAG_ONGOING_EVENT) != 0
		val boolSummary = (objNotif.flags and Notification.FLAG_GROUP_SUMMARY) != 0
		val stringAppLabel = InstalledAppsRepository.getAppLabel(applicationContext, stringPkg)

		objScope.launch {
			TriggerDispatcher.onNotificationPosted(
				objContext = applicationContext,
				stringPackage = stringPkg,
				stringAppName = stringAppLabel,
				stringTitle = stringTitle,
				stringBody = stringBody,
				boolIsOngoing = boolOngoing,
				boolIsGroupSummary = boolSummary
			)
		}
	}

	override fun onNotificationRemoved(
		objSbn: StatusBarNotification?,
		objRankingMap: RankingMap?,
		intReason: Int
	) {
		val sbn = objSbn ?: return
		val stringPkg = sbn.packageName ?: return
		if (stringPkg == packageName) return

		refreshActiveNotificationPackages()

		val objNotif = sbn.notification
		val objExtras = objNotif?.extras
		val stringTitle = objExtras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
		val stringBigText = objExtras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
		val stringShortText = objExtras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
		val stringBody = stringBigText.ifBlank { stringShortText }
		val stringAppLabel = InstalledAppsRepository.getAppLabel(applicationContext, stringPkg)

		val dismissReason = when (intReason) {
			REASON_CLICK -> NotificationDismissReason.USER_CLICKED
			REASON_CANCEL, REASON_CANCEL_ALL, REASON_CHANNEL_BANNED -> NotificationDismissReason.USER_DISMISSED
			REASON_APP_CANCEL, REASON_APP_CANCEL_ALL -> NotificationDismissReason.APP_CANCELLED
			else -> NotificationDismissReason.ANY
		}

		objScope.launch {
			TriggerDispatcher.onNotificationDismissed(
				objContext = applicationContext,
				stringPackage = stringPkg,
				stringAppName = stringAppLabel,
				stringTitle = stringTitle,
				stringBody = stringBody,
				dismissReason = dismissReason
			)
		}
	}


	// ---- Redaction & Active Status Bar Helpers ----

	private fun refreshActiveNotificationPackages(boolRedacted: Boolean = false) {
		try {
			val setPkgs = activeNotifications?.mapNotNull { it.packageName }?.toSet().orEmpty()
			DeviceStateCollector.updateActiveNotifications(setPkgs, boolRedacted)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggeredNotificationListenerService] Error reading active notifications - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun isSystemRedactedPlaceholder(stringText: String): Boolean {
		if (stringText.isBlank()) return false
		if (stringText.contains("Sensitive notification content hidden", ignoreCase = true)) return true
		return try {
			val intResId = Resources.getSystem().getIdentifier("redacted_notification_message", "string", "android")
			if (intResId != 0) {
				val stringLocalized = Resources.getSystem().getString(intResId)
				stringLocalized.isNotBlank() && stringText.trim() == stringLocalized.trim()
			} else false
		} catch (objException: Exception) {
			false
		}
	}


	// ---- MediaSessionManager Integration ----

	private fun registerMediaSessionTracking() {
		try {
			val objMediaMgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
			val objComponent = ComponentName(this, TriggeredNotificationListenerService::class.java)
			objMediaMgr.addOnActiveSessionsChangedListener(objSessionsListener, objComponent)
			val listInitial = objMediaMgr.getActiveSessions(objComponent)
			attachMediaControllers(listInitial)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggeredNotificationListenerService] Error registering MediaSession listener - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun unregisterMediaSessionTracking() {
		try {
			val objMediaMgr = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
			objMediaMgr?.removeOnActiveSessionsChangedListener(objSessionsListener)
			synchronized(mapRegisteredControllers) {
				mapRegisteredControllers.values.forEach { (objCtrl, objCb) ->
					runCatching { objCtrl.unregisterCallback(objCb) }
				}
				mapRegisteredControllers.clear()
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[TriggeredNotificationListenerService] Error unregistering MediaSession listener - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun attachMediaControllers(listControllers: List<MediaController>) {
		synchronized(mapRegisteredControllers) {
			mapRegisteredControllers.values.forEach { (objCtrl, objCb) ->
				runCatching { objCtrl.unregisterCallback(objCb) }
			}
			mapRegisteredControllers.clear()

			for (objController in listControllers) {
				val stringKey = "${objController.packageName}:${objController.sessionToken.hashCode()}"
				val objCallback = object : MediaController.Callback() {
					override fun onPlaybackStateChanged(objState: PlaybackState?) {
						evaluateAllMediaControllers(objController.packageName)
					}

					override fun onMetadataChanged(objMetadata: MediaMetadata?) {
						evaluateAllMediaControllers(objController.packageName)
					}
				}
				runCatching {
					objController.registerCallback(objCallback)
					mapRegisteredControllers[stringKey] = objController to objCallback
				}
			}
		}
		evaluateAllMediaControllers("")
	}

	private fun evaluateAllMediaControllers(stringChangedPkg: String) {
		val listSnapshot = synchronized(mapRegisteredControllers) {
			mapRegisteredControllers.values.map { it.first }
		}

		val setPlayingPkgs = mutableSetOf<String>()
		var stringPrimaryPkg = stringChangedPkg
		var stringTitle = ""
		var stringArtist = ""

		for (objCtrl in listSnapshot) {
			if (objCtrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
				setPlayingPkgs.add(objCtrl.packageName)
				if (stringTitle.isEmpty() || objCtrl.packageName == stringChangedPkg) {
					stringPrimaryPkg = objCtrl.packageName
					val objMeta = objCtrl.metadata
					stringTitle = objMeta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
					stringArtist = objMeta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
				}
			}
		}

		DeviceStateCollector.updateMediaSessions(applicationContext, setPlayingPkgs, stringTitle, stringArtist)

		val boolAnyPlayingNow = setPlayingPkgs.isNotEmpty()
		val stringNewTrackSig = "$stringPrimaryPkg|$stringTitle|$stringArtist"

		if (boolAnyPlayingNow != boolLastAnyMediaPlaying) {
			boolLastAnyMediaPlaying = boolAnyPlayingNow
			stringLastTrackSigIfPlaying(boolAnyPlayingNow, stringNewTrackSig)
			objScope.launch {
				TriggerDispatcher.onMediaPlaybackEvent(
					objContext = applicationContext,
					boolPlaybackActive = boolAnyPlayingNow,
					boolIsTrackChangeOnly = false,
					stringPackage = stringPrimaryPkg,
					stringTitle = stringTitle,
					stringArtist = stringArtist
				)
			}
		} else if (boolAnyPlayingNow && stringNewTrackSig != stringLastTrackSignature && stringTitle.isNotBlank()) {
			stringLastTrackSignature = stringNewTrackSig
			objScope.launch {
				TriggerDispatcher.onMediaPlaybackEvent(
					objContext = applicationContext,
					boolPlaybackActive = true,
					boolIsTrackChangeOnly = true,
					stringPackage = stringPrimaryPkg,
					stringTitle = stringTitle,
					stringArtist = stringArtist
				)
			}
		}
	}

	private fun stringLastTrackSigIfPlaying(boolPlaying: Boolean, stringSig: String) {
		stringLastTrackSignature = if (boolPlaying) stringSig else ""
	}

	companion object {
		private const val TAG = "TriggeredNLS"

		@Volatile
		var boolIsConnected: Boolean = false
			private set
	}
}
