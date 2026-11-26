package com.antigravity.triggeredactions

import android.app.Application
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.notification.ActionNotificationHelper
import com.antigravity.triggeredactions.scheduler.ActionAlarmScheduler
import com.antigravity.triggeredactions.scheduler.ActionGeofenceScheduler
import com.antigravity.triggeredactions.service.TriggeredMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entrypoint wiring repository configuration changes to background schedulers and monitor services.
 */
class TriggeredActionsApp : Application() {

	private val objAppScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onCreate() {
		super.onCreate()
		ActionNotificationHelper.ensureChannelsCreated(this)

		val objRepo = ActionsRepository.getInstance(this)
		objRepo.setOnConfigChangedListener { objConfig ->
			ActionGeofenceScheduler.synchronizeGeofences(
				this,
				objConfig.listGeofences,
				objConfig.listActionSets,
				objConfig.objSettings.boolMasterEngineEnabled
			)
			ActionAlarmScheduler.synchronizeTimeTriggers(
				this,
				objConfig.listActionSets,
				objConfig.objSettings.boolMasterEngineEnabled
			)
			TriggeredMonitorService.synchronizeMonitorService(
				this,
				objConfig.listActionSets,
				objConfig.objSettings
			)
		}

		objAppScope.launch {
			objRepo.ensureLoaded()
			ActionGeofenceScheduler.synchronizeGeofences(
				this@TriggeredActionsApp,
				objRepo.flowGeofences.value,
				objRepo.flowActionSets.value,
				objRepo.flowSettings.value.boolMasterEngineEnabled
			)
			ActionAlarmScheduler.synchronizeTimeTriggers(
				this@TriggeredActionsApp,
				objRepo.flowActionSets.value,
				objRepo.flowSettings.value.boolMasterEngineEnabled
			)
			TriggeredMonitorService.synchronizeMonitorService(
				this@TriggeredActionsApp,
				objRepo.flowActionSets.value,
				objRepo.flowSettings.value
			)
		}
	}
}
