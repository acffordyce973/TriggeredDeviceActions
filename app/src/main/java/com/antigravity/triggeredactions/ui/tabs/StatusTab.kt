package com.antigravity.triggeredactions.ui.tabs

import android.app.NotificationManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antigravity.triggeredactions.engine.ConditionEvaluator
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.AppSettings
import com.antigravity.triggeredactions.model.ExecutionLogEntry
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.LiveDeviceState
import com.antigravity.triggeredactions.model.RuleAuditIssue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Status tab displaying Master Engine controls, permission/conflict warnings, live device telemetry,
 * and recent Action Set execution logs.
 */
@Composable
fun StatusTab(
	objSettings: AppSettings,
	objLiveState: LiveDeviceState,
	listActionSets: List<ActionSet>,
	listGeofences: List<GeofenceArea>,
	listAuditIssues: List<RuleAuditIssue>,
	listExecutionLogs: List<ExecutionLogEntry>,
	onToggleMasterEngine: (Boolean) -> Unit,
	onRefreshState: () -> Unit,
	onClearLogs: () -> Unit,
	onJumpToRulesTab: () -> Unit
) {
	val objLifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(objLifecycleOwner) {
		val objObserver = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				onRefreshState()
			}
		}
		objLifecycleOwner.lifecycle.addObserver(objObserver)
		onDispose { objLifecycleOwner.lifecycle.removeObserver(objObserver) }
	}

	val intEnabledSets = listActionSets.count { it.boolIsEnabled }
	val boolIsDark = isSystemInDarkTheme()

	val listInsideGeoNames = listGeofences.filter { objGeo ->
		objLiveState.setActiveGeofenceIds.contains(objGeo.stringId) || (
			objLiveState.doubleLatitude != null && objLiveState.doubleLongitude != null &&
				ConditionEvaluator.computeDistanceMeters(
					objLiveState.doubleLatitude,
					objLiveState.doubleLongitude,
					objGeo.doubleLatitude,
					objGeo.doubleLongitude
				) <= objGeo.floatRadiusMeters
			)
	}.map { it.stringName }

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		// 1. Master Engine Card
		Card(
			modifier = Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(
				containerColor = if (objSettings.boolMasterEngineEnabled) {
					MaterialTheme.colorScheme.primaryContainer
				} else {
					MaterialTheme.colorScheme.surfaceVariant
				}
			)
		) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
						Icon(
							Icons.Default.Bolt,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
							modifier = Modifier.size(28.dp)
						)
						Spacer(modifier = Modifier.width(10.dp))
						Column {
							Text(
								text = if (objSettings.boolMasterEngineEnabled) "Automation Engine Active" else "Automation Engine Paused",
								style = MaterialTheme.typography.titleMedium,
								fontWeight = FontWeight.Bold
							)
							Text(
								text = "$intEnabledSets enabled set(s) of ${listActionSets.size} total",
								style = MaterialTheme.typography.bodySmall
							)
						}
					}
					Switch(
						checked = objSettings.boolMasterEngineEnabled,
						onCheckedChange = onToggleMasterEngine
					)
				}

				OutlinedButton(
					onClick = onRefreshState,
					modifier = Modifier.fillMaxWidth()
				) {
					Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
					Spacer(modifier = Modifier.width(6.dp))
					Text("Refresh Live Sensors")
				}
			}
		}

		// 2. Audit / Permission Warning Banner
		if (listAuditIssues.isNotEmpty()) {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
			) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(14.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						Icons.Default.Warning,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.error,
						modifier = Modifier.size(26.dp)
					)
					Spacer(modifier = Modifier.width(10.dp))
					Column(modifier = Modifier.weight(1f)) {
						Text(
							text = "${listAuditIssues.size} Rule / Permission Notice(s)",
							style = MaterialTheme.typography.titleSmall,
							fontWeight = FontWeight.Bold,
							color = MaterialTheme.colorScheme.onErrorContainer
						)
						Text(
							text = listAuditIssues.first().stringTitle,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onErrorContainer
						)
					}
					TextButton(onClick = onJumpToRulesTab) {
						Text("View")
					}
				}
			}
		}

		// 3. Live Device State Diagnostics (TLW style)
		Text(
			text = "Live Device Context & Sensors",
			style = MaterialTheme.typography.titleMedium,
			fontWeight = FontWeight.Bold
		)

		Card(
			modifier = Modifier.fillMaxWidth(),
			colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
		) {
			Column(
				modifier = Modifier.fillMaxWidth().padding(16.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				val boolInsideGeo = listInsideGeoNames.isNotEmpty()
				val stringGeoValue = when {
					boolInsideGeo -> "Inside: ${listInsideGeoNames.joinToString()}"
					objLiveState.doubleLatitude != null -> String.format(
						Locale.US,
						"Outside (%.4f, %.4f)",
						objLiveState.doubleLatitude,
						objLiveState.doubleLongitude
					)
					else -> "Waiting for fix"
				}

				val stringDndLabel = when (objLiveState.intDndFilter) {
					NotificationManager.INTERRUPTION_FILTER_ALL -> "Off (Allow All)"
					NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "On (Priority)"
					NotificationManager.INTERRUPTION_FILTER_ALARMS -> "On (Alarms Only)"
					NotificationManager.INTERRUPTION_FILTER_NONE -> "On (Total Silence)"
					else -> "Unknown"
				}

				val stringCallLabel = when (objLiveState.intCallState) {
					TelephonyManager.CALL_STATE_RINGING -> "Ringing"
					TelephonyManager.CALL_STATE_OFFHOOK -> "Ongoing Call"
					else -> "Idle"
				}

				StatusRowItem(
					icon = Icons.Default.LocationOn,
					stringLabel = "Geofence / GPS",
					stringValue = stringGeoValue,
					boolHighlighted = boolInsideGeo,
					highlightContainer = if (boolIsDark) Color(0xFF1B4D2E) else Color(0xFFC8E6C9),
					highlightContent = if (boolIsDark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
				)
				StatusRowItem(
					icon = Icons.Default.Schedule,
					stringLabel = "Local Time",
					stringValue = String.format(
						Locale.US,
						"%02d:%02d (Day %d, Month %d)",
						objLiveState.intHour,
						objLiveState.intMinute,
						objLiveState.intDayOfWeek,
						objLiveState.intMonth
					)
				)
				StatusRowItem(
					icon = Icons.Default.LockOpen,
					stringLabel = "Screen & Lock",
					stringValue = "${if (objLiveState.boolScreenOn) "Screen On" else "Screen Off"} • ${if (objLiveState.boolDeviceUnlocked) "Unlocked" else "Locked"}"
				)
				StatusRowItem(
					icon = Icons.Default.DoNotDisturbOn,
					stringLabel = "Do Not Disturb",
					stringValue = stringDndLabel
				)
				StatusRowItem(
					icon = Icons.Default.BatteryChargingFull,
					stringLabel = "Battery & Power",
					stringValue = "${objLiveState.intBatteryPercent}% • ${if (objLiveState.boolIsCharging) "Charging (${objLiveState.chargingSource.name})" else "Discharging"}"
				)
				StatusRowItem(
					icon = Icons.Default.Wifi,
					stringLabel = "Wi-Fi",
					stringValue = if (objLiveState.boolWifiConnected) {
						objLiveState.stringWifiSsid.ifBlank { "Connected" }
					} else "Disconnected"
				)
				StatusRowItem(
					icon = Icons.Default.SignalCellularAlt,
					stringLabel = "Mobile Data",
					stringValue = if (objLiveState.boolMobileDataConnected) "Connected" else "Inactive"
				)
				StatusRowItem(
					icon = Icons.Default.Bluetooth,
					stringLabel = "Bluetooth",
					stringValue = if (objLiveState.setConnectedBluetoothNames.isNotEmpty()) {
						objLiveState.setConnectedBluetoothNames.joinToString()
					} else if (objLiveState.boolBluetoothEnabled) "On (Idle)" else "Off"
				)
				StatusRowItem(
					icon = Icons.Default.MusicNote,
					stringLabel = "Media Playback",
					stringValue = if (objLiveState.boolMediaPlaying) {
						objLiveState.stringActiveMediaTitle.ifBlank { "Playing" }
					} else "Stopped"
				)
				StatusRowItem(
					icon = Icons.Default.Call,
					stringLabel = "Phone Call State",
					stringValue = stringCallLabel
				)
				StatusRowItem(
					icon = Icons.Default.Apps,
					stringLabel = "Foreground App",
					stringValue = objLiveState.stringCurrentForegroundPackage.substringAfterLast('.').ifBlank { "Grant Usage Access" }
				)
			}
		}

		// 4. Recent Execution Logs
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				text = "Recent Execution History (${listExecutionLogs.size})",
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold
			)
			if (listExecutionLogs.isNotEmpty()) {
				IconButton(onClick = onClearLogs) {
					Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.error)
				}
			}
		}

		if (listExecutionLogs.isEmpty()) {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Text(
					text = "No Action Sets have fired yet. Enable a set or tap the Play button on any set in the Sets tab to test.",
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.padding(16.dp)
				)
			}
		} else {
			listExecutionLogs.take(25).forEach { objLog ->
				ExecutionLogCard(objLog)
			}
		}
	}
}

