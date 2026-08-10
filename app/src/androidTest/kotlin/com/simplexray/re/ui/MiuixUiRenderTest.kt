package com.simplexray.re.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simplexray.re.ui.screens.StatRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@RunWith(AndroidJUnit4::class)
class MiuixUiRenderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testStatRowRendersLabelAndValue() {
        composeTestRule.setContent {
            val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            val themeController = ThemeController(isDark = false)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                MiuixTheme(controller = themeController) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        StatRow(label = "Uplink", value = "1.23 MB")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Uplink").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.23 MB").assertIsDisplayed()
    }

    @Test
    fun testMiuixCardAndTitleRenderInDarkTheme() {
        composeTestRule.setContent {
            val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            val themeController = ThemeController(isDark = true)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                MiuixTheme(controller = themeController) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Card {
                            SmallTitle(text = "HyperOS Title Test")
                            Text(text = "Miuix Card Content")
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("HyperOS Title Test").assertIsDisplayed()
        composeTestRule.onNodeWithText("Miuix Card Content").assertIsDisplayed()
    }

    @Test
    fun testMiuixButtonInteractivity() {
        var clicked = false
        composeTestRule.setContent {
            val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            val themeController = ThemeController(isDark = false)
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                MiuixTheme(controller = themeController) {
                    Button(onClick = { clicked = true }) {
                        Text(text = "Start Service")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Start Service").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Service").performClick()
        assert(clicked)
    }

    @Test
    fun testMiuixNavigationRailRender() {
        composeTestRule.setContent {
            val dispatcherOwner = rememberNavigationEventDispatcherOwner(parent = null)
            val themeController = ThemeController(isDark = false)
            val railState = remember { top.yukonga.miuix.kmp.basic.NavigationRailState() }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides dispatcherOwner) {
                MiuixTheme(controller = themeController) {
                    top.yukonga.miuix.kmp.basic.NavigationRail(state = railState) {
                        top.yukonga.miuix.kmp.basic.NavigationRailItem(
                            selected = true,
                            onClick = {},
                            icon = androidx.compose.material.icons.Icons.Default.MoreVert,
                            label = "Dashboard"
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Dashboard").assertIsDisplayed()
    }
}
