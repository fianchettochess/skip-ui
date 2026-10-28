// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertTrue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationContextMenuTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun directLongPressConsumesReleaseAndElsewhereOpensMenu() {
        val taps = mutableIntStateOf(0)
        val direct = mutableIntStateOf(0)
        rule.setContent {
            Button(action = { taps.intValue++ }, label = { Text(verbatim = "surface") })
                .androidLocationContextMenu(
                    bridgedMenuItems = Button(action = {}, label = { Text(verbatim = "Board Setup") }),
                    action = { x, _ -> if (x < 30.0) { direct.intValue++; true } else false }
                ).Compose()
        }
        rule.onNodeWithText("surface").performTouchInput {
            down(Offset(5f, center.y)); advanceEventTime(700); up()
        }
        rule.runOnIdle { assertEquals(1, direct.intValue); assertEquals(0, taps.intValue) }
        rule.onNodeWithText("Board Setup").assertDoesNotExist()
        rule.onNodeWithText("surface").performTouchInput {
            down(Offset(width - 5f, center.y)); advanceEventTime(700); up()
        }
        rule.onNodeWithText("Board Setup").assertExists()
        rule.runOnIdle { assertEquals(1, direct.intValue); assertEquals(0, taps.intValue) }
    }

    @Test fun tapAndMovementDoNotDispatchLongPress() {
        val taps = mutableIntStateOf(0)
        val direct = mutableIntStateOf(0)
        rule.setContent {
            Button(action = { taps.intValue++ }, label = { Text(verbatim = "surface") })
                .androidLocationContextMenu(
                    bridgedMenuItems = Button(action = {}, label = { Text(verbatim = "Board Setup") }),
                    action = { _, _ -> direct.intValue++; true }
                ).Compose()
        }
        rule.onNodeWithText("surface").performTouchInput { down(center); up() }
        rule.runOnIdle { assertEquals(1, taps.intValue); assertEquals(0, direct.intValue) }
        rule.onNodeWithText("surface").performTouchInput {
            down(center); moveTo(Offset(width + 100f, center.y)); advanceEventTime(700); up()
        }
        rule.onNodeWithText("surface").performTouchInput {
            down(center); advanceEventTime(100); cancel()
        }
        rule.runOnIdle { assertEquals(1, taps.intValue); assertEquals(0, direct.intValue) }
        rule.onNodeWithText("Board Setup").assertDoesNotExist()
    }

    @Test fun locationUsesUpdatedDensity() {
        val density = mutableStateOf(Density(1f))
        val locations = mutableListOf<Double>()
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density.value) {
                Button(action = {}, label = { Text(verbatim = "density surface") })
                    .androidLocationContextMenu(
                        bridgedMenuItems = Button(action = {}, label = { Text(verbatim = "Board Setup") }),
                        action = { x, _ -> locations.add(x); true }
                    ).Compose()
            }
        }
        rule.onNodeWithText("density surface").performTouchInput {
            down(Offset(20f, center.y)); advanceEventTime(700); up()
        }
        rule.runOnIdle { density.value = Density(2f) }
        rule.onNodeWithText("density surface").performTouchInput {
            down(Offset(20f, center.y)); advanceEventTime(700); up()
        }
        rule.runOnIdle {
            assertEquals(2, locations.size)
            assertTrue(kotlin.math.abs(locations[0] - 20.0) < 0.1)
            assertTrue(kotlin.math.abs(locations[1] - 10.0) < 0.1)
        }
    }
}
