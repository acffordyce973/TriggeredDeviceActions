package com.antigravity.triggeredactions.network

import android.util.Log
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Result of executing a Discord or generic HTTP webhook request.
 */
data class WebhookExecutionResult(
	val boolSuccess: Boolean,
	val intStatusCode: Int,
	val stringDetail: String
)

/**
 * Dispatches Discord webhooks (content + optional rich embed) and generic HTTP REST webhooks.
 */
object WebhookClient {

	private const val TAG = "WebhookClient"

	val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(20, TimeUnit.SECONDS)
		.writeTimeout(20, TimeUnit.SECONDS)
		.build()

	/**
	 * Sends a Discord-formatted JSON payload to a Discord Webhook URL.
	 *
	 * @param stringWebhookUrl Target Discord webhook URL.
	 * @param stringMessageContent Main text message (`content`).
	 * @param stringEmbedTitle Optional embed card title; if non-blank, wraps details in an embed.
	 * @param stringUsername Optional custom bot display name.
	 * @param mapHeaders Optional extra HTTP headers.
	 * @return [WebhookExecutionResult] describing HTTP status and outcome.
	 */
	suspend fun sendDiscordWebhook(
		stringWebhookUrl: String,
		stringMessageContent: String,
		stringEmbedTitle: String = "",
		stringUsername: String = "",
		mapHeaders: Map<String, String> = emptyMap()
	): WebhookExecutionResult = withContext(Dispatchers.IO) {
		val stringJsonBody = buildDiscordJsonPayload(
			stringContent = stringMessageContent,
			stringEmbedTitle = stringEmbedTitle,
			stringUsername = stringUsername
		)

		executeGenericWebhook(
			stringUrl = stringWebhookUrl,
			stringMethod = "POST",
			mapHeaders = mapHeaders + ("Content-Type" to "application/json"),
			stringBody = stringJsonBody
		)
	}

	/**
	 * Constructs the JSON string body for a Discord webhook call.
	 *
	 * @param stringContent Primary content text.
	 * @param stringEmbedTitle Optional embed title.
	 * @param stringUsername Optional webhook username override.
	 * @return Valid JSON string for Discord's webhook endpoint.
	 */
	fun buildDiscordJsonPayload(
		stringContent: String,
		stringEmbedTitle: String = "",
		stringUsername: String = ""
	): String {
		val objPayload = buildJsonObject {
			if (stringUsername.isNotBlank()) {
				put("username", stringUsername)
			}
			if (stringEmbedTitle.isNotBlank()) {
				put(
					"embeds",
					buildJsonArray {
						add(
							buildJsonObject {
								put("title", stringEmbedTitle)
								put("description", stringContent)
								put("color", 0x38BDF8)
								put("timestamp", Instant.now().toString())
							}
						)
					}
				)
			} else {
				put("content", stringContent)
			}
		}
		return objPayload.toString()
	}

	/**
	 * Executes an arbitrary HTTP request (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`) with custom headers and body.
	 *
	 * @param stringUrl Target HTTP/HTTPS endpoint.
	 * @param stringMethod HTTP verb.
	 * @param mapHeaders Request headers.
	 * @param stringBody Rendered request payload.
	 * @return [WebhookExecutionResult] with status code and detail message.
	 */
	suspend fun executeGenericWebhook(
		stringUrl: String,
		stringMethod: String = "POST",
		mapHeaders: Map<String, String> = emptyMap(),
		stringBody: String = ""
	): WebhookExecutionResult = withContext(Dispatchers.IO) {
		val stringCleanUrl = stringUrl.trim()
		if (!stringCleanUrl.startsWith("http://", ignoreCase = true) &&
			!stringCleanUrl.startsWith("https://", ignoreCase = true)
		) {
			return@withContext WebhookExecutionResult(
				boolSuccess = false,
				intStatusCode = 0,
				stringDetail = "Invalid webhook URL: $stringCleanUrl"
			)
		}

		try {
			val stringVerb = stringMethod.trim().uppercase().ifBlank { "POST" }
			val stringTrimmedBody = stringBody.trim()
			val stringDefaultMime = if (stringTrimmedBody.startsWith("{") || stringTrimmedBody.startsWith("[")) {
				"application/json; charset=utf-8"
			} else {
				"text/plain; charset=utf-8"
			}
			val stringEffectiveMime = mapHeaders.entries
				.firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
				?.value ?: stringDefaultMime

			val objReqBuilder = Request.Builder().url(stringCleanUrl)
			for ((stringHeaderKey, stringHeaderVal) in mapHeaders) {
				if (stringHeaderKey.isNotBlank()) {
					objReqBuilder.header(stringHeaderKey.trim(), stringHeaderVal)
				}
			}

			if (stringVerb == "GET" || stringVerb == "HEAD") {
				objReqBuilder.method(stringVerb, null)
			} else {
				val objRequestBody = stringBody.toRequestBody(stringEffectiveMime.toMediaType())
				objReqBuilder.method(stringVerb, objRequestBody)
			}

			sharedHttpClient.newCall(objReqBuilder.build()).execute().use { objResp ->
				val stringRespBody = objResp.body?.string().orEmpty().take(300)
				val boolOk = objResp.isSuccessful
				WebhookExecutionResult(
					boolSuccess = boolOk,
					intStatusCode = objResp.code,
					stringDetail = if (boolOk) {
						"HTTP ${objResp.code} OK"
					} else {
						"HTTP ${objResp.code}: $stringRespBody"
					}
				)
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[WebhookClient] Failed sending webhook to $stringCleanUrl - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			WebhookExecutionResult(
				boolSuccess = false,
				intStatusCode = -1,
				stringDetail = "${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}
}
