package com.antigravity.triggeredactions.ui.dialogs

import android.widget.Toast
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.model.WebhookProfile
import com.antigravity.triggeredactions.network.WebhookClient
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Dialog for creating, editing, and live-testing a Discord or generic HTTP [WebhookProfile].
 */
@Composable
fun WebhookProfileDialog(
	objInitialProfile: WebhookProfile?,
	onDismiss: () -> Unit,
	onSave: (WebhookProfile) -> Unit
) {
	val objContext = LocalContext.current
	val objScope = rememberCoroutineScope()

	var stringName by remember { mutableStateOf(objInitialProfile?.stringName ?: "") }
	var stringUrl by remember { mutableStateOf(objInitialProfile?.stringUrl ?: "") }
	var boolIsDiscord by remember { mutableStateOf(objInitialProfile?.boolIsDiscord ?: true) }
	var stringMethod by remember { mutableStateOf(objInitialProfile?.stringDefaultMethod ?: "POST") }
	var stringUsername by remember { mutableStateOf(objInitialProfile?.stringDiscordUsername ?: "") }
	var stringHeadersLines by remember {
		mutableStateOf(
			objInitialProfile?.mapHeaders?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }.orEmpty()
		)
	}
	var boolIsTesting by remember { mutableStateOf(false) }

	fun parseHeadersMap(): Map<String, String> {
		return stringHeadersLines.lines()
			.mapNotNull { stringLine ->
				val intColon = stringLine.indexOf(':')
				if (intColon > 0) {
					stringLine.substring(0, intColon).trim() to stringLine.substring(intColon + 1).trim()
				} else null
			}
			.toMap()
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = if (objInitialProfile == null) "New Webhook Profile" else "Edit Webhook Profile",
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(12.dp)
			) {
				OutlinedTextField(
					value = stringName,
					onValueChange = { stringName = it },
					label = { Text("Profile Name") },
					placeholder = { Text("e.g. Discord OTP Channel") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedTextField(
					value = stringUrl,
					onValueChange = { stringUrl = it },
					label = { Text("Webhook URL") },
					placeholder = { Text("https://discord.com/api/webhooks/...") },
					modifier = Modifier.fillMaxWidth()
				)

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text("Discord Webhook Format", fontWeight = FontWeight.SemiBold)
					Switch(checked = boolIsDiscord, onCheckedChange = { boolIsDiscord = it })
				}

				if (boolIsDiscord) {
					OutlinedTextField(
						value = stringUsername,
						onValueChange = { stringUsername = it },
						label = { Text("Discord Bot Name (Optional)") },
						placeholder = { Text("Triggered Actions") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
				} else {
					Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						listOf("POST", "GET", "PUT", "PATCH").forEach { stringVerb ->
							FilterChip(
								selected = stringMethod == stringVerb,
								onClick = { stringMethod = stringVerb },
								label = { Text(stringVerb) }
							)
						}
					}
				}

				OutlinedTextField(
					value = stringHeadersLines,
					onValueChange = { stringHeadersLines = it },
					label = { Text("Custom Headers (One 'Header: Value' per line)") },
					placeholder = { Text("Authorization: Bearer ...") },
					minLines = 2,
					modifier = Modifier.fillMaxWidth()
				)

				OutlinedButton(
					enabled = !boolIsTesting && stringUrl.isNotBlank(),
					onClick = {
						boolIsTesting = true
						objScope.launch {
							val objRes = if (boolIsDiscord) {
								WebhookClient.sendDiscordWebhook(
									stringWebhookUrl = stringUrl,
									stringMessageContent = "Test webhook from **Triggered Device Actions**!",
									stringUsername = stringUsername,
									mapHeaders = parseHeadersMap()
								)
							} else {
								WebhookClient.executeGenericWebhook(
									stringUrl = stringUrl,
									stringMethod = stringMethod,
									mapHeaders = parseHeadersMap(),
									stringBody = "{\"test\":true,\"source\":\"Triggered Device Actions\"}"
								)
							}
							boolIsTesting = false
							Toast.makeText(objContext, objRes.stringDetail, Toast.LENGTH_LONG).show()
						}
					},
					modifier = Modifier.fillMaxWidth()
				) {
					Text(if (boolIsTesting) "Sending Test..." else "Send Test Request")
				}
			}
		},
		confirmButton = {
			Button(
				enabled = stringUrl.isNotBlank(),
				onClick = {
					onSave(
						WebhookProfile(
							stringId = objInitialProfile?.stringId ?: UUID.randomUUID().toString(),
							stringName = stringName.ifBlank { if (boolIsDiscord) "Discord Webhook" else "HTTP Webhook" },
							stringUrl = stringUrl.trim(),
							boolIsDiscord = boolIsDiscord,
							stringDefaultMethod = stringMethod,
							mapHeaders = parseHeadersMap(),
							stringDiscordUsername = stringUsername.trim()
						)
					)
				}
			) {
				Text("Save Profile")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}
