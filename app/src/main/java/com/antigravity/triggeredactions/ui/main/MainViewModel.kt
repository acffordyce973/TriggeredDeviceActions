package com.antigravity.triggeredactions.ui.main

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.data.ExecutionLogStore
import com.antigravity.triggeredactions.engine.ConflictAndAuditDetector
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.engine.TriggerDispatcher
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.AppSettings
import com.antigravity.triggeredactions.model.ExecutionLogEntry
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.LiveDeviceState
import com.antigravity.triggeredactions.model.RuleAuditIssue
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.WebhookProfile
import com.antigravity.triggeredactions.model.formatBackupFilename
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aggregated UI state consumed by [MainScreen] and all 4 primary tabs.
 */
data class MainScreenUiState(
	val boolIsLoading: Boolean = true,
	val listActionSets: List<ActionSet> = emptyList(),
	val listGeofences: List<GeofenceArea> = emptyList(),
	val listWebhookProfiles: List<WebhookProfile> = emptyList(),
	val listShareXProfiles: List<ShareXProfile> = emptyList(),
	val objSettings: AppSettings = AppSettings(),
	val listExecutionLogs: List<ExecutionLogEntry> = emptyList(),
	val objLiveState: LiveDeviceState = LiveDeviceState(),
	val listAuditIssues: List<RuleAuditIssue> = emptyList()
)

