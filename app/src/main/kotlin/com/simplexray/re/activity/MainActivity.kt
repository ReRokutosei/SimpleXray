package com.simplexray.re.activity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.ColorMode
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.ui.navigation.AppNavHost
import com.simplexray.re.viewmodel.MainViewModel
import com.simplexray.re.viewmodel.MainViewModelFactory
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        updateTaskDescription()

        setContent {
            val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
            val themeMode = settingsState.switches.themeMode
            val colorMode = settingsState.switches.colorMode
            val isMonochrome = colorMode == ColorMode.Monochrome
            val systemInDarkTheme = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.Auto -> systemInDarkTheme
            }

            SideEffect {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
            }

            LaunchedEffect(settingsState.switches.hideFromRecents) {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                activityManager?.appTasks?.firstOrNull()?.setExcludeFromRecents(settingsState.switches.hideFromRecents)
            }

            val currentIcon by mainViewModel.appIcon.collectAsStateWithLifecycle()
            LaunchedEffect(currentIcon) {
                // Keep the recents-card icon in sync with the chosen launcher icon
                // (launcher icon itself is switched via activity-alias).
                updateTaskDescription()
            }

            val colorSchemeMode = when (themeMode) {
                ThemeMode.Light -> ColorSchemeMode.MonetLight
                ThemeMode.Dark -> ColorSchemeMode.MonetDark
                ThemeMode.Auto -> ColorSchemeMode.MonetSystem
            }

            val keyColor = if (isMonochrome) Color(0xFF808080) else null
            val paletteStyle = if (isMonochrome) ThemePaletteStyle.Monochrome else ThemePaletteStyle.TonalSpot

            val themeController = remember(colorSchemeMode, isDark, keyColor, paletteStyle) {
                ThemeController(
                    colorSchemeMode = colorSchemeMode,
                    keyColor = keyColor,
                    paletteStyle = paletteStyle,
                    isDark = isDark
                )
            }

            top.yukonga.miuix.kmp.theme.MiuixTheme(controller = themeController) {
                val miuixColors = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
                val colorScheme = remember(miuixColors, isDark) {
                    if (isDark) {
                        darkColorScheme(
                            primary = miuixColors.primary,
                            onPrimary = miuixColors.onPrimary,
                            surface = miuixColors.surface,
                            onSurface = miuixColors.onSurface,
                            background = miuixColors.background,
                            onBackground = miuixColors.onBackground
                        )
                    } else {
                        lightColorScheme(
                            primary = miuixColors.primary,
                            onPrimary = miuixColors.onPrimary,
                            surface = miuixColors.surface,
                            onSurface = miuixColors.onSurface,
                            background = miuixColors.background,
                            onBackground = miuixColors.onBackground
                        )
                    }
                }

                CompositionLocalProvider(
                    top.yukonga.miuix.kmp.squircle.LocalSquircleEnabled provides true
                ) {
                    MaterialTheme(colorScheme = colorScheme) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background
                        ) {
                            AppNavHost(mainViewModel)
                        }
                    }
                }
            }
        }

        Log.d(TAG, "MainActivity onCreate called.")
    }

    override fun onResume() {
        super.onResume()
        mainViewModel.updateSettingsState()
        mainViewModel.refreshCustomDatFiles()
    }

    /**
     * Updates the recents-card icon and label. The launcher icon itself is
     * switched through activity-alias; recents/notifications read the app icon
     * statically, so this keeps them in sync at runtime.
     */
    private fun updateTaskDescription() {
        val iconRes = appIconRes(mainViewModel.prefs.appIcon)
        setTaskDescription(
            ActivityManager.TaskDescription.Builder()
                .setLabel(getString(R.string.app_name))
                .setIcon(iconRes)
                .build()
        )
    }

    private fun appIconRes(key: String?): Int = when (key) {
        "flat" -> R.mipmap.ic_launcher_flat
        "lineal" -> R.mipmap.ic_launcher_lineal
        else -> R.mipmap.ic_launcher_lineal_color
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed.")
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
