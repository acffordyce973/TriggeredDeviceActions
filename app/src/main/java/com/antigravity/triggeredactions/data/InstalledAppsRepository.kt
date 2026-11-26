package com.antigravity.triggeredactions.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import android.util.Log

/**
 * Lightweight descriptor for an installed Android package.
 */
data class InstalledAppInfo(
	val stringPackageName: String,
	val stringAppLabel: String,
	val boolHasLaunchIntent: Boolean
)

/**
 * Descriptor for an exported Activity inside an installed package.
 */
data class ExportedActivityInfo(
	val stringPackageName: String,
	val stringClassName: String,
	val stringDisplayLabel: String
)

/**
 * Descriptor for a launchable static or legacy shortcut discovered for an app.
 */
data class DiscoveredShortcutInfo(
	val stringPackageName: String,
	val stringLabel: String,
	val stringIntentUri: String
)

/**
 * Queries installed applications, exported activities, and static `<shortcuts>` XML definitions.
 */
object InstalledAppsRepository {

	private const val TAG = "InstalledAppsRepository"
	private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

	@Volatile
	private var listCachedApps: List<InstalledAppInfo>? = null

	/**
	 * Returns an A–Z sorted list of installed applications on the device.
	 *
	 * @param objContext Android context.
	 * @param boolForceRefresh Whether to bypass the in-memory cache.
	 * @return Sorted list of [InstalledAppInfo].
	 */
	fun getInstalledApps(objContext: Context, boolForceRefresh: Boolean = false): List<InstalledAppInfo> {
		if (!boolForceRefresh) {
			listCachedApps?.let { return it }
		}

		return try {
			val objPm = objContext.packageManager
			val listApps = objPm.getInstalledApplications(PackageManager.GET_META_DATA)
				.mapNotNull { objAppInfo ->
					val stringPkg = objAppInfo.packageName ?: return@mapNotNull null
					val stringLabel = objPm.getApplicationLabel(objAppInfo).toString().ifBlank { stringPkg }
					val boolLaunchable = objPm.getLaunchIntentForPackage(stringPkg) != null
					InstalledAppInfo(
						stringPackageName = stringPkg,
						stringAppLabel = stringLabel,
						boolHasLaunchIntent = boolLaunchable
					)
				}
				.sortedWith(compareByDescending<InstalledAppInfo> { it.boolHasLaunchIntent }.thenBy { it.stringAppLabel.lowercase() })
			listCachedApps = listApps
			listApps
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[InstalledAppsRepository] Error listing installed apps - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			emptyList()
		}
	}

	/**
	 * Resolves a human-readable application label for a given package name.
	 *
	 * @param objContext Android context.
	 * @param stringPackageName Target package name.
	 * @return Human-readable app name, or the package name if not found.
	 */
	fun getAppLabel(objContext: Context, stringPackageName: String): String {
		if (stringPackageName.isBlank()) return ""
		listCachedApps?.firstOrNull { it.stringPackageName == stringPackageName }?.let {
			return it.stringAppLabel
		}
		return try {
			val objPm = objContext.packageManager
			val objInfo = objPm.getApplicationInfo(stringPackageName, 0)
			objPm.getApplicationLabel(objInfo).toString().ifBlank { stringPackageName }
		} catch (objException: Exception) {
			stringPackageName
		}
	}

	/**
	 * Lists all exported `<activity>` components declared by the target package.
	 *
	 * @param objContext Android context.
	 * @param stringPackageName Package name to inspect.
	 * @return A–Z sorted list of [ExportedActivityInfo].
	 */
	@Suppress("DEPRECATION")
	fun getExportedActivities(objContext: Context, stringPackageName: String): List<ExportedActivityInfo> {
		if (stringPackageName.isBlank()) return emptyList()
		return try {
			val objPm = objContext.packageManager
			val objPkgInfo = objPm.getPackageInfo(stringPackageName, PackageManager.GET_ACTIVITIES)
			val arrayActivities = objPkgInfo.activities ?: return emptyList()
			arrayActivities
				.filter { it.exported }
				.map { objAct ->
					val stringCls = objAct.name.orEmpty()
					val stringShort = stringCls.substringAfterLast('.')
					val stringLabel = objAct.loadLabel(objPm).toString().takeIf { it.isNotBlank() } ?: stringShort
					ExportedActivityInfo(
						stringPackageName = stringPackageName,
						stringClassName = stringCls,
						stringDisplayLabel = "$stringLabel ($stringShort)"
					)
				}
				.sortedBy { it.stringDisplayLabel.lowercase() }
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[InstalledAppsRepository] Error reading activities for $stringPackageName - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			emptyList()
		}
	}

