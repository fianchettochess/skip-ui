// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import android.view.KeyEvent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import skip.ui.Binding
import skip.ui.ComposeContext
import skip.ui.NavigationStack
import skip.ui.NavigationLink
import skip.ui.NavigationPath
import skip.ui.Text

/** Exercises SkipUI's real Boolean destination and Nav3 saved back stack. */
@RunWith(AndroidJUnit4::class)
class BooleanNavigationRestorationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun restoredTruePresentationRendersAndBackClearsOnce() {
        val presented = mutableStateOf(true)
        val writes = mutableListOf<Boolean>()
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val context = remember { ComposeContext() }
            NavigationStack(root = {
                Text(verbatim = "Navigation root").navigationDestination(
                    isPresented = Binding(get = { presented.value }, set = {
                        writes.add(it)
                        presented.value = it
                    }),
                    destination = { Text(verbatim = "Restored destination") }
                )
            }).Compose(context = context)
        }
        rule.onNodeWithText("Restored destination").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Restored destination").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue("Restoration must preserve presentation intent", presented.value)
            assertTrue("Restoration must not clear the binding", writes.isEmpty())
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Navigation root").assertIsDisplayed()
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf(false), writes) }
    }
    @Test fun booleanOnValueRouteRestoresWithoutDiscardingPath() {
        val presented = mutableStateOf(false)
        val path = mutableStateOf(NavigationPath())
        val writes = mutableListOf<Boolean>()
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val context = remember { ComposeContext() }
            NavigationStack(path = Binding(get = { path.value }, set = { path.value = it }), root = {
                NavigationLink(value = "chapter", label = { Text(verbatim = "Open chapter") })
                    .navigationDestination(for_ = String::class, destination = {
                        Text(verbatim = "Chapter destination").navigationDestination(
                            isPresented = Binding(get = { presented.value }, set = {
                                writes.add(it)
                                presented.value = it
                            }), destination = { Text(verbatim = "Chapter detail") }
                        )
                    })
            }).Compose(context = context)
        }
        rule.onNodeWithText("Open chapter").performClick()
        rule.onNodeWithText("Chapter destination").assertIsDisplayed()
        rule.runOnIdle { presented.value = true }
        rule.onNodeWithText("Chapter detail").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Chapter detail").assertIsDisplayed()
        rule.runOnIdle { assertTrue(presented.value && writes.isEmpty()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Chapter destination").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(false), writes) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Open chapter").assertIsDisplayed()
    }

    @Test fun nestedStackRestoresOnlyItsOwnBooleanDestination() {
        val presented = mutableStateOf(true)
        val writes = mutableListOf<Boolean>()
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val context = remember { ComposeContext() }
            NavigationStack(root = {
                NavigationStack(root = {
                    Text(verbatim = "Nested root").navigationDestination(
                        isPresented = Binding(get = { presented.value }, set = {
                            writes.add(it)
                            presented.value = it
                        }), destination = { Text(verbatim = "Nested destination") }
                    )
                })
            }).Compose(context = context)
        }
        rule.onNodeWithText("Nested destination").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Nested destination").assertIsDisplayed()
        rule.runOnIdle { assertTrue(presented.value && writes.isEmpty()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Nested root").assertIsDisplayed()
        rule.runOnIdle { assertEquals(listOf(false), writes) }
    }

}
