package com.antigravity.triggeredactions.ui.dialogs

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antigravity.triggeredactions.data.DiscoveredShortcutInfo
import com.antigravity.triggeredactions.data.ExportedActivityInfo
import com.antigravity.triggeredactions.data.InstalledAppInfo
import com.antigravity.triggeredactions.data.InstalledAppsRepository
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Searchable dialog for picking an installed application package.
 */
@Composable
fun InstalledAppPickerDialog(
	boolLaunchableOnly: Boolean = false,
	onDismiss: () -> Unit,
	onAppSelected: (InstalledAppInfo) -> Unit
) {
	val objContext = LocalContext.current
	var listApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
	var boolLoading by remember { mutableStateOf(true) }
	var stringQuery by remember { mutableStateOf("") }

	LaunchedEffect(Unit) {
		listApps = withContext(Dispatchers.IO) {
			val listAll = InstalledAppsRepository.getInstalledApps(objContext)
			if (boolLaunchableOnly) listAll.filter { it.boolHasLaunchIntent } else listAll
		}
		boolLoading = false
	}

	val listFiltered = remember(listApps, stringQuery) {
		if (stringQuery.isBlank()) listApps
		else listApps.filter {
			it.stringAppLabel.contains(stringQuery, ignoreCase = true) ||
				it.stringPackageName.contains(stringQuery, ignoreCase = true)
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Select Application", fontWeight = FontWeight.Bold) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = stringQuery,
					onValueChange = { stringQuery = it },
					placeholder = { Text("Search app name or package...") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				if (boolLoading) {
					CircularProgressIndicator(modifier = Modifier.padding(16.dp))
				} else {
					LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
						items(listFiltered, key = { it.stringPackageName }) { objApp ->
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onAppSelected(objApp) }
									.padding(vertical = 10.dp, horizontal = 4.dp)
							) {
								Text(objApp.stringAppLabel, fontWeight = FontWeight.SemiBold)
								Text(
									objApp.stringPackageName,
									style = MaterialTheme.typography.bodySmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							HorizontalDivider()
						}
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Close") }
		}
	)
}

/**
 * Dialog for browsing exported `<activity>` components of a chosen package.
 */
@Composable
fun ExportedActivityPickerDialog(
	stringPackageName: String,
	onDismiss: () -> Unit,
	onActivitySelected: (ExportedActivityInfo) -> Unit
) {
	val objContext = LocalContext.current
	var listActivities by remember { mutableStateOf<List<ExportedActivityInfo>>(emptyList()) }
	var stringQuery by remember { mutableStateOf("") }

	LaunchedEffect(stringPackageName) {
		listActivities = withContext(Dispatchers.IO) {
			InstalledAppsRepository.getExportedActivities(objContext, stringPackageName)
		}
	}

	val listFiltered = remember(listActivities, stringQuery) {
		if (stringQuery.isBlank()) listActivities
		else listActivities.filter {
			it.stringDisplayLabel.contains(stringQuery, ignoreCase = true) ||
				it.stringClassName.contains(stringQuery, ignoreCase = true)
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Select Exported Activity", fontWeight = FontWeight.Bold) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedTextField(
					value = stringQuery,
					onValueChange = { stringQuery = it },
					placeholder = { Text("Filter activities...") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
				if (listFiltered.isEmpty()) {
					Text(
						"No exported activities found for $stringPackageName.",
						style = MaterialTheme.typography.bodySmall
					)
				} else {
					LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
						items(listFiltered, key = { it.stringClassName }) { objAct ->
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onActivitySelected(objAct) }
									.padding(vertical = 8.dp)
							) {
								Text(objAct.stringDisplayLabel, fontWeight = FontWeight.SemiBold)
								Text(
									objAct.stringClassName,
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							HorizontalDivider()
						}
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Close") }
		}
	)
}

/**
 * Dialog combining discovered static `<shortcuts>` XML items with Android's system `ACTION_CREATE_SHORTCUT` picker.
 */
@Composable
fun AppShortcutPickerDialog(
	stringPackageName: String,
	onDismiss: () -> Unit,
	onShortcutSelected: (DiscoveredShortcutInfo) -> Unit
) {
	val objContext = LocalContext.current
	var listStaticShortcuts by remember { mutableStateOf<List<DiscoveredShortcutInfo>>(emptyList()) }

	LaunchedEffect(stringPackageName) {
		if (stringPackageName.isNotBlank()) {
			listStaticShortcuts = withContext(Dispatchers.IO) {
				InstalledAppsRepository.getStaticAppShortcuts(objContext, stringPackageName)
			}
		}
	}

	@Suppress("DEPRECATION")
	val objSystemShortcutLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.StartActivityForResult()
	) { objResult ->
		val intentData = objResult.data ?: return@rememberLauncherForActivityResult
		val intentShortcut = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
		} else {
			intentData.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
		}
		val stringName = intentData.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty().ifBlank { "App Shortcut" }
		if (intentShortcut != null) {
			onShortcutSelected(
				DiscoveredShortcutInfo(
					stringPackageName = intentShortcut.`package` ?: stringPackageName,
					stringLabel = stringName,
					stringIntentUri = intentShortcut.toUri(Intent.URI_INTENT_SCHEME)
				)
			)
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Pick App Shortcut", fontWeight = FontWeight.Bold) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
				OutlinedButton(
					onClick = {
						val intentCreate = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
							if (stringPackageName.isNotBlank()) setPackage(stringPackageName)
						}
						objSystemShortcutLauncher.launch(Intent.createChooser(intentCreate, "Select Shortcut"))
					},
					modifier = Modifier.fillMaxWidth()
				) {
					Text("Open System Shortcut Creator...")
				}

				if (listStaticShortcuts.isNotEmpty()) {
					Text(
						"Static Shortcuts in $stringPackageName:",
						style = MaterialTheme.typography.labelMedium,
						fontWeight = FontWeight.Bold
					)
					LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
						items(listStaticShortcuts, key = { it.stringIntentUri }) { objSc ->
							Column(
								modifier = Modifier
									.fillMaxWidth()
									.clickable { onShortcutSelected(objSc) }
									.padding(vertical = 8.dp)
							) {
								Text(objSc.stringLabel, fontWeight = FontWeight.SemiBold)
								Text(
									objSc.stringPackageName,
									style = MaterialTheme.typography.labelSmall,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
							HorizontalDivider()
						}
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Close") }
		}
	)
}

/**
 * Dialog listing bonded/paired Bluetooth devices so the user can pick a Bluetooth name or MAC without typing.
 */
@SuppressLint("MissingPermission")
@Composable
fun PairedBluetoothPickerDialog(
	onDismiss: () -> Unit,
	onDevicePicked: (String) -> Unit
) {
	val objContext = LocalContext.current
	val listBonded = remember {
		if (!DeviceStateCollector.hasBluetoothConnectPermission(objContext)) {
			emptyList()
		} else {
			val objBtMgr = objContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
			objBtMgr?.adapter?.bondedDevices?.mapNotNull { objDev ->
				val stringName = objDev.name.orEmpty()
				val stringMac = objDev.address.orEmpty()
				if (stringName.isBlank() && stringMac.isBlank()) null else (stringName to stringMac)
			}?.sortedBy { it.first.lowercase() }.orEmpty()
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Select Paired Bluetooth Device", fontWeight = FontWeight.Bold) },
		text = {
			if (listBonded.isEmpty()) {
				Text(
					"No paired Bluetooth devices found (or Nearby Devices permission not yet granted).",
					style = MaterialTheme.typography.bodySmall
				)
			} else {
				LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
					items(listBonded, key = { it.second }) { (stringName, stringMac) ->
						Column(
							modifier = Modifier
								.fillMaxWidth()
								.clickable { onDevicePicked(stringName.ifBlank { stringMac }) }
								.padding(vertical = 10.dp)
						) {
							Text(stringName.ifBlank { "Unnamed Device" }, fontWeight = FontWeight.SemiBold)
							Text(
								stringMac,
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						HorizontalDivider()
					}
				}
			}
		},
		confirmButton = {},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text("Close") }
		}
	)
}
