package com.antigravity.triggeredactions.ui.dialogs

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.engine.ContextTemplateEngine
import com.antigravity.triggeredactions.model.AppRunningCheckMode
import com.antigravity.triggeredactions.model.BatteryThresholdOperator
import com.antigravity.triggeredactions.model.CallEventDirection
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import com.antigravity.triggeredactions.model.ContactFilterMode
import com.antigravity.triggeredactions.model.DndConditionMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.GeofenceTransitionMode
import com.antigravity.triggeredactions.model.MissedCallFilter
import com.antigravity.triggeredactions.model.NotificationDismissReason
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.TextMatchMode
import java.util.Locale


// ---- Add Trigger & Add Condition Modals ----

/**
 * Modal selector listing all 16 supported trigger types.
 */
@Composable
fun AddTriggerTypeDialog(
	listGeofences: List<GeofenceArea>,
	onDismiss: () -> Unit,
	onTriggerAdded: (SetTrigger) -> Unit
) {
	val stringDefaultGeoId = listGeofences.firstOrNull()?.stringId.orEmpty()
	val listOptions: List<Pair<String, () -> SetTrigger>> = listOf(
		"Geofence Location (Enter / Exit)" to { SetTrigger.GeofenceTrigger(stringGeofenceId = stringDefaultGeoId) },
		"Time (Clock Schedule or Interval)" to { SetTrigger.TimeScheduleTrigger() },
		"Device Unlocked / Locked" to { SetTrigger.DeviceLockTrigger(boolOnUnlocked = true) },
		"Screen On / Off" to { SetTrigger.ScreenStateTrigger(boolOnScreenOn = true) },
		"Wi-Fi Connected / Disconnected" to { SetTrigger.WifiTrigger(boolOnConnected = true) },
		"Mobile Data Connected / Disconnected" to { SetTrigger.MobileDataTrigger(boolOnConnected = true) },
		"Bluetooth Connected / Disconnected" to { SetTrigger.BluetoothTrigger(boolOnConnected = true) },
		"Media Playing / Stopped / Track Change" to { SetTrigger.MediaPlaybackTrigger(boolOnPlaybackStarted = true) },
		"Incoming Phone Call" to { SetTrigger.IncomingCallTrigger() },
		"Missed Call" to { SetTrigger.MissedCallTrigger() },
		"Ongoing / Answered / Ended Call" to { SetTrigger.OngoingCallTrigger() },
		"SMS Received" to { SetTrigger.SmsReceivedTrigger() },
		"Battery Level Threshold" to { SetTrigger.BatteryStatusTrigger() },
		"Charging / Discharging (Plug / Unplug)" to { SetTrigger.ChargingStateTrigger(boolOnChargingConnected = true) },
		"Notification Received (Regex / OTP)" to { SetTrigger.NotificationReceivedTrigger() },
		"Notification Dismissed" to { SetTrigger.NotificationDismissedTrigger() }
	)

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Add Trigger", fontWeight = FontWeight.Bold) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				items(listOptions, key = { it.first }) { (stringLabel, funcFactory) ->
					Text(
						text = stringLabel,
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier
							.fillMaxWidth()
							.clickable { onTriggerAdded(funcFactory()) }
							.padding(vertical = 12.dp, horizontal = 4.dp)
					)
					HorizontalDivider()
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Cancel") }
		}
	)
}

/**
 * Modal selector listing all 13 supported condition types.
 */
