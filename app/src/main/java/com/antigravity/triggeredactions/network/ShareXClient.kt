package com.antigravity.triggeredactions.network

import android.util.Log
import android.webkit.MimeTypeMap
import com.antigravity.triggeredactions.model.ShareXProfile
import java.io.File
import java.util.UUID
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Outcome of uploading text or a file via a ShareX Custom Uploader (.sxcu) profile.
 */
data class ShareXUploadResult(
	val boolSuccess: Boolean,
	val stringResultUrl: String,
	val stringErrorDetail: String = ""
)

/**
 * Parses ShareX Custom Uploader (`.sxcu`) JSON definitions and executes text or file uploads
 * (fully compatible with ShareX v13–v16 and `WebServer/Shared` `/Projects/Shared/?upload=post`).
 */
object ShareXClient {

	private const val TAG = "ShareXClient"

	private val objLenientJson = Json {
		ignoreUnknownKeys = true
		isLenient = true
	}

	/**
	 * Parses raw `.sxcu` JSON text into a [ShareXProfile].
	 *
	 * @param stringJson Raw `.sxcu` file or clipboard JSON content.
	 * @return Parsed [ShareXProfile], or null if required fields (`RequestURL`) are missing.
	 */
	fun parseSxcuJson(stringJson: String): ShareXProfile? {
		return try {
			val objRoot = objLenientJson.parseToJsonElement(stringJson).jsonObject
			val stringRequestUrl = objRoot.getStringCaseInsensitive("RequestURL")
			if (stringRequestUrl.isBlank()) return null

			val stringName = objRoot.getStringCaseInsensitive("Name").ifBlank { "Imported ShareX Uploader" }
			val stringMethod = objRoot.getStringCaseInsensitive("RequestMethod").ifBlank { "POST" }.uppercase()
			val stringBody = objRoot.getStringCaseInsensitive("Body").ifBlank { "MultipartFormData" }
			val stringFileForm = objRoot.getStringCaseInsensitive("FileFormName").ifBlank { "uploaded_file" }
			val stringUrlTpl = objRoot.getStringCaseInsensitive("URL").ifBlank { "{response}" }
			val stringErrorTpl = objRoot.getStringCaseInsensitive("ErrorMessage").ifBlank { "{response}" }

			val mapHeaders = objRoot.getMapCaseInsensitive("Headers")
			val mapParams = objRoot.getMapCaseInsensitive("Parameters")
			val mapArgs = objRoot.getMapCaseInsensitive("Arguments")

			ShareXProfile(
				stringId = UUID.randomUUID().toString(),
				stringName = stringName,
				stringRequestMethod = stringMethod,
				stringRequestUrl = stringRequestUrl,
				mapHeaders = mapHeaders,
				mapParameters = mapParams,
				stringBodyType = stringBody,
				stringFileFormName = stringFileForm,
				mapArguments = mapArgs,
				stringUrlTemplate = stringUrlTpl,
				stringErrorTemplate = stringErrorTpl
			)
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ShareXClient] Error parsing .sxcu JSON - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			null
		}
	}

	/**
	 * Uploads text content using [objProfile] (either via `$input$` form/JSON arguments if defined,
	 * or as an in-memory `.txt` file attachment via `MultipartFormData` `FileFormName`).
	 *
	 * @param objProfile Configured [ShareXProfile].
	 * @param stringTextContent Text content to upload.
	 * @param stringFileName Virtual filename (e.g., `otp_20261126.txt`).
	 * @return [ShareXUploadResult] containing the resolved share URL or error detail.
	 */
	suspend fun uploadText(
		objProfile: ShareXProfile,
		stringTextContent: String,
		stringFileName: String = "shared_text.txt"
	): ShareXUploadResult = withContext(Dispatchers.IO) {
		val stringSafeFileName = stringFileName.trim().ifBlank { "shared_text.txt" }.let {
			if (it.contains('.')) it else "$it.txt"
		}
		executeShareXRequest(
			objProfile = objProfile,
			fileSource = null,
			stringTextPayload = stringTextContent,
			stringVirtualFileName = stringSafeFileName
		)
	}

	/**
	 * Uploads a physical file from disk using [objProfile].
	 *
	 * @param objProfile Configured [ShareXProfile].
	 * @param fileTarget Existing readable file on device storage.
	 * @return [ShareXUploadResult] containing the resolved share URL or error detail.
	 */
	suspend fun uploadFile(
		objProfile: ShareXProfile,
		fileTarget: File
	): ShareXUploadResult = withContext(Dispatchers.IO) {
		if (!fileTarget.exists() || !fileTarget.isFile || !fileTarget.canRead()) {
			return@withContext ShareXUploadResult(
				boolSuccess = false,
				stringResultUrl = "",
				stringErrorDetail = "Target file not found or unreadable: ${fileTarget.absolutePath}"
			)
		}
		executeShareXRequest(
			objProfile = objProfile,
			fileSource = fileTarget,
			stringTextPayload = "",
			stringVirtualFileName = fileTarget.name
		)
	}

	private fun executeShareXRequest(
		objProfile: ShareXProfile,
		fileSource: File?,
		stringTextPayload: String,
		stringVirtualFileName: String
	): ShareXUploadResult {
		return try {
			val objBaseHttpUrl = objProfile.stringRequestUrl.trim().toHttpUrlOrNull()
				?: return ShareXUploadResult(false, "", "Invalid RequestURL: ${objProfile.stringRequestUrl}")

			val objUrlBuilder = objBaseHttpUrl.newBuilder()
			for ((stringParamKey, stringParamVal) in objProfile.mapParameters) {
				objUrlBuilder.addQueryParameter(
					stringParamKey,
					substituteShareXInputTokens(stringParamVal, stringTextPayload, stringVirtualFileName)
				)
			}

			val stringBodyMode = objProfile.stringBodyType.trim()
			val objRequestBody = when {
				stringBodyMode.equals("FormURLEncoded", ignoreCase = true) -> {
					val objForm = FormBody.Builder()
					for ((stringArgKey, stringArgVal) in objProfile.mapArguments) {
						objForm.add(
							stringArgKey,
							substituteShareXInputTokens(stringArgVal, stringTextPayload, stringVirtualFileName)
						)
					}
					objForm.build()
				}

				stringBodyMode.equals("Binary", ignoreCase = true) -> {
					if (fileSource != null) {
						fileSource.asRequestBody(guessMimeType(fileSource.name).toMediaType())
					} else {
						stringTextPayload.toByteArray(Charsets.UTF_8)
							.toRequestBody("text/plain; charset=utf-8".toMediaType())
					}
				}

				else -> {
					// Default: MultipartFormData (used by ShareX and WebServer/Shared)
					val objMultipart = MultipartBody.Builder().setType(MultipartBody.FORM)
					var boolInputConsumedInArgs = false
					for ((stringArgKey, stringArgVal) in objProfile.mapArguments) {
						if (stringArgVal.contains("\$input\$", ignoreCase = true) || stringArgVal.contains("{input}", ignoreCase = true)) {
							boolInputConsumedInArgs = true
						}
						objMultipart.addFormDataPart(
							stringArgKey,
							substituteShareXInputTokens(stringArgVal, stringTextPayload, stringVirtualFileName)
						)
					}

					val stringFormField = objProfile.stringFileFormName.ifBlank { "uploaded_file" }
					if (fileSource != null) {
						val objPartBody = fileSource.asRequestBody(guessMimeType(fileSource.name).toMediaType())
						objMultipart.addFormDataPart(stringFormField, fileSource.name, objPartBody)
					} else if (!boolInputConsumedInArgs) {
						val objTextBytes = stringTextPayload.toByteArray(Charsets.UTF_8)
						val objPartBody = objTextBytes.toRequestBody("text/plain; charset=utf-8".toMediaType())
						objMultipart.addFormDataPart(stringFormField, stringVirtualFileName, objPartBody)
					}
					objMultipart.build()
				}
			}

			val objReqBuilder = Request.Builder()
				.url(objUrlBuilder.build())
				.method(objProfile.stringRequestMethod.uppercase().ifBlank { "POST" }, objRequestBody)

			for ((stringHeaderKey, stringHeaderVal) in objProfile.mapHeaders) {
				if (stringHeaderKey.isNotBlank()) {
					objReqBuilder.header(
						stringHeaderKey.trim(),
						substituteShareXInputTokens(stringHeaderVal, stringTextPayload, stringVirtualFileName)
					)
				}
			}

			WebhookClient.sharedHttpClient.newCall(objReqBuilder.build()).execute().use { objResp ->
				val stringRawResponse = objResp.body?.string().orEmpty().trim()
				if (objResp.isSuccessful) {
					val stringResolvedUrl = resolveResponseTemplate(objProfile.stringUrlTemplate, stringRawResponse)
					ShareXUploadResult(
						boolSuccess = true,
						stringResultUrl = stringResolvedUrl.ifBlank { stringRawResponse }
					)
				} else {
					val stringErrMsg = resolveResponseTemplate(objProfile.stringErrorTemplate, stringRawResponse)
					ShareXUploadResult(
						boolSuccess = false,
						stringResultUrl = "",
						stringErrorDetail = "HTTP ${objResp.code}: ${stringErrMsg.take(250)}"
					)
				}
			}
		} catch (objException: Exception) {
			Log.e(
				TAG,
				"[ShareXClient] Error uploading to ${objProfile.stringRequestUrl} - ${objException.javaClass.simpleName}: ${objException.message}"
			)
			ShareXUploadResult(
				boolSuccess = false,
				stringResultUrl = "",
				stringErrorDetail = "${objException.javaClass.simpleName}: ${objException.message}"
			)
		}
	}

	/**
	 * Evaluates ShareX response syntax (`{response}`, `$response$`, `{json:data.url}`, `$json:data.url$`, `{regex:...|1}`).
	 *
	 * @param stringTemplate ShareX `URL` or `ErrorMessage` template.
	 * @param stringRawResponse Raw HTTP response body text.
	 * @return Extracted URL or message string.
	 */
	fun resolveResponseTemplate(stringTemplate: String, stringRawResponse: String): String {
		val stringTpl = stringTemplate.trim()
		if (stringTpl.isEmpty() ||
			stringTpl.equals("{response}", ignoreCase = true) ||
			stringTpl.equals("\$response\$", ignoreCase = true)
		) {
			return stringRawResponse
		}

		var stringWorking = stringTpl
			.replace("{response}", stringRawResponse, ignoreCase = true)
			.replace("\$response\$", stringRawResponse, ignoreCase = true)

		// {json:path.to.value} or $json:path.to.value$
		val objJsonPattern = Pattern.compile("(?:\\{json:([^}]+)\\}|\\\$json:([^$]+)\\\$)", Pattern.CASE_INSENSITIVE)
		val objMatcher = objJsonPattern.matcher(stringWorking)
		val objBuf = StringBuffer()
		while (objMatcher.find()) {
			val stringPath = (objMatcher.group(1) ?: objMatcher.group(2)).orEmpty().trim()
			val stringExtracted = extractJsonPath(stringRawResponse, stringPath)
			objMatcher.appendReplacement(objBuf, java.util.regex.Matcher.quoteReplacement(stringExtracted))
		}
		objMatcher.appendTail(objBuf)
		stringWorking = objBuf.toString()

		return stringWorking.trim()
	}

	private fun extractJsonPath(stringJsonText: String, stringDotPath: String): String {
		return try {
			var objCurrent: JsonElement = objLenientJson.parseToJsonElement(stringJsonText)
			for (stringSegment in stringDotPath.split('.').filter { it.isNotBlank() }) {
				if (objCurrent is JsonObject) {
					objCurrent = objCurrent[stringSegment] ?: return ""
				} else {
					return ""
				}
			}
			(objCurrent as? JsonPrimitive)?.content.orEmpty()
		} catch (objException: Exception) {
			""
		}
	}

	private fun substituteShareXInputTokens(
		stringValue: String,
		stringTextPayload: String,
		stringFileName: String
	): String {
		return stringValue
			.replace("\$input\$", stringTextPayload, ignoreCase = true)
			.replace("{input}", stringTextPayload, ignoreCase = true)
			.replace("\$filename\$", stringFileName, ignoreCase = true)
			.replace("{filename}", stringFileName, ignoreCase = true)
	}

	private fun guessMimeType(stringFileName: String): String {
		val stringExt = stringFileName.substringAfterLast('.', "").lowercase()
		if (stringExt.isEmpty()) return "application/octet-stream"
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(stringExt) ?: when (stringExt) {
			"txt", "log", "md" -> "text/plain; charset=utf-8"
			"json" -> "application/json"
			"png" -> "image/png"
			"jpg", "jpeg" -> "image/jpeg"
			else -> "application/octet-stream"
		}
	}

	private fun JsonObject.getStringCaseInsensitive(stringTargetKey: String): String {
		val objElem = entries.firstOrNull { it.key.equals(stringTargetKey, ignoreCase = true) }?.value
		return (objElem as? JsonPrimitive)?.content?.trim().orEmpty()
	}

	private fun JsonObject.getMapCaseInsensitive(stringTargetKey: String): Map<String, String> {
		val objSub = entries.firstOrNull { it.key.equals(stringTargetKey, ignoreCase = true) }?.value as? JsonObject
			?: return emptyMap()
		return objSub.entries.associate { (k, v) ->
			k to ((v as? JsonPrimitive)?.content.orEmpty())
		}
	}
}
