package io.github.homura251.webshortcuts.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.homura251.webshortcuts.ui.components.ConnectedChoiceItem
import io.github.homura251.webshortcuts.ui.components.ConnectedGroup
import io.github.homura251.webshortcuts.ui.components.ConnectedSwitchItem
import io.github.homura251.webshortcuts.ui.components.ExpressiveButton
import io.github.homura251.webshortcuts.ui.components.InteractiveTonalCard
import io.github.homura251.webshortcuts.ui.theme.WebShortcutsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpressiveComponentsInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedCardActionDoesNotOpenTheWebsiteAndLongPressRetainsSelectionSemantics() {
        var opened = 0
        var actions = 0
        var selected by mutableStateOf(false)
        compose.setContent {
            WebShortcutsTheme(dynamicColor = false) {
                InteractiveTonalCard(onClick = { opened++ }, onLongClick = { selected = true },
                    selected = selected, modifier = Modifier.testTag("card")) {
                    Text("An offline reference")
                    TextButton(onClick = { actions++ }, modifier = Modifier.testTag("actions")) { Text("Actions") }
                }
            }
        }
        compose.onNodeWithTag("actions").performClick()
        compose.runOnIdle { assertEquals(1, actions); assertEquals(0, opened) }
        compose.onNodeWithText("An offline reference", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("card").assertIsSelected()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithTag("card").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun connectedRowsAndPrimaryActionRemainUsableAtTwoHundredPercentFontScale() {
        var chosen by mutableStateOf(false)
        var dynamic by mutableStateOf(false)
        var saved = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                WebShortcutsTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(320.dp, 480.dp)) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            ConnectedGroup {
                                ConnectedChoiceItem(0, 2, "使用浏览器的独立登录状态", chosen, { chosen = true },
                                    supporting = "同一个网站可以通过不同浏览器使用不同账号。", modifier = Modifier.testTag("choice"))
                                ConnectedSwitchItem(1, 2, "跟随系统动态配色", dynamic, { dynamic = it },
                                    modifier = Modifier.testTag("dynamic"))
                            }
                            ExpressiveButton({ saved++ }, Modifier.testTag("save")) { Text("保存快捷方式") }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("choice").performScrollTo().assertHeightIsAtLeast(48.dp).performClick().assertIsSelected()
        compose.onNodeWithTag("dynamic").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("save").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, saved) }
    }
}
