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
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simplexray.re.R
import com.simplexray.re.common.ThemeMode
import com.simplexray.re.ui.navigation.AppNavHost
import com.simplexray.re.viewmodel.MainViewModel
import com.simplexray.re.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        updateTaskDescription()

        setContent {
            val context = LocalContext.current
            val dynamicColor = true
            val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()
            val themeMode = settingsState.switches.themeMode
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

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            val colorSchemeMode = when (themeMode) {
                ThemeMode.Light -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
                ThemeMode.Dark -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetDark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
                ThemeMode.Auto -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetSystem else top.yukonga.miuix.kmp.theme.ColorSchemeMode.System
            }

            val themeController = remember(colorSchemeMode, isDark) {
                top.yukonga.miuix.kmp.theme.ThemeController(
                    colorSchemeMode = colorSchemeMode,
                    isDark = isDark
                )
            }

            val dispatcherOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            top.yukonga.miuix.kmp.theme.MiuixTheme(controller = themeController) {
                CompositionLocalProvider(
                    androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner provides dispatcherOwner,
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
