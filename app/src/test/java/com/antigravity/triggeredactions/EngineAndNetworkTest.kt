package com.antigravity.triggeredactions

import android.app.NotificationManager
import com.antigravity.triggeredactions.data.ActionsRepository
import com.antigravity.triggeredactions.engine.ConditionEvaluator
import com.antigravity.triggeredactions.engine.ConflictAndAuditDetector
import com.antigravity.triggeredactions.engine.ContextTemplateEngine
import com.antigravity.triggeredactions.model.ActionSet
import com.antigravity.triggeredactions.model.ActionsConfigData
import com.antigravity.triggeredactions.model.AppRunningCheckMode
import com.antigravity.triggeredactions.model.ChargingSourceFilter
import com.antigravity.triggeredactions.model.ConditionMatchMode
import com.antigravity.triggeredactions.model.ContactFilterMode
import com.antigravity.triggeredactions.model.DndConditionMode
import com.antigravity.triggeredactions.model.DndTargetMode
import com.antigravity.triggeredactions.model.GeofenceArea
import com.antigravity.triggeredactions.model.LiveDeviceState
import com.antigravity.triggeredactions.model.SetAction
import com.antigravity.triggeredactions.model.SetCondition
import com.antigravity.triggeredactions.model.SetTrigger
import com.antigravity.triggeredactions.model.TextMatchMode
import com.antigravity.triggeredactions.model.TriggerEventContext
import com.antigravity.triggeredactions.network.ShareXClient
import com.antigravity.triggeredactions.network.WebhookClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying OTP/regex extraction, template rendering, AND/OR/NOT condition evaluation,
 * polymorphic JSON serialization, conflict detection, Discord webhooks, and ShareX (.sxcu) uploads.
 */
class EngineAndNetworkTest {

	@Test
	fun testOtpExtractionAndFalsePositiveFiltering() {
		val objSteamOtp = ContextTemplateEngine.evaluateTextMatch(
			"Your Steam Guard verification code is 849201. Never share this code.",
			TextMatchMode.OTP_PRESET,
			""
		)
		assertTrue(objSteamOtp.boolMatched)
		assertEquals("849201", objSteamOtp.stringPrimaryMatch)

		val objHyphenOtp = ContextTemplateEngine.evaluateTextMatch(
			"Use security code G4K-912 to sign in.",
			TextMatchMode.OTP_PRESET,
			""
		)
		assertTrue(objHyphenOtp.boolMatched)
		assertEquals("G4K-912", objHyphenOtp.stringPrimaryMatch)

		val objPlainCalendarYear = ContextTemplateEngine.evaluateTextMatch(
			"Meeting notes from 2026 project roadmap at https://example.com/202699",
			TextMatchMode.OTP_PRESET,
			""
		)
		assertFalse(objPlainCalendarYear.boolMatched)
	}

	@Test
	fun testRegexSafetyAndCaptureTemplateRendering() {
		assertNotNull(ContextTemplateEngine.validateRegexSafety("(a+)+$"))

		val objCustomRegex = ContextTemplateEngine.evaluateTextMatch(
			"Order #AZ-99412 has shipped",
			TextMatchMode.REGEX,
			"Order #([A-Z0-9-]+)"
		)
		assertTrue(objCustomRegex.boolMatched)
		assertEquals("AZ-99412", objCustomRegex.stringPrimaryMatch)

		val objCtx = TriggerEventContext(
			stringCallerOrSenderNumber = "+447700900123",
			stringContactName = "Alice",
			stringNotificationAppName = "Messages",
			stringMatchedValue = objCustomRegex.stringPrimaryMatch,
			listRegexGroups = objCustomRegex.listCaptureGroups
		)
		val stringRendered = ContextTemplateEngine.renderTemplate(
			"Caller {caller_name} ({caller_number}) code={match_1}",
			objCtx,
			"Order Watcher"
		)
		assertEquals("Caller Alice (+447700900123) code=AZ-99412", stringRendered)
	}

