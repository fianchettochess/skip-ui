// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class NarrowDatePickerTests {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun allWeekdaysFitAt360dp() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assumeTrue(config.screenWidthDp == 360 && Locale.getDefault().language == "en")
        rule.setContent {
            val state = rememberDatePickerState(initialSelectedDateMillis = 1761609600000L)
            SimpleDatePickerDialog(onDismissRequest = {}, isCalendar = true) { DatePicker(state = state) }
        }
        val weekdays = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            .map { rule.onNodeWithContentDescription(it, useUnmergedTree = true).fetchSemanticsNode() }
        val root = rule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInWindow }.filter { it.width > 0f && it.height > 0f }.minBy { it.width * it.height }
        weekdays.forEach { day ->
            val b = day.boundsInWindow
            assertTrue("Weekday outside dialog: $b vs $root", b.left >= root.left && b.right <= root.right)
            assertTrue("Weekday clipped: $b", b.width >= 48f * config.densityDpi / 160f)
        }
        val cell = weekdays.first().boundsInWindow
        val dates = rule.onAllNodes(hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInWindow }
            .filter { it.top >= cell.bottom && kotlin.math.abs(it.width - cell.width) < 1f && kotlin.math.abs(it.height - cell.height) < 1f }
        assertEquals("All 31 dates must have a full weekday-sized target", 31, dates.size)
    }

    @Test fun narrowerCalendarCanRevealBothEnds() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assumeTrue(config.screenWidthDp < 360 && Locale.getDefault().language == "en")
        rule.setContent {
            val state = rememberDatePickerState(initialSelectedDateMillis = 1761609600000L)
            SimpleDatePickerDialog(onDismissRequest = {}, isCalendar = true) { DatePicker(state = state) }
        }
        rule.onNodeWithContentDescription("Saturday", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Sunday", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun narrowerRtlCalendarCanRevealBothEnds() {
        val config = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration
        assumeTrue(config.screenWidthDp < 360 && Locale.getDefault().language == "en")
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val state = rememberDatePickerState(initialSelectedDateMillis = 1761609600000L)
                SimpleDatePickerDialog(onDismissRequest = {}, isCalendar = true) { DatePicker(state = state) }
            }
        }
        rule.onNodeWithContentDescription("Sunday", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Saturday", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }
}
