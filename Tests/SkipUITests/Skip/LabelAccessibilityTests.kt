// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.foundation.layout.Row
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Real Label rendering, not a separate semantics implementation. */
@RunWith(AndroidJUnit4::class)
class LabelAccessibilityTests {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val symbol = "exclamationmark.triangle.fill"

    @Test fun titleAndIconExposesTitleWithoutDecorativeSymbol() {
        // The four positional parameters remain callable by external Kotlin clients.
        val legacy = ComposeContext(Modifier, null, ComposeContext().stateSaver, null)
        assertFalse(legacy.hasExplicitAccessibilityLabel)
        val copy = legacy.scopy() as ComposeContext
        assertEquals(legacy, copy)
        legacy.hasExplicitAccessibilityLabel = true
        assertTrue(legacy != copy)
        assertFalse(legacy.content().hasExplicitAccessibilityLabel)
        rule.setContent { Label(title = "Engine unavailable", systemImage = symbol).labelStyle(LabelStyle.titleAndIcon).Compose(context = copy) }
        rule.onNodeWithText("Engine unavailable").assertExists()
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun nativeBridgedLabelSuppressesOnlyDefaultSymbolName() {
        rule.setContent {
            Label(bridgedTitle = Text(verbatim = "Engine unavailable"), bridgedImage = Image(systemName = symbol))
                .labelStyle(LabelStyle.titleAndIcon).Compose()
        }
        rule.onAllNodesWithText("Engine unavailable").assertCountEquals(1)
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun bridgedIconOnlyUsesActualTitle() {
        rule.setContent {
            Label(bridgedTitle = Text(verbatim = "Bridged title"), bridgedImage = Image(systemName = symbol))
                .labelStyle(LabelStyle.iconOnly).Compose()
        }
        rule.onAllNodesWithText("Bridged title").assertCountEquals(1)
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun iconOnlyKeepsTitleAndUpdatesWithoutSymbol() {
        val title = mutableStateOf("First dynamic title")
        rule.setContent { Label(title = title.value, systemImage = symbol).labelStyle(LabelStyle.iconOnly).Compose() }
        rule.onNodeWithText("First dynamic title").assertExists()
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { title.value = "Second dynamic title" }
        rule.onNodeWithText("First dynamic title").assertDoesNotExist()
        rule.onNodeWithText("Second dynamic title").assertExists()
    }

    @Test fun iconOnlyLocalizedKeyUsesTheTitle() {
        rule.setContent { Label(titleKey = LocalizedStringKey(string = "Localized title"), systemImage = symbol).labelStyle(LabelStyle.iconOnly).Compose() }
        rule.onNodeWithText("Localized title").assertExists()
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun iconOnlyCustomTitleRemainsAccessible() {
        rule.setContent {
            Label(title = { Text(verbatim = "Custom title") }, icon = { Image(systemName = symbol) })
                .labelStyle(LabelStyle.iconOnly).Compose()
        }
        rule.onNodeWithText("Custom title").assertExists()
    }

    @Test fun iconOnlyCustomStackRetainsBothTitlesAndExplicitIcon() {
        rule.setContent {
            Label(title = { VStack { ComposeBuilder { context ->
                Text(verbatim = "First custom").Compose(context)
                Text(verbatim = "Second custom").Compose(context)
                ComposeResult.ok
            } } }, icon = { Image(systemName = symbol).accessibilityLabel("Custom icon") })
                .labelStyle(LabelStyle.iconOnly).Compose()
        }
        rule.onNodeWithText("First custom", substring = true).assertExists()
        rule.onNodeWithText("Second custom", substring = true).assertExists()
        rule.onNodeWithContentDescription("Custom icon", substring = true).assertExists()
    }

    @Test fun iconOnlyLabelMeasuresLikeIcon() {
        rule.setContent {
            Row {
                Label(title = "A long descriptive title", systemImage = symbol).labelStyle(LabelStyle.iconOnly).Compose()
                Image(systemName = symbol).Compose()
            }
        }
        val label = rule.onNodeWithText("A long descriptive title").fetchSemanticsNode().boundsInRoot
        val icon = rule.onNodeWithContentDescription(symbol).fetchSemanticsNode().boundsInRoot
        assertEquals(icon.width, label.width, 0.01f)
        assertEquals(icon.height, label.height, 0.01f)
    }

    @Test fun titleOnlyAndStandaloneImageRemainIndependent() {
        rule.setContent { Label(title = "Only title", systemImage = symbol).labelStyle(LabelStyle.titleOnly).Compose() }
        rule.onNodeWithText("Only title").assertExists()
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun standaloneImageRetainsItsOwnSemantics() {
        rule.setContent { Image(systemName = symbol).Compose() }
        rule.onNodeWithContentDescription(symbol).assertExists()
    }

    @Test fun explicitLabelOverridesConvenienceTitle() {
        rule.setContent { Label(title = "Visible title", systemImage = symbol).labelStyle(LabelStyle.iconOnly).accessibilityLabel("Explicit accessible title").Compose() }
        rule.onNodeWithContentDescription("Explicit accessible title").assertExists()
        rule.onNodeWithText("Visible title").assertDoesNotExist()
        rule.onNodeWithContentDescription(symbol, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun explicitTitleAndIconReplacesInternalTitle() {
        rule.setContent { Label(title = "Internal title", systemImage = symbol).labelStyle(LabelStyle.titleAndIcon).accessibilityLabel("Replacement title").Compose() }
        rule.onNodeWithContentDescription("Replacement title").assertExists()
        rule.onNodeWithText("Internal title").assertDoesNotExist()
    }

    @Test fun explicitTitleOnlyReplacesInternalTitle() {
        rule.setContent { Label(title = "Internal title", systemImage = symbol).labelStyle(LabelStyle.titleOnly).accessibilityLabel("Replacement title").Compose() }
        rule.onNodeWithContentDescription("Replacement title").assertExists()
        rule.onNodeWithText("Internal title").assertDoesNotExist()
    }

    @Test fun disabledExplicitLabelLeavesTitleAccessible() {
        rule.setContent { Label(title = "Original title", systemImage = symbol).labelStyle(LabelStyle.iconOnly).accessibilityLabel("Disabled override", isEnabled = false).Compose() }
        rule.onNodeWithText("Original title").assertExists()
        rule.onNodeWithContentDescription("Disabled override").assertDoesNotExist()
    }

    @Test fun explicitContainerLabelDoesNotLeakIntoNestedLabel() {
        rule.setContent {
            VStack { Label(title = "Nested title", systemImage = symbol).labelStyle(LabelStyle.iconOnly) }
                .accessibilityLabel("Container label").Compose()
        }
        rule.onNodeWithContentDescription("Container label").assertExists()
        rule.onNodeWithText("Nested title", useUnmergedTree = true).assertExists()
    }

    @Test fun customExplicitIconSurvivesWhenLabelHasNoOverride() {
        rule.setContent {
            Label(title = { Text(verbatim = "Custom title") }, icon = { Image(systemName = symbol).accessibilityLabel("Custom icon") })
                .labelStyle(LabelStyle.iconOnly).Compose()
        }
        rule.onNodeWithContentDescription("Custom icon", substring = true).assertExists()
        rule.onNodeWithText("Custom title", substring = true).assertExists()
    }

    @Test fun customIconRemainsExplicitlyAccessible() {
        rule.setContent {
            Label(title = { Text(verbatim = "Custom title") }, icon = { Image(systemName = symbol).accessibilityLabel("Custom icon") })
                .labelStyle(LabelStyle.titleAndIcon).Compose()
        }
        rule.onNodeWithContentDescription("Custom icon").assertExists()
        rule.onNodeWithText("Custom title").assertExists()
    }
}
