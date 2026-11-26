package com.antigravity.triggeredactions.ui.dialogs

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.engine.ContextTemplateEngine
import com.antigravity.triggeredactions.model.AudioStreamTarget
import com.antigravity.triggeredactions.model.DndTargetMode
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.SmsRecipientMode
import com.antigravity.triggeredactions.model.SystemActivityPreset
import com.antigravity.triggeredactions.model.WebhookProfile

/**
 * Modal selector listing all 11 supported sequential Action types.
 */
@Composable
fun AddActionTypeDialog(
	listWebhookProfiles: List<WebhookProfile>,
	listShareXProfiles: List<ShareXProfile>,
	onDismiss: () -> Unit,
	onActionAdded: (SetAction) -> Unit
) {
	val stringDefaultWebhook = listWebhookProfiles.firstOrNull()?.stringId.orEmpty()
	val stringDefaultShareX = listShareXProfiles.firstOrNull()?.stringId.orEmpty()

	val listOptions: List<Pair<String, () -> SetAction>> = listOf(
		"Make a Sound / Vibrate" to { SetAction.PlaySoundAction() },
		"Send Notification" to { SetAction.SendNotificationAction() },
		"Set Do Not Disturb (DnD)" to { SetAction.SetDndAction() },
		"End / Reject Active Phone Call" to { SetAction.EndCallAction() },
		"Send SMS Message" to { SetAction.SendSmsAction() },
		"Send Webhook (Discord / HTTP)" to { SetAction.SendWebhookAction(stringProfileId = stringDefaultWebhook) },
		"ShareX Compatible Upload (Text / File)" to { SetAction.ShareXUploadAction(stringShareXProfileId = stringDefaultShareX) },
		"Start App" to { SetAction.StartAppAction() },
		"Start App Shortcut" to { SetAction.StartAppShortcutAction() },
		"Start App Activity / OS App Settings" to { SetAction.StartAppActivityAction() },
		"Wait / Delay (or Wait Until Call Ends)" to { SetAction.WaitDelayAction() }
	)

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Add Sequential Action", fontWeight = FontWeight.Bold) },
		text = {
			LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
				items(listOptions, key = { it.first }) { (stringLabel, funcFactory) ->
					Text(
						text = stringLabel,
						style = MaterialTheme.typography.bodyLarge,
						modifier = Modifier
							.fillMaxWidth()
							.clickable { onActionAdded(funcFactory()) }
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
 * Renders inline parameters, step ordering arrows, and placeholder variable chips for a single [SetAction].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionStepEditorCard(
	intStepNumber: Int,
	intTotalSteps: Int,
	objAction: SetAction,
	listWebhookProfiles: List<WebhookProfile>,
	listShareXProfiles: List<ShareXProfile>,
	onMoveUp: () -> Unit,
	onMoveDown: () -> Unit,
	onUpdate: (SetAction) -> Unit,
	onDelete: () -> Unit
) {
	var boolShowAppPicker by remember { mutableStateOf(false) }
	var boolShowActivityPicker by remember { mutableStateOf(false) }
	var boolShowShortcutPicker by remember { mutableStateOf(false) }

	@Suppress("DEPRECATION")
	val objRingtoneLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.StartActivityForResult()
	) { objResult ->
		val uriPicked = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			objResult.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
		} else {
			objResult.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
		}
		if (uriPicked != null && objAction is SetAction.PlaySoundAction) {
			onUpdate(objAction.copy(stringRingtoneUri = uriPicked.toString()))
		}
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
					text = "$intStepNumber. ${ContextTemplateEngine.getActionSummary(objAction)}",
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.weight(1f)
				)
				IconButton(onClick = onMoveUp, enabled = intStepNumber > 1) {
					Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
				}
				IconButton(onClick = onMoveDown, enabled = intStepNumber < intTotalSteps) {
					Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
				}
				IconButton(onClick = onDelete) {
					Icon(Icons.Default.Delete, contentDescription = "Delete Step", tint = MaterialTheme.colorScheme.error)
				}
			}

			when (objAction) {
				is SetAction.PlaySoundAction -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						AudioStreamTarget.entries.forEach { stream ->
							FilterChip(
								selected = objAction.audioStream == stream,
								onClick = { onUpdate(objAction.copy(audioStream = stream)) },
								label = { Text(stream.name) }
							)
						}
					}
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
						OutlinedButton(
							onClick = {
								val intentRing = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
									putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
									putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
								}
								objRingtoneLauncher.launch(intentRing)
							}
						) {
							Text(if (objAction.stringRingtoneUri.isBlank()) "Pick System Sound" else "Change Sound")
						}
						Text("Vibrate", modifier = Modifier.padding(start = 8.dp))
						Switch(
							checked = objAction.boolVibrate,
							onCheckedChange = { onUpdate(objAction.copy(boolVibrate = it)) }
						)
					}
					OutlinedTextField(
						value = objAction.stringCustomFilePath,
						onValueChange = { onUpdate(objAction.copy(stringCustomFilePath = it)) },
						label = { Text("Or Direct Audio File Path (e.g. /sdcard/Alarms/alert.mp3)") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}

				is SetAction.SendNotificationAction -> {
					OutlinedTextField(
						value = objAction.stringTitleTemplate,
						onValueChange = { onUpdate(objAction.copy(stringTitleTemplate = it)) },
						label = { Text("Notification Title") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					OutlinedTextField(
						value = objAction.stringMessageTemplate,
						onValueChange = { onUpdate(objAction.copy(stringMessageTemplate = it)) },
						label = { Text("Notification Message") },
						minLines = 2,
						modifier = Modifier.fillMaxWidth()
					)
					VariableTokenInsertRow { token ->
						onUpdate(objAction.copy(stringMessageTemplate = objAction.stringMessageTemplate + token))
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
						Text("High Priority Heads-Up")
						Switch(
							checked = objAction.boolHighPriority,
							onCheckedChange = { onUpdate(objAction.copy(boolHighPriority = it)) }
						)
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
						Text("Include 'Copy {match}' Button")
						Switch(
							checked = objAction.boolAddCopyMatchButton,
							onCheckedChange = { onUpdate(objAction.copy(boolAddCopyMatchButton = it)) }
						)
					}
				}

				is SetAction.SetDndAction -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						DndTargetMode.entries.forEach { dnd ->
							FilterChip(
								selected = objAction.targetMode == dnd,
								onClick = { onUpdate(objAction.copy(targetMode = dnd)) },
								label = { Text(dnd.name) }
							)
						}
					}
				}

				is SetAction.EndCallAction -> {
					Text(
						"Ends/declines the active or ringing phone call via TelecomManager (ideal before opening a temporary DnD callback window).",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}

				is SetAction.SendSmsAction -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = objAction.recipientMode == SmsRecipientMode.TRIGGER_CALLER_OR_SENDER,
							onClick = { onUpdate(objAction.copy(recipientMode = SmsRecipientMode.TRIGGER_CALLER_OR_SENDER)) },
							label = { Text("Trigger Caller / Sender") }
						)
						FilterChip(
							selected = objAction.recipientMode == SmsRecipientMode.CUSTOM_NUMBER,
							onClick = { onUpdate(objAction.copy(recipientMode = SmsRecipientMode.CUSTOM_NUMBER)) },
							label = { Text("Custom Number") }
						)
					}
					if (objAction.recipientMode == SmsRecipientMode.CUSTOM_NUMBER) {
						OutlinedTextField(
							value = objAction.stringCustomPhoneNumber,
							onValueChange = { onUpdate(objAction.copy(stringCustomPhoneNumber = it)) },
							label = { Text("Recipient Phone Number") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}
					OutlinedTextField(
						value = objAction.stringMessageTemplate,
						onValueChange = { onUpdate(objAction.copy(stringMessageTemplate = it)) },
						label = { Text("SMS Message Body") },
						minLines = 2,
						modifier = Modifier.fillMaxWidth()
					)
					VariableTokenInsertRow { token ->
						onUpdate(objAction.copy(stringMessageTemplate = objAction.stringMessageTemplate + token))
					}
				}

				is SetAction.SendWebhookAction -> {
					if (listWebhookProfiles.isNotEmpty()) {
						Text("Saved Webhook Profile:", style = MaterialTheme.typography.labelMedium)
						FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							FilterChip(
								selected = objAction.stringProfileId.isBlank(),
								onClick = { onUpdate(objAction.copy(stringProfileId = "")) },
								label = { Text("Custom URL") }
							)
							listWebhookProfiles.forEach { prof ->
								FilterChip(
									selected = objAction.stringProfileId == prof.stringId,
									onClick = {
										onUpdate(
											objAction.copy(
												stringProfileId = prof.stringId,
												boolIsDiscordFormat = prof.boolIsDiscord
											)
										)
									},
									label = { Text(prof.stringName) }
								)
							}
						}
					}
					if (objAction.stringProfileId.isBlank()) {
						OutlinedTextField(
							value = objAction.stringCustomUrl,
							onValueChange = { onUpdate(objAction.copy(stringCustomUrl = it)) },
							label = { Text("Webhook URL") },
							placeholder = { Text("https://discord.com/api/webhooks/...") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
							Text("Format as Discord Webhook")
							Switch(
								checked = objAction.boolIsDiscordFormat,
								onCheckedChange = { onUpdate(objAction.copy(boolIsDiscordFormat = it)) }
							)
						}
					}
					if (objAction.boolIsDiscordFormat) {
						OutlinedTextField(
							value = objAction.stringDiscordEmbedTitle,
							onValueChange = { onUpdate(objAction.copy(stringDiscordEmbedTitle = it)) },
							label = { Text("Discord Embed Title (Optional)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}
					OutlinedTextField(
						value = objAction.stringBodyOrMessageTemplate,
						onValueChange = { onUpdate(objAction.copy(stringBodyOrMessageTemplate = it)) },
						label = { Text(if (objAction.boolIsDiscordFormat) "Discord Message Content" else "HTTP Request Body") },
						minLines = 2,
						modifier = Modifier.fillMaxWidth()
					)
					VariableTokenInsertRow { token ->
						onUpdate(objAction.copy(stringBodyOrMessageTemplate = objAction.stringBodyOrMessageTemplate + token))
					}
				}

				is SetAction.ShareXUploadAction -> {
					Text("ShareX (.sxcu) Uploader Profile:", style = MaterialTheme.typography.labelMedium)
					if (listShareXProfiles.isEmpty()) {
						Text(
							"Add or import a .sxcu uploader profile in the Settings tab first.",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.error
						)
					} else {
						FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
							listShareXProfiles.forEach { prof ->
								FilterChip(
									selected = objAction.stringShareXProfileId == prof.stringId,
									onClick = { onUpdate(objAction.copy(stringShareXProfileId = prof.stringId)) },
									label = { Text(prof.stringName) }
								)
							}
						}
					}
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						FilterChip(
							selected = !objAction.boolUploadFile,
							onClick = { onUpdate(objAction.copy(boolUploadFile = false)) },
							label = { Text("Upload Text") }
						)
						FilterChip(
							selected = objAction.boolUploadFile,
							onClick = { onUpdate(objAction.copy(boolUploadFile = true)) },
							label = { Text("Upload File") }
						)
					}
					if (!objAction.boolUploadFile) {
						OutlinedTextField(
							value = objAction.stringTextFileNameTemplate,
							onValueChange = { onUpdate(objAction.copy(stringTextFileNameTemplate = it)) },
							label = { Text("Filename (e.g. note_{datetime}.txt)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
						OutlinedTextField(
							value = objAction.stringTextContentTemplate,
							onValueChange = { onUpdate(objAction.copy(stringTextContentTemplate = it)) },
							label = { Text("Text Content to Upload") },
							minLines = 2,
							modifier = Modifier.fillMaxWidth()
						)
						VariableTokenInsertRow { token ->
							onUpdate(objAction.copy(stringTextContentTemplate = objAction.stringTextContentTemplate + token))
						}
					} else {
						OutlinedTextField(
							value = objAction.stringFilePathOrDirectory,
							onValueChange = { onUpdate(objAction.copy(stringFilePathOrDirectory = it)) },
							label = { Text("File Path or Folder Path (e.g. /sdcard/Pictures/Screenshots)") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
							Text("Pick Latest File in Folder")
							Switch(
								checked = objAction.boolPickLatestFileInDir,
								onCheckedChange = { onUpdate(objAction.copy(boolPickLatestFileInDir = it)) }
							)
						}
					}
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
						Text("Copy Returned URL to Clipboard")
						Switch(
							checked = objAction.boolCopyUrlToClipboard,
							onCheckedChange = { onUpdate(objAction.copy(boolCopyUrlToClipboard = it)) }
						)
					}
				}

				is SetAction.StartAppAction -> {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedTextField(
							value = objAction.stringPackageName,
							onValueChange = { onUpdate(objAction.copy(stringPackageName = it)) },
							label = { Text("Package Name") },
							singleLine = true,
							modifier = Modifier.weight(1f)
						)
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick App") }
					}
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							boolLaunchableOnly = true,
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objAction.copy(stringPackageName = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
				}

				is SetAction.StartAppShortcutAction -> {
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("1. Filter App") }
						OutlinedButton(onClick = { boolShowShortcutPicker = true }) { Text("2. Pick Shortcut") }
					}
					OutlinedTextField(
						value = objAction.stringShortcutLabel,
						onValueChange = { onUpdate(objAction.copy(stringShortcutLabel = it)) },
						label = { Text("Shortcut Label") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					OutlinedTextField(
						value = objAction.stringIntentUri,
						onValueChange = { onUpdate(objAction.copy(stringIntentUri = it)) },
						label = { Text("Shortcut Intent URI") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objAction.copy(stringPackageName = it.stringPackageName))
								boolShowAppPicker = false
								boolShowShortcutPicker = true
							}
						)
					}
					if (boolShowShortcutPicker) {
						AppShortcutPickerDialog(
							stringPackageName = objAction.stringPackageName,
							onDismiss = { boolShowShortcutPicker = false },
							onShortcutSelected = { sc ->
								onUpdate(
									objAction.copy(
										stringPackageName = sc.stringPackageName,
										stringShortcutLabel = sc.stringLabel,
										stringIntentUri = sc.stringIntentUri
									)
								)
								boolShowShortcutPicker = false
							}
						)
					}
				}

				is SetAction.StartAppActivityAction -> {
					FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						SystemActivityPreset.entries.forEach { preset ->
							FilterChip(
								selected = objAction.systemPreset == preset,
								onClick = { onUpdate(objAction.copy(systemPreset = preset)) },
								label = { Text(preset.name, style = MaterialTheme.typography.labelSmall) }
							)
						}
					}

					if (objAction.systemPreset == SystemActivityPreset.APP_DETAILS_SETTINGS ||
						objAction.systemPreset == SystemActivityPreset.NONE_CUSTOM_COMPONENT ||
						objAction.systemPreset == SystemActivityPreset.CUSTOM_URI_DEEPLINK
					) {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							OutlinedTextField(
								value = objAction.stringTargetPackage,
								onValueChange = { onUpdate(objAction.copy(stringTargetPackage = it)) },
								label = { Text("Target Package (or {foreground_pkg})") },
								singleLine = true,
								modifier = Modifier.weight(1f)
							)
							OutlinedButton(onClick = { boolShowAppPicker = true }) { Text("Pick App") }
						}
					}

					if (objAction.systemPreset == SystemActivityPreset.NONE_CUSTOM_COMPONENT) {
						Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
							OutlinedTextField(
								value = objAction.stringActivityClassName,
								onValueChange = { onUpdate(objAction.copy(stringActivityClassName = it)) },
								label = { Text("Activity Class Name") },
								singleLine = true,
								modifier = Modifier.weight(1f)
							)
							if (objAction.stringTargetPackage.isNotBlank()) {
								OutlinedButton(onClick = { boolShowActivityPicker = true }) { Text("Browse") }
							}
						}
					}

					if (objAction.systemPreset == SystemActivityPreset.CUSTOM_URI_DEEPLINK) {
						OutlinedTextField(
							value = objAction.stringCustomDataUri,
							onValueChange = { onUpdate(objAction.copy(stringCustomDataUri = it)) },
							label = { Text("Deep Link / Data URI") },
							singleLine = true,
							modifier = Modifier.fillMaxWidth()
						)
					}

					if (boolShowAppPicker) {
						InstalledAppPickerDialog(
							onDismiss = { boolShowAppPicker = false },
							onAppSelected = {
								onUpdate(objAction.copy(stringTargetPackage = it.stringPackageName))
								boolShowAppPicker = false
							}
						)
					}
					if (boolShowActivityPicker) {
						ExportedActivityPickerDialog(
							stringPackageName = objAction.stringTargetPackage,
							onDismiss = { boolShowActivityPicker = false },
							onActivitySelected = { act ->
								onUpdate(objAction.copy(stringActivityClassName = act.stringClassName))
								boolShowActivityPicker = false
							}
						)
					}
				}

				is SetAction.WaitDelayAction -> {
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Wait Until Phone Call Ends First", fontWeight = FontWeight.SemiBold)
							Text(
								"Pauses until active/ringing call returns to IDLE before starting the delay countdown.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = objAction.boolWaitUntilCallEndsFirst,
							onCheckedChange = { onUpdate(objAction.copy(boolWaitUntilCallEndsFirst = it)) }
						)
					}
					OutlinedTextField(
						value = objAction.intDelaySeconds.toString(),
						onValueChange = { s ->
							val intSecs = s.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 86400) ?: 0
							onUpdate(objAction.copy(intDelaySeconds = intSecs))
						},
						label = { Text("Delay Seconds") },
						keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		}
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VariableTokenInsertRow(onInsertToken: (String) -> Unit) {
	val listTokens = listOf(
		"{match}",
		"{caller_number}",
		"{caller_name}",
		"{notif_title}",
		"{notif_text}",
		"{sms_body}",
		"{last_upload_url}",
		"{datetime}"
	)
	FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
		listTokens.forEach { stringToken ->
			SuggestionChip(
				onClick = { onInsertToken(stringToken) },
				label = { Text(stringToken, style = MaterialTheme.typography.labelSmall) }
			)
		}
	}
}
