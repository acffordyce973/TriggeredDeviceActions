package com.antigravity.triggeredactions.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.antigravity.triggeredactions.data.ActionsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings Tile providing a 1-tap toggle to pause or resume the master Triggered Actions engine.
 */
class TriggeredQuickTileService : TileService() {

	private val objScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	override fun onStartListening() {
		super.onStartListening()
		objScope.launch {
			val objRepo = ActionsRepository.getInstance(applicationContext)
			objRepo.ensureLoaded()
			refreshTileVisual(objRepo.flowSettings.value.boolMasterEngineEnabled)
		}
	}

	override fun onClick() {
		super.onClick()
		objScope.launch {
			val objRepo = ActionsRepository.getInstance(applicationContext)
			objRepo.ensureLoaded()
			val boolNext = !objRepo.flowSettings.value.boolMasterEngineEnabled
			objRepo.updateAppSettings { it.copy(boolMasterEngineEnabled = boolNext) }
			refreshTileVisual(boolNext)
		}
	}

	private fun refreshTileVisual(boolActive: Boolean) {
		val objTile = qsTile ?: return
		objTile.state = if (boolActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
		objTile.subtitle = if (boolActive) "Active" else "Paused"
		objTile.updateTile()
	}
}
