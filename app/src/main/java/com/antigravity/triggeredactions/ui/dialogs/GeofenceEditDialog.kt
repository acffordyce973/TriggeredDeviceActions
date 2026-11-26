package com.antigravity.triggeredactions.ui.dialogs

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.ui.components.InteractiveGeofenceMap
import com.google.android.gms.location.LocationServices
import java.util.Locale
import java.util.UUID

/**
 * Dialog for creating or editing a [GeofenceArea] with interactive OpenStreetMap pin placement and radius slider.
 */
@SuppressLint("MissingPermission")
@Composable
fun GeofenceEditDialog(
	objInitialGeofence: GeofenceArea?,
	onDismiss: () -> Unit,
	onSave: (GeofenceArea) -> Unit
) {
	val objContext = LocalContext.current
	val objFusedClient = remember { LocationServices.getFusedLocationProviderClient(objContext) }

	var stringName by remember { mutableStateOf(objInitialGeofence?.stringName ?: "") }
	var doubleLat by remember { mutableDoubleStateOf(objInitialGeofence?.doubleLatitude ?: 51.5074) }
	var doubleLng by remember { mutableDoubleStateOf(objInitialGeofence?.doubleLongitude ?: -0.1278) }
	var floatRadius by remember { mutableFloatStateOf(objInitialGeofence?.floatRadiusMeters ?: 200f) }

	val objPermissionLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) { mapPerms ->
		val boolGranted = mapPerms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
			mapPerms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
		if (boolGranted) {
			objFusedClient.lastLocation.addOnSuccessListener { objLoc: Location? ->
				if (objLoc != null) {
					doubleLat = objLoc.latitude
					doubleLng = objLoc.longitude
				}
			}
		}
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = {
			Text(
				text = if (objInitialGeofence == null) "New Geofence Location" else "Edit Geofence Location",
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold
			)
		},
		text = {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState()),
				verticalArrangement = Arrangement.spacedBy(14.dp)
			) {
				OutlinedTextField(
					value = stringName,
					onValueChange = { stringName = it },
					label = { Text("Location Name") },
					placeholder = { Text("e.g. Home, Office, Gym") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceBetween,
					verticalAlignment = Alignment.CenterVertically
				) {
					Text(
						text = "Pin Location & Radius",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold
					)
					OutlinedButton(
						onClick = {
							val boolFine = ContextCompat.checkSelfPermission(
								objContext,
								Manifest.permission.ACCESS_FINE_LOCATION
							) == PackageManager.PERMISSION_GRANTED
							val boolCoarse = ContextCompat.checkSelfPermission(
								objContext,
								Manifest.permission.ACCESS_COARSE_LOCATION
							) == PackageManager.PERMISSION_GRANTED
							if (boolFine || boolCoarse) {
								objFusedClient.lastLocation.addOnSuccessListener { objLoc ->
									if (objLoc != null) {
										doubleLat = objLoc.latitude
										doubleLng = objLoc.longitude
									}
								}
							} else {
								objPermissionLauncher.launch(
									arrayOf(
										Manifest.permission.ACCESS_FINE_LOCATION,
										Manifest.permission.ACCESS_COARSE_LOCATION
									)
								)
							}
						}
					) {
						Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
						Spacer(modifier = Modifier.width(4.dp))
						Text("My Location")
					}
				}

				InteractiveGeofenceMap(
					doubleLatitude = doubleLat,
					doubleLongitude = doubleLng,
					floatRadiusMeters = floatRadius,
					onLocationChanged = { lat, lng ->
						doubleLat = lat
						doubleLng = lng
					}
				)

				Card(
					modifier = Modifier.fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
				) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(horizontal = 12.dp, vertical = 8.dp),
						horizontalArrangement = Arrangement.SpaceBetween,
						verticalAlignment = Alignment.CenterVertically
					) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(
								Icons.Default.LocationOn,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.primary,
								modifier = Modifier.size(18.dp)
							)
							Spacer(modifier = Modifier.width(6.dp))
							Text(
								text = String.format(Locale.US, "%.5f, %.5f", doubleLat, doubleLng),
								style = MaterialTheme.typography.labelMedium,
								fontWeight = FontWeight.SemiBold
							)
						}
						Surface(
							shape = RoundedCornerShape(6.dp),
							color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
						) {
							Text(
								text = "${floatRadius.toInt()}m radius",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
							)
						}
					}
				}

				Column {
					val intRadius = floatRadius.toInt()
					val intFeet = (floatRadius * 3.28084).toInt()
					Text(
						text = "Radius: $intRadius meters (~$intFeet ft)",
						style = MaterialTheme.typography.titleSmall,
						fontWeight = FontWeight.SemiBold
					)
					Spacer(modifier = Modifier.height(4.dp))
					Slider(
						value = floatRadius,
						onValueChange = { floatRadius = it },
						valueRange = 50f..2000f,
						steps = 38,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		},
		confirmButton = {
			Button(
				onClick = {
					onSave(
						GeofenceArea(
							stringId = objInitialGeofence?.stringId ?: UUID.randomUUID().toString(),
							stringName = stringName.ifBlank { "Location" },
							doubleLatitude = doubleLat,
							doubleLongitude = doubleLng,
							floatRadiusMeters = floatRadius
						)
					)
				}
			) {
				Text("Save Location")
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text("Cancel")
			}
		}
	)
}
