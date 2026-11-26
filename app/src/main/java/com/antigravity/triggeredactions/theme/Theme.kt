package com.antigravity.triggeredactions.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
	primary = SkyBlue80,
	secondary = SlateGrey80,
	tertiary = Teal80
)

private val LightColorScheme = lightColorScheme(
	primary = SkyBlue40,
	secondary = SlateGrey40,
	tertiary = Teal40
)

/**
 * Material 3 theme wrapper supporting Android 12+ Material You dynamic color palettes.
 */
@Composable
fun TriggeredActionsTheme(
	boolDarkTheme: Boolean = isSystemInDarkTheme(),
	boolDynamicColor: Boolean = true,
	content: @Composable () -> Unit
) {
	val objColorScheme = when {
		boolDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
			val objContext = LocalContext.current
			if (boolDarkTheme) dynamicDarkColorScheme(objContext) else dynamicLightColorScheme(objContext)
		}
		boolDarkTheme -> DarkColorScheme
		else -> LightColorScheme
	}

	MaterialTheme(
		colorScheme = objColorScheme,
		typography = Typography,
		content = content
	)
}
