package com.antigravity.triggeredactions.engine

import com.antigravity.triggeredactions.model.ContactFilterMode
import com.antigravity.triggeredactions.model.DndTargetMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.SmsRecipientMode
import com.antigravity.triggeredactions.model.SystemActivityPreset
import com.antigravity.triggeredactions.model.TextMatchMode
import com.antigravity.triggeredactions.model.TriggerEventContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * Result of matching an input string against a configured [TextMatchMode] filter.
 */
data class PatternMatchOutcome(
	val boolMatched: Boolean,
	val stringPrimaryMatch: String = "",
	val listCaptureGroups: List<String> = emptyList()
)

/**
 * Evaluates text/regex/OTP refinements, contact filters, context token substitution, and human-readable rule summaries.
 */
object ContextTemplateEngine {

	private const val INT_MAX_REGEX_LENGTH = 250
	private const val LONG_REGEX_TIMEOUT_MS = 150L

	private val regexNestedQuantifierGuard = Pattern.compile("(\\([^)]*[+*][^)]*\\))[+*?]")

	// Non-OTP "code" collocations (e.g. "offer code", "promo code", "zip code", "error code") stripped prior to keyword scan
	private val regexNonAuthCodeCollocations = Pattern.compile(
		"\\b(offer|promo|promotional|discount|voucher|coupon|gift|referral|invite|basket|checkout|zip|post|postal|area|country|sort|swift|iban|dress|error|exit|status|color|colour|source|bar|qr)\\s+codes?\\b",
		Pattern.CASE_INSENSITIVE
	)

	// Promotional / newsletter marketing phrases that invalidate weak generic "code" matches unless strong 2FA phrases exist
	private val regexMarketingNoise = Pattern.compile(
		"(\\b\\d{1,2}%\\s*off\\b|\\bin\\s+the\\s+basket\\b|\\bat\\s+checkout\\b|\\bfree\\s+gift\\b|\\bblack\\s+friday\\b|\\bview\\s+this\\s+email\\s+online\\b|\\bunsubscribe\\b|\\boffer\\s+expires\\b)",
		Pattern.CASE_INSENSITIVE
	)

	// Strong unambiguous 2FA / authentication phrases
	private val regexStrongAuthKeyword = Pattern.compile(
		"\\b(otp|passcode|verification|2fa|two[- ]factor|one[- ]time|authenticator|login\\s+code|security\\s+code|auth(?:entication)?\\s+code|sign[- ]in\\s+code|never\\s+share|do\\s+not\\s+share)\\b",
		Pattern.CASE_INSENSITIVE
	)

	// General OTP keyword anchors (used once marketing/promo collocations have been stripped)
	private val regexOtpKeywordHint = Pattern.compile(
		"\\b(otp|passcode|verification|verify|2fa|two[- ]factor|one[- ]time|authenticate|security\\s+code|login\\s+code|auth\\s+code|pin|code)\\b",
		Pattern.CASE_INSENSITIVE
	)

	// Date & clock patterns stripped so "27th November 2026" or "11:59pm" never leak calendar years or times as OTPs
	private val regexCalendarDatesAndTimes = Pattern.compile(
		"(\\b\\d{1,2}(?:st|nd|rd|th)?\\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{4}\\b" +
			"|\\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{1,2}(?:st|nd|rd|th)?,?\\s+\\d{4}\\b" +
			"|\\b\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}\\b" +
			"|\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:am|pm)?\\b)",
		Pattern.CASE_INSENSITIVE
	)

	// Strict candidate shape: 4-8 digits, digit-hyphen-digit, or UPPERCASE-only alphanumeric hyphenated (e.g. G4K-912, G-482910)
	private val regexOtpCandidate = Pattern.compile(
		"(?<![\\dA-Za-z/_-])(?<!\\d\\.)(\\d{4,8}|\\d{3,4}-\\d{3,4}|[A-Z]{1,2}-\\d{4,8}|[A-Z0-9]{3,4}-[A-Z0-9]{3,4})(?![\\dA-Za-z/_-])(?!\\.\\w)"
	)

