package com.antigravity.triggeredactions.ui.tabs

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antigravity.triggeredactions.engine.DeviceStateCollector
import com.antigravity.triggeredactions.model.AppSettings
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.WebhookProfile
import com.antigravity.triggeredactions.model.formatBackupFilename
import com.antigravity.triggeredactions.ui.dialogs.GeofenceEditDialog
import com.antigravity.triggeredactions.ui.dialogs.ShareXProfileDialog
import com.antigravity.triggeredactions.ui.dialogs.WebhookProfileDialog
import java.io.File
import java.util.Locale

/**
 * Settings tab containing the Permissions & System Access Hub, Backup Export/Import (plus Triggered
 * Live Wallpaper Geofence import), Geofence Locations manager, Saved Webhook Profiles, and ShareX (.sxcu) Uploaders.
 */
@Composable
fun SettingsTab(
	objSettings: AppSettings,
	listGeofences: List<GeofenceArea>,
	listWebhookProfiles: List<WebhookProfile>,
	listShareXProfiles: List<ShareXProfile>,
	onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
	onSaveGeofence: (GeofenceArea) -> Unit,
	onDeleteGeofence: (String) -> Unit,
	onSaveWebhook: (WebhookProfile) -> Unit,
	onDeleteWebhook: (String) -> Unit,
	onSaveShareX: (ShareXProfile) -> Unit,
	onDeleteShareX: (String) -> Unit,
	onExportConfig: ((File?) -> Unit) -> Unit,
	onImportConfig: (String, (Boolean) -> Unit) -> Unit,
	onImportTlwGeofences: (String, (Int) -> Unit) -> Unit
) {
	val objContext = LocalContext.current
	val objLifecycleOwner = LocalLifecycleOwner.current
	var intPermRefreshTick by remember { mutableIntStateOf(0) }

	DisposableEffect(objLifecycleOwner) {
		val objObserver = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				intPermRefreshTick++
			}
		}
		objLifecycleOwner.lifecycle.addObserver(objObserver)
		onDispose { objLifecycleOwner.lifecycle.removeObserver(objObserver) }
	}

	var boolShowGeoDialog by remember { mutableStateOf(false) }
	var objEditingGeo by remember { mutableStateOf<GeofenceArea?>(null) }

	var boolShowWebhookDialog by remember { mutableStateOf(false) }
	var objEditingWebhook by remember { mutableStateOf<WebhookProfile?>(null) }

	var boolShowShareXDialog by remember { mutableStateOf(false) }
	var objEditingShareX by remember { mutableStateOf<ShareXProfile?>(null) }

	val objRuntimePermLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) { intPermRefreshTick++ }

	val objImportFullLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uriFile ->
		if (uriFile != null) {
			val stringJson = runCatching {
				objContext.contentResolver.openInputStream(uriFile)?.bufferedReader()?.use { it.readText() }
			}.getOrNull().orEmpty()
			if (stringJson.isNotBlank()) {
				onImportConfig(stringJson) { boolOk ->
					Toast.makeText(
						objContext,
						if (boolOk) "Configuration imported!" else "Invalid configuration JSON.",
						Toast.LENGTH_SHORT
					).show()
				}
			}
		}
	}

	val objImportTlwGeoLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.GetContent()
	) { uriFile ->
		if (uriFile != null) {
			val stringJson = runCatching {
				objContext.contentResolver.openInputStream(uriFile)?.bufferedReader()?.use { it.readText() }
			}.getOrNull().orEmpty()
			if (stringJson.isNotBlank()) {
				onImportTlwGeofences(stringJson) { intCount ->
					val stringMsg = if (intCount >= 0) {
						"Imported $intCount geofence(s)!"
					} else {
						"Could not parse geofences from JSON."
					}
					Toast.makeText(objContext, stringMsg, Toast.LENGTH_SHORT).show()
				}
			}
		}
	}

	// Live permission flags recomputed whenever ON_RESUME increments intPermRefreshTick
	val boolHasNotifListener = remember(intPermRefreshTick) { DeviceStateCollector.hasNotificationListenerPermission(objContext) }
	val boolHasDndAccess = remember(intPermRefreshTick) { DeviceStateCollector.hasDndPolicyPermission(objContext) }
	val boolHasOverlay = remember(intPermRefreshTick) { DeviceStateCollector.hasOverlayPermission(objContext) }
	val boolHasUsageAccess = remember(intPermRefreshTick) { DeviceStateCollector.hasUsageStatsPermission(objContext) }
	val boolHasAllFiles = remember(intPermRefreshTick) { DeviceStateCollector.hasAllFilesPermission() }
	val boolIgnoringBattery = remember(intPermRefreshTick) { DeviceStateCollector.isIgnoringBatteryOptimizations(objContext) }
	val boolHasBgLocation = remember(intPermRefreshTick) {
		DeviceStateCollector.hasForegroundLocationPermission(objContext) &&
			DeviceStateCollector.hasBackgroundLocationPermission(objContext)
	}
	val boolHasTelephonySms = remember(intPermRefreshTick) {
		listOf(
			Manifest.permission.READ_PHONE_STATE,
			Manifest.permission.READ_CALL_LOG,
			Manifest.permission.READ_CONTACTS,
			Manifest.permission.ANSWER_PHONE_CALLS,
			Manifest.permission.RECEIVE_SMS,
			Manifest.permission.SEND_SMS
		).all { ContextCompat.checkSelfPermission(objContext, it) == PackageManager.PERMISSION_GRANTED }
	}
	val boolHasBtConnect = remember(intPermRefreshTick) { DeviceStateCollector.hasBluetoothConnectPermission(objContext) }
	val boolSensitiveNotifAllowed = remember(intPermRefreshTick) { DeviceStateCollector.isSensitiveNotificationsAllowed(objContext) }

	LazyColumn(
		modifier = Modifier.fillMaxSize().padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(14.dp)
	) {
		// 1. Sideloaded Restricted Settings Helper
		item {
			Text("Permissions & System Access Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		}

		item {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text("Sideloaded APK Tip (Android 13+)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
					Text(
						"If Notification Access or SMS toggles say 'Restricted setting', tap Open App Info -> top-right ⋮ menu -> 'Allow restricted settings'. (No AccessibilityService is included in this app so banking apps never flag it.)",
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
					OutlinedButton(
						onClick = {
							val intentInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
								data = Uri.fromParts("package", objContext.packageName, null)
							}
							objContext.startActivity(intentInfo)
						},
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Open App Info (Allow Restricted Settings)")
					}
				}
			}
		}

		item {
			PermissionStatusCard(
				stringTitle = "Phone, Call Log, Contacts, End Call & SMS",
				stringDescription = "Required for Incoming/Missed/Ongoing Call triggers, Contact checks, End Call action, and SMS triggers/actions.",
				boolGranted = boolHasTelephonySms,
				stringButtonText = "Grant Telephony & SMS Permissions",
				onGrantClick = {
					val listPerms = mutableListOf(
						Manifest.permission.READ_PHONE_STATE,
						Manifest.permission.READ_CALL_LOG,
						Manifest.permission.READ_CONTACTS,
						Manifest.permission.ANSWER_PHONE_CALLS,
						Manifest.permission.RECEIVE_SMS,
						Manifest.permission.SEND_SMS
					)
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
						listPerms.add(Manifest.permission.POST_NOTIFICATIONS)
					}
					objRuntimePermLauncher.launch(listPerms.toTypedArray())
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Do Not Disturb Access",
				stringDescription = "Allows reading and toggling global Do Not Disturb interruption filters.",
				boolGranted = boolHasDndAccess,
				stringButtonText = "Grant Do Not Disturb Access",
				onGrantClick = {
					objContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Notification & Media Listener Access",
				stringDescription = "Powers Notification Received/Dismissed triggers, OTP extraction, and Media Session tracking.",
				boolGranted = boolHasNotifListener,
				stringButtonText = "Enable Notification Access",
				onGrantClick = {
					objContext.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
				}
			)
		}

		if (Build.VERSION.SDK_INT >= 35 && !boolSensitiveNotifAllowed) {
			item {
				val stringAdbCmd = "adb shell cmd appops set --user 0 ${objContext.packageName} RECEIVE_SENSITIVE_NOTIFICATIONS allow"
				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text("Android 15+ Push OTP Notification Unlock", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
						Text(
							"SMS OTPs work automatically without ADB. To also read non-SMS push OTP notifications on Android 15+, run this one-time ADB command:",
							style = MaterialTheme.typography.bodySmall
						)
						OutlinedButton(
							onClick = {
								val objClip = objContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
								objClip?.setPrimaryClip(ClipData.newPlainText("ADB Command", stringAdbCmd))
								Toast.makeText(objContext, "ADB command copied!", Toast.LENGTH_SHORT).show()
							},
							modifier = Modifier.fillMaxWidth()
						) {
							Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(6.dp))
							Text("Copy One-Time ADB Command")
						}
					}
				}
			}
		}

		item {
			PermissionStatusCard(
				stringTitle = "Display Over Other Apps (Background Launch)",
				stringDescription = "Allows opening apps, shortcuts, and OS App Info pages reliably from background triggers.",
				boolGranted = boolHasOverlay,
				stringButtonText = "Grant Overlay Permission",
				onGrantClick = {
					val intentOverlay = Intent(
						Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
						Uri.parse("package:${objContext.packageName}")
					)
					objContext.startActivity(intentOverlay)
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Usage Access (App Running Condition)",
				stringDescription = "Detects foreground/open apps cleanly via UsageStatsManager without AccessibilityService.",
				boolGranted = boolHasUsageAccess,
				stringButtonText = "Grant Usage Access",
				onGrantClick = {
					objContext.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Background Location ('Allow all the time')",
				stringDescription = "Required for Geofence triggers/conditions and reading Wi-Fi SSIDs in the background.",
				boolGranted = boolHasBgLocation,
				stringButtonText = "Grant Location Access",
				onGrantClick = {
					if (!DeviceStateCollector.hasForegroundLocationPermission(objContext)) {
						objRuntimePermLauncher.launch(
							arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
						)
					} else {
						objRuntimePermLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
					}
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Nearby Devices (Bluetooth)",
				stringDescription = "Required on Android 12+ to identify connected Bluetooth device names.",
				boolGranted = boolHasBtConnect,
				stringButtonText = "Grant Bluetooth Permission",
				onGrantClick = {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
						objRuntimePermLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
					}
				}
			)
		}

		item {
			PermissionStatusCard(
				stringTitle = "Unrestricted Battery & All Files Access",
				stringDescription = "Prevents OEM Doze kills and enables direct filesystem paths for ShareX file uploads and custom audio.",
				boolGranted = boolIgnoringBattery && boolHasAllFiles,
				stringButtonText = if (!boolIgnoringBattery) "Disable Battery Optimization" else "Grant All Files Access",
				onGrantClick = {
					if (!boolIgnoringBattery) {
						val intentBat = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
							data = Uri.parse("package:${objContext.packageName}")
						}
						objContext.startActivity(intentBat)
					} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
						val intentFiles = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
							data = Uri.parse("package:${objContext.packageName}")
						}
						objContext.startActivity(intentFiles)
					}
				}
			)
		}

		// 2. Configuration Backup & Sharing (TLW Style)
		item {
			Text("Configuration Backup & Sharing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		}

		item {
			Card(
				modifier = Modifier.fillMaxWidth(),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
					OutlinedTextField(
						value = objSettings.stringBackupFilenameTemplate,
						onValueChange = { stringVal -> onUpdateSettings { it.copy(stringBackupFilenameTemplate = stringVal) } },
						label = { Text("Backup Filename Template") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth()
					)
					Text(
						text = "Preview: ${formatBackupFilename(objSettings.stringBackupFilenameTemplate)}",
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.primary,
						fontWeight = FontWeight.SemiBold
					)
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Text("Include Webhook & ShareX Secret Keys", style = MaterialTheme.typography.bodySmall)
						Switch(
							checked = objSettings.boolIncludeSecretsInBackup,
							onCheckedChange = { boolVal -> onUpdateSettings { it.copy(boolIncludeSecretsInBackup = boolVal) } }
						)
					}
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
						Button(
							onClick = {
								onExportConfig { fileOut ->
									if (fileOut != null && fileOut.exists()) {
										Toast.makeText(objContext, "Saved to Downloads: ${fileOut.name}", Toast.LENGTH_SHORT).show()
										runCatching {
											val uriShare = FileProvider.getUriForFile(
												objContext,
												"${objContext.packageName}.fileprovider",
												fileOut
											)
											val intentShare = Intent(Intent.ACTION_SEND).apply {
												type = "application/json"
												putExtra(Intent.EXTRA_STREAM, uriShare)
												addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
											}
											objContext.startActivity(Intent.createChooser(intentShare, "Share Configuration"))
										}
									}
								}
							},
							modifier = Modifier.weight(1f)
						) {
							Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("Export")
						}
						OutlinedButton(
							onClick = { objImportFullLauncher.launch("application/json") },
							modifier = Modifier.weight(1f)
						) {
							Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
							Spacer(modifier = Modifier.width(4.dp))
							Text("Import")
						}
					}
					OutlinedButton(
						onClick = { objImportTlwGeoLauncher.launch("application/json") },
						modifier = Modifier.fillMaxWidth()
					) {
						Text("Import Geofences from Triggered Wallpaper JSON")
					}
				}
			}
		}

		// 3. Geofence Locations Section
		item {
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("Geofence Locations (${listGeofences.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
				Button(onClick = {
					objEditingGeo = null
					boolShowGeoDialog = true
				}) {
					Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(modifier = Modifier.width(4.dp))
					Text("Add Location")
				}
			}
		}

		items(listGeofences, key = { "geo_${it.stringId}" }) { objGeo ->
			ProfileItemCard(
				stringTitle = objGeo.stringName,
				stringSubtitle = String.format(
					Locale.US,
					"%.5f, %.5f • %dm radius",
					objGeo.doubleLatitude,
					objGeo.doubleLongitude,
					objGeo.floatRadiusMeters.toInt()
				),
				onEdit = {
					objEditingGeo = objGeo
					boolShowGeoDialog = true
				},
				onDelete = { onDeleteGeofence(objGeo.stringId) }
			)
		}

		// 4. Saved Webhook Profiles
		item {
			HorizontalDivider()
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("Webhook Profiles (${listWebhookProfiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
				Button(onClick = {
					objEditingWebhook = null
					boolShowWebhookDialog = true
				}) {
					Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(modifier = Modifier.width(4.dp))
					Text("Add Webhook")
				}
			}
		}

		items(listWebhookProfiles, key = { "wh_${it.stringId}" }) { objWh ->
			ProfileItemCard(
				stringTitle = objWh.stringName,
				stringSubtitle = if (objWh.boolIsDiscord) "Discord Webhook" else "HTTP ${objWh.stringDefaultMethod}",
				onEdit = {
					objEditingWebhook = objWh
					boolShowWebhookDialog = true
				},
				onDelete = { onDeleteWebhook(objWh.stringId) }
			)
		}

		// 5. Saved ShareX (.sxcu) Profiles
		item {
			HorizontalDivider()
			Row(
				modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
				horizontalArrangement = Arrangement.SpaceBetween,
				verticalAlignment = Alignment.CenterVertically
			) {
				Text("ShareX (.sxcu) Uploaders (${listShareXProfiles.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
				Button(onClick = {
					objEditingShareX = null
					boolShowShareXDialog = true
				}) {
					Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
					Spacer(modifier = Modifier.width(4.dp))
					Text("Add .sxcu")
				}
			}
		}

		items(listShareXProfiles, key = { "sx_${it.stringId}" }) { objSx ->
			ProfileItemCard(
				stringTitle = objSx.stringName,
				stringSubtitle = "${objSx.stringRequestMethod} • ${objSx.stringRequestUrl}",
				onEdit = {
					objEditingShareX = objSx
					boolShowShareXDialog = true
				},
				onDelete = { onDeleteShareX(objSx.stringId) }
			)
		}

		// 6. General Engine Preferences
		item {
			HorizontalDivider()
			Card(
				modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
				colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
			) {
				Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Persistent Status Notification", fontWeight = FontWeight.Bold)
							Text(
								"Keeps a silent notification active so dynamic screen, power, and network triggers stay alive on strict OEMs.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = objSettings.boolShowPersistentMonitorNotification,
							onCheckedChange = { boolVal ->
								onUpdateSettings { it.copy(boolShowPersistentMonitorNotification = boolVal) }
							}
						)
					}

					HorizontalDivider()

					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text("Ignore Unsupported Hardware Conditions", fontWeight = FontWeight.Bold)
							Text(
								"Skips telephony/GPS conditions on Wi-Fi-only tablets when syncing backups across devices.",
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						Switch(
							checked = objSettings.boolIgnoreUnsupportedConditions,
							onCheckedChange = { boolVal ->
								onUpdateSettings { it.copy(boolIgnoreUnsupportedConditions = boolVal) }
							}
						)
					}
				}
			}
		}
	}

	if (boolShowGeoDialog) {
		GeofenceEditDialog(
			objInitialGeofence = objEditingGeo,
			onDismiss = { boolShowGeoDialog = false },
			onSave = {
				onSaveGeofence(it)
				boolShowGeoDialog = false
			}
		)
	}

	if (boolShowWebhookDialog) {
		WebhookProfileDialog(
			objInitialProfile = objEditingWebhook,
			onDismiss = { boolShowWebhookDialog = false },
			onSave = {
				onSaveWebhook(it)
				boolShowWebhookDialog = false
			}
		)
	}

	if (boolShowShareXDialog) {
		ShareXProfileDialog(
			objInitialProfile = objEditingShareX,
			onDismiss = { boolShowShareXDialog = false },
			onSave = {
				onSaveShareX(it)
				boolShowShareXDialog = false
			}
		)
	}
}