/**
 * Primary Jetpack Compose ViewModel coordinating [ActionsRepository], [ExecutionLogStore],
 * [DeviceStateCollector], and [ConflictAndAuditDetector].
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

	private val objRepo = ActionsRepository.getInstance(application)
	private val objLogStore = ExecutionLogStore.getInstance(application)

	val flowUiState: StateFlow<MainScreenUiState> = combine(
		combine(
			objRepo.flowActionSets,
			objRepo.flowGeofences,
			objRepo.flowWebhookProfiles,
			objRepo.flowShareXProfiles
		) { listSets, listGeos, listWebhooks, listShareX ->
			Quadruple(listSets, listGeos, listWebhooks, listShareX)
		},
		objRepo.flowSettings,
		objLogStore.flowLogs,
		DeviceStateCollector.flowLiveState
	) { objQuad, objSettings, listLogs, objLive ->
		val listIssues = ConflictAndAuditDetector.auditActionSets(
			listActionSets = objQuad.first,
			listWebhookProfiles = objQuad.third,
			listShareXProfiles = objQuad.fourth,
			objContext = getApplication()
		)
		MainScreenUiState(
			boolIsLoading = false,
			listActionSets = objQuad.first,
			listGeofences = objQuad.second,
			listWebhookProfiles = objQuad.third,
			listShareXProfiles = objQuad.fourth,
			objSettings = objSettings,
			listExecutionLogs = listLogs,
			objLiveState = objLive,
			listAuditIssues = listIssues
		)
	}.stateIn(
		scope = viewModelScope,
		started = SharingStarted.WhileSubscribed(5000L),
		initialValue = MainScreenUiState()
	)

	init {
		refreshLiveDeviceState()
	}

	/**
	 * Refreshes live device state and requests a passive/cached GPS fix.
	 */
	fun refreshLiveDeviceState() {
		val objApp = getApplication<Application>()
		DeviceStateCollector.refreshDeviceState(objApp)
		DeviceStateCollector.requestCachedLocationFix(objApp)
	}


	// ---- Action Set Operations ----

	/**
	 * Saves a new or updated [ActionSet].
	 */
	fun saveActionSet(objSet: ActionSet) {
		viewModelScope.launch {
			objRepo.saveActionSet(objSet)
		}
	}

	/**
	 * Clones an existing [ActionSet] with a new ID and `(Copy)` suffix.
	 */
	fun duplicateActionSet(objSet: ActionSet) {
		viewModelScope.launch {
			val objCopy = objSet.copy(
				stringId = UUID.randomUUID().toString(),
				stringName = "${objSet.stringName} (Copy)",
				boolIsEnabled = false
			)
			objRepo.saveActionSet(objCopy)
		}
	}

	/**
	 * Deletes an [ActionSet] by ID.
	 */
	fun deleteActionSet(stringSetId: String) {
		viewModelScope.launch {
			objRepo.deleteActionSet(stringSetId)
		}
	}

	/**
	 * Toggles the enabled flag of an [ActionSet].
	 */
	fun toggleActionSetEnabled(objSet: ActionSet, boolEnabled: Boolean) {
		viewModelScope.launch {
			objRepo.saveActionSet(objSet.copy(boolIsEnabled = boolEnabled))
		}
	}

	/**
	 * Immediately executes an [ActionSet] for manual verification.
	 */
	fun testRunActionSet(objSet: ActionSet) {
		TriggerDispatcher.executeManualTestRun(getApplication(), objSet)
	}


	// ---- Geofence & Integration Profile Operations ----

	/**
	 * Saves a [GeofenceArea].
	 */
	fun saveGeofence(objGeofence: GeofenceArea) {
		viewModelScope.launch { objRepo.saveGeofence(objGeofence) }
	}

	/**
	 * Deletes a [GeofenceArea].
	 */
	fun deleteGeofence(stringGeofenceId: String) {
		viewModelScope.launch { objRepo.deleteGeofence(stringGeofenceId) }
	}

	/**
	 * Saves a [WebhookProfile].
	 */
	fun saveWebhookProfile(objProfile: WebhookProfile) {
		viewModelScope.launch { objRepo.saveWebhookProfile(objProfile) }
	}

	/**
	 * Deletes a [WebhookProfile].
	 */
	fun deleteWebhookProfile(stringProfileId: String) {
		viewModelScope.launch { objRepo.deleteWebhookProfile(stringProfileId) }
	}

	/**
	 * Saves a [ShareXProfile].
	 */
	fun saveShareXProfile(objProfile: ShareXProfile) {
		viewModelScope.launch { objRepo.saveShareXProfile(objProfile) }
	}

	/**
	 * Deletes a [ShareXProfile].
	 */
	fun deleteShareXProfile(stringProfileId: String) {
		viewModelScope.launch { objRepo.deleteShareXProfile(stringProfileId) }
	}

	/**
	 * Updates [AppSettings].
	 */
	fun updateSettings(transform: (AppSettings) -> AppSettings) {
		viewModelScope.launch { objRepo.updateAppSettings(transform) }
	}

	/**
	 * Clears execution history logs.
	 */
	fun clearExecutionLogs() {
		viewModelScope.launch { objLogStore.clearAllLogs() }
	}


	// ---- Configuration Export & Import ----

	/**
	 * Exports the JSON configuration to Downloads (and internal cache for FileProvider Share Sheet).
	 */
	fun exportConfiguration(onResult: (File?) -> Unit) {
		viewModelScope.launch {
			val fileShare = withContext(Dispatchers.IO) {
				try {
					objRepo.ensureLoaded()
					val objSettings = objRepo.flowSettings.value
					val stringFileName = formatBackupFilename(objSettings.stringBackupFilenameTemplate)
					val stringJson = objRepo.exportConfigurationJson(objSettings.boolIncludeSecretsInBackup)

					// Write to public Downloads via MediaStore (works on Android 10+ even without All Files Access)
					val objApp = getApplication<Application>()
					runCatching {
						val objValues = ContentValues().apply {
							put(MediaStore.Downloads.DISPLAY_NAME, stringFileName)
							put(MediaStore.Downloads.MIME_TYPE, "application/json")
							put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
							put(MediaStore.Downloads.IS_PENDING, 1)
						}
						val uriInserted = objApp.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, objValues)
						if (uriInserted != null) {
							objApp.contentResolver.openOutputStream(uriInserted)?.use {
								it.write(stringJson.toByteArray(Charsets.UTF_8))
							}
							objValues.clear()
							objValues.put(MediaStore.Downloads.IS_PENDING, 0)
							objApp.contentResolver.update(uriInserted, objValues, null, null)
						}
					}

					val dirExports = File(objApp.cacheDir, "exports").apply { mkdirs() }
					val fileCached = File(dirExports, stringFileName)
					fileCached.writeText(stringJson)
					fileCached
				} catch (objException: Exception) {
					Log.e(
						TAG,
						"[MainViewModel] Error exporting configuration - ${objException.javaClass.simpleName}: ${objException.message}"
					)
					null
				}
			}
			onResult(fileShare)
		}
	}

	/**
	 * Imports a full Triggered Actions configuration JSON string.
	 */
	fun importConfiguration(stringJson: String, onResult: (Boolean) -> Unit) {
		viewModelScope.launch {
			val boolOk = objRepo.importConfigurationJson(stringJson)
			onResult(boolOk)
		}
	}

	/**
	 * Imports `listGeofences` from a Triggered Live Wallpaper (or Triggered Actions) JSON backup.
	 */
	fun importGeofencesFromExternalBackup(stringJson: String, onResult: (Int) -> Unit) {
		viewModelScope.launch {
			val intCount = objRepo.importGeofencesFromExternalJson(stringJson)
			onResult(intCount)
		}
	}

	private data class Quadruple<A, B, C, D>(
		val first: A,
		val second: B,
		val third: C,
		val fourth: D
	)

	companion object {
		private const val TAG = "MainViewModel"
	}
}
