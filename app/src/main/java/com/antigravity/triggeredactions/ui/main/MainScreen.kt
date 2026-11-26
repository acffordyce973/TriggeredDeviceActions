package com.antigravity.triggeredactions.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antigravity.triggeredactions.ui.tabs.ActionSetsTab
import com.antigravity.triggeredactions.ui.tabs.RulesAuditTab
import com.antigravity.triggeredactions.ui.tabs.SettingsTab
import com.antigravity.triggeredactions.ui.tabs.StatusTab

/**
 * Root 4-tab Material 3 scaffold mirroring Triggered Live Wallpaper (`Status`, `Sets`, `Rules`, `Settings`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
	modifier: Modifier = Modifier,
	objViewModel: MainViewModel = viewModel()
) {
	val objContext = LocalContext.current
	val objUiState by objViewModel.flowUiState.collectAsStateWithLifecycle()
	var intSelectedTab by rememberSaveable { mutableIntStateOf(0) }

	val stringVersionName = remember(objContext) {
		runCatching {
			objContext.packageManager.getPackageInfo(objContext.packageName, 0).versionName ?: "1.0.0"
		}.getOrDefault("1.0.0")
	}

	Scaffold(
		modifier = modifier,
		topBar = {
			TopAppBar(
				title = {
					Text(
						text = "Triggered Device Actions v$stringVersionName",
						style = MaterialTheme.typography.titleLarge,
						fontWeight = FontWeight.Bold
					)
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.surface
				)
			)
		},
		bottomBar = {
			NavigationBar {
				NavigationBarItem(
					selected = intSelectedTab == 0,
					onClick = { intSelectedTab = 0 },
					icon = { Icon(Icons.Default.Dashboard, contentDescription = "Status") },
					label = { Text("Status") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 1,
					onClick = { intSelectedTab = 1 },
					icon = { Icon(Icons.Default.Bolt, contentDescription = "Sets") },
					label = { Text("Sets") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 2,
					onClick = { intSelectedTab = 2 },
					icon = {
						if (objUiState.listAuditIssues.isNotEmpty()) {
							BadgedBox(
								badge = { Badge { Text(objUiState.listAuditIssues.size.toString()) } }
							) {
								Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Rules")
							}
						} else {
							Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Rules")
						}
					},
					label = { Text("Rules") }
				)
				NavigationBarItem(
					selected = intSelectedTab == 3,
					onClick = { intSelectedTab = 3 },
					icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
					label = { Text("Settings") }
				)
			}
		}
	) { innerPadding ->
		if (objUiState.boolIsLoading) {
			Box(
				modifier = Modifier.fillMaxSize().padding(innerPadding),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}
		} else {
			Box(
				modifier = Modifier.fillMaxSize().padding(innerPadding)
			) {
				when (intSelectedTab) {
					0 -> StatusTab(
						objSettings = objUiState.objSettings,
						objLiveState = objUiState.objLiveState,
						listActionSets = objUiState.listActionSets,
						listGeofences = objUiState.listGeofences,
						listAuditIssues = objUiState.listAuditIssues,
						listExecutionLogs = objUiState.listExecutionLogs,
						onToggleMasterEngine = { boolVal ->
							objViewModel.updateSettings { it.copy(boolMasterEngineEnabled = boolVal) }
						},
						onRefreshState = { objViewModel.refreshLiveDeviceState() },
						onClearLogs = { objViewModel.clearExecutionLogs() },
						onJumpToRulesTab = { intSelectedTab = 2 }
					)

					1 -> ActionSetsTab(
						listActionSets = objUiState.listActionSets,
						listGeofences = objUiState.listGeofences,
						listWebhookProfiles = objUiState.listWebhookProfiles,
						listShareXProfiles = objUiState.listShareXProfiles,
						onSaveSet = { objViewModel.saveActionSet(it) },
						onDuplicateSet = { objViewModel.duplicateActionSet(it) },
						onDeleteSet = { objViewModel.deleteActionSet(it) },
						onToggleEnabled = { objSet, boolEnabled -> objViewModel.toggleActionSetEnabled(objSet, boolEnabled) },
						onTestRunSet = { objViewModel.testRunActionSet(it) }
					)

					2 -> RulesAuditTab(
						listAuditIssues = objUiState.listAuditIssues,
						listActionSets = objUiState.listActionSets
					)

					3 -> SettingsTab(
						objSettings = objUiState.objSettings,
						listGeofences = objUiState.listGeofences,
						listWebhookProfiles = objUiState.listWebhookProfiles,
						listShareXProfiles = objUiState.listShareXProfiles,
						onUpdateSettings = { objViewModel.updateSettings(it) },
						onSaveGeofence = { objViewModel.saveGeofence(it) },
						onDeleteGeofence = { objViewModel.deleteGeofence(it) },
						onSaveWebhook = { objViewModel.saveWebhookProfile(it) },
						onDeleteWebhook = { objViewModel.deleteWebhookProfile(it) },
						onSaveShareX = { objViewModel.saveShareXProfile(it) },
						onDeleteShareX = { objViewModel.deleteShareXProfile(it) },
						onExportConfig = { objViewModel.exportConfiguration(it) },
						onImportConfig = { json, cb -> objViewModel.importConfiguration(json, cb) },
						onImportTlwGeofences = { json, cb -> objViewModel.importGeofencesFromExternalBackup(json, cb) }
					)
				}
			}
		}
	}
}
