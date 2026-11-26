package com.antigravity.triggeredactions

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.antigravity.triggeredactions.theme.TriggeredActionsTheme
import com.antigravity.triggeredactions.ui.main.MainScreen

/**
 * Launcher Activity hosting the Jetpack Compose [MainScreen] (displayed in the launcher as "Triggered Actions").
 */
class MainActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			TriggeredActionsTheme {
				MainScreen()
			}
		}
	}
}
