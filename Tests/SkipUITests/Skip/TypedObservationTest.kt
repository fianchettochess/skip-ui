package skip.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import skip.ui.ComposeContext
import skip.ui.EmptyView
import skip.ui.Renderable
import skip.ui.ScenePhase
import skip.ui.View

/** Same erased wrapper class, different payload types: the Swift bridge's case. */
@RunWith(AndroidJUnit4::class)
class TypedObservationTest {
    @get:Rule val rule = createComposeRule()

    private data class Erased(val payload: Any?)
    private data class Change(val key: String, val old: Any?, val new: Any?)

    // A diagnostic run can disable only the observation key to retain a failing
    // control for slot reuse. Shipping app behavior has no such switch.
    private fun key(type: String): String? =
        if (InstrumentationRegistry.getArguments().getString("typedObservationKeysDisabled") == "true") null else type

    @Test fun replacementDoesNotDeliverAnotherTypesOldValue() = replacement(initial = false)

    @Test fun replacementWithInitialUsesTheNewTypeForBothArguments() = replacement(initial = true)

    private fun replacement(initial: Boolean) {
        val boolean = mutableStateOf(false)
        val phase = mutableStateOf(ScenePhase.active)
        val flag = mutableStateOf(false)
        val events = mutableListOf<Change>()
        rule.setContent {
            val type = if (boolean.value) "Swift.Bool" else "SkipSwiftUI.ScenePhase"
            val value = Erased(if (boolean.value) flag.value else phase.value)
            EmptyView().onChangeErased(of = value, initial = initial, observationKey = key(type)) { old, new ->
                events += Change(type, old.payload, new.payload)
            }.Compose()
        }
        rule.runOnIdle {
            assertEquals(if (initial) listOf(Change("SkipSwiftUI.ScenePhase", ScenePhase.active, ScenePhase.active)) else emptyList<Change>(), events)
            phase.value = ScenePhase.background
        }
        rule.runOnIdle {
            assertEquals(Change("SkipSwiftUI.ScenePhase", ScenePhase.active, ScenePhase.background), events.last())
            events.clear()
            boolean.value = true
        }
        rule.runOnIdle {
            assertEquals(if (initial) listOf(Change("Swift.Bool", false, false)) else emptyList<Change>(), events)
            flag.value = true
        }
        rule.runOnIdle {
            assertEquals(Change("Swift.Bool", false, true), events.last())
            events.clear()
            boolean.value = false
        }
        rule.runOnIdle {
            assertEquals(if (initial) listOf(Change("SkipSwiftUI.ScenePhase", ScenePhase.background, ScenePhase.background)) else emptyList<Change>(), events)
            phase.value = ScenePhase.active
        }
        rule.runOnIdle {
            assertEquals(Change("SkipSwiftUI.ScenePhase", ScenePhase.background, ScenePhase.active), events.last())
        }
    }

    @Test fun sameTypeUpdatesKeepOldValuesWithoutRemountingContent() {
        val value = mutableStateOf(false)
        val unrelated = mutableStateOf(0)
        val tokens = mutableListOf<Any>()
        val events = mutableListOf<Pair<Any?, Any?>>()
        rule.setContent {
            unrelated.value
            LifetimeProbe(tokens).onChangeErased(of = Erased(value.value), initial = false, observationKey = key("Swift.Bool")) { old, new ->
                events += old.payload to new.payload
            }.Compose()
        }
        var token: Any? = null
        rule.runOnIdle { token = tokens.last(); value.value = true }
        rule.runOnIdle {
            assertEquals(listOf(false to true), events)
            assertTrue(tokens.all { it === token })
            unrelated.value++
        }
        rule.runOnIdle {
            assertEquals(1, events.size)
            assertSame(token, tokens.last())
            value.value = false
        }
        rule.runOnIdle { assertEquals(listOf(false to true, true to false), events); assertSame(token, tokens.last()) }
    }

    @Test fun typeReplacementDoesNotRemountContent() {
        val boolean = mutableStateOf(false)
        val tokens = mutableListOf<Any>()
        rule.setContent {
            val type = if (boolean.value) "Swift.Bool" else "SkipSwiftUI.ScenePhase"
            LifetimeProbe(tokens).onChangeErased(of = Erased(if (boolean.value) false else ScenePhase.active), initial = false, observationKey = key(type)) { _, _ -> }.Compose()
        }
        var token: Any? = null
        rule.runOnIdle { token = tokens.last(); boolean.value = true }
        rule.runOnIdle { assertSame(token, tokens.last()); boolean.value = false }
        rule.runOnIdle { assertSame(token, tokens.last()) }
    }

