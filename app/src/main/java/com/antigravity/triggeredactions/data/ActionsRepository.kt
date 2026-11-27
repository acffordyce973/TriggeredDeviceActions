package com.antigravity.triggeredactions.data

import android.content.Context
import android.util.Log
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.ActionsConfigData
import com.antigravity.triggeredactions.model.AppRunningCheckMode
import com.antigravity.triggeredactions.model.AppSettings
import com.antigravity.triggeredactions.model.ConditionMatchMode
import com.antigravity.triggeredactions.model.ContactFilterMode
import com.antigravity.triggeredactions.model.DndConditionMode
import com.antigravity.triggeredactions.model.DndTargetMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.GeofenceTransitionMode
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.SmsRecipientMode
import com.antigravity.triggeredactions.model.SystemActivityPreset
import com.antigravity.triggeredactions.model.TextMatchMode
import com.antigravity.triggeredactions.model.WebhookProfile
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Minimal schema subset allowing direct import of `listGeofences` from a Triggered Live Wallpaper JSON backup.
 */
@Serializable
private data class ExternalGeofenceEnvelope(
	val listGeofences: List<GeofenceArea> = emptyList()
)

/**
 * Thread-safe persistent JSON repository for Action Sets, Geofences, Webhook/ShareX Profiles, and AppSettings.
 */
class ActionsRepository private constructor(private val appContext: Context) {

	private val fileConfig = File(appContext.filesDir, "actions_config.json")
	private val objMutex = Mutex()
	private val objRepositoryScope = CoroutineScope(Dispatchers.IO)

	@Volatile
	private var boolIsInitialized = false

	private val _flowActionSets = MutableStateFlow<List<ActionSet>>(emptyList())
	val flowActionSets: StateFlow<List<ActionSet>> = _flowActionSets.asStateFlow()

	private val _flowGeofences = MutableStateFlow<List<GeofenceArea>>(emptyList())
	val flowGeofences: StateFlow<List<GeofenceArea>> = _flowGeofences.asStateFlow()

	private val _flowWebhookProfiles = MutableStateFlow<List<WebhookProfile>>(emptyList())
	val flowWebhookProfiles: StateFlow<List<WebhookProfile>> = _flowWebhookProfiles.asStateFlow()

	private val _flowShareXProfiles = MutableStateFlow<List<ShareXProfile>>(emptyList())
	val flowShareXProfiles: StateFlow<List<ShareXProfile>> = _flowShareXProfiles.asStateFlow()

	private val _flowSettings = MutableStateFlow(AppSettings())
	val flowSettings: StateFlow<AppSettings> = _flowSettings.asStateFlow()

	private var onConfigChangedCallback: ((ActionsConfigData) -> Unit)? = null

	init {
		objRepositoryScope.launch {
			ensureLoaded()
		}
	}

	/**
	 * Registers a listener invoked whenever sets, geofences, or master settings change so schedulers stay synced.
	 *
	 * @param callback Listener receiving the updated [ActionsConfigData].
	 */
	fun setOnConfigChangedListener(callback: (ActionsConfigData) -> Unit) {
		onConfigChangedCallback = callback
	}