	@Test
	fun testConditionEvaluatorAndOrInvertAndMidnightWrap() {
		val objHomeGeo = GeofenceArea(
			stringId = "geo_home",
			stringName = "Home",
			doubleLatitude = 51.5074,
			doubleLongitude = -0.1278,
			floatRadiusMeters = 200f
		)

		val objInsideState = LiveDeviceState(
			doubleLatitude = 51.5075,
			doubleLongitude = -0.1278,
			intHour = 23,
			intMinute = 30,
			boolIsCharging = false,
			intDndFilter = NotificationManager.INTERRUPTION_FILTER_PRIORITY,
			stringCurrentForegroundPackage = "com.example.targetapp"
		)

		val objAndSet = ActionSet(
			stringName = "Unplug Home App Running",
			conditionMatchMode = ConditionMatchMode.ALL_AND,
			listConditions = listOf(
				SetCondition.GeofenceCondition(stringGeofenceId = "geo_home", boolInside = true),
				SetCondition.TimeCondition(intStartHour = 22, intStartMinute = 0, intEndHour = 6, intEndMinute = 0),
				SetCondition.ChargingCondition(boolRequireCharging = true, boolInvert = true),
				SetCondition.DndStatusCondition(dndConditionMode = DndConditionMode.ANY_DND_ON),
				SetCondition.AppRunningCondition(
					stringPackageName = "com.example.targetapp",
					checkMode = AppRunningCheckMode.FOREGROUND_NOW
				)
			)
		)
		assertTrue(ConditionEvaluator.evaluateSetConditions(objAndSet, objInsideState, listOf(objHomeGeo)))

		val objFailedAndSet = objAndSet.copy(
			listConditions = objAndSet.listConditions + SetCondition.WifiCondition(boolRequireConnected = true)
		)
		assertFalse(ConditionEvaluator.evaluateSetConditions(objFailedAndSet, objInsideState, listOf(objHomeGeo)))

		val objPassingOrSet = objFailedAndSet.copy(conditionMatchMode = ConditionMatchMode.ANY_OR)
		assertTrue(ConditionEvaluator.evaluateSetConditions(objPassingOrSet, objInsideState, listOf(objHomeGeo)))
	}

	@Test
	fun testPolymorphicSerializationRoundTripAndFiveTemplates() {
		val objDefaultConfig = ActionsRepository.buildDefaultSampleConfiguration()
		assertEquals(5, objDefaultConfig.listActionSets.size)

		val stringEncoded = ActionsRepository.jsonSerializer.encodeToString(objDefaultConfig)
		val objDecoded = ActionsRepository.jsonSerializer.decodeFromString<ActionsConfigData>(stringEncoded)

		assertEquals(5, objDecoded.listActionSets.size)
		assertEquals(1, objDecoded.listGeofences.size)
		val objDndSet = objDecoded.listActionSets.first { it.stringId == "template_dnd_callback" }
		assertTrue(objDndSet.listActions.any { it is SetAction.EndCallAction })
		assertTrue(objDndSet.listActions.any { it is SetAction.WaitDelayAction })
	}

	@Test
	fun testConflictDetectorCatchesContradictoryDndSets() {
		val objSetA = ActionSet(
			stringId = "a",
			stringName = "Home Plug -> DnD Off",
			boolIsEnabled = true,
			listTriggers = listOf(SetTrigger.ChargingStateTrigger(boolOnChargingConnected = true, sourceFilter = ChargingSourceFilter.ANY)),
			listActions = listOf(SetAction.SetDndAction(targetMode = DndTargetMode.OFF_ALLOW_ALL))
		)
		val objSetB = ActionSet(
			stringId = "b",
			stringName = "Any Plug -> DnD Silence",
			boolIsEnabled = true,
			listTriggers = listOf(SetTrigger.ChargingStateTrigger(boolOnChargingConnected = true)),
			listActions = listOf(SetAction.SetDndAction(targetMode = DndTargetMode.TOTAL_SILENCE))
		)

		val listIssues = ConflictAndAuditDetector.auditActionSets(listOf(objSetA, objSetB))
		assertTrue(listIssues.any { it.stringIssueId.startsWith("dnd_conflict_") })
	}