@Composable
fun AddConditionTypeDialog(
	listGeofences: List<GeofenceArea>,
	onDismiss: () -> Unit,
	onConditionAdded: (SetCondition) -> Unit
) {
	val stringDefaultGeoId = listGeofences.firstOrNull()?.stringId.orEmpty()
	val listOptions: List<Pair<String, () -> SetCondition>> = listOf(
		"Geofence (Inside / Outside)" to { SetCondition.GeofenceCondition(stringGeofenceId = stringDefaultGeoId) },
		"Time Window / Days / Months" to { SetCondition.TimeCondition() },
		"Device Unlocked or Locked" to { SetCondition.DeviceUnlockedCondition() },
		"Screen On or Off" to { SetCondition.ScreenStateCondition() },
		"Wi-Fi State & SSID" to { SetCondition.WifiCondition() },
		"Mobile Data State" to { SetCondition.MobileDataCondition() },
		"Bluetooth State & Device" to { SetCondition.BluetoothCondition() },
		"Media Playing or Not" to { SetCondition.MediaPlayingCondition() },
		"Ongoing Call State" to { SetCondition.OngoingCallCondition() },
		"Do Not Disturb (DnD) Status" to { SetCondition.DndStatusCondition() },
		"Battery Level" to { SetCondition.BatteryStatusCondition() },
		"Charging / Discharging State" to { SetCondition.ChargingCondition() },
		"App Running / Open" to { SetCondition.AppRunningCondition() }
	)

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Add Condition", fontWeight = FontWeight.Bold) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				items(listOptions, key = { it.first }) { (stringLabel, funcFactory) ->
					Text(
						text = stringLabel,
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier
							.fillMaxWidth()
							.clickable { onConditionAdded(funcFactory()) }
							.padding(vertical = 12.dp, horizontal = 4.dp)
					)
					HorizontalDivider()
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Cancel") }
		}
	)
}


// ---- Inline Trigger Refinement Card ----

