package com.antigravity.triggeredactions.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.RuleAuditIssue
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.ShareXProfile
import com.antigravity.triggeredactions.model.TextMatchMode
import com.antigravity.triggeredactions.model.WebhookProfile

/**
 * Performs static analysis across Action Sets to flag missing OS permissions, contradictory actions,
 * self-trigger notification loops, and incomplete action parameters.
 */
object ConflictAndAuditDetector {

	/**
	 * Audits all enabled [ActionSet]s in pure rule space plus optional runtime OS permission checks.
	 *
	 * @param listActionSets All configured Action Sets.
	 * @param listWebhookProfiles Configured webhook profiles.
	 * @param listShareXProfiles Configured ShareX profiles.
	 * @param objContext Optional Android context to inspect live OS permission grants.
	 * @return Ordered list of [RuleAuditIssue] items.
	 */
	fun auditActionSets(
		listActionSets: List<ActionSet>,
		listWebhookProfiles: List<WebhookProfile> = emptyList(),
		listShareXProfiles: List<ShareXProfile> = emptyList(),
		objContext: Context? = null
	): List<RuleAuditIssue> {
		val listIssues = mutableListOf<RuleAuditIssue>()
		val listEnabledSets = listActionSets.filter { it.boolIsEnabled }

		for (objSet in listEnabledSets) {
			if (objSet.listTriggers.isEmpty()) {
				listIssues.add(
					RuleAuditIssue(
						stringIssueId = "empty_triggers_${objSet.stringId}",
						stringTitle = "${objSet.stringName}: No Triggers Configured",
						stringDescription = "This enabled Action Set has no triggers and will only run when triggered manually.",
						stringRecommendation = "Add at least one Trigger in the Set Editor or disable the set.",
						boolIsError = false
					)
				)
			}

			if (objSet.listActions.isEmpty()) {
				listIssues.add(
					RuleAuditIssue(
						stringIssueId = "empty_actions_${objSet.stringId}",
						stringTitle = "${objSet.stringName}: No Actions Configured",
						stringDescription = "This enabled Action Set has zero actions to execute when matched.",
						stringRecommendation = "Add one or more Actions in the Set Editor.",
						boolIsError = true
					)
				)
			}

			// Self-notification loop check
			val boolHasUnfilteredNotifTrigger = objSet.listTriggers.any {
				it is SetTrigger.NotificationReceivedTrigger && it.stringPackageFilter.isBlank()
			}
			val boolSendsNotification = objSet.listActions.any { it is SetAction.SendNotificationAction }
			if (boolHasUnfilteredNotifTrigger && boolSendsNotification) {
				listIssues.add(
					RuleAuditIssue(
						stringIssueId = "notif_loop_${objSet.stringId}",
						stringTitle = "${objSet.stringName}: Broad Notification Filter",
						stringDescription = "Triggers on notifications from any app while also posting notifications (self-package notifications are automatically ignored, but cross-app notification echoes could still occur).",
						stringRecommendation = "Consider restricting the Notification Trigger to specific target app package(s).",
						boolIsError = false
					)
				)
			}

			// Action configuration completeness checks
			for (objAction in objSet.listActions) {
				when (objAction) {
					is SetAction.SendWebhookAction -> {
						val boolHasProfile = objAction.stringProfileId.isNotBlank() &&
							listWebhookProfiles.any { it.stringId == objAction.stringProfileId && it.stringUrl.isNotBlank() }
						val boolHasUrl = objAction.stringCustomUrl.startsWith("http", ignoreCase = true)
						if (!boolHasProfile && !boolHasUrl) {
							listIssues.add(
								RuleAuditIssue(
									stringIssueId = "webhook_missing_url_${objSet.stringId}_${objAction.stringId}",
									stringTitle = "${objSet.stringName}: Webhook URL Not Set",
									stringDescription = "Send Webhook action has no valid Webhook Profile or URL configured.",
									stringRecommendation = "Select a saved Webhook Profile in Settings or enter a valid https:// webhook URL.",
									boolIsError = true
								)
							)
						}
					}
					is SetAction.ShareXUploadAction -> {
						val boolHasProfile = listShareXProfiles.any { it.stringId == objAction.stringShareXProfileId }
						if (!boolHasProfile) {
							listIssues.add(
								RuleAuditIssue(
									stringIssueId = "sharex_missing_profile_${objSet.stringId}_${objAction.stringId}",
									stringTitle = "${objSet.stringName}: ShareX Uploader Profile Missing",
									stringDescription = "ShareX Upload action does not reference a saved .sxcu uploader profile.",
									stringRecommendation = "Import or create a ShareX Profile in Settings -> ShareX Uploaders and select it in this set.",
									boolIsError = true
								)
							)
						}
					}
					is SetAction.StartAppAction -> {
						if (objAction.stringPackageName.isBlank()) {
							listIssues.add(
								RuleAuditIssue(
									stringIssueId = "start_app_blank_${objSet.stringId}_${objAction.stringId}",
									stringTitle = "${objSet.stringName}: Target App Not Selected",
									stringDescription = "Start App action has an empty package name.",
									stringRecommendation = "Edit the set and pick an installed application.",
									boolIsError = true
								)
							)
						}
					}
					else -> Unit
				}
			}

			if (objContext != null) {
				auditLivePermissionsForSet(objContext, objSet, listIssues)
			}
		}

		// Pairwise contradictory DND conflict detection
		for (intI in listEnabledSets.indices) {
			for (intJ in (intI + 1) until listEnabledSets.size) {
				val objSetA = listEnabledSets[intI]
				val objSetB = listEnabledSets[intJ]
				val objDndA = objSetA.listActions.filterIsInstance<SetAction.SetDndAction>().lastOrNull()
				val objDndB = objSetB.listActions.filterIsInstance<SetAction.SetDndAction>().lastOrNull()
				if (objDndA != null && objDndB != null && objDndA.targetMode != objDndB.targetMode) {
					if (setsShareTriggerFamily(objSetA, objSetB) && conditionsPotentiallyOverlap(objSetA, objSetB)) {
						listIssues.add(
							RuleAuditIssue(
								stringIssueId = "dnd_conflict_${objSetA.stringId}_${objSetB.stringId}",
								stringTitle = "Conflicting DnD Actions: ${objSetA.stringName} ⇄ ${objSetB.stringName}",
								stringDescription = "Both sets can trigger on the same event family with overlapping conditions, but set final Do Not Disturb states to ${objDndA.targetMode.name} vs ${objDndB.targetMode.name}.",
								stringRecommendation = "Add mutually exclusive Time, Geofence, or DnD conditions so only one set matches at a time.",
								boolIsError = true
							)
						)
					}
				}
			}
		}

		return listIssues.distinctBy { it.stringIssueId }
	}

