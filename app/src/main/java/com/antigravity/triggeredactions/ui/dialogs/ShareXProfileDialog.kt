package com.antigravity.triggeredactions.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.network.ShareXClient
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Dialog for importing `.sxcu` ShareX Custom Uploader configs (from file or clipboard),
 * editing fields manually, and testing text uploads.
 */
@Composable
fun ShareXProfileDialog(
	objInitialProfile: ShareXProfile?,
	onDismiss: () -> Unit,
	onSave: (ShareXProfile) -> Unit
) {
	val objContext = LocalContext.current
	val objScope = rememberCoroutineScope()

	var stringName by remember { mutableStateOf(objInitialProfile?.stringName ?: "") }
	var stringRequestUrl by remember { mutableStateOf(objInitialProfile?.stringRequestUrl ?: "") }
	var stringMethod by remember { mutableStateOf(objInitialProfile?.stringRequestMethod ?: "POST") }
	var stringBodyType by remember { mutableStateOf(objInitialProfile?.stringBodyType ?: "MultipartFormData") }
	var stringFileFormName by remember { mutableStateOf(objInitialProfile?.stringFileFormName ?: "uploaded_file") }
	var stringUrlTemplate by remember { mutableStateOf(objInitialProfile?.stringUrlTemplate ?: "{response}") }
	var stringHeadersLines by remember {
		mutableStateOf(
			objInitialProfile?.mapHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()
		)
	}
	var stringArgsLines by remember {
		mutableStateOf(
			objInitialProfile?.mapArguments?.entries?.joinToString("\n") { "${it.key}=${it.value}" }.orEmpty()
		)
	}
	var boolIsTesting by remember { mutableStateOf(false) }

	fun applyImportedProfile(objParsed: ShareXProfile) {
		stringName = objParsed.stringName
		stringRequestUrl = objParsed.stringRequestUrl
		stringMethod = objParsed.stringRequestMethod
		stringBodyType = objParsed.stringBodyType
		stringFileFormName = objParsed.stringFileFormName
		stringUrlTemplate = objParsed.stringUrlTemplate
		stringHeadersLines = objParsed.mapHeaders.entries.joinToString("\n") { "${it.key}: ${it.value}" }
		stringArgsLines = objParsed.mapArguments.entries.joinToString("\n") { "${it.key}=${it.value}" }
	}

	val objSxcuFileLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uriFile ->
		if (uriFile != null) {
			val stringContent = runCatching {
				objContext.contentResolver.openInputStream(uriFile)?.bufferedReader()?.use { it.readText() }
			}.getOrNull().orEmpty()
			val objParsed = ShareXClient.parseSxcuJson(stringContent)
			if (objParsed != null) {
				applyImportedProfile(objParsed)
				Toast.makeText(objContext, "Imported ShareX .sxcu: ${objParsed.stringName}", Toast.LENGTH_SHORT).show()
			} else {
				Toast.makeText(objContext, "Invalid .sxcu JSON file", Toast.LENGTH_LONG).show()
			}
		}
	}

	fun parseKeyVals(stringLines: String, charDelim: Char): Map<String, String> {
		return stringLines.lines()
			.mapNotNull { stringLine ->
				val intIdx = stringLine.indexOf(charDelim)
				if (intIdx > 0) {
					stringLine.substring(0, intIdx).trim() to stringLine.substring(intIdx + 1).trim()
				} else null
			}
			.toMap()
	}

	fun buildCurrentProfile(): ShareXProfile {
		return ShareXProfile(
			stringId = objInitialProfile?.stringId ?: UUID.randomUUID().toString(),
			stringName = stringName.ifBlank { "ShareX Uploader" },
			stringRequestMethod = stringMethod,
			stringRequestUrl = stringRequestUrl.trim(),
			mapHeaders = parseKeyVals(stringHeadersLines, ':'),
			stringBodyType = stringBodyType,
			stringFileFormName = stringFileFormName.ifBlank { "uploaded_file" },
			mapArguments = parseKeyVals(stringArgsLines, '='),
			stringUrlTemplate = stringUrlTemplate.ifBlank { "{response}" }
		)
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = if (objInitialProfile == null) "New ShareX (.sxcu) Profile" else "Edit ShareX Profile",
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(10.dp)
			) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					OutlinedButton(
						onClick = { objSxcuFileLauncher.launch("*/*") },
						modifier = Modifier.weight(1f)
					) {
						Text("Import .sxcu")
					}
					OutlinedButton(
						onClick = {
							val objClip = objContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
							val stringClip = objClip?.primaryClip?.getItemAt(0)?.coerceToText(objContext)?.toString().orEmpty()
							val objParsed = ShareXClient.parseSxcuJson(stringClip)
							if (objParsed != null) {
								applyImportedProfile(objParsed)
								Toast.makeText(objContext, "Imported from clipboard!", Toast.LENGTH_SHORT).show()
							} else {
								Toast.makeText(objContext, "Clipboard does not contain valid ShareX JSON", Toast.LENGTH_LONG).show()
							}
						},
						modifier = Modifier.weight(1f)
					) {
						Text("Paste JSON")
					}
				}

				OutlinedTextField(
					value = stringName,
					onValueChange = { stringName = it },
					label = { Text("Uploader Name") },
					placeholder = { Text("e.g. Shared Server") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = stringRequestUrl,
					onValueChange = { stringRequestUrl = it },
					label = { Text("RequestURL") },
					placeholder = { Text("https://example.com/Projects/Shared/?upload=post") },
					modifier = Modifier.fillMaxWidth()
				)

				Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
					listOf("MultipartFormData", "FormURLEncoded", "Binary").forEach { stringType ->
						FilterChip(
							selected = stringBodyType.equals(stringType, ignoreCase = true),
							onClick = { stringBodyType = stringType },
							label = { Text(stringType, style = MaterialTheme.typography.labelSmall) }
						)
					}
				}

				OutlinedTextField(
					value = stringFileFormName,
					onValueChange = { stringFileFormName = it },
					label = { Text("FileFormName") },
					placeholder = { Text("uploaded_file") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = stringHeadersLines,
					onValueChange = { stringHeadersLines = it },
					label = { Text("Headers (Header: Value per line)") },
					placeholder = { Text("X-Upload-Key: YOUR_KEY") },
					minLines = 2,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = stringArgsLines,
					onValueChange = { stringArgsLines = it },
					label = { Text("Arguments (key=value per line, supports \$input\$)") },
					minLines = 2,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = stringUrlTemplate,
					onValueChange = { stringUrlTemplate = it },
					label = { Text("Response URL Template") },
					placeholder = { Text("{response} or {json:data.url}") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedButton(
					enabled = !boolIsTesting && stringRequestUrl.isNotBlank(),
					onClick = {
						boolIsTesting = true
						val objTestProfile = buildCurrentProfile()
						objScope.launch {
							val objRes = ShareXClient.uploadText(
								objTestProfile,
								"Triggered Device Actions ShareX upload test",
								"triggered_actions_test.txt"
							)
							boolIsTesting = false
							val stringMsg = if (objRes.boolSuccess) "Uploaded: ${objRes.stringResultUrl}" else objRes.stringErrorDetail
							Toast.makeText(objContext, stringMsg, Toast.LENGTH_LONG).show()
						}
					},
					modifier = Modifier.fillMaxWidth()
				) {
					Text(if (boolIsTesting) "Uploading Test..." else "Test Text Upload")
				}
			}
		},
		confirmButton = {
			Button(
				enabled = stringRequestUrl.isNotBlank(),
				onClick = { onSave(buildCurrentProfile()) }
			) {
				Text("Save Uploader")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}
