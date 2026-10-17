package com.lewisw.lockgestures

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lewisw.lockgestures.ui.AppTheme
import com.lewisw.lockgestures.ui.MainScreen
import com.lewisw.lockgestures.ui.SettingsScreen
import com.lewisw.lockgestures.ui.ZoneEditorScreen

private enum class Screen { MAIN, ZONES, SETTINGS }

class MainActivity : ComponentActivity() {

    /** Bumped every time the app comes back to the front, so permission states refresh. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                var screen by remember { mutableStateOf(Screen.MAIN) }
                BackHandler(enabled = screen != Screen.MAIN) { screen = Screen.MAIN }
                when (screen) {
                    Screen.MAIN -> MainScreen(
                        resumeTick = resumeTick,
                        onSetZones = { screen = Screen.ZONES },
                        onSettings = { screen = Screen.SETTINGS },
                    )
                    Screen.ZONES -> ZoneEditorScreen(onDone = { screen = Screen.MAIN })
                    Screen.SETTINGS -> SettingsScreen(
                        resumeTick = resumeTick,
                        onBack = { screen = Screen.MAIN },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