	/**
	 * Bounded [CharSequence] wrapper that aborts catastrophic backtracking if regex matching exceeds [LONG_REGEX_TIMEOUT_MS].
	 */
	private class TimeoutCharSequence(
		private val innerSequence: CharSequence,
		private val longDeadlineMs: Long
	) : CharSequence {
		override val length: Int get() = innerSequence.length

		override fun get(index: Int): Char {
			if (System.currentTimeMillis() > longDeadlineMs) {
				throw RuntimeException("Regex evaluation timed out")
			}
			return innerSequence[index]
		}

		override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
			return TimeoutCharSequence(innerSequence.subSequence(startIndex, endIndex), longDeadlineMs)
		}

		override fun toString(): String = innerSequence.toString()
	}


	// ---- Pattern & OTP Matching ----

	/**
	 * Evaluates [stringInput] against [matchMode] and [stringPattern], extracting primary match and capture groups.
	 *
	 * @param stringInput Text to inspect (e.g., notification body, SMS text, Wi-Fi SSID).
	 * @param matchMode Selected [TextMatchMode].
	 * @param stringPattern Filter pattern (ignored for `ANY` and `OTP_PRESET`).
	 * @return [PatternMatchOutcome] with match status and extracted values.
	 */
	fun evaluateTextMatch(
		stringInput: String,
		matchMode: TextMatchMode,
		stringPattern: String
	): PatternMatchOutcome {
		return when (matchMode) {
			TextMatchMode.ANY -> PatternMatchOutcome(boolMatched = true, stringPrimaryMatch = stringInput)

			TextMatchMode.CONTAINS -> {
				if (stringPattern.isEmpty()) {
					PatternMatchOutcome(boolMatched = true, stringPrimaryMatch = stringInput)
				} else {
					val intIdx = stringInput.indexOf(stringPattern, ignoreCase = true)
					if (intIdx >= 0) {
						val stringSub = stringInput.substring(intIdx, intIdx + stringPattern.length)
						PatternMatchOutcome(boolMatched = true, stringPrimaryMatch = stringSub)
					} else {
						PatternMatchOutcome(boolMatched = false)
					}
				}
			}

			TextMatchMode.EQUALS -> {
				val boolEq = stringInput.trim().equals(stringPattern.trim(), ignoreCase = true)
				PatternMatchOutcome(
					boolMatched = boolEq,
					stringPrimaryMatch = if (boolEq) stringInput.trim() else ""
				)
			}

			TextMatchMode.WILDCARD -> {
				if (stringPattern.isBlank()) {
					PatternMatchOutcome(boolMatched = true, stringPrimaryMatch = stringInput)
				} else {
					val stringGlobRegex = buildGlobRegex(stringPattern.trim())
					evaluateSafeRegex(stringInput, stringGlobRegex, boolFullMatch = true)
				}
			}

			TextMatchMode.REGEX -> {
				if (stringPattern.isBlank()) {
					PatternMatchOutcome(boolMatched = false)
				} else {
					evaluateSafeRegex(stringInput, stringPattern, boolFullMatch = false)
				}
			}

			TextMatchMode.OTP_PRESET -> extractOtpFromText(stringInput)
		}
	}

	/**
	 * Extracts a genuine one-time passcode (4–8 digits or uppercase/numeric hyphenated token like `123-456` / `G4K-912`)
	 * while filtering out lowercase device hostnames (e.g. `acf-gzf7`), calendar dates (`November 2026`),
	 * and retail promotional/offer codes (`offer code 33202`, `30% off`).
	 *
	 * @param stringText Incoming notification or SMS message body.
	 * @return [PatternMatchOutcome] containing the extracted OTP code in `stringPrimaryMatch`.
	 */
	fun extractOtpFromText(stringText: String): PatternMatchOutcome {
		if (stringText.isBlank()) return PatternMatchOutcome(boolMatched = false)

		// 1. Strip URLs, non-auth "promo/offer code" collocations, and calendar date/time strings (preserving length alignment with spaces)
		var stringCleaned = replaceMatchesWithSpaces(stringText, Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE))
		stringCleaned = replaceMatchesWithSpaces(stringCleaned, regexNonAuthCodeCollocations)
		stringCleaned = replaceMatchesWithSpaces(stringCleaned, regexCalendarDatesAndTimes)

		// 2. Reject retail marketing newsletters unless an unambiguous 2FA phrase is explicitly present
		val boolHasMarketingNoise = regexMarketingNoise.matcher(stringText).find()
		val boolHasStrongAuth = regexStrongAuthKeyword.matcher(stringCleaned).find()
		if (boolHasMarketingNoise && !boolHasStrongAuth) {
			return PatternMatchOutcome(boolMatched = false)
		}

		// 3. Require at least one explicit OTP/verification keyword anchor (prevents random alerts/hostnames from triggering)
		val listKeywordPositions = mutableListOf<Int>()
		val objKwMatcher = regexOtpKeywordHint.matcher(stringCleaned)
		while (objKwMatcher.find()) {
			listKeywordPositions.add(objKwMatcher.start())
		}
		if (listKeywordPositions.isEmpty()) {
			return PatternMatchOutcome(boolMatched = false)
		}

		// 4. Collect valid candidate tokens and rank by proximity to the nearest OTP keyword
		val objCandidateMatcher = regexOtpCandidate.matcher(stringCleaned)
		data class ScoredCandidate(val stringCode: String, val intDistance: Int)
		val listCandidates = mutableListOf<ScoredCandidate>()

		while (objCandidateMatcher.find()) {
			val stringToken = objCandidateMatcher.group(1) ?: continue
			val intDigitCount = stringToken.count { it.isDigit() }
			// Require at least 2 digits so words/identifiers like "PIN-1" or "AW26-A" are ignored
			if (intDigitCount < 2) continue
			// Filter standalone 4-digit plausible years (1990..2035) unless strong 4-digit PIN phrasing is right next to it
			val intAsYear = stringToken.toIntOrNull()
			val intPos = objCandidateMatcher.start()
			val intMinDist = listKeywordPositions.minOf { abs(intPos - it) }
			if (stringToken.length == 4 && intAsYear != null && intAsYear in 1990..2035 && intMinDist > 25) {
				continue
			}
			listCandidates.add(ScoredCandidate(stringToken, intMinDist))
		}

		if (listCandidates.isEmpty()) {
			return PatternMatchOutcome(boolMatched = false)
		}

		val stringBest = listCandidates.minBy { it.intDistance }.stringCode
		return PatternMatchOutcome(
			boolMatched = true,
			stringPrimaryMatch = stringBest,
			listCaptureGroups = listOf(stringBest)
		)
	}

	private fun replaceMatchesWithSpaces(stringInput: String, objPattern: Pattern): String {
		val objMatcher = objPattern.matcher(stringInput)
		val objChars = stringInput.toCharArray()
		while (objMatcher.find()) {
			for (intI in objMatcher.start() until objMatcher.end()) {
				objChars[intI] = ' '
			}
		}
		return String(objChars)
	}

	/**
	 * Validates whether a user regex string is safe to compile (within length limit and without nested quantifiers).
	 *
	 * @param stringRegex Raw regular expression pattern.
	 * @return Null if valid, or a descriptive error message if invalid/unsafe.
	 */
	fun validateRegexSafety(stringRegex: String): String? {
		if (stringRegex.length > INT_MAX_REGEX_LENGTH) {
			return "Regex exceeds maximum length of $INT_MAX_REGEX_LENGTH characters."
		}
		if (regexNestedQuantifierGuard.matcher(stringRegex).find()) {
			return "Regex contains nested quantifiers that could cause catastrophic backtracking."
		}
		return try {
			Pattern.compile(stringRegex)
			null
		} catch (objException: Exception) {
			"Invalid regex syntax: ${objException.message}"
		}
	}

	private fun evaluateSafeRegex(
		stringInput: String,
		stringPattern: String,
		boolFullMatch: Boolean
	): PatternMatchOutcome {
		if (validateRegexSafety(stringPattern) != null) {
			return PatternMatchOutcome(boolMatched = false)
		}
		return try {
			val objCompiled = Pattern.compile(stringPattern, Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
			val objBoundedInput = TimeoutCharSequence(
				stringInput,
				System.currentTimeMillis() + LONG_REGEX_TIMEOUT_MS
			)
			val objMatcher = objCompiled.matcher(objBoundedInput)
			val boolHit = if (boolFullMatch) objMatcher.matches() else objMatcher.find()
			if (!boolHit) {
				PatternMatchOutcome(boolMatched = false)
			} else {
				val listGroups = mutableListOf<String>()
				for (intIdx in 1..objMatcher.groupCount()) {
					listGroups.add(objMatcher.group(intIdx).orEmpty())
				}
				val stringPrimary = listGroups.firstOrNull { it.isNotEmpty() } ?: objMatcher.group(0).orEmpty()
				PatternMatchOutcome(
					boolMatched = true,
					stringPrimaryMatch = stringPrimary,
					listCaptureGroups = listGroups
				)
			}
		} catch (objException: Exception) {
			PatternMatchOutcome(boolMatched = false)
		}
	}

	private fun buildGlobRegex(stringGlob: String): String {
		val objBuilder = StringBuilder("^")
		for (charItem in stringGlob) {
			when (charItem) {
				'*' -> objBuilder.append(".*")
				'?' -> objBuilder.append('.')
				'.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
					objBuilder.append('\\').append(charItem)
				}
				else -> objBuilder.append(charItem)
			}
		}
		objBuilder.append('$')
		return objBuilder.toString()
	}


	// ---- Contact Filter Matching ----

	/**
	 * Evaluates whether a phone number or sender satisfies a [ContactFilterMode] constraint.
	 *
	 * @param contactFilterMode Target [ContactFilterMode].
	 * @param stringNumberOrSender Phone number or alphanumeric sender ID.
	 * @param boolIsInContacts Whether the number matched a saved contact.
	 * @param boolIsStarred Whether the matched contact is starred/favorited.
	 * @param stringPattern Optional comma-separated number list or regex when using `SPECIFIC_NUMBERS_OR_REGEX`.
	 * @return True if the caller/sender matches the filter.
	 */
	fun matchesContactFilter(
		contactFilterMode: ContactFilterMode,
		stringNumberOrSender: String,
		boolIsInContacts: Boolean,
		boolIsStarred: Boolean,
		stringPattern: String = ""
	): Boolean {
		return when (contactFilterMode) {
			ContactFilterMode.ANY -> true
			ContactFilterMode.IN_CONTACTS -> boolIsInContacts
			ContactFilterMode.NOT_IN_CONTACTS -> !boolIsInContacts
			ContactFilterMode.STARRED_CONTACTS -> boolIsStarred
			ContactFilterMode.SPECIFIC_NUMBERS_OR_REGEX -> {
				if (stringPattern.isBlank()) return true
				val stringNormalizedInput = normalizePhoneDigits(stringNumberOrSender)
				val listTokens = stringPattern.split(',').map { it.trim() }.filter { it.isNotEmpty() }
				listTokens.any { stringToken ->
					if (stringToken.startsWith("re:", ignoreCase = true)) {
						evaluateSafeRegex(stringNumberOrSender, stringToken.substring(3), boolFullMatch = false).boolMatched
					} else {
						val stringNormToken = normalizePhoneDigits(stringToken).trimStart('0')
						val stringInputSubscriber = stringNormalizedInput.trimStart('0')
						(stringNormToken.length >= 4 && (
							stringInputSubscriber.endsWith(stringNormToken) ||
								stringNormToken.endsWith(stringInputSubscriber)
							)) ||
							stringNumberOrSender.contains(stringToken, ignoreCase = true)
					}
				}
			}
		}
	}

	private fun normalizePhoneDigits(stringRaw: String): String {
		return stringRaw.filter { it.isDigit() }
	}


	// ---- Context Variable Substitution ----

	/**
	 * Substitutes `{variable}` placeholders in an action string template using values from [TriggerEventContext].
	 *
	 * @param stringTemplate Template containing `{tokens}`.
	 * @param objContext Runtime event data captured from the firing trigger.
	 * @param stringSetName Name of the executing [com.antigravity.triggeredactions.model.ActionSet].
	 * @param boolJsonEscapeValues Whether substituted values should be JSON-string escaped.
	 * @return Rendered string with placeholders expanded.
	 */
	fun renderTemplate(
		stringTemplate: String,
		objContext: TriggerEventContext,
		stringSetName: String = "",
		boolJsonEscapeValues: Boolean = false
	): String {
		if (!stringTemplate.contains('{')) return stringTemplate

		val dateEvent = Date(objContext.longTimestampMs)
		val mapTokens = mutableMapOf(
			"set_name" to stringSetName,
			"trigger" to objContext.stringTriggerSummary,
			"caller_number" to objContext.stringCallerOrSenderNumber,
			"caller_name" to objContext.stringContactName.ifBlank { objContext.stringCallerOrSenderNumber },
			"sms_sender" to objContext.stringCallerOrSenderNumber,
			"sms_body" to objContext.stringSmsBody,
			"notif_app" to objContext.stringNotificationAppName.ifBlank { objContext.stringNotificationPackage },
			"notif_pkg" to objContext.stringNotificationPackage,
			"notif_title" to objContext.stringNotificationTitle,
			"notif_text" to objContext.stringNotificationText,
			"match" to objContext.stringMatchedValue,
			"otp" to objContext.stringMatchedValue,
			"geofence_name" to objContext.stringGeofenceName,
			"geofence_transition" to objContext.stringGeofenceTransition,
			"bt_device_name" to objContext.stringBluetoothDeviceName,
			"bt_device_mac" to objContext.stringBluetoothDeviceMac,
			"wifi_ssid" to objContext.stringWifiSsid,
			"battery_level" to if (objContext.intBatteryPercent >= 0) "${objContext.intBatteryPercent}%" else "",
			"media_app" to objContext.stringMediaAppPackage,
			"media_title" to objContext.stringMediaTitle,
			"media_artist" to objContext.stringMediaArtist,
			"foreground_pkg" to objContext.stringForegroundPackage,
			"last_upload_url" to objContext.stringLastUploadUrl,
			"date" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dateEvent),
			"time" to SimpleDateFormat("HH:mm:ss", Locale.US).format(dateEvent),
			"datetime" to SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(dateEvent)
		)

		objContext.listRegexGroups.forEachIndexed { intIdx, stringGroup ->
			mapTokens["match_${intIdx + 1}"] = stringGroup
		}

		var stringOutput = stringTemplate
		for ((stringKey, stringRawVal) in mapTokens) {
			val stringSafeVal = if (boolJsonEscapeValues) escapeJsonValue(stringRawVal) else stringRawVal
			stringOutput = stringOutput.replace("{$stringKey}", stringSafeVal)
		}
		return stringOutput
	}

	private fun escapeJsonValue(stringRaw: String): String {
		return buildString(stringRaw.length) {
			for (charItem in stringRaw) {
				when (charItem) {
					'\\' -> append("\\\\")
					'"' -> append("\\\"")
					'\n' -> append("\\n")
					'\r' -> append("\\r")
					'\t' -> append("\\t")
					else -> append(charItem)
				}
			}
		}
	}


	// ---- Human-Readable Rule Summaries ----

	/**
	 * Formats a concise human-readable chip label for a [SetTrigger].
	 *
	 * @param objTrigger Trigger instance.
	 * @param listGeofences Available geofences to resolve names.
	 * @return Readable summary string.
	 */
	fun getTriggerSummary(objTrigger: SetTrigger, listGeofences: List<GeofenceArea> = emptyList()): String {
		return when (objTrigger) {
			is SetTrigger.GeofenceTrigger -> {
				val stringGeo = listGeofences.find { it.stringId == objTrigger.stringGeofenceId }?.stringName ?: "Geofence"
				"Geofence: $stringGeo (${objTrigger.transitionMode.name})"
			}
			is SetTrigger.TimeScheduleTrigger -> {
				if (objTrigger.boolIsInterval) {
					"Every ${objTrigger.intIntervalMinutes}m"
				} else {
					String.format(Locale.US, "At %02d:%02d", objTrigger.intHour, objTrigger.intMinute)
				}
			}
			is SetTrigger.DeviceLockTrigger -> if (objTrigger.boolOnUnlocked) "Device Unlocked" else "Device Locked"
			is SetTrigger.ScreenStateTrigger -> if (objTrigger.boolOnScreenOn) "Screen On" else "Screen Off"
			is SetTrigger.WifiTrigger -> {
				val stringState = if (objTrigger.boolOnConnected) "Wi-Fi Connected" else "Wi-Fi Disconnected"
				if (objTrigger.ssidMatchMode == TextMatchMode.ANY || objTrigger.stringSsidFilter.isBlank()) stringState
				else "$stringState (${objTrigger.stringSsidFilter})"
			}
			is SetTrigger.MobileDataTrigger -> if (objTrigger.boolOnConnected) "Mobile Data Connected" else "Mobile Data Disconnected"
			is SetTrigger.BluetoothTrigger -> {
				val stringState = if (objTrigger.boolOnConnected) "BT Connected" else "BT Disconnected"
				if (objTrigger.deviceMatchMode == TextMatchMode.ANY || objTrigger.stringDeviceNameOrMac.isBlank()) stringState
				else "$stringState (${objTrigger.stringDeviceNameOrMac})"
			}
			is SetTrigger.MediaPlaybackTrigger -> {
				val stringState = if (objTrigger.boolOnPlaybackStarted) "Media Playing" else "Media Stopped"
				if (objTrigger.stringAppPackage.isBlank()) stringState else "$stringState (${objTrigger.stringAppPackage.substringAfterLast('.')})"
			}
			is SetTrigger.IncomingCallTrigger -> "Incoming Call (${objTrigger.contactFilterMode.name})"
			is SetTrigger.MissedCallTrigger -> "Missed Call (${objTrigger.contactFilterMode.name})"
			is SetTrigger.OngoingCallTrigger -> "Call: ${objTrigger.callDirection.name}"
			is SetTrigger.SmsReceivedTrigger -> {
				if (objTrigger.bodyMatchMode == TextMatchMode.OTP_PRESET) "SMS Received (OTP)"
				else "SMS Received (${objTrigger.contactFilterMode.name})"
			}
			is SetTrigger.BatteryStatusTrigger -> "Battery ${objTrigger.thresholdOperator.name} ${objTrigger.intBatteryPercent}%"
			is SetTrigger.ChargingStateTrigger -> if (objTrigger.boolOnChargingConnected) "Plugged In (${objTrigger.sourceFilter.name})" else "Unplugged"
			is SetTrigger.NotificationReceivedTrigger -> {
				val stringFilter = if (objTrigger.bodyMatchMode == TextMatchMode.OTP_PRESET) "OTP Code" else objTrigger.bodyMatchMode.name
				val intExclCount = objTrigger.stringExcludedPackages.split(',').count { it.isNotBlank() }
				val stringExclSuffix = if (intExclCount > 0) " [-$intExclCount app]" else ""
				if (objTrigger.stringPackageFilter.isBlank()) "Notification ($stringFilter)$stringExclSuffix"
				else "Notif: ${objTrigger.stringPackageFilter.substringAfterLast('.')} ($stringFilter)$stringExclSuffix"
			}
			is SetTrigger.NotificationDismissedTrigger -> "Notification Dismissed (${objTrigger.dismissReason.name})"
			is SetTrigger.DeviceBootedTrigger -> {
				if (objTrigger.intBootDelaySeconds > 0) "Device Booted (+${objTrigger.intBootDelaySeconds}s)"
				else "Device Booted"
			}
		}
	}

	/**
	 * Formats a concise human-readable chip label for a [SetCondition].
	 *
	 * @param objCondition Condition instance.
	 * @param listGeofences Available geofences to resolve names.
	 * @return Readable summary string.
	 */
	fun getConditionSummary(objCondition: SetCondition, listGeofences: List<GeofenceArea> = emptyList()): String {
		val stringPrefix = if (objCondition.boolInvert) "NOT " else ""
		val stringCore = when (objCondition) {
			is SetCondition.GeofenceCondition -> {
				val stringGeo = listGeofences.find { it.stringId == objCondition.stringGeofenceId }?.stringName ?: "Geofence"
				val stringDir = if (objCondition.boolInside) "Inside" else "Outside"
				"$stringDir $stringGeo"
			}
			is SetCondition.TimeCondition -> {
				if (objCondition.boolUseTimeWindow) {
					String.format(
						Locale.US,
						"Time %02d:%02d–%02d:%02d",
						objCondition.intStartHour,
						objCondition.intStartMinute,
						objCondition.intEndHour,
						objCondition.intEndMinute
					)
				} else {
					"Selected Days/Months"
				}
			}
			is SetCondition.DeviceUnlockedCondition -> if (objCondition.boolRequireUnlocked) "Device Unlocked" else "Device Locked"
			is SetCondition.ScreenStateCondition -> if (objCondition.boolRequireScreenOn) "Screen On" else "Screen Off"
			is SetCondition.WifiCondition -> {
				val stringBase = if (objCondition.boolRequireConnected) "Wi-Fi Connected" else "Wi-Fi Disconnected"
				if (objCondition.ssidMatchMode == TextMatchMode.ANY || objCondition.stringSsidFilter.isBlank()) stringBase
				else "$stringBase (${objCondition.stringSsidFilter})"
			}
			is SetCondition.MobileDataCondition -> if (objCondition.boolRequireConnected) "Mobile Data On" else "Mobile Data Off"
			is SetCondition.BluetoothCondition -> {
				val stringBase = if (objCondition.boolRequireConnected) "BT Connected" else "BT Disconnected"
				if (objCondition.deviceMatchMode == TextMatchMode.ANY || objCondition.stringDeviceNameOrMac.isBlank()) stringBase
				else "$stringBase (${objCondition.stringDeviceNameOrMac})"
			}
			is SetCondition.MediaPlayingCondition -> if (objCondition.boolRequirePlaying) "Media Playing" else "Media Not Playing"
			is SetCondition.OngoingCallCondition -> if (objCondition.boolRequireInCall) "Ongoing Call" else "No Active Call"
			is SetCondition.DndStatusCondition -> "DnD: ${objCondition.dndConditionMode.name}"
			is SetCondition.BatteryStatusCondition -> "Battery ${objCondition.thresholdOperator.name} ${objCondition.intBatteryPercent}%"
			is SetCondition.ChargingCondition -> if (objCondition.boolRequireCharging) "Charging (${objCondition.sourceFilter.name})" else "Discharging"
			is SetCondition.AppRunningCondition -> "App Open: ${objCondition.stringPackageName.substringAfterLast('.').ifBlank { "Unset" }}"
		}
		return "$stringPrefix$stringCore"
	}

	/**
	 * Formats a concise human-readable chip label for a [SetAction].
	 *
	 * @param objAction Action instance.
	 * @return Readable summary string.
	 */
	fun getActionSummary(objAction: SetAction): String {
		return when (objAction) {
			is SetAction.PlaySoundAction -> "Play Sound (${objAction.audioStream.name})"
			is SetAction.SendNotificationAction -> "Notify: ${objAction.stringTitleTemplate}"
			is SetAction.SetDndAction -> {
				if (objAction.targetMode == DndTargetMode.OFF_ALLOW_ALL && objAction.boolSetRingerLoudWhenOff) {
					"Set DnD: OFF (Loud)"
				} else {
					"Set DnD: ${objAction.targetMode.name}"
				}
			}
			is SetAction.SetBrightnessAction -> {
				if (objAction.boolAutoBrightness) "Brightness: Auto"
				else "Brightness: ${objAction.intBrightnessPercent}%"
			}
			is SetAction.EndCallAction -> "End Phone Call"
			is SetAction.SendSmsAction -> {
				val stringDest = if (objAction.recipientMode == SmsRecipientMode.TRIGGER_CALLER_OR_SENDER) {
					"Caller/Sender"
				} else {
					objAction.stringCustomPhoneNumber.ifBlank { "Number" }
				}
				"Send SMS -> $stringDest"
			}
			is SetAction.SendWebhookAction -> if (objAction.boolIsDiscordFormat) "Discord Webhook" else "HTTP Webhook (${objAction.stringHttpMethod})"
			is SetAction.ShareXUploadAction -> if (objAction.boolUploadFile) "ShareX Upload File" else "ShareX Upload Text"
			is SetAction.StartAppAction -> "Start App: ${objAction.stringPackageName.substringAfterLast('.').ifBlank { "Unset" }}"
			is SetAction.StartAppShortcutAction -> "Shortcut: ${objAction.stringShortcutLabel.ifBlank { objAction.stringPackageName }}"
			is SetAction.StartAppActivityAction -> {
				if (objAction.systemPreset == SystemActivityPreset.NONE_CUSTOM_COMPONENT) {
					"Activity: ${objAction.stringActivityClassName.substringAfterLast('.')}"
				} else {
					"OS Activity: ${objAction.systemPreset.name}"
				}
			}
			is SetAction.WaitDelayAction -> {
				if (objAction.boolWaitUntilCallEndsFirst) {
					"Wait Call End + ${objAction.intDelaySeconds}s"
				} else {
					"Wait ${objAction.intDelaySeconds}s"
				}
			}
		}
	}
}