    @Test fun removedObserverStartsFreshWhenReinserted() {
        val visible = mutableStateOf(true)
        val value = mutableStateOf(false)
        val events = mutableListOf<Pair<Any?, Any?>>()
        rule.setContent {
            if (visible.value) {
                EmptyView().onChangeErased(of = Erased(value.value), initial = false, observationKey = key("Swift.Bool")) { old, new ->
                    events += old.payload to new.payload
                }.Compose()
            }
        }
        rule.runOnIdle { value.value = true }
        rule.runOnIdle { assertEquals(listOf(false to true), events); visible.value = false }
        rule.runOnIdle { value.value = false; events.clear() }
        rule.runOnIdle { assertTrue(events.isEmpty()); visible.value = true }
        rule.runOnIdle { assertTrue(events.isEmpty()); value.value = true }
        rule.runOnIdle { assertEquals(listOf(false to true), events) }
    }

    @Test fun legacyOnChangeRetainsInitialAndUpdateSemantics() {
        val value = mutableStateOf(false)
        val events = mutableListOf<Pair<Boolean, Boolean>>()
        rule.setContent {
            EmptyView().onChange(of = value.value, initial = true) { old, new -> events += old to new }.Compose()
        }
        rule.runOnIdle { assertEquals(listOf(false to false), events); value.value = true }
        rule.runOnIdle { assertEquals(listOf(false to false, false to true), events) }
    }

    @Test fun geometryReplacementUsesTheNewTypeForBothArguments() {
        val boolean = mutableStateOf(false)
        val events = mutableListOf<Change>()
        val tokens = mutableListOf<Any>()
        rule.setContent {
            val type = if (boolean.value) "Swift.Bool" else "SkipSwiftUI.ScenePhase"
            val payload: Any = if (boolean.value) false else ScenePhase.active
            LifetimeProbe(tokens).onGeometryChangeErased(of = { Erased(payload) }, observationKey = key(type), action = { old, new ->
                events += Change(type, old.payload, new.payload)
            }).Compose()
        }
        rule.waitForIdle()
        var token: Any? = null
        rule.runOnIdle {
            token = tokens.last()
            assertEquals(listOf(Change("SkipSwiftUI.ScenePhase", ScenePhase.active, ScenePhase.active)), events)
            events.clear(); boolean.value = true
        }
        rule.runOnIdle { assertEquals(listOf(Change("Swift.Bool", false, false)), events); assertSame(token, tokens.last()) }
    }

    @Test fun adjacentChangeAndGeometryObserversKeepIndependentHistory() {
        val flag = mutableStateOf(false)
        val phase = mutableStateOf(ScenePhase.active)
        val changes = mutableListOf<Pair<Any?, Any?>>()
        val geometry = mutableListOf<Pair<Any?, Any?>>()
        rule.setContent {
            val phaseValue = phase.value
            LifetimeProbe(mutableListOf()).onChangeErased(of = Erased(flag.value), initial = false, observationKey = key("Swift.Bool")) { old, new ->
                changes += old.payload to new.payload
            }.onGeometryChangeErased(of = { Erased(phaseValue) }, observationKey = key("SkipSwiftUI.ScenePhase"), action = { old, new ->
                geometry += old.payload to new.payload
            }).Compose()
        }
        rule.waitForIdle()
        rule.runOnIdle { assertTrue(changes.isEmpty()); assertEquals(listOf(ScenePhase.active to ScenePhase.active), geometry); flag.value = true }
        rule.runOnIdle { assertEquals(listOf(false to true), changes); assertEquals(1, geometry.size); phase.value = ScenePhase.background }
        rule.runOnIdle {
            assertEquals(listOf(false to true), changes)
            assertEquals(listOf(ScenePhase.active to ScenePhase.active, ScenePhase.active to ScenePhase.background), geometry)
        }
    }
}

private class LifetimeProbe(private val tokens: MutableList<Any>) : View, Renderable {
    @Composable override fun Render(context: ComposeContext) {
        val token = remember { Any() }
        SideEffect { tokens += token }
        Box(context.modifier.size(80.dp, 40.dp))
    }
}
