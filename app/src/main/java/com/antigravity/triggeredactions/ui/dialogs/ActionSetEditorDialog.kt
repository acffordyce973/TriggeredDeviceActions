package com.antigravity.triggeredactions.ui.dialogs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.ConcurrencyPolicy
import com.antigravity.triggeredactions.model.ConditionMatchMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.WebhookProfile
import java.util.UUID

/**
 * Full-screen Material 3 editor dialog for creating and editing an [ActionSet] with multiple Triggers,
 * `AND`/`OR` Conditions, and ordered sequential Actions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ActionSetEditorDialog(
	objInitialSet: ActionSet?,
	listGeofences: List<GeofenceArea>,
	listWebhookProfiles: List<WebhookProfile>,
	listShareXProfiles: List<ShareXProfile>,
	onDismiss: () -> Unit,
	onSave: (ActionSet) -> Unit
) {
	var stringName by remember { mutableStateOf(objInitialSet?.stringName ?: "") }
	var stringDescription by remember { mutableStateOf(objInitialSet?.stringDescription ?: "") }
	var boolIsEnabled by remember { mutableStateOf(objInitialSet?.boolIsEnabled ?: true) }
	var listTriggers by remember { mutableStateOf<List<SetTrigger>>(objInitialSet?.listTriggers ?: emptyList()) }
	var conditionMatchMode by remember {
		mutableStateOf(objInitialSet?.conditionMatchMode ?: ConditionMatchMode.ALL_AND)
	}
	var listConditions by remember { mutableStateOf<List<SetCondition>>(objInitialSet?.listConditions ?: emptyList()) }
	var listActions by remember { mutableStateOf<List<SetAction>>(objInitialSet?.listActions ?: emptyList()) }
	var concurrencyPolicy by remember {
		mutableStateOf(objInitialSet?.concurrencyPolicy ?: ConcurrencyPolicy.SKIP_IF_RUNNING)
	}
	var intCooldownSeconds by remember { mutableIntStateOf(objInitialSet?.intCooldownSeconds ?: 0) }

	var boolShowAddTrigger by remember { mutableStateOf(false) }
	var boolShowAddCondition by remember { mutableStateOf(false) }
	var boolShowAddAction by remember { mutableStateOf(false) }

	Dialog(
		onDismissRequest = onDismiss,
		properties = DialogProperties(usePlatformDefaultWidth = false)
	) {
		Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
			Scaffold(
				topBar = {
					TopAppBar(
						title = {
							Text(
								text = if (objInitialSet == null) "New Action Set" else "Edit Action Set",
								fontWeight = FontWeight.Bold
							)
						},
						navigationIcon = {
							IconButton(onClick = onDismiss) {
								Icon(Icons.Default.Close, contentDescription = "Close")
							}
						},
						actions = {
							Button(
								onClick = {
									onSave(
										ActionSet(
											stringId = objInitialSet?.stringId ?: UUID.randomUUID().toString(),
											stringName = stringName.ifBlank { "Action Set" },
											stringDescription = stringDescription.trim(),
											boolIsEnabled = boolIsEnabled,
											listTriggers = listTriggers,
											conditionMatchMode = conditionMatchMode,
											listConditions = listConditions,
											listActions = listActions,
											concurrencyPolicy = concurrencyPolicy,
											intCooldownSeconds = intCooldownSeconds
										)
									)
								},
								modifier = Modifier.padding(end = 8.dp)
							) {
								Text("Save Set")
							}
						}
					)
				}
			) { innerPadding ->
				Column(
					modifier = Modifier
						.fillMaxSize()
						.padding(innerPadding)
						.verticalScroll(rememberScrollState())
						.padding(16.dp),
					verticalArrangement = Arrangement.spacedBy(14.dp)
				) {
					// 1. Metadata & Enable Toggle
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = if (boolIsEnabled) "Action Set Enabled" else "Action Set Disabled",
								fontWeight = FontWeight.Bold,
								color = if (boolIsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
							)
							Text(
								text = "Controls whether background triggers evaluate this rule set.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(checked = boolIsEnabled, onCheckedChange = { boolIsEnabled = it })
					}

					OutlinedTextField(
						value = stringName,
						onValueChange = { stringName = it },
						label = { Text("Set Name") },
						placeholder = { Text("e.g. Home Charge -> Open Clock") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)

					OutlinedTextField(
						value = stringDescription,
						onValueChange = { stringDescription = it },
						label = { Text("Optional Description") },
						modifier = Modifier.fillMaxWidth()
					)

					HorizontalDivider()

					// 2. Triggers Section (OR entrypoints)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("1. Triggers (${listTriggers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
							Text("Fires when ANY of these events occur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						OutlinedButton(onClick = { boolShowAddTrigger = true }) {
							Icon(Icons.Default.Add, contentDescription = null)
							Spacer(modifier = Modifier.width(4.dp))
							Text("Add Trigger")
						}
					}

					listTriggers.forEachIndexed { intIdx, objTrig ->
						TriggerEditorCard(
							objTrigger = objTrig,
							listGeofences = listGeofences,
							onUpdate = { objUpdated ->
								listTriggers = listTriggers.toMutableList().also { it[intIdx] = objUpdated }
							},
							onDelete = {
								listTriggers = listTriggers.filterIndexed { i, _ -> i != intIdx }
							}
						)
					}

					HorizontalDivider()

					// 3. Conditions Section (AND / OR)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("2. Conditions (${listConditions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
							Text("Empty = Always true when triggered", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						OutlinedButton(onClick = { boolShowAddCondition = true }) {
							Icon(Icons.Default.Add, contentDescription = null)
							Spacer(modifier = Modifier.width(4.dp))
							Text("Add Condition")
						}
					}

					if (listConditions.size > 1) {
						Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
							Text("Match Mode:", fontWeight = FontWeight.SemiBold)
							FilterChip(
								selected = conditionMatchMode == ConditionMatchMode.ALL_AND,
								onClick = { conditionMatchMode = ConditionMatchMode.ALL_AND },
								label = { Text("ALL Match (AND)") }
							)
							FilterChip(
								selected = conditionMatchMode == ConditionMatchMode.ANY_OR,
								onClick = { conditionMatchMode = ConditionMatchMode.ANY_OR },
								label = { Text("ANY Matches (OR)") }
							)
						}
					}

					listConditions.forEachIndexed { intIdx, objCond ->
						ConditionEditorCard(
							objCondition = objCond,
							listGeofences = listGeofences,
							onUpdate = { objUpdated ->
								listConditions = listConditions.toMutableList().also { it[intIdx] = objUpdated }
							},
							onDelete = {
								listConditions = listConditions.filterIndexed { i, _ -> i != intIdx }
							}
						)
					}

					HorizontalDivider()

					// 4. Sequential Actions Section
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("3. Sequential Actions (${listActions.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
							Text("Executed in order from top to bottom", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
						}
						Button(onClick = { boolShowAddAction = true }) {
							Icon(Icons.Default.Add, contentDescription = null)
							Spacer(modifier = Modifier.width(4.dp))
							Text("Add Action")
						}
					}

					listActions.forEachIndexed { intIdx, objAct ->
						ActionStepEditorCard(
							intStepNumber = intIdx + 1,
							intTotalSteps = listActions.size,
							objAction = objAct,
							listWebhookProfiles = listWebhookProfiles,
							listShareXProfiles = listShareXProfiles,
							onMoveUp = {
								if (intIdx > 0) {
									val listMut = listActions.toMutableList()
									val objTmp = listMut[intIdx - 1]
									listMut[intIdx - 1] = listMut[intIdx]
									listMut[intIdx] = objTmp
									listActions = listMut
								}
							},
							onMoveDown = {
								if (intIdx < listActions.lastIndex) {
									val listMut = listActions.toMutableList()
									val objTmp = listMut[intIdx + 1]
									listMut[intIdx + 1] = listMut[intIdx]
									listMut[intIdx] = objTmp
									listActions = listMut
								}
							},
							onUpdate = { objUpdated ->
								listActions = listActions.toMutableList().also { it[intIdx] = objUpdated }
							},
							onDelete = {
								listActions = listActions.filterIndexed { i, _ -> i != intIdx }
							}
						)
					}

					HorizontalDivider()

					// 5. Re-Entry & Cooldown Settings
					Text("Execution & Re-Entry Policy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						ConcurrencyPolicy.entries.forEach { policy ->
							FilterChip(
								selected = concurrencyPolicy == policy,
								onClick = { concurrencyPolicy = policy },
								label = { Text(policy.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					OutlinedTextField(
						value = intCooldownSeconds.toString(),
						onValueChange = { s ->
							intCooldownSeconds = s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 86400) ?: 0
						},
						label = { Text("Minimum Cooldown Between Runs (Seconds, 0 = None)") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)

					Spacer(modifier = Modifier.height(36.dp))
				}
			}
		}
	}

	if (boolShowAddTrigger) {
		AddTriggerTypeDialog(
			listGeofences = listGeofences,
			onDismiss = { boolShowAddTrigger = false },
			onTriggerAdded = {
				listTriggers = listTriggers + it
				boolShowAddTrigger = false
			}
		)
	}

	if (boolShowAddCondition) {
		AddConditionTypeDialog(
			listGeofences = listGeofences,
			onDismiss = { boolShowAddCondition = false },
			onConditionAdded = {
				listConditions = listConditions + it
				boolShowAddCondition = false
			}
		)
	}

	if (boolShowAddAction) {
		AddActionTypeDialog(
			listWebhookProfiles = listWebhookProfiles,
			listShareXProfiles = listShareXProfiles,
			onDismiss = { boolShowAddAction = false },
			onActionAdded = {
				listActions = listActions + it
				boolShowAddAction = false
			}
		)
	}
}