	/**
	 * Guarantees that persistent configuration has been loaded from disk before background receivers evaluate rules.
	 */
	suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
		if (boolIsInitialized) return@withContext
		objMutex.withLock {
			if (boolIsInitialized) return@withLock
			try {
				if (fileConfig.exists()) {
					val stringJson = fileConfig.readText()
					val objLoaded = jsonSerializer.decodeFromString<ActionsConfigData>(stringJson)
					applyConfigToFlows(objLoaded)
				} else {
					val objSampleConfig = buildDefaultSampleConfiguration()
					saveConfigInternal(objSampleConfig)
					applyConfigToFlows(objSampleConfig)
				}
				boolIsInitialized = true
			} catch (objException: Exception) {
				Log.e(
					TAG,
					"[ActionsRepository] Error loading config from ${fileConfig.absolutePath} - ${objException.javaClass.simpleName}: ${objException.message}"
				)
				boolIsInitialized = true
			}
		}
	}

	private fun applyConfigToFlows(objData: ActionsConfigData) {
		_flowActionSets.value = objData.listActionSets
		_flowGeofences.value = objData.listGeofences
		_flowWebhookProfiles.value = objData.listWebhookProfiles
		_flowShareXProfiles.value = objData.listShareXProfiles
		_flowSettings.value = objData.objSettings
	}

	private fun currentSnapshot(): ActionsConfigData {
		return ActionsConfigData(
			listActionSets = _flowActionSets.value,
			listGeofences = _flowGeofences.value,
			listWebhookProfiles = _flowWebhookProfiles.value,
			listShareXProfiles = _flowShareXProfiles.value,
			objSettings = _flowSettings.value
		)
	}

	private fun saveConfigInternal(objData: ActionsConfigData) {
		try {
			fileConfig.writeText(jsonSerializer.encodeToString(objData))
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ActionsRepository] Error writing config to ${fileConfig.absolutePath} - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	private fun notifySchedulers(objData: ActionsConfigData) {
		onConfigChangedCallback?.invoke(objData)
	}


	// ---- Action Sets CRUD ----

	/**
	 * Inserts or updates an [ActionSet] and persists configuration.
	 *
	 * @param objSet Action set to save.
	 */
	suspend fun saveActionSet(objSet: ActionSet) = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objSnapshot = objMutex.withLock {
			val listCurrent = _flowActionSets.value.toMutableList()
			val intIndex = listCurrent.indexOfFirst { it.stringId == objSet.stringId }
			if (intIndex >= 0) {
				listCurrent[intIndex] = objSet
			} else {
				listCurrent.add(objSet)
			}
			_flowActionSets.value = listCurrent
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
	}

	/**
	 * Deletes an [ActionSet] by its ID and persists configuration.
	 *
	 * @param stringSetId Target set identifier.
	 */
	suspend fun deleteActionSet(stringSetId: String) = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objSnapshot = objMutex.withLock {
			_flowActionSets.value = _flowActionSets.value.filterNot { it.stringId == stringSetId }
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
	}


	// ---- Geofences CRUD ----

	/**
	 * Inserts or updates a [GeofenceArea] and persists configuration.
	 *
	 * @param objGeofence Geofence area to save.
	 */
	suspend fun saveGeofence(objGeofence: GeofenceArea) = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objSnapshot = objMutex.withLock {
			val listCurrent = _flowGeofences.value.toMutableList()
			val intIndex = listCurrent.indexOfFirst { it.stringId == objGeofence.stringId }
			if (intIndex >= 0) {
				listCurrent[intIndex] = objGeofence
			} else {
				listCurrent.add(objGeofence)
			}
			_flowGeofences.value = listCurrent
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
	}

	/**
	 * Deletes a [GeofenceArea] by its ID.
	 *
	 * @param stringGeofenceId Target geofence identifier.
	 */
	suspend fun deleteGeofence(stringGeofenceId: String) = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objSnapshot = objMutex.withLock {
			_flowGeofences.value = _flowGeofences.value.filterNot { it.stringId == stringGeofenceId }
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
	}


	// ---- Webhook & ShareX Profiles CRUD ----

	/**
	 * Inserts or updates a [WebhookProfile].
	 *
	 * @param objProfile Webhook profile to persist.
	 */
	suspend fun saveWebhookProfile(objProfile: WebhookProfile) = withContext(Dispatchers.IO) {
		ensureLoaded()
		objMutex.withLock {
			val listCurrent = _flowWebhookProfiles.value.toMutableList()
			val intIndex = listCurrent.indexOfFirst { it.stringId == objProfile.stringId }
			if (intIndex >= 0) listCurrent[intIndex] = objProfile else listCurrent.add(objProfile)
			_flowWebhookProfiles.value = listCurrent
			saveConfigInternal(currentSnapshot())
		}
	}

	/**
	 * Deletes a [WebhookProfile] by ID.
	 *
	 * @param stringProfileId Target webhook profile ID.
	 */
	suspend fun deleteWebhookProfile(stringProfileId: String) = withContext(Dispatchers.IO) {
		ensureLoaded()
		objMutex.withLock {
			_flowWebhookProfiles.value = _flowWebhookProfiles.value.filterNot { it.stringId == stringProfileId }
			saveConfigInternal(currentSnapshot())
		}
	}

	/**
	 * Inserts or updates a [ShareXProfile].
	 *
	 * @param objProfile ShareX uploader profile to persist.
	 */
	suspend fun saveShareXProfile(objProfile: ShareXProfile) = withContext(Dispatchers.IO) {
		ensureLoaded()
		objMutex.withLock {
			val listCurrent = _flowShareXProfiles.value.toMutableList()
			val intIndex = listCurrent.indexOfFirst { it.stringId == objProfile.stringId }
			if (intIndex >= 0) listCurrent[intIndex] = objProfile else listCurrent.add(objProfile)
			_flowShareXProfiles.value = listCurrent
			saveConfigInternal(currentSnapshot())
		}
	}

	/**
	 * Deletes a [ShareXProfile] by ID.
	 *
	 * @param stringProfileId Target ShareX profile ID.
	 */
	suspend fun deleteShareXProfile(stringProfileId: String) = withContext(Dispatchers.IO) {
		ensureLoaded()
		objMutex.withLock {
			_flowShareXProfiles.value = _flowShareXProfiles.value.filterNot { it.stringId == stringProfileId }
			saveConfigInternal(currentSnapshot())
		}
	}

	/**
	 * Atomically transforms and persists [AppSettings].
	 *
	 * @param transform Pure function producing updated [AppSettings].
	 */
	suspend fun updateAppSettings(transform: (AppSettings) -> AppSettings) = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objSnapshot = objMutex.withLock {
			val objUpdatedSettings = transform(_flowSettings.value)
			if (objUpdatedSettings == _flowSettings.value) return@withContext
			_flowSettings.value = objUpdatedSettings
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
	}


	// ---- Export & Import (Full Config + Triggered Live Wallpaper Geofences) ----

	/**
	 * Serializes the complete configuration to pretty-printed JSON, optionally redacting webhook/ShareX secrets.
	 *
	 * @param boolIncludeSecrets Whether to keep webhook URLs and upload headers intact.
	 * @return Serialized JSON string.
	 */
	fun exportConfigurationJson(boolIncludeSecrets: Boolean = _flowSettings.value.boolIncludeSecretsInBackup): String {
		val objBase = currentSnapshot()
		val objExportable = if (boolIncludeSecrets) {
			objBase
		} else {
			objBase.copy(
				listWebhookProfiles = objBase.listWebhookProfiles.map {
					it.copy(stringUrl = "REDACTED", mapHeaders = emptyMap())
				},
				listShareXProfiles = objBase.listShareXProfiles.map {
					it.copy(mapHeaders = emptyMap(), mapArguments = emptyMap())
				}
			)
		}
		return jsonSerializer.encodeToString(objExportable)
	}

	/**
	 * Imports a full Triggered Actions JSON backup and synchronizes schedulers.
	 *
	 * @param stringJson Raw JSON text to decode.
	 * @return True if import succeeded, false if JSON was invalid.
	 */
	suspend fun importConfigurationJson(stringJson: String): Boolean = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objImported = try {
			jsonSerializer.decodeFromString<ActionsConfigData>(stringJson)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ActionsRepository] Error decoding imported configuration JSON - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			return@withContext false
		}

		objMutex.withLock {
			applyConfigToFlows(objImported)
			saveConfigInternal(objImported)
		}
		notifySchedulers(objImported)
		true
	}

	/**
	 * Merges `listGeofences` from any compatible JSON backup (including Triggered Live Wallpaper exports).
	 *
	 * @param stringJson Raw JSON text containing `listGeofences`.
	 * @return Number of geofences imported/updated, or `-1` on parse error.
	 */
	suspend fun importGeofencesFromExternalJson(stringJson: String): Int = withContext(Dispatchers.IO) {
		ensureLoaded()
		val objEnvelope = try {
			jsonSerializer.decodeFromString<ExternalGeofenceEnvelope>(stringJson)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ActionsRepository] Error decoding geofences JSON - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			return@withContext -1
		}

		if (objEnvelope.listGeofences.isEmpty()) return@withContext 0

		val objSnapshot = objMutex.withLock {
			val listMerged = _flowGeofences.value.toMutableList()
			for (objGeo in objEnvelope.listGeofences) {
				val intExisting = listMerged.indexOfFirst {
					it.stringId == objGeo.stringId || it.stringName.equals(objGeo.stringName, ignoreCase = true)
				}
				if (intExisting >= 0) {
					listMerged[intExisting] = objGeo
				} else {
					listMerged.add(objGeo)
				}
			}
			_flowGeofences.value = listMerged
			val objUpdated = currentSnapshot()
			saveConfigInternal(objUpdated)
			objUpdated
		}
		notifySchedulers(objSnapshot)
		objEnvelope.listGeofences.size
	}


	// ---- Built-In Disabled Example Action Sets ----

	companion object {
		private const val TAG = "ActionsRepository"

		val jsonSerializer = Json {
			ignoreUnknownKeys = true
			prettyPrint = true
			encodeDefaults = true
			classDiscriminator = "type"
		}

		@Volatile
		private var objInstance: ActionsRepository? = null

		/**
		 * Returns the singleton [ActionsRepository] instance.
		 *
		 * @param objContext Android context.
		 * @return Singleton [ActionsRepository].
		 */
		fun getInstance(objContext: Context): ActionsRepository {
			return objInstance ?: synchronized(this) {
				objInstance ?: ActionsRepository(objContext.applicationContext).also { objInstance = it }
			}
		}

		/**
		 * Builds the initial out-of-the-box configuration containing a default Home geofence and
		 * 6 pre-built starter templates (disabled by default until customized and enabled by the user).
		 *
		 * @return Default [ActionsConfigData].
		 */
		fun buildDefaultSampleConfiguration(): ActionsConfigData {
			val stringHomeGeoId = "geo_home"
			val objHomeGeo = GeofenceArea(
				stringId = stringHomeGeoId,
				stringName = "Home",
				doubleLatitude = 51.5074,
				doubleLongitude = -0.1278,
				floatRadiusMeters = 200f
			)

			val listStarterSets = listOf(
				ActionSet(
					stringId = "template_dnd_callback",
					stringName = "DnD Repeat-Caller Breakthrough",
					stringDescription = "On missed call from a known contact while DnD is on: texts caller, opens a 20s DnD-off window so a callback rings, then restores DnD.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.MissedCallTrigger(
							contactFilterMode = ContactFilterMode.IN_CONTACTS
						)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = listOf(
						SetCondition.DndStatusCondition(dndConditionMode = DndConditionMode.ANY_DND_ON)
					),
					listActions = listOf(
						SetAction.SendSmsAction(
							recipientMode = SmsRecipientMode.TRIGGER_CALLER_OR_SENDER,
							stringMessageTemplate = "My device is currently in Do Not Disturb. Call again within 20 seconds and it will ring.",
							intRecipientCooldownSeconds = 60
						),
						SetAction.SetDndAction(
							targetMode = DndTargetMode.OFF_ALLOW_ALL,
							boolSetRingerLoudWhenOff = true
						),
						SetAction.WaitDelayAction(intDelaySeconds = 20, boolWaitUntilCallEndsFirst = false),
						SetAction.SetDndAction(targetMode = DndTargetMode.PRIORITY_ONLY)
					)
				),
				ActionSet(
					stringId = "template_morning_home_loud",
					stringName = "Morning Home: Loud & DnD Off",
					stringDescription = "At 08:00 whilst inside the Home geofence, disables Do Not Disturb and sets the ringer to Loud.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.TimeScheduleTrigger(
							boolIsInterval = false,
							intHour = 8,
							intMinute = 0
						)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = listOf(
						SetCondition.GeofenceCondition(stringGeofenceId = stringHomeGeoId, boolInside = true)
					),
					listActions = listOf(
						SetAction.SetDndAction(
							targetMode = DndTargetMode.OFF_ALLOW_ALL,
							boolSetRingerLoudWhenOff = true
						)
					)
				),
				ActionSet(
					stringId = "template_night_dnd_on",
					stringName = "Night: Do Not Disturb On",
					stringDescription = "At 22:00 every night regardless of location, enables Do Not Disturb.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.TimeScheduleTrigger(
							boolIsInterval = false,
							intHour = 22,
							intMinute = 0
						)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = emptyList(),
					listActions = listOf(
						SetAction.SetDndAction(targetMode = DndTargetMode.PRIORITY_ONLY)
					)
				),
				ActionSet(
					stringId = "template_charge_home_open_app",
					stringName = "Plug In at Home -> Open App",
					stringDescription = "Opens a chosen app automatically when plugging in power inside the Home geofence.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.ChargingStateTrigger(boolOnChargingConnected = true)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = listOf(
						SetCondition.GeofenceCondition(stringGeofenceId = stringHomeGeoId, boolInside = true)
					),
					listActions = listOf(
						SetAction.StartAppAction(stringPackageName = "com.android.deskclock")
					)
				),
				ActionSet(
					stringId = "template_unplug_home_app_settings",
					stringName = "Unplug at Home -> App Info Settings",
					stringDescription = "When unplugging from power at Home while the target app is open, opens its OS App Info page to Force Stop or Clear Cache.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.ChargingStateTrigger(boolOnChargingConnected = false)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = listOf(
						SetCondition.GeofenceCondition(stringGeofenceId = stringHomeGeoId, boolInside = true),
						SetCondition.AppRunningCondition(
							stringPackageName = "com.android.chrome",
							checkMode = AppRunningCheckMode.RECENT_FOREGROUND,
							intRecentWindowSeconds = 20
						)
					),
					listActions = listOf(
						SetAction.StartAppActivityAction(
							systemPreset = SystemActivityPreset.APP_DETAILS_SETTINGS,
							stringTargetPackage = "com.android.chrome"
						)
					)
				),
				ActionSet(
					stringId = "template_otp_discord_webhook",
					stringName = "Forward OTP Notification to Discord",
					stringDescription = "Detects one-time passcodes in incoming notifications (excluding Discord) and forwards the extracted code via Discord webhook.",
					boolIsEnabled = false,
					listTriggers = listOf(
						SetTrigger.NotificationReceivedTrigger(
							stringExcludedPackages = "com.discord",
							bodyMatchMode = TextMatchMode.OTP_PRESET
						)
					),
					conditionMatchMode = ConditionMatchMode.ALL_AND,
					listConditions = emptyList(),
					listActions = listOf(
						SetAction.SendWebhookAction(
							boolIsDiscordFormat = true,
							stringDiscordEmbedTitle = "OTP Code from {notif_app}",
							stringBodyOrMessageTemplate = "Code: **`{match}`**\nSource: {notif_title} — {notif_text}"
						)
					)
				)
			)

			return ActionsConfigData(
				listActionSets = listStarterSets,
				listGeofences = listOf(objHomeGeo),
				objSettings = AppSettings()
			)
		}
	}
}