	@Test
	fun testContactFilterMatching() {
		assertTrue(
			ContextTemplateEngine.matchesContactFilter(
				ContactFilterMode.IN_CONTACTS,
				"+447700900111",
				boolIsInContacts = true,
				boolIsStarred = false
			)
		)
		assertFalse(
			ContextTemplateEngine.matchesContactFilter(
				ContactFilterMode.IN_CONTACTS,
				"+447700900111",
				boolIsInContacts = false,
				boolIsStarred = false
			)
		)
		assertTrue(
			ContextTemplateEngine.matchesContactFilter(
				ContactFilterMode.SPECIFIC_NUMBERS_OR_REGEX,
				"+44 7700 900555",
				boolIsInContacts = false,
				boolIsStarred = false,
				stringPattern = "07700900555"
			)
		)
	}

	@Test
	fun testShareXSxcuParsingAndUploadExecution() = runTest {
		val objMockServer = MockWebServer()
		objMockServer.start()
		try {
			val stringEndpointUrl = objMockServer.url("/Projects/Shared/?upload=post").toString()
			val stringSxcu = """
				{
				  "Version": "15.0.0",
				  "Name": "Shared Uploader",
				  "DestinationType": "ImageUploader, TextUploader, FileUploader",
				  "RequestMethod": "POST",
				  "RequestURL": "$stringEndpointUrl",
				  "Headers": {
				    "X-Upload-Key": "test_secret_key"
				  },
				  "Body": "MultipartFormData",
				  "FileFormName": "uploaded_file",
				  "URL": "{response}",
				  "ErrorMessage": "{response}"
				}
			""".trimIndent()

			val objProfile = ShareXClient.parseSxcuJson(stringSxcu)
			assertNotNull(objProfile)
			assertEquals("Shared Uploader", objProfile!!.stringName)
			assertEquals("uploaded_file", objProfile.stringFileFormName)

			objMockServer.enqueue(MockResponse().setResponseCode(200).setBody("https://example.com/Projects/Shared/?view=12345"))
			val objRes = ShareXClient.uploadText(objProfile, "OTP 991204", "otp.txt")
			assertTrue(objRes.boolSuccess)
			assertEquals("https://example.com/Projects/Shared/?view=12345", objRes.stringResultUrl)

			val objRecorded = objMockServer.takeRequest()
			assertEquals("test_secret_key", objRecorded.getHeader("X-Upload-Key"))
			val stringRawBody = objRecorded.body.readUtf8()
			assertTrue(stringRawBody.contains("name=\"uploaded_file\""))
			assertTrue(stringRawBody.contains("OTP 991204"))
		} finally {
			objMockServer.shutdown()
		}
	}

	@Test
	fun testDiscordWebhookPayloadAndExecution() = runTest {
		val objMockServer = MockWebServer()
		objMockServer.start()
		try {
			objMockServer.enqueue(MockResponse().setResponseCode(204))
			val stringWebhookUrl = objMockServer.url("/api/webhooks/123/abc").toString()
			val objRes = WebhookClient.sendDiscordWebhook(
				stringWebhookUrl = stringWebhookUrl,
				stringMessageContent = "Code: **482910**",
				stringEmbedTitle = "OTP Received",
				stringUsername = "Triggered Actions"
			)
			assertTrue(objRes.boolSuccess)

			val objRecorded = objMockServer.takeRequest()
			val stringBody = objRecorded.body.readUtf8()
			assertTrue(stringBody.contains("OTP Received"))
			assertTrue(stringBody.contains("482910"))
		} finally {
			objMockServer.shutdown()
		}
	}
}