@Composable
private fun StatusRowItem(
	icon: ImageVector,
	stringLabel: String,
	stringValue: String,
	boolHighlighted: Boolean = false,
	highlightContainer: Color = Color.Unspecified,
	highlightContent: Color = Color.Unspecified
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
			Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
			Spacer(modifier = Modifier.width(10.dp))
			Text(stringLabel, style = MaterialTheme.typography.bodyMedium)
		}
		Surface(
			shape = RoundedCornerShape(8.dp),
			color = if (boolHighlighted) highlightContainer else MaterialTheme.colorScheme.surface
		) {
			Text(
				text = stringValue,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.SemiBold,
				color = if (boolHighlighted) highlightContent else MaterialTheme.colorScheme.primary,
				modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
			)
		}
	}
}

@Composable
private fun ExecutionLogCard(objLog: ExecutionLogEntry) {
	val stringTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(objLog.longTimestampMs))
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
					Icon(
						imageVector = if (objLog.boolSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
						contentDescription = null,
						tint = if (objLog.boolSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
						modifier = Modifier.size(18.dp)
					)
					Spacer(modifier = Modifier.width(6.dp))
					Text(objLog.stringSetName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
				}
				Text(stringTimestamp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Text(
				text = "Trigger: ${objLog.stringTriggerSummary}",
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.primary
			)
			objLog.listStepLogs.forEachIndexed { intIdx, stringStep ->
				Text(
					text = "  ${intIdx + 1}. $stringStep",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}
