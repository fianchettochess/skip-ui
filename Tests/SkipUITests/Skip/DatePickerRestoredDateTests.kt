// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import kotlin.math.abs
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.view.KeyEvent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import skip.foundation.Date

/** Exercise SkipUI's real dialog and binding, including Material's initial normalization. */
@RunWith(AndroidJUnit4::class)
class DatePickerRestoredDateTests {
    @get:Rule val rule = createComposeRule()
    private lateinit var originalLocale: Locale
    private lateinit var originalZone: TimeZone

    @Before fun useUTCAndEnglish() {
        originalLocale = Locale.getDefault()
        originalZone = TimeZone.getDefault()
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After fun restoreLocaleAndZone() {
        Locale.setDefault(originalLocale)
        TimeZone.setDefault(originalZone)
    }

    @Test fun maximumRestoredDateIsSelectedAndOpeningOrDismissingDoesNotWrite() {
        assertOpeningPreserves(253402300799.0, "Dec 31, 9999", "December 9999")
    }

    @Test fun restoredDateBeforeMaterialDefaultRangeIsSelected() {
        assertOpeningPreserves(-5364662399.75, "Jan 1, 1800", "January 1800")
    }

    @Test fun ordinaryNonMidnightTimestampIsNotNormalizedWithoutAnEdit() {
        assertOpeningPreserves(946684799.25, "Dec 31, 1999", "December 1999")
    }

    private fun assertOpeningPreserves(epoch: Double, label: String, month: String) {
        val date = mutableStateOf(Date(timeIntervalSince1970 = epoch))
        val writes = mutableListOf<Double>()
        rule.setContent {
            DatePicker(selection = Binding(get = { date.value }, set = {
                writes.add(it.timeIntervalSince1970)
                date.value = it
            }), displayedComponents = DatePickerComponents.date, label = { Text(verbatim = "Since") }).Compose()
        }
        rule.onNodeWithText(label).performClick()
        rule.onNodeWithText(month).assertIsDisplayed()
        rule.runOnIdle {
            assertTrue("Opening a picker must not call the binding setter", writes.isEmpty())
            assertEquals(epoch, date.value.timeIntervalSince1970, 0.0)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText(label).assertIsDisplayed()
        rule.runOnIdle {
            assertTrue("Dismissing a picker must not call the binding setter", writes.isEmpty())
            assertEquals(epoch, date.value.timeIntervalSince1970, 0.0)
        }
    }

    @Test fun selectingAnotherDayPublishesOnceAndReopeningDoesNotWriteAgain() {
        val date = mutableStateOf(Date(timeIntervalSince1970 = 253402300799.0))
        val writes = mutableListOf<Double>()
        rule.setContent {
            DatePicker(selection = Binding(get = { date.value }, set = {
                writes.add(it.timeIntervalSince1970)
                date.value = it
            }), displayedComponents = DatePickerComponents.date, label = { Text(verbatim = "Since") }).Compose()
        }
        rule.onNodeWithText("Dec 31, 9999").performClick()
        rule.onNodeWithText("December 9999").assertIsDisplayed()
        try {
            // Material may omit day descriptions without a running accessibility
            // service. Measure its real grid rather than depending on those labels.
            fun calendarDays(): List<SemanticsNode> {
                val heading = rule.onNodeWithContentDescription("Thursday", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                return rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
                    .filter { node ->
                        val bounds = node.boundsInRoot
                        abs(bounds.width - heading.width) < 1f &&
                            abs(bounds.height - heading.height) < 1f && bounds.top >= heading.bottom
                    }
                    .sortedWith(compareBy<SemanticsNode> { it.boundsInRoot.top }
                        .thenBy { it.boundsInRoot.left })
            }
            rule.waitUntil(timeoutMillis = 5_000) { calendarDays().size == 31 }
            val days = calendarDays()
            assertEquals("December must expose all 31 real day targets", 31, days.size)
            val day30 = days[29]
            rule.onNode(SemanticsMatcher("the measured December 30 cell") { it.id == day30.id },
                useUnmergedTree = true).performClick()
        } catch (failure: Throwable) {
            fun describe(node: SemanticsNode) {
                println("DATE_PICKER_NODE ${node.id} ${node.boundsInRoot} ${node.config}")
                node.children.forEach(::describe)
            }
            rule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::describe)
            throw failure
        }
        rule.runOnIdle {
            assertEquals(1, writes.size)
            val selected = Instant.ofEpochMilli((writes.single() * 1000).toLong()).atZone(ZoneOffset.UTC)
            assertEquals(9999, selected.year)
            assertEquals(12, selected.monthValue)
            assertEquals(30, selected.dayOfMonth)
            assertEquals(23, selected.hour)
            assertEquals(59, selected.minute)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Dec 30, 9999").performClick()
        rule.onNodeWithText("December 9999").assertIsDisplayed()
        rule.runOnIdle { assertEquals("Reopening must not replay the selection", 1, writes.size) }
    }
    @Test fun maximumUTCDateInPositiveOffsetKeepsItsLocalYearSelected() {
        val epoch = 253402300799.0
        val date = mutableStateOf(Date(timeIntervalSince1970 = epoch))
        val writes = mutableListOf<Double>()
        rule.setContent {
            DatePicker(selection = Binding(get = { date.value }, set = {
                writes.add(it.timeIntervalSince1970)
                date.value = it
            }), displayedComponents = DatePickerComponents.date, label = { Text(verbatim = "Since") })
                .environment({ zone: skip.foundation.TimeZone -> EnvironmentValues.shared.settimeZone(zone) },
                    skip.foundation.TimeZone(secondsFromGMT = 14 * 3600)).Compose()
        }
        rule.onNodeWithText("Jan 1, 10000").performClick()
        rule.onNodeWithText("January 10000").assertIsDisplayed()
        rule.runOnIdle {
            assertTrue("The local calendar year must not clear or rewrite the restored timestamp", writes.isEmpty())
            assertEquals(epoch, date.value.timeIntervalSince1970, 0.0)
        }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.onNodeWithText("Jan 1, 10000").assertIsDisplayed()
        rule.runOnIdle { assertTrue(writes.isEmpty()) }
    }

}
