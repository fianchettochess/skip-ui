// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Exercise actual lazy row registration, rather than replacing the reader's action. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ScrollViewReaderTests {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun rows(): View = ScrollView {
        LazyVStack {
            ForEach(0 until 100) { id ->
                Text(verbatim = "Row $id").frame(height = 48.0).id(id)
            }
        }
    }

    @Test fun initialOnAppearRequestSurvivesLazyRegistration() {
        rule.setContent {
            Box(Modifier.size(300.dp)) {
                ScrollViewReader { proxy -> rows().onAppear { proxy.scrollTo(90) } }.Compose()
            }
        }
        rule.onNodeWithText("Row 90").assertIsDisplayed()
        rule.onNodeWithText("Row 0").assertDoesNotExist()
    }

    @Test fun retainedInitialProxyUsesCurrentRegisteredActionWithoutReplay() {
        var retained: ScrollViewProxy? = null
        val version = mutableStateOf(0)
        rule.setContent {
            Box(Modifier.size(300.dp)) {
                val ignored = version.value
                ScrollViewReader { proxy ->
                    rows().onAppear { retained = proxy }
                }.Compose()
            }
        }
        rule.onNodeWithText("Row 0").assertIsDisplayed()
        rule.runOnIdle { assertNotNull(retained); retained!!.scrollTo(90) }
        rule.onNodeWithText("Row 90").assertIsDisplayed()
        rule.runOnIdle { retained!!.scrollTo(10) }
        rule.onNodeWithText("Row 10").assertIsDisplayed()
        rule.runOnIdle { version.value++ }
        rule.onNodeWithText("Row 10").assertIsDisplayed()
        rule.onNodeWithText("Row 90").assertDoesNotExist()
    }

    @Test fun recreatedReaderReceivesItsOwnInitialRequest() {
        val visible = mutableStateOf(true)
        var target = 90
        rule.setContent {
            Box(Modifier.size(300.dp)) {
                if (visible.value) {
                    ScrollViewReader { proxy -> rows().onAppear { proxy.scrollTo(target) } }.Compose()
                }
            }
        }
        rule.onNodeWithText("Row 90").assertIsDisplayed()
        rule.runOnIdle { visible.value = false }
        rule.onNodeWithText("Row 90").assertDoesNotExist()
        rule.runOnIdle { target = 30; visible.value = true }
        rule.onNodeWithText("Row 30").assertIsDisplayed()
        rule.onNodeWithText("Row 90").assertDoesNotExist()
    }
}