	private fun setsShareTriggerFamily(objSetA: ActionSet, objSetB: ActionSet): Boolean {
		val setClassesA = objSetA.listTriggers.map { it::class }.toSet()
		val setClassesB = objSetB.listTriggers.map { it::class }.toSet()
		return setClassesA.intersect(setClassesB).isNotEmpty()
	}

	private fun conditionsPotentiallyOverlap(objSetA: ActionSet, objSetB: ActionSet): Boolean {
		if (objSetA.listConditions.isEmpty() || objSetB.listConditions.isEmpty()) return true
		val objGeoA = objSetA.listConditions.filterIsInstance<SetCondition.GeofenceCondition>().singleOrNull()
		val objGeoB = objSetB.listConditions.filterIsInstance<SetCondition.GeofenceCondition>().singleOrNull()
		if (objGeoA != null && objGeoB != null && objGeoA.stringGeofenceId == objGeoB.stringGeofenceId) {
			val boolEffectiveInsideA = if (objGeoA.boolInvert) !objGeoA.boolInside else objGeoA.boolInside
			val boolEffectiveInsideB = if (objGeoB.boolInvert) !objGeoB.boolInside else objGeoB.boolInside
			if (boolEffectiveInsideA != boolEffectiveInsideB) return false
		}
		return true
	}

