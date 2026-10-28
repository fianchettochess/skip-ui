package skip.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import skip.ui.Animation
import skip.ui.Color
import skip.ui.ComposeBuilder
import skip.ui.ComposeResult
import skip.ui.ForEach
import skip.ui.Text
import skip.ui.View
import skip.ui.ZStack

/** Position changes must interpolate without evaluating their drawing content each frame. */
@RunWith(AndroidJUnit4::class)
class AnimatedPositionTests {
    @get:Rule val rule = createComposeRule()

    @Test fun positionValueAnimationHasIntermediatePlacements() {
        val x = mutableStateOf(50.0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Text(verbatim = "moving piece")
                .position(x = x.value, y = 50.0)
                .animation(Animation.linear(duration = 0.2), value = x.value)
                .Compose()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val initial = left()
        rule.runOnIdle { x.value = 250.0 }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
        val middle = left()
        assertTrue("The piece must leave its source: $initial -> $middle", middle > initial + 5f)
        assertTrue("A 200 ms move must have an intermediate position", middle < initial + 195f)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertEquals(initial + 200f, left(), 1f)
    }

    @Test fun ordinaryPositionChangeRemainsImmediate() {
        val x = mutableStateOf(50.0)
        rule.mainClock.autoAdvance = false
        rule.setContent { Text(verbatim = "moving piece").position(x = x.value, y = 50.0).Compose() }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val initial = left()
        rule.runOnIdle { x.value = 250.0 }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertEquals(initial + 200f, left(), 1f)
    }

    @Test fun positionAnimationDoesNotReevaluateDrawingContentPerFrame() {
        val x = mutableStateOf(50.0)
        val evaluations = AtomicInteger()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ComposeBuilder { context ->
                evaluations.incrementAndGet()
                Text(verbatim = "moving piece").Compose(context = context)
                ComposeResult.ok
            }.position(x = x.value, y = 50.0)
                .animation(Animation.linear(duration = 0.2), value = x.value)
                .Compose()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { x.value = 250.0 }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val atAnimationStart = evaluations.get()
        repeat(6) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        assertEquals("Moving a drawing must only update placement", atAnimationStart, evaluations.get())
    }

    @Test fun positionAnimationTicksDoNotRecompose() {
        val x = mutableStateOf(50.0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Text(verbatim = "moving piece")
                .position(x = x.value, y = 50.0)
                .animation(Animation.linear(duration = 0.2), value = x.value)
                .Compose()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val initial = left()
        rule.runOnIdle { x.value = 250.0 }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(32)
        rule.waitForIdle()
        val recomposers = rule.runOnIdle { Recomposer.runningRecomposers.value.toList() }
        assertTrue("The test must observe an active composition", recomposers.isNotEmpty())
        val before = recomposers.sumOf { it.changeCount }
        repeat(4) {
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
        }
        val after = recomposers.sumOf { it.changeCount }
        val middle = left()
        assertTrue("The animation must still move during the measured interval", middle > initial + 5f)
        assertTrue("The measured interval must end before the animation finishes", middle < initial + 195f)
        assertEquals("Position animation ticks must update placement without recomposition", before, after)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertEquals(initial + 200f, left(), 1f)
    }

    @Test fun unchangedDragAnimationKeepsSquareAnimation() {
        val x = mutableStateOf(50.0)
        rule.mainClock.autoAdvance = false
        rule.setContent { piece(x.value, "moving piece", true).Compose() }
        assertIntermediateMove { x.value = 250.0 }
    }

    @Test fun bridgedStablePieceIdentityKeepsPositionAnimation() = assertBridgedPieces(false)

    @Test fun bridgedPieceDragModifierChainKeepsPositionAnimation() = assertBridgedPieces(true)

    private fun assertBridgedPieces(fullChain: Boolean) {
        val x = mutableStateOf(50.0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ZStack {
                ForEach(startIndex = { 0 }, endIndex = { 32 }, identifier = { "piece-$it" }, bridgedContent = { id ->
                    if (id == 0) piece(x.value, "moving piece", fullChain)
                    else Text(verbatim = "stationary $id").position(x = 300.0 + (id % 8) * 32, y = 150.0 + (id / 8) * 32)
                })
            }.Compose()
        }
        assertIntermediateMove { x.value = 250.0 }
    }

    private fun piece(x: Double, label: String, fullChain: Boolean): View {
        if (!fullChain) return Text(verbatim = label).position(x = x, y = 50.0)
            .animation(Animation.linear(duration = 0.2), value = x)
        return Text(verbatim = label)
            .frame(width = 32.0, height = 32.0)
            .shadow(color = Color.black.opacity(0.25), radius = 1.0, x = 0.0, y = 1.0)
            .scaleEffect(1.0)
            .position(x = x, y = 50.0)
            .offset(x = 0.0, y = 0.0)
            .zIndex(0.0)
            .animation(Animation.spring(response = 0.28, dampingFraction = 0.62), value = false)
            .animation(Animation.linear(duration = 0.2), value = x)
    }

    private fun assertIntermediateMove(move: () -> Unit) {
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        val initial = left()
        rule.runOnIdle(move)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        rule.waitForIdle()
        val middle = left()
        assertTrue("The piece must leave its source: $initial -> $middle", middle > initial + 5f)
        assertTrue("The 200 ms move must have an intermediate position: $initial -> $middle", middle < initial + 195f)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertEquals(initial + 200f, left(), 1f)
    }

    private fun left(): Float {
        // Current SkipUI shadows expose both the drawing and its offset copy.
        // Measure every copy and require them to move together; do not choose
        // an arbitrary match or mistake the duplicate for an animation failure.
        val nodes = rule.onAllNodesWithText("moving piece")
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("Expected the piece and at most its shadow: $count", count in 1..2)
        val positions = (0 until count).map { nodes[it].getUnclippedBoundsInRoot().left.value }
        assertTrue("Drawing and shadow must share their horizontal placement: $positions",
            positions.all { abs(it - positions.first()) < 1f })
        return positions.first()
    }
}
