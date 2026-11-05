// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Renderer identity and geometry when a conditional middle child disappears. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class TrailingSpacerIdentityTests {
    @get:org.junit.Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun horizontalDefaultSpacingRetainsLeadingControl() = exercise(horizontal = true, spacing = null)
    @Test fun verticalDefaultSpacingRetainsLeadingControl() = exercise(horizontal = false, spacing = null)
    @Test fun horizontalZeroSpacingRetainsLeadingControl() = exercise(horizontal = true, spacing = 0.0)
    @Test fun verticalZeroSpacingRetainsLeadingControl() = exercise(horizontal = false, spacing = 0.0)

    @Test fun horizontalSpacingChangeRetainsLeadingControl() = exercise(horizontal = true, spacing = null, changeSpacing = true)
    @Test fun verticalSpacingChangeRetainsLeadingControl() = exercise(horizontal = false, spacing = null, changeSpacing = true)

    private fun exercise(horizontal: Boolean, spacing: Double?, changeSpacing: Boolean = false) {
        val showMiddle = mutableStateOf(true)
        val currentSpacing = mutableStateOf(spacing)
        val probe = MountProbe()
        rule.setContent {
            Box(Modifier.size(200.dp)) {
                val children = {
                    ComposeBuilder { context ->
                        StatefulControl(probe).Compose(context)
                        if (showMiddle.value) Color.red.frame(width = 20.0, height = 20.0).Compose(context)
                        Spacer().Compose(context)
                        ComposeResult.ok
                    }
                }
                val stack: View = if (horizontal) HStack(spacing = currentSpacing.value, content = children)
                                  else VStack(spacing = currentSpacing.value, content = children)
                stack.Compose(ComposeContext(modifier = Modifier.size(200.dp)))
            }
        }
        rule.waitForIdle()
        var initialToken: Any? = null
        var initialPosition: Offset? = null
        rule.runOnIdle {
            initialToken = probe.token
            initialPosition = probe.position
            assertNotNull("Control was composed", initialToken)
            assertNotNull("Control was placed", initialPosition)
            assertEquals(1, probe.mounts)
            assertEquals(0, probe.disposals)
            if (changeSpacing) currentSpacing.value = 0.0 else showMiddle.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertSame("Changing spacing or removing a later child must not recreate the leading control", initialToken, probe.token)
            assertEquals("Control must not remount", 1, probe.mounts)
            assertEquals("Control must not be disposed", 0, probe.disposals)
            assertEquals("Leading control stays at the same position", initialPosition, probe.position)
            assertTrue("Control has nonzero geometry", probe.width > 0 && probe.height > 0)
        }
    }
}

private class MountProbe {
    var token: Any? = null
    var position: Offset? = null
    var width = 0
    var height = 0
    var mounts = 0
    var disposals = 0
}

private fun StatefulControl(probe: MountProbe): View = ComposeView { context ->
    val token = remember { Any() }
    DisposableEffect(Unit) {
        probe.mounts++
        onDispose { probe.disposals++ }
    }
    SideEffect { probe.token = token }
    Box(context.modifier.size(20.dp).onGloballyPositioned {
        probe.position = it.positionInRoot()
        probe.width = it.size.width
        probe.height = it.size.height
    })
}