@Composable
private fun PermissionStatusCard(
	stringTitle: String,
	stringDescription: String,
	boolGranted: Boolean,
	stringButtonText: String,
	onGrantClick: () -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(
			containerColor = if (boolGranted) Color(0xFF1B3D2F) else MaterialTheme.colorScheme.surfaceVariant
		)
	) {
		Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(
					imageVector = if (boolGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
					contentDescription = null,
					tint = if (boolGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
					modifier = Modifier.size(22.dp)
				)
				Spacer(modifier = Modifier.width(8.dp))
				Text(
					text = stringTitle,
					style = MaterialTheme.typography.titleSmall,
					fontWeight = FontWeight.Bold,
					color = if (boolGranted) Color.White else MaterialTheme.colorScheme.onSurface
				)
			}
			Text(
				text = stringDescription,
				style = MaterialTheme.typography.bodySmall,
				color = if (boolGranted) Color(0xFFB5E4CC) else MaterialTheme.colorScheme.onSurfaceVariant
			)
			if (!boolGranted) {
				Button(onClick = onGrantClick, modifier = Modifier.fillMaxWidth()) {
					Text(stringButtonText)
				}
			}
		}
	}
}

@Composable
private fun ProfileItemCard(
	stringTitle: String,
	stringSubtitle: String,
	onEdit: () -> Unit,
	onDelete: () -> Unit
) {
	Card(
		modifier = Modifier.fillMaxWidth(),
		colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
	) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(stringTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
				Text(stringSubtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Row {
				IconButton(onClick = onEdit) {
					Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
				}
				IconButton(onClick = onDelete) {
					Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
				}
			}
		}
	}
}
