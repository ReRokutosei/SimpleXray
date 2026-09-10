package com.simplexray.re.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.simplexray.re.common.ROUTE_APP_LIST
import com.simplexray.re.common.ROUTE_CONFIG_EDIT
import com.simplexray.re.common.ROUTE_MAIN
import com.simplexray.re.ui.screens.AppListScreen
import com.simplexray.re.ui.screens.ConfigEditScreen
import com.simplexray.re.ui.screens.MainScreen
import com.simplexray.re.viewmodel.AppListViewModel
import com.simplexray.re.viewmodel.ConfigEditViewModel
import com.simplexray.re.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import java.io.File

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = ROUTE_MAIN
    ) {
        composable(
            route = ROUTE_MAIN,
            enterTransition = { EnterTransition.None },
            exitTransition = { exitTransition() },
            popEnterTransition = { popEnterTransition() },
            popExitTransition = { ExitTransition.None }
        ) {
            MainScreen(
                mainViewModel = mainViewModel,
                appNavController = navController,
                snackbarHostState = remember { SnackbarHostState() }
            )
        }

        composable(
            route = ROUTE_APP_LIST,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            val appListViewModel: AppListViewModel = viewModel()
            AppListScreen(
                viewModel = appListViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = ROUTE_CONFIG_EDIT,
            enterTransition = { enterTransition() },
            exitTransition = { exitTransition() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { popExitTransition() }
        ) {
            val path = mainViewModel.editingFilePath ?: mainViewModel.prefs.selectedConfigPath
            if (!path.isNullOrEmpty() && File(path).exists()) {
                val configEditViewModel: ConfigEditViewModel = viewModel(
                    key = path,
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ConfigEditViewModel(
                                application = mainViewModel.getApplication(),
                                initialFilePath = path,
                                prefs = mainViewModel.prefs
                            ) as T
                        }
                    }
                )
                ConfigEditScreen(
                    onBackClick = {
                        mainViewModel.editingFilePath = null
                        navController.popBackStack()
                    },
                    snackbarHostState = remember { SnackbarHostState() },
                    viewModel = configEditViewModel
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enterTransition() =
    scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(400))

private fun exitTransition() =
    fadeOut(animationSpec = tween(300)) + scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(400, easing = FastOutSlowInEasing)
    )

private fun popEnterTransition() = fadeIn(animationSpec = tween(400)) + scaleIn(
    initialScale = 0.9f,
    animationSpec = tween(400, easing = FastOutSlowInEasing)
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExitTransition() =
    scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(400))
