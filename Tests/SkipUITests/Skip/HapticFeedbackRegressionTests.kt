// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowVibrator

/** Exercises real Android feedback entrypoints at service and effect API boundaries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29, 30, 31, 35])
class HapticFeedbackRegressionTests {
    private fun prepare(supportsPrimitives: Boolean = false): ShadowVibrator {
        ShadowVibrator.reset()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        return shadowOf(vibrator).apply {
            setHasVibrator(true)
            setHasAmplitudeControl(true)
            setSupportedPrimitives(if (supportsPrimitives) (0..8).toList() else emptyList())
        }
    }

    private fun ShadowVibrator.recordedRequest(): Boolean =
        isVibrating || milliseconds != 0L || pattern != null ||
            effectId != 0 || primitiveEffects.isNotEmpty()

    private fun capture(action: () -> Unit): String {
        val vibrator = prepare()
        action()
        assertTrue("The available vibrator must receive a request on API ${Build.VERSION.SDK_INT}",
            vibrator.recordedRequest())
        return "${vibrator.milliseconds}|${vibrator.pattern?.contentToString()}|" +
            "${vibrator.effectId}|${vibrator.primitiveEffects}"
    }

    @Test fun standardFeedbackEntryPointsReachAvailableVibrator() {
        for (feedback in listOf(SensoryFeedback.success, SensoryFeedback.error, SensoryFeedback.selection)) {
            capture { feedback.activate() }
        }
        for (style in UIImpactFeedbackGenerator.FeedbackStyle.values()) {
            capture { UIImpactFeedbackGenerator(style = style).impactOccurred() }
        }
        capture { UIImpactFeedbackGenerator(style = UIImpactFeedbackGenerator.FeedbackStyle.heavy)
            .impactOccurred(intensity = 0.5) }
        for (kind in UINotificationFeedbackGenerator.FeedbackType.values()) {
            capture { UINotificationFeedbackGenerator().notificationOccurred(kind) }
        }
        capture { UISelectionFeedbackGenerator().selectionChanged() }
    }

    @Test fun impactWeightsStillDifferAfterIntegerBridgeRoundTrip() {
        val requests = SensoryFeedback.Weight.values().map { weight ->
            val raw = SensoryFeedback.impact(weight = weight).rawValue
            capture { SensoryFeedback(rawValue = raw).activate() }
        }
        assertEquals("The bridge must preserve three different Android impact requests", 3,
            requests.toSet().size)
    }

    @Test fun zeroIntensityIsSilent() {
        val vibrator = prepare()
        SensoryFeedback.impact(weight = SensoryFeedback.Weight.heavy, intensity = 0.0).activate()
        assertFalse("Zero intensity must stay silent after crossing the integer bridge",
            vibrator.recordedRequest())
    }

    @Test fun notificationFallsBackWhenPrimitivesAreUnavailable() {
        val vibrator = prepare(supportsPrimitives = false)
        SensoryFeedback.error.activate()
        assertTrue(vibrator.recordedRequest())
        assertTrue("An unsupported composition must use an available fallback",
            vibrator.primitiveEffects.isEmpty())
        assertTrue("The fallback must retain multiple error pulses",
            (vibrator.pattern?.size ?: 0) >= 5)
    }

    @Test @Config(sdk = [30]) fun api30UsesFallbackForNewerPrimitives() {
        val vibrator = prepare(supportsPrimitives = true)
        SensoryFeedback.error.activate()
        assertTrue(vibrator.recordedRequest())
        assertTrue("LOW_TICK was added in API31, even if a device reports the numeric ID",
            vibrator.primitiveEffects.isEmpty())
        assertTrue((vibrator.pattern?.size ?: 0) >= 5)
    }

    @Test fun supportedSuccessCompositionKeepsItsPrimitives() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val vibrator = prepare(supportsPrimitives = true)
        SensoryFeedback.success.activate()
        assertEquals(listOf(
            Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 0),
            Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
        ), vibrator.primitiveEffects.map { Triple(it.id, it.scale, it.delay) })
    }
}
