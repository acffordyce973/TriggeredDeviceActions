package com.antigravity.triggeredactions.ui.tabs

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.engine.ContextTemplateEngine
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.WebhookProfile
import com.antigravity.triggeredactions.ui.dialogs.ActionSetEditorDialog

/**
 * Sets tab mirroring Triggered Live Wallpaper's `FolderSetsTab` with search, category filter chips,
 * A–Z sorted Action Set cards, 1-tap manual Test Run, Duplicate, Edit, Delete, and Enable toggles.
 */
@Composable
fun ActionSetsTab(
	listActionSets: List<ActionSet>,
	listGeofences: List<GeofenceArea>,
	listWebhookProfiles: List<WebhookProfile>,
	listShareXProfiles: List<ShareXProfile>,
	onSaveSet: (ActionSet) -> Unit,
	onDuplicateSet: (ActionSet) -> Unit,
	onDeleteSet: (String) -> Unit,
	onToggleEnabled: (ActionSet, Boolean) -> Unit,
	onTestRunSet: (ActionSet) -> Unit
) {
	val objContext = LocalContext.current
	var boolShowEditor by remember { mutableStateOf(false) }
	var objEditingSet by remember { mutableStateOf<ActionSet?>(null) }
	var stringSearchQuery by remember { mutableStateOf("") }
	var stringSelectedFilter by remember { mutableStateOf("All") }

	val listFilterOptions = listOf(
		"All",
		"Enabled Only",
		"Disabled Only",
		"Phone Calls",
		"SMS",
		"Notifications",
		"Geofence",
		"Device Boot",
		"Charging / Battery",
		"Wi-Fi / Data / BT",
		"Webhooks / ShareX",
		"DnD Control",
		"Brightness",
		"App Launch"
	)

	val listDisplaySets = remember(listActionSets, stringSearchQuery, stringSelectedFilter) {
		listActionSets
			.filter { objSet ->
				if (stringSearchQuery.isBlank()) true
				else objSet.stringName.contains(stringSearchQuery.trim(), ignoreCase = true) ||
					objSet.stringDescription.contains(stringSearchQuery.trim(), ignoreCase = true)
			}
			.filter { objSet ->
				when (stringSelectedFilter) {
					"Enabled Only" -> objSet.boolIsEnabled
					"Disabled Only" -> !objSet.boolIsEnabled
					"Phone Calls" -> objSet.listTriggers.any {
						it is SetTrigger.IncomingCallTrigger || it is SetTrigger.MissedCallTrigger || it is SetTrigger.OngoingCallTrigger
					} || objSet.listActions.any { it is SetAction.EndCallAction }
					"SMS" -> objSet.listTriggers.any { it is SetTrigger.SmsReceivedTrigger } ||
						objSet.listActions.any { it is SetAction.SendSmsAction }
					"Notifications" -> objSet.listTriggers.any {
						it is SetTrigger.NotificationReceivedTrigger || it is SetTrigger.NotificationDismissedTrigger
					}
					"Geofence" -> objSet.listTriggers.any { it is SetTrigger.GeofenceTrigger } ||
						objSet.listConditions.any { it is com.antigravity.triggeredactions.model.SetCondition.GeofenceCondition }
					"Device Boot" -> objSet.listTriggers.any { it is SetTrigger.DeviceBootedTrigger }
					"Charging / Battery" -> objSet.listTriggers.any {
						it is SetTrigger.ChargingStateTrigger || it is SetTrigger.BatteryStatusTrigger
					}
					"Wi-Fi / Data / BT" -> objSet.listTriggers.any {
						it is SetTrigger.WifiTrigger || it is SetTrigger.MobileDataTrigger || it is SetTrigger.BluetoothTrigger
					}
					"Webhooks / ShareX" -> objSet.listActions.any {
						it is SetAction.SendWebhookAction || it is SetAction.ShareXUploadAction
					}
					"DnD Control" -> objSet.listActions.any { it is SetAction.SetDndAction }
					"Brightness" -> objSet.listActions.any { it is SetAction.SetBrightnessAction }
					"App Launch" -> objSet.listActions.any {
						it is SetAction.StartAppAction || it is SetAction.StartAppShortcutAction || it is SetAction.StartAppActivityAction
					}
					else -> true
				}
			}
			.sortedBy { it.stringName.lowercase() }
	}

	Scaffold(
		floatingActionButton = {
			FloatingActionButton(
				onClick = {
					objEditingSet = null
					boolShowEditor = true
				},
				containerColor = MaterialTheme.colorScheme.primaryContainer,
				contentColor = MaterialTheme.colorScheme.onPrimaryContainer
			) {
				Icon(Icons.Default.Add, contentDescription = "Add Action Set")
			}
		}
	) { innerPadding ->
		if (listActionSets.isEmpty()) {
			Column(
				modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center
			) {
				Icon(
					Icons.Default.Bolt,
					contentDescription = null,
					modifier = Modifier.size(64.dp),
					tint = MaterialTheme.colorScheme.primary
				)
				Spacer(modifier = Modifier.height(16.dp))
				Text("No Action Sets Yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
				Spacer(modifier = Modifier.height(8.dp))
				Text(
					"Create your first Action Set to combine triggers, AND/OR conditions, and sequential actions.",
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Spacer(modifier = Modifier.height(20.dp))
				Button(onClick = {
					objEditingSet = null
					boolShowEditor = true
				}) {
					Icon(Icons.Default.Add, contentDescription = null)
					Spacer(modifier = Modifier.width(6.dp))
					Text("Create Action Set")
				}
			}
		} else {
			LazyColumn(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding)
					.padding(horizontal = 16.dp, vertical = 8.dp),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				item {
					OutlinedTextField(
						value = stringSearchQuery,
						onValueChange = { stringSearchQuery = it },
						placeholder = { Text("Search sets by name or description...") },
						leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
						trailingIcon = {
							if (stringSearchQuery.isNotEmpty()) {
								IconButton(onClick = { stringSearchQuery = "" }) {
									Icon(Icons.Default.Clear, contentDescription = "Clear")
								}
							}
						},
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}

				item {
					LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
						items(listFilterOptions) { stringFilter ->
							FilterChip(
								selected = stringSelectedFilter == stringFilter,
								onClick = { stringSelectedFilter = stringFilter },
								label = { Text(stringFilter, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
				}

				item {
					Text(
						text = "${listDisplaySets.size} set(s) (A–Z)",
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						fontWeight = FontWeight.SemiBold
					)
				}

				items(listDisplaySets, key = { it.stringId }) { objSet ->
					ActionSetCard(
						objSet = objSet,
						listGeofences = listGeofences,
						onTestRun = {
							onTestRunSet(objSet)
							Toast.makeText(objContext, "Running \"${objSet.stringName}\"...", Toast.LENGTH_SHORT).show()
						},
						onEdit = {
							objEditingSet = objSet
							boolShowEditor = true
						},
						onDuplicate = { onDuplicateSet(objSet) },
						onDelete = { onDeleteSet(objSet.stringId) },
						onToggleEnabled = { onToggleEnabled(objSet, it) }
					)
				}

				item { Spacer(modifier = Modifier.height(72.dp)) }
			}
		}
	}

	if (boolShowEditor) {
		ActionSetEditorDialog(
			objInitialSet = objEditingSet,
			listGeofences = listGeofences,
			listWebhookProfiles = listWebhookProfiles,
			listShareXProfiles = listShareXProfiles,
			onDismiss = { boolShowEditor = false },
			onSave = { objSaved ->
				onSaveSet(objSaved)
				boolShowEditor = false
			}
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionSetCard(
	objSet: ActionSet,
	listGeofences: List<GeofenceArea>,
	onTestRun: () -> Unit,
	onEdit: () -> Unit,
	onDuplicate: () -> Unit,
	onDelete: () -> Unit,
	onToggleEnabled: (Boolean) -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		shape = RoundedCornerShape(16.dp),
		colors = CardDefaults.cardColors(
			containerColor = if (objSet.boolIsEnabled) MaterialTheme.colorScheme.surfaceVariant
			else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
		)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Column(modifier = Modifier.weight(1f)) {
					Text(
						text = objSet.stringName,
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold
					)
					if (objSet.stringDescription.isNotBlank()) {
						Text(
							text = objSet.stringDescription,
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
					if (!objSet.boolIsEnabled) {
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.errorContainer,
							modifier = Modifier.padding(top = 4.dp)
						) {
							Text(
								text = "DISABLED",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onErrorContainer,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
							)
						}
					}
				}
				Switch(checked = objSet.boolIsEnabled, onCheckedChange = onToggleEnabled)
			}

			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				IconButton(onClick = onTestRun) {
					Icon(Icons.Default.PlayArrow, contentDescription = "Test Run", tint = MaterialTheme.colorScheme.tertiary)
				}
				IconButton(onClick = onDuplicate) {
					Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.secondary)
				}
				IconButton(onClick = onEdit) {
					Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
				}
				IconButton(onClick = onDelete) {
					Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
				}
			}

			Text("Triggers (OR):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				if (objSet.listTriggers.isEmpty()) {
					SuggestionChip(onClick = {}, label = { Text("Manual Only") })
				} else {
					objSet.listTriggers.forEach { trig ->
						SuggestionChip(
							onClick = {},
							label = { Text(ContextTemplateEngine.getTriggerSummary(trig, listGeofences)) },
							colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
						)
					}
				}
			}

			Text(
				text = "Conditions (${objSet.conditionMatchMode.name}):",
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold
			)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				if (objSet.listConditions.isEmpty()) {
					SuggestionChip(onClick = {}, label = { Text("Always (Unconditional)") })
				} else {
					objSet.listConditions.forEach { cond ->
						SuggestionChip(
							onClick = {},
							label = { Text(ContextTemplateEngine.getConditionSummary(cond, listGeofences)) }
						)
					}
				}
			}

			Text("Actions (${objSet.listActions.size}):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
				objSet.listActions.forEachIndexed { intIdx, act ->
					SuggestionChip(
						onClick = {},
						label = { Text("${intIdx + 1}. ${ContextTemplateEngine.getActionSummary(act)}") },
						colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
					)
				}
			}
		}
	}
}
