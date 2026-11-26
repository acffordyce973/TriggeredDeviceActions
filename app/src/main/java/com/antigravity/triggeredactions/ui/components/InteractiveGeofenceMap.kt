package com.antigravity.triggeredactions.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader

/**
 * JavaScript bridge forwarding Leaflet pin drag/tap events from `map.html` back into Compose state.
 */
class MapBridge(private val onMarkerMovedCallback: (Double, Double) -> Unit) {
	@JavascriptInterface
	fun onMarkerMoved(doubleLat: Double, doubleLng: Double) {
		onMarkerMovedCallback(doubleLat, doubleLng)
	}
}

/**
 * Interactive OpenStreetMap + Leaflet geofence picker matching Triggered Live Wallpaper.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveGeofenceMap(
	doubleLatitude: Double,
	doubleLongitude: Double,
	floatRadiusMeters: Float,
	modifier: Modifier = Modifier,
	onLocationChanged: (Double, Double) -> Unit
) {
	var objWebView by remember { mutableStateOf<WebView?>(null) }
	var boolIsMapLoaded by remember { mutableStateOf(false) }

	LaunchedEffect(doubleLatitude, doubleLongitude) {
		if (boolIsMapLoaded) {
			objWebView?.evaluateJavascript("setCoordinates($doubleLatitude, $doubleLongitude);", null)
		}
	}

	LaunchedEffect(floatRadiusMeters) {
		if (boolIsMapLoaded) {
			objWebView?.evaluateJavascript("setRadius($floatRadiusMeters);", null)
		}
	}

	AndroidView(
		modifier = modifier
			.fillMaxWidth()
			.height(260.dp)
			.clip(RoundedCornerShape(12.dp))
			.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
		factory = { objCtx ->
			val objAssetLoader = WebViewAssetLoader.Builder()
				.addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(objCtx))
				.build()

			WebView(objCtx).apply {
				settings.apply {
					javaScriptEnabled = true
					domStorageEnabled = true
					allowFileAccess = false
					allowContentAccess = false
					mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
					cacheMode = WebSettings.LOAD_DEFAULT
					userAgentString = "TriggeredDeviceActions/1.0 (Android) " + userAgentString
				}

				addJavascriptInterface(
					MapBridge { doubleLat, doubleLng ->
						post { onLocationChanged(doubleLat, doubleLng) }
					},
					"AndroidBridge"
				)

				webViewClient = object : WebViewClient() {
					override fun shouldInterceptRequest(
						objView: WebView?,
						objRequest: WebResourceRequest?
					): WebResourceResponse? {
						val uriReq = objRequest?.url ?: return null
						return objAssetLoader.shouldInterceptRequest(uriReq)
							?: super.shouldInterceptRequest(objView, objRequest)
					}

					override fun onPageFinished(objView: WebView?, stringUrl: String?) {
						super.onPageFinished(objView, stringUrl)
						boolIsMapLoaded = true
						evaluateJavascript(
							"setInitialLocation($doubleLatitude, $doubleLongitude, $floatRadiusMeters);",
							null
						)
					}
				}

				setOnTouchListener { objV, objEvent ->
					when (objEvent.action) {
						MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
							objV.parent?.requestDisallowInterceptTouchEvent(true)
						}
						MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
							objV.parent?.requestDisallowInterceptTouchEvent(false)
						}
					}
					false
				}

				loadUrl("https://appassets.androidplatform.net/assets/map.html")
				objWebView = this
			}
		},
		update = { objView -> objWebView = objView }
	)
}