	private fun auditLivePermissionsForSet(
		objContext: Context,
		objSet: ActionSet,
		listIssues: MutableList<RuleAuditIssue>
	) {
		val boolUsesGeofence = objSet.listTriggers.any { it is SetTrigger.GeofenceTrigger } ||
			objSet.listConditions.any { it is SetCondition.GeofenceCondition }
		if (boolUsesGeofence && (!DeviceStateCollector.hasForegroundLocationPermission(objContext) ||
				!DeviceStateCollector.hasBackgroundLocationPermission(objContext))
		) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_geo_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Background Location Required",
					stringDescription = "Uses Geofences, which require 'Allow all the time' location permission.",
					stringRecommendation = "Open the Settings tab -> Location & Geofencing Permissions to grant background access.",
					boolIsError = true
				)
			)
		}

		val boolUsesCalls = objSet.listTriggers.any {
			it is SetTrigger.IncomingCallTrigger || it is SetTrigger.MissedCallTrigger || it is SetTrigger.OngoingCallTrigger
		} || objSet.listConditions.any { it is SetCondition.OngoingCallCondition }
		if (boolUsesCalls) {
			val boolHasPhone = ContextCompat.checkSelfPermission(objContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
			val boolHasCallLog = ContextCompat.checkSelfPermission(objContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
			if (!boolHasPhone || !boolHasCallLog) {
				listIssues.add(
					RuleAuditIssue(
						stringIssueId = "perm_call_${objSet.stringId}",
						stringTitle = "${objSet.stringName}: Phone & Call Log Permissions Needed",
						stringDescription = "Android 9+ requires both Phone State and Call Log permissions to read incoming caller numbers.",
						stringRecommendation = "Grant Phone & Contacts permissions in the Settings tab.",
						boolIsError = true
					)
				)
			}
		}

		if (objSet.listActions.any { it is SetAction.EndCallAction }) {
			val boolHasAnswer = ContextCompat.checkSelfPermission(objContext, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
			if (!boolHasAnswer) {
				listIssues.add(
					RuleAuditIssue(
						stringIssueId = "perm_endcall_${objSet.stringId}",
						stringTitle = "${objSet.stringName}: Answer/End Calls Permission Needed",
						stringDescription = "End Phone Call action requires ANSWER_PHONE_CALLS permission.",
						stringRecommendation = "Grant Phone & Call Control permissions in the Settings tab.",
						boolIsError = true
					)
				)
			}
		}

		if (objSet.listActions.any { it is SetAction.SendSmsAction } &&
			ContextCompat.checkSelfPermission(objContext, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED
		) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_sendsms_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Send SMS Permission Needed",
					stringDescription = "Send SMS action cannot dispatch texts until SEND_SMS is granted.",
					stringRecommendation = "Allow Restricted Settings (if prompted) and grant SMS permission in Settings.",
					boolIsError = true
				)
			)
		}

		if (objSet.listActions.any { it is SetAction.SetDndAction } && !DeviceStateCollector.hasDndPolicyPermission(objContext)) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_dnd_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Do Not Disturb Access Needed",
					stringDescription = "Set DnD action requires Notification Policy (Do Not Disturb) access.",
					stringRecommendation = "Grant Do Not Disturb Access in the Settings tab.",
					boolIsError = true
				)
			)
		}

		val boolUsesNotifOrMedia = objSet.listTriggers.any {
			it is SetTrigger.NotificationReceivedTrigger || it is SetTrigger.NotificationDismissedTrigger || it is SetTrigger.MediaPlaybackTrigger
		}
		if (boolUsesNotifOrMedia && !DeviceStateCollector.hasNotificationListenerPermission(objContext)) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_nls_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Notification Access Needed",
					stringDescription = "Notification and rich Media Session triggers require Notification Listener access.",
					stringRecommendation = "Enable Notification Access for Triggered Device Actions in Settings.",
					boolIsError = true
				)
			)
		}

		val boolUsesNotifOtp = objSet.listTriggers.any {
			it is SetTrigger.NotificationReceivedTrigger && it.bodyMatchMode == TextMatchMode.OTP_PRESET
		}
		if (boolUsesNotifOtp && Build.VERSION.SDK_INT >= 35 && !DeviceStateCollector.isSensitiveNotificationsAllowed(objContext)) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_otp_android15_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Android 15+ OTP Notification Redaction Active",
					stringDescription = "Android 15+ hides non-SMS OTP notifications from third-party listeners until RECEIVE_SENSITIVE_NOTIFICATIONS is allowed via ADB (SMS OTP triggers are unaffected).",
					stringRecommendation = "Copy and run the one-time ADB command shown in the Settings tab -> Notification Access card.",
					boolIsError = false
				)
			)
		}

		if (objSet.listConditions.any { it is SetCondition.AppRunningCondition } &&
			!DeviceStateCollector.hasUsageStatsPermission(objContext)
		) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_usage_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Usage Access Required",
					stringDescription = "App Running/Open condition uses UsageStatsManager (banking-app safe) and needs Usage Access.",
					stringRecommendation = "Grant Usage Access in the Settings tab.",
					boolIsError = true
				)
			)
		}

		val boolLaunchesUi = objSet.listActions.any {
			it is SetAction.StartAppAction || it is SetAction.StartAppShortcutAction || it is SetAction.StartAppActivityAction
		}
		if (boolLaunchesUi && !DeviceStateCollector.hasOverlayPermission(objContext)) {
			listIssues.add(
				RuleAuditIssue(
					stringIssueId = "perm_overlay_${objSet.stringId}",
					stringTitle = "${objSet.stringName}: Display Over Other Apps Needed",
					stringDescription = "Launching apps, shortcuts, or OS Settings pages from background triggers requires Display Over Other Apps permission.",
					stringRecommendation = "Grant Display Over Other Apps in the Settings tab.",
					boolIsError = true
				)
			)
		}
	}
}
