package com.simplexray.re.activity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        mainViewModel.reloadView = { initView() }
        initView()

        Log.d(TAG, "MainActivity onCreate called.")
    }

    private fun initView() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val currentNightMode =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = when (mainViewModel.prefs.theme) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.Auto -> currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }
        insetsController.isAppearanceLightStatusBars = !isDark

        setContent {
            val context = LocalContext.current
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val settingsState by mainViewModel.settingsState.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(settingsState.switches.hideFromRecents) {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                activityManager?.appTasks?.firstOrNull()?.setExcludeFromRecents(settingsState.switches.hideFromRecents)
            }

            val colorScheme = when {
                dynamicColor && isDark -> dynamicDarkColorScheme(context)
                dynamicColor && !isDark -> dynamicLightColorScheme(context)
                isDark -> darkColorScheme()
                else -> lightColorScheme()
            }

            val colorSchemeMode = when (mainViewModel.prefs.theme) {
                ThemeMode.Light -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetLight else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Light
                ThemeMode.Dark -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetDark else top.yukonga.miuix.kmp.theme.ColorSchemeMode.Dark
                ThemeMode.Auto -> if (dynamicColor) top.yukonga.miuix.kmp.theme.ColorSchemeMode.MonetSystem else top.yukonga.miuix.kmp.theme.ColorSchemeMode.System
            }

            val themeController = androidx.compose.runtime.remember(colorSchemeMode, isDark) {
                top.yukonga.miuix.kmp.theme.ThemeController(
                    colorSchemeMode = colorSchemeMode,
                    isDark = isDark
                )
            }

            val dispatcherOwner = androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner(parent = null)
            top.yukonga.miuix.kmp.theme.MiuixTheme(controller = themeController) {
                androidx.compose.runtime.CompositionLocalProvider(
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
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity Coroutine Scope cancelled.")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = when (mainViewModel.prefs.theme) {
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
            ThemeMode.Auto -> currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        Log.d(TAG, "MainActivity onConfigurationChanged called.")
    }

    companion object {
        const val TAG = "MainActivity"
    }
}