	/**
	 * Parses static `<shortcuts>` XML metadata declared on a package's launcher activities so static app shortcuts
	 * can be selected and launched directly without requiring default Home Launcher host privileges.
	 *
	 * @param objContext Android context.
	 * @param stringPackageName Target package to inspect.
	 * @return List of discovered [DiscoveredShortcutInfo] items.
	 */
	@Suppress("DEPRECATION")
	fun getStaticAppShortcuts(objContext: Context, stringPackageName: String): List<DiscoveredShortcutInfo> {
		if (stringPackageName.isBlank()) return emptyList()
		val listDiscovered = mutableListOf<DiscoveredShortcutInfo>()
		try {
			val objPm = objContext.packageManager
			val objPkgInfo = objPm.getPackageInfo(
				stringPackageName,
				PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA
			)
			val objTargetResources = objPm.getResourcesForApplication(stringPackageName)
			val arrayActivities = objPkgInfo.activities ?: return emptyList()

			for (objAct in arrayActivities) {
				val intXmlResId = objAct.metaData?.getInt("android.app.shortcuts", 0) ?: 0
				if (intXmlResId == 0) continue

				objTargetResources.getXml(intXmlResId).use { objParser ->
					var stringCurrentLabel = ""
					var intEvent = objParser.eventType
					while (intEvent != XmlResourceParser.END_DOCUMENT) {
						if (intEvent == XmlResourceParser.START_TAG) {
							when (objParser.name) {
								"shortcut" -> {
									val intLabelRes = objParser.getAttributeResourceValue(ANDROID_NS, "shortcutShortLabel", 0)
										.takeIf { it != 0 }
										?: objParser.getAttributeResourceValue(ANDROID_NS, "shortcutLongLabel", 0)
									stringCurrentLabel = if (intLabelRes != 0) {
										runCatching { objTargetResources.getString(intLabelRes) }.getOrDefault("")
									} else {
										objParser.getAttributeValue(ANDROID_NS, "shortcutId").orEmpty()
									}
								}
								"intent" -> {
									val stringAction = objParser.getAttributeValue(ANDROID_NS, "action") ?: Intent.ACTION_VIEW
									val stringTargetPkg = objParser.getAttributeValue(ANDROID_NS, "targetPackage") ?: stringPackageName
									val stringTargetClass = objParser.getAttributeValue(ANDROID_NS, "targetClass").orEmpty()
									val stringData = objParser.getAttributeValue(ANDROID_NS, "data").orEmpty()

									val intentShortcut = Intent(stringAction).apply {
										if (stringTargetClass.isNotBlank()) {
											component = ComponentName(stringTargetPkg, stringTargetClass)
										} else {
											setPackage(stringTargetPkg)
										}
										if (stringData.isNotBlank()) {
											data = android.net.Uri.parse(stringData)
										}
										addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									}
									val stringDisplay = stringCurrentLabel.ifBlank {
										stringTargetClass.substringAfterLast('.').ifBlank { stringAction }
									}
									listDiscovered.add(
										DiscoveredShortcutInfo(
											stringPackageName = stringPackageName,
											stringLabel = stringDisplay,
											stringIntentUri = intentShortcut.toUri(Intent.URI_INTENT_SCHEME)
										)
									)
								}
							}
						}
						intEvent = objParser.next()
					}
				}
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[InstalledAppsRepository] Error parsing static shortcuts for $stringPackageName - ${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
		return listDiscovered.distinctBy { it.stringIntentUri }
	}
}
