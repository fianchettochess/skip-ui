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

    // Robolectric exposes API31+ segment compositions through a different accessor.
    private fun ShadowVibrator.recordedPrimitives() =
        if (Build.VERSION.SDK_INT >= 31) primitiveSegmentsInPrimitiveEffects else primitiveEffects

    private fun ShadowVibrator.recordedRequest(): Boolean =
        isVibrating || milliseconds != 0L || pattern != null ||
            effectId != 0 || !recordedPrimitives().isNullOrEmpty()

    private fun capture(action: () -> Unit): String {
        val vibrator = prepare()
        action()
        assertTrue("The available vibrator must receive a request on API ${Build.VERSION.SDK_INT}",
            vibrator.recordedRequest())
        return "${vibrator.milliseconds}|${vibrator.pattern?.contentToString()}|" +
            "${vibrator.effectId}|${vibrator.recordedPrimitives()}"
    }

    @Test fun standardFeedbackEntryPointsReachAvailableVibrator() {
        for (feedback in listOf(SensoryFeedback.success, SensoryFeedback.warning, SensoryFeedback.error,
            SensoryFeedback.selection, SensoryFeedback.increase, SensoryFeedback.decrease,
            SensoryFeedback.start, SensoryFeedback.stop, SensoryFeedback.alignment, SensoryFeedback.levelChange)) {
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
            vibrator.recordedPrimitives().isNullOrEmpty())
        assertTrue("The fallback must retain multiple error pulses",
            (vibrator.pattern?.size ?: 0) >= 5)
    }

    @Test @Config(sdk = [30]) fun api30UsesFallbackForNewerPrimitives() {
        val vibrator = prepare(supportsPrimitives = true)
        SensoryFeedback.error.activate()
        assertTrue(vibrator.recordedRequest())
        assertTrue("LOW_TICK was added in API31, even if a device reports the numeric ID",
            vibrator.recordedPrimitives().isNullOrEmpty())
        assertTrue((vibrator.pattern?.size ?: 0) >= 5)
    }

    @Test fun supportedSuccessCompositionKeepsItsPrimitives() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val vibrator = prepare(supportsPrimitives = true)
        SensoryFeedback.success.activate()
        val primitives = requireNotNull(vibrator.recordedPrimitives()) {
            "Supported success primitives must actually reach the vibrator"
        }
        assertEquals(listOf(
            Triple(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 0),
            Triple(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 100)
        ), primitives.map { Triple(it.id, it.scale, it.delay) })
    }

    @Test fun impactFlexibilitySurvivesIntegerBridgeRoundTrip() {
        val requests = SensoryFeedback.Flexibility.values().map { flexibility ->
            val raw = SensoryFeedback.impact(flexibility = flexibility).rawValue
            capture { SensoryFeedback(rawValue = raw).activate() }
        }
        assertEquals(3, requests.toSet().size)
    }

    @Test fun zeroUIKitIntensityIsSilentForEveryStyle() {
        for (style in UIImpactFeedbackGenerator.FeedbackStyle.values()) {
            val vibrator = prepare()
            UIImpactFeedbackGenerator(style = style).impactOccurred(intensity = 0.0)
            assertFalse(vibrator.recordedRequest())
        }
    }

    @Test fun missingVibratorStaysSilent() {
        val vibrator = prepare()
        vibrator.setHasVibrator(false)
        SensoryFeedback.success.activate()
        UIImpactFeedbackGenerator().impactOccurred()
        UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackGenerator.FeedbackType.error)
        UISelectionFeedbackGenerator().selectionChanged()
        assertFalse(vibrator.recordedRequest())
    }

    @Test fun successRequiresEveryCompositionPrimitive() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val vibrator = prepare()
        vibrator.setSupportedPrimitives(listOf(VibrationEffect.Composition.PRIMITIVE_CLICK))
        SensoryFeedback.success.activate()
        assertTrue(vibrator.recordedRequest())
        assertTrue(vibrator.recordedPrimitives().isNullOrEmpty())
        assertTrue((vibrator.pattern?.size ?: 0) >= 3)
    }

    @Test fun reducedIntensityReachesSupportedPrimitive() {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val vibrator = prepare(supportsPrimitives = true)
        val raw = SensoryFeedback.impact(weight = SensoryFeedback.Weight.medium, intensity = 0.25).rawValue
        SensoryFeedback(rawValue = raw).activate()
        val primitives = requireNotNull(vibrator.recordedPrimitives())
        assertEquals(1, primitives.size)
        assertEquals(VibrationEffect.Composition.PRIMITIVE_CLICK, primitives.single().id)
        assertEquals(0.25f, primitives.single().scale, 1.0f / 65535.0f)
    }

    @Test fun errorKeepsItsPulsesWithoutAmplitudeControl() {
        val vibrator = prepare()
        vibrator.setHasAmplitudeControl(false)
        SensoryFeedback.error.activate()
        assertTrue(vibrator.recordedRequest())
        assertTrue(vibrator.recordedPrimitives().isNullOrEmpty())
        assertTrue((vibrator.pattern?.size ?: 0) >= 5)
    }

}