/**
 * Renders refinement controls for any [SetTrigger] subclass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TriggerEditorCard(
	objTrigger: SetTrigger,
	listGeofences: List<GeofenceArea>,
	onUpdate: (SetTrigger) -> Unit,
	onDelete: () -> Unit
) {
	val objContext = LocalContext.current
	var boolShowAppPicker by remember { mutableStateOf(false) }
	var boolShowBtPicker by remember { mutableStateOf(false) }

	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = ContextTemplateEngine.getTriggerSummary(objTrigger, listGeofences),
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.weight(1f)
				)
				IconButton(onClick = onDelete) {
					Icon(Icons.Default.Delete, contentDescription = "Remove Trigger", tint = MaterialTheme.colorScheme.error)
				}
			}

			when (objTrigger) {
				is SetTrigger.GeofenceTrigger -> {
					Text("Location:", style = MaterialTheme.typography.labelMedium)
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listGeofences.forEach { objGeo ->
							FilterChip(
								selected = objTrigger.stringGeofenceId == objGeo.stringId,
								onClick = { onUpdate(objTrigger.copy(stringGeofenceId = objGeo.stringId)) },
								label = { Text(objGeo.stringName) }
							)
						}
					}
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						GeofenceTransitionMode.entries.forEach { mode ->
							FilterChip(
								selected = objTrigger.transitionMode == mode,
								onClick = { onUpdate(objTrigger.copy(transitionMode = mode)) },
								label = { Text(mode.name) }
							)
						}
					}
				}

				is SetTrigger.TimeScheduleTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = !objTrigger.boolIsInterval,
							onClick = { onUpdate(objTrigger.copy(boolIsInterval = false)) },
							label = { Text("Exact Time") }
						)
						FilterChip(
							selected = objTrigger.boolIsInterval,
							onClick = { onUpdate(objTrigger.copy(boolIsInterval = true)) },
							label = { Text("Every N Minutes") }
						)
					}
					if (objTrigger.boolIsInterval) {
						OutlinedTextField(
							value = objTrigger.intIntervalMinutes.toString(),
							onValueChange = { stringVal ->
								val intMins = stringVal.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 10080) ?: 15
								onUpdate(objTrigger.copy(intIntervalMinutes = intMins))
							},
							label = { Text("Interval (Minutes)") },
							keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					} else {
						OutlinedButton(
							onClick = {
								TimePickerDialog(
									objContext,
									{ _, intH, intM -> onUpdate(objTrigger.copy(intHour = intH, intMinute = intM)) },
									objTrigger.intHour,
									objTrigger.intMinute,
									true
								).show()
							}
						) {
							Text(String.format(Locale.US, "Pick Time: %02d:%02d", objTrigger.intHour, objTrigger.intMinute))
						}
						DayOfWeekChips(
							listSelected = objTrigger.listDaysOfWeek,
							onChanged = { onUpdate(objTrigger.copy(listDaysOfWeek = it)) }
						)
					}
				}

				is SetTrigger.DeviceLockTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnUnlocked,
							onClick = { onUpdate(objTrigger.copy(boolOnUnlocked = true)) },
							label = { Text("Device Unlocked") }
						)
						FilterChip(
							selected = !objTrigger.boolOnUnlocked,
							onClick = { onUpdate(objTrigger.copy(boolOnUnlocked = false)) },
							label = { Text("Device Locked") }
						)
					}
				}

				is SetTrigger.ScreenStateTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnScreenOn,
							onClick = { onUpdate(objTrigger.copy(boolOnScreenOn = true)) },
							label = { Text("Screen On") }
						)
						FilterChip(
							selected = !objTrigger.boolOnScreenOn,
							onClick = { onUpdate(objTrigger.copy(boolOnScreenOn = false)) },
							label = { Text("Screen Off") }
						)
					}
				}

				is SetTrigger.WifiTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = false)) },
							label = { Text("Disconnected") }
						)
					}
					TextMatchEditorRow(
						stringLabel = "SSID Filter (Optional)",
						matchMode = objTrigger.ssidMatchMode,
						stringPattern = objTrigger.stringSsidFilter,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(ssidMatchMode = mode, stringSsidFilter = pat)) }
					)
				}

				is SetTrigger.MobileDataTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = false)) },
							label = { Text("Disconnected") }
						)
					}
					if (objTrigger.boolOnConnected) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Text("Require Not Roaming", modifier = Modifier.weight(1f))
							Switch(
								checked = objTrigger.boolRequireNotRoaming,
								onCheckedChange = { onUpdate(objTrigger.copy(boolRequireNotRoaming = it)) }
							)
						}
					}
				}

				is SetTrigger.BluetoothTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objTrigger.boolOnConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnConnected = false)) },
							label = { Text("Disconnected") }
						)
						OutlinedButton(onClick = { boolShowBtPicker = true }) {
							Text("Pick Paired BT")
						}
					}
					TextMatchEditorRow(
						stringLabel = "Device Name or MAC",
						matchMode = objTrigger.deviceMatchMode,
						stringPattern = objTrigger.stringDeviceNameOrMac,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(deviceMatchMode = mode, stringDeviceNameOrMac = pat)) }
					)
					if (boolShowBtPicker) {
						PairedBluetoothPickerDialog(
							onDismiss = { boolShowBtPicker = false },
							onDevicePicked = { stringDev ->
								onUpdate(objTrigger.copy(deviceMatchMode = TextMatchMode.EQUALS, stringDeviceNameOrMac = stringDev))
								boolShowBtPicker = false
							}
						)
					}
				}

				is SetTrigger.MediaPlaybackTrigger -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						FilterChip(
							selected = objTrigger.boolOnPlaybackStarted && !objTrigger.boolOnTrackChanged,
							onClick = { onUpdate(objTrigger.copy(boolOnPlaybackStarted = true, boolOnTrackChanged = false)) },
							label = { Text("Playback Started") }
						)
						FilterChip(
							selected = !objTrigger.boolOnPlaybackStarted && !objTrigger.boolOnTrackChanged,
							onClick = { onUpdate(objTrigger.copy(boolOnPlaybackStarted = false, boolOnTrackChanged = false)) },
							label = { Text("Playback Stopped") }
						)
						FilterChip(
							selected = objTrigger.boolOnTrackChanged,
							onClick = { onUpdate(objTrigger.copy(boolOnPlaybackStarted = true, boolOnTrackChanged = true)) },
							label = { Text("Track Changed") }
						)
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objTrigger.stringAppPackage,
							onValueChange = { onUpdate(objTrigger.copy(stringAppPackage = it)) },
							label = { Text("Media App Package (Blank = Any)") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick") }
					}
					TextMatchEditorRow(
						stringLabel = "Track / Artist Filter",
						matchMode = objTrigger.textMatchMode,
						stringPattern = objTrigger.stringTrackOrArtistFilter,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(textMatchMode = mode, stringTrackOrArtistFilter = pat)) }
					)
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objTrigger.copy(stringAppPackage = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}

				is SetTrigger.IncomingCallTrigger -> {
					ContactFilterEditor(
						mode = objTrigger.contactFilterMode,
						stringPattern = objTrigger.stringNumberOrRegex,
						onChanged = { m, p -> onUpdate(objTrigger.copy(contactFilterMode = m, stringNumberOrRegex = p)) }
					)
				}

				is SetTrigger.MissedCallTrigger -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						MissedCallFilter.entries.forEach { mf ->
							FilterChip(
								selected = objTrigger.missedCallFilter == mf,
								onClick = { onUpdate(objTrigger.copy(missedCallFilter = mf)) },
								label = { Text(mf.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					ContactFilterEditor(
						mode = objTrigger.contactFilterMode,
						stringPattern = objTrigger.stringNumberOrRegex,
						onChanged = { m, p -> onUpdate(objTrigger.copy(contactFilterMode = m, stringNumberOrRegex = p)) }
					)
				}

				is SetTrigger.OngoingCallTrigger -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						CallEventDirection.entries.forEach { dir ->
							FilterChip(
								selected = objTrigger.callDirection == dir,
								onClick = { onUpdate(objTrigger.copy(callDirection = dir)) },
								label = { Text(dir.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					ContactFilterEditor(
						mode = objTrigger.contactFilterMode,
						stringPattern = objTrigger.stringNumberOrRegex,
						onChanged = { m, p -> onUpdate(objTrigger.copy(contactFilterMode = m, stringNumberOrRegex = p)) }
					)
				}

				is SetTrigger.SmsReceivedTrigger -> {
					ContactFilterEditor(
						mode = objTrigger.contactFilterMode,
						stringPattern = objTrigger.stringSenderFilter,
						onChanged = { m, p -> onUpdate(objTrigger.copy(contactFilterMode = m, stringSenderFilter = p)) }
					)
					TextMatchEditorRow(
						stringLabel = "SMS Message Body Filter",
						matchMode = objTrigger.bodyMatchMode,
						stringPattern = objTrigger.stringBodyPattern,
						boolShowOtpPreset = true,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(bodyMatchMode = mode, stringBodyPattern = pat)) }
					)
				}

				is SetTrigger.BatteryStatusTrigger -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listOf(
							BatteryThresholdOperator.DROPS_AT_OR_BELOW,
							BatteryThresholdOperator.RISES_AT_OR_ABOVE,
							BatteryThresholdOperator.EQUALS
						).forEach { op ->
							FilterChip(
								selected = objTrigger.thresholdOperator == op,
								onClick = { onUpdate(objTrigger.copy(thresholdOperator = op)) },
								label = { Text(op.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					OutlinedTextField(
						value = objTrigger.intBatteryPercent.toString(),
						onValueChange = { s ->
							val intPct = s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 100) ?: 20
							onUpdate(objTrigger.copy(intBatteryPercent = intPct))
						},
						label = { Text("Battery Percentage (%)") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}

				is SetTrigger.ChargingStateTrigger -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objTrigger.boolOnChargingConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnChargingConnected = true)) },
							label = { Text("Plugged In") }
						)
						FilterChip(
							selected = !objTrigger.boolOnChargingConnected,
							onClick = { onUpdate(objTrigger.copy(boolOnChargingConnected = false)) },
							label = { Text("Unplugged") }
						)
					}
					if (objTrigger.boolOnChargingConnected) {
						FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							ChargingSourceFilter.entries.forEach { src ->
								FilterChip(
									selected = objTrigger.sourceFilter == src,
									onClick = { onUpdate(objTrigger.copy(sourceFilter = src)) },
									label = { Text(src.name) }
								)
							}
						}
					}
				}

				is SetTrigger.NotificationReceivedTrigger -> {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objTrigger.stringPackageFilter,
							onValueChange = { onUpdate(objTrigger.copy(stringPackageFilter = it)) },
							label = { Text("Source App Package (Blank = Any)") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick") }
					}
					TextMatchEditorRow(
						stringLabel = "Notification Body Match",
						matchMode = objTrigger.bodyMatchMode,
						stringPattern = objTrigger.stringBodyPattern,
						boolShowOtpPreset = true,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(bodyMatchMode = mode, stringBodyPattern = pat)) }
					)
					TextMatchEditorRow(
						stringLabel = "Notification Title Match",
						matchMode = objTrigger.titleMatchMode,
						stringPattern = objTrigger.stringTitlePattern,
						onChanged = { mode, pat -> onUpdate(objTrigger.copy(titleMatchMode = mode, stringTitlePattern = pat)) }
					)
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objTrigger.copy(stringPackageFilter = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}

				is SetTrigger.NotificationDismissedTrigger -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						NotificationDismissReason.entries.forEach { r ->
							FilterChip(
								selected = objTrigger.dismissReason == r,
								onClick = { onUpdate(objTrigger.copy(dismissReason = r)) },
								label = { Text(r.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objTrigger.stringPackageFilter,
							onValueChange = { onUpdate(objTrigger.copy(stringPackageFilter = it)) },
							label = { Text("Source App Package (Blank = Any)") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick") }
					}
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objTrigger.copy(stringPackageFilter = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}
			}
		}
	}
}


// ---- Inline Condition Refinement Card ----

/**
 * Renders refinement controls and `NOT` inversion toggle for any [SetCondition] subclass.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionEditorCard(
	objCondition: SetCondition,
	listGeofences: List<GeofenceArea>,
	onUpdate: (SetCondition) -> Unit,
	onDelete: () -> Unit
) {
	val objContext = LocalContext.current
	var boolShowAppPicker by remember { mutableStateOf(false) }
	var boolShowBtPicker by remember { mutableStateOf(false) }

	fun toggleInvert(boolInv: Boolean): SetCondition = when (objCondition) {
		is SetCondition.GeofenceCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.TimeCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.DeviceUnlockedCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.ScreenStateCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.WifiCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.MobileDataCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.BluetoothCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.MediaPlayingCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.OngoingCallCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.DndStatusCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.BatteryStatusCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.ChargingCondition -> objCondition.copy(boolInvert = boolInv)
		is SetCondition.AppRunningCondition -> objCondition.copy(boolInvert = boolInv)
	}

	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Column(
			modifier = Modifier.fillMaxWidth().padding(12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = ContextTemplateEngine.getConditionSummary(objCondition, listGeofences),
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.weight(1f)
				)
				FilterChip(
					selected = objCondition.boolInvert,
					onClick = { onUpdate(toggleInvert(!objCondition.boolInvert)) },
					label = { Text("NOT", style = MaterialTheme.typography.labelSmall) }
				)
				Spacer(modifier = Modifier.width(4.dp))
				IconButton(onClick = onDelete) {
					Icon(Icons.Default.Delete, contentDescription = "Remove Condition", tint = MaterialTheme.colorScheme.error)
				}
			}

			when (objCondition) {
				is SetCondition.GeofenceCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolInside,
							onClick = { onUpdate(objCondition.copy(boolInside = true)) },
							label = { Text("Inside") }
						)
						FilterChip(
							selected = !objCondition.boolInside,
							onClick = { onUpdate(objCondition.copy(boolInside = false)) },
							label = { Text("Outside") }
						)
					}
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listGeofences.forEach { objGeo ->
							FilterChip(
								selected = objCondition.stringGeofenceId == objGeo.stringId,
								onClick = { onUpdate(objCondition.copy(stringGeofenceId = objGeo.stringId)) },
								label = { Text(objGeo.stringName) }
							)
						}
					}
				}

				is SetCondition.TimeCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(
							onClick = {
								TimePickerDialog(
									objContext,
									{ _, intH, intM -> onUpdate(objCondition.copy(intStartHour = intH, intStartMinute = intM)) },
									objCondition.intStartHour,
									objCondition.intStartMinute,
									true
								).show()
							}
						) {
							Text(String.format(Locale.US, "Start %02d:%02d", objCondition.intStartHour, objCondition.intStartMinute))
						}
						OutlinedButton(
							onClick = {
								TimePickerDialog(
									objContext,
									{ _, intH, intM -> onUpdate(objCondition.copy(intEndHour = intH, intEndMinute = intM)) },
									objCondition.intEndHour,
									objCondition.intEndMinute,
									true
								).show()
							}
						) {
							Text(String.format(Locale.US, "End %02d:%02d", objCondition.intEndHour, objCondition.intEndMinute))
						}
					}
					DayOfWeekChips(
						listSelected = objCondition.listDaysOfWeek,
						onChanged = { onUpdate(objCondition.copy(listDaysOfWeek = it)) }
					)
				}

				is SetCondition.DeviceUnlockedCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireUnlocked,
							onClick = { onUpdate(objCondition.copy(boolRequireUnlocked = true)) },
							label = { Text("Unlocked") }
						)
						FilterChip(
							selected = !objCondition.boolRequireUnlocked,
							onClick = { onUpdate(objCondition.copy(boolRequireUnlocked = false)) },
							label = { Text("Locked") }
						)
					}
				}

				is SetCondition.ScreenStateCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireScreenOn,
							onClick = { onUpdate(objCondition.copy(boolRequireScreenOn = true)) },
							label = { Text("Screen On") }
						)
						FilterChip(
							selected = !objCondition.boolRequireScreenOn,
							onClick = { onUpdate(objCondition.copy(boolRequireScreenOn = false)) },
							label = { Text("Screen Off") }
						)
					}
				}

				is SetCondition.WifiCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = false)) },
							label = { Text("Disconnected") }
						)
					}
					if (objCondition.boolRequireConnected) {
						TextMatchEditorRow(
							stringLabel = "SSID Filter",
							matchMode = objCondition.ssidMatchMode,
							stringPattern = objCondition.stringSsidFilter,
							onChanged = { m, p -> onUpdate(objCondition.copy(ssidMatchMode = m, stringSsidFilter = p)) }
						)
					}
				}

				is SetCondition.MobileDataCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = false)) },
							label = { Text("Disconnected") }
						)
					}
				}

				is SetCondition.BluetoothCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = true)) },
							label = { Text("Connected") }
						)
						FilterChip(
							selected = !objCondition.boolRequireConnected,
							onClick = { onUpdate(objCondition.copy(boolRequireConnected = false)) },
							label = { Text("Disconnected") }
						)
						OutlinedButton(onClick = { boolShowBtPicker = true }) { Text("Pick BT") }
					}
					TextMatchEditorRow(
						stringLabel = "Bluetooth Device Name / MAC",
						matchMode = objCondition.deviceMatchMode,
						stringPattern = objCondition.stringDeviceNameOrMac,
						onChanged = { m, p -> onUpdate(objCondition.copy(deviceMatchMode = m, stringDeviceNameOrMac = p)) }
					)
					if (boolShowBtPicker) {
						PairedBluetoothPickerDialog(
							onDismiss = { boolShowBtPicker = false },
							onDevicePicked = {
								onUpdate(objCondition.copy(deviceMatchMode = TextMatchMode.EQUALS, stringDeviceNameOrMac = it))
								boolShowBtPicker = false
							}
						)
					}
				}

				is SetCondition.MediaPlayingCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequirePlaying,
							onClick = { onUpdate(objCondition.copy(boolRequirePlaying = true)) },
							label = { Text("Playing") }
						)
						FilterChip(
							selected = !objCondition.boolRequirePlaying,
							onClick = { onUpdate(objCondition.copy(boolRequirePlaying = false)) },
							label = { Text("Not Playing") }
						)
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objCondition.stringAppPackage,
							onValueChange = { onUpdate(objCondition.copy(stringAppPackage = it)) },
							label = { Text("Media App Package (Optional)") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick") }
					}
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objCondition.copy(stringAppPackage = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}

				is SetCondition.OngoingCallCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireInCall,
							onClick = { onUpdate(objCondition.copy(boolRequireInCall = true)) },
							label = { Text("In Active Call") }
						)
						FilterChip(
							selected = !objCondition.boolRequireInCall,
							onClick = { onUpdate(objCondition.copy(boolRequireInCall = false)) },
							label = { Text("Idle (No Call)") }
						)
					}
				}

				is SetCondition.DndStatusCondition -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						DndConditionMode.entries.forEach { dndMode ->
							FilterChip(
								selected = objCondition.dndConditionMode == dndMode,
								onClick = { onUpdate(objCondition.copy(dndConditionMode = dndMode)) },
								label = { Text(dndMode.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
				}

				is SetCondition.BatteryStatusCondition -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listOf(
							BatteryThresholdOperator.LESS_THAN_OR_EQUAL,
							BatteryThresholdOperator.GREATER_THAN_OR_EQUAL,
							BatteryThresholdOperator.EQUALS
						).forEach { op ->
							FilterChip(
								selected = objCondition.thresholdOperator == op,
								onClick = { onUpdate(objCondition.copy(thresholdOperator = op)) },
								label = { Text(op.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					OutlinedTextField(
						value = objCondition.intBatteryPercent.toString(),
						onValueChange = { s ->
							val intPct = s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 100) ?: 50
							onUpdate(objCondition.copy(intBatteryPercent = intPct))
						},
						label = { Text("Battery %") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}

				is SetCondition.ChargingCondition -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objCondition.boolRequireCharging,
							onClick = { onUpdate(objCondition.copy(boolRequireCharging = true)) },
							label = { Text("Charging") }
						)
						FilterChip(
							selected = !objCondition.boolRequireCharging,
							onClick = { onUpdate(objCondition.copy(boolRequireCharging = false)) },
							label = { Text("Discharging") }
						)
					}
					if (objCondition.boolRequireCharging) {
						FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							ChargingSourceFilter.entries.forEach { src ->
								FilterChip(
									selected = objCondition.sourceFilter == src,
									onClick = { onUpdate(objCondition.copy(sourceFilter = src)) },
									label = { Text(src.name) }
								)
							}
						}
					}
				}

				is SetCondition.AppRunningCondition -> {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objCondition.stringPackageName,
							onValueChange = { onUpdate(objCondition.copy(stringPackageName = it)) },
							label = { Text("Target App Package") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick App") }
					}
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						AppRunningCheckMode.entries.forEach { mode ->
							FilterChip(
								selected = objCondition.checkMode == mode,
								onClick = { onUpdate(objCondition.copy(checkMode = mode)) },
								label = { Text(mode.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objCondition.copy(stringPackageName = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}
			}
		}
	}
}


// ---- Shared Sub-Widgets ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextMatchEditorRow(
	stringLabel: String,
	matchMode: TextMatchMode,
	stringPattern: String,
	boolShowOtpPreset: Boolean = false,
	onChanged: (TextMatchMode, String) -> Unit
) {
	val listModes = remember(boolShowOtpPreset) {
		if (boolShowOtpPreset) TextMatchMode.entries else TextMatchMode.entries.filterNot { it == TextMatchMode.OTP_PRESET }
	}
	Text(stringLabel, style = MaterialTheme.typography.labelMedium)
	FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		listModes.forEach { mode ->
			FilterChip(
				selected = matchMode == mode,
				onClick = { onChanged(mode, stringPattern) },
				label = { Text(mode.name, style = MaterialTheme.typography.labelSmall) }
			)
		}
	}
	if (matchMode != TextMatchMode.ANY && matchMode != TextMatchMode.OTP_PRESET) {
		OutlinedTextField(
			value = stringPattern,
			onValueChange = { onChanged(matchMode, it) },
			label = { Text("Pattern (${matchMode.name})") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactFilterEditor(
	mode: ContactFilterMode,
	stringPattern: String,
	onChanged: (ContactFilterMode, String) -> Unit
) {
	FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		ContactFilterMode.entries.forEach { cf ->
			FilterChip(
				selected = mode == cf,
				onClick = { onChanged(cf, stringPattern) },
				label = { Text(cf.name, style = MaterialTheme.typography.labelSmall) }
			)
		}
	}
	if (mode == ContactFilterMode.SPECIFIC_NUMBERS_OR_REGEX) {
		OutlinedTextField(
			value = stringPattern,
			onValueChange = { onChanged(mode, it) },
			label = { Text("Numbers (comma-separated) or re:<regex>") },
			singleLine = true,
			modifier = Modifier.fillMaxWidth()
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekChips(
	listSelected: List<Int>,
	onChanged: (List<Int>) -> Unit
) {
	val listDays = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
	FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
		listDays.forEach { (intDay, stringShort) ->
			val boolActive = listSelected.contains(intDay)
			FilterChip(
				selected = boolActive,
				onClick = {
					val listNext = if (boolActive) listSelected - intDay else (listSelected + intDay).sorted()
					onChanged(listNext)
				},
				label = { Text(stringShort, style = MaterialTheme.typography.labelSmall) }
			)
		}
	}
}
