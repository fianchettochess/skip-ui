// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition.*
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.collections.List
import skip.foundation.ProcessInfo

/** Android effects shared by the sensory-feedback and UIKit entrypoints. */
internal object AndroidHapticFeedback {
    private data class Pulse(val primitive: Int, val scale: Float, val delay: Int,
        val duration: Long, val amplitude: Int)

    private fun vibrator(): Vibrator? {
        val context = ProcessInfo.processInfo.androidContext
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator ?: context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    fun impact(style: Int, intensity: Double) {
        val strength = if (intensity.isNaN()) 0.0 else intensity.coerceIn(0.0, 1.0)
        // A primitive's scale zero can still be perceptible on real hardware.
        if (strength <= 0.0) return
        val vibrator = vibrator() ?: return
        val pulse = when (style) {
            0 -> Pulse(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 0, 8, 70)
            2 -> Pulse(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 0, 25, 255)
            3 -> Pulse(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, 0.5f, 0, 20, 90)
            4 -> Pulse(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1.0f, 0, 5, 210)
            else -> Pulse(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 0, 15, 150)
        }
        if (canCompose(vibrator, listOf(pulse))) {
            play(vibrator, listOf(pulse), strength)
        } else if (Build.VERSION.SDK_INT >= 29 && strength == 1.0 && style in 0..2) {
            // Android supplies the hardware fallback for predefined effects.
            val effect = when (style) {
                0 -> VibrationEffect.EFFECT_TICK
                2 -> VibrationEffect.EFFECT_HEAVY_CLICK
                else -> VibrationEffect.EFFECT_CLICK
            }
            vibrator.vibrate(VibrationEffect.createPredefined(effect))
        } else {
            waveform(vibrator, listOf(pulse), strength)
        }
    }

    fun sensory(identifier: Int) {
        val pulses = when (identifier) {
            1 -> listOf(Pulse(PRIMITIVE_CLICK, 1.0f, 0, 15, 255), Pulse(PRIMITIVE_TICK, 0.5f, 100, 8, 128))
            2 -> listOf(Pulse(PRIMITIVE_SLOW_RISE, 1.0f, 0, 25, 255), Pulse(PRIMITIVE_QUICK_FALL, 1.0f, 100, 25, 255))
            3 -> listOf(Pulse(PRIMITIVE_LOW_TICK, 1.0f, 0, 12, 255), Pulse(PRIMITIVE_LOW_TICK, 0.75f, 100, 12, 191),
                Pulse(PRIMITIVE_LOW_TICK, 0.5f, 200, 12, 128))
            4 -> listOf(Pulse(PRIMITIVE_TICK, 0.3f, 0, 6, 76))
            5 -> listOf(Pulse(PRIMITIVE_QUICK_RISE, 0.7f, 0, 15, 178))
            6 -> listOf(Pulse(PRIMITIVE_QUICK_FALL, 0.7f, 0, 10, 178))
            7 -> listOf(Pulse(PRIMITIVE_QUICK_RISE, 1.0f, 0, 20, 255))
            8 -> listOf(Pulse(PRIMITIVE_QUICK_FALL, 1.0f, 0, 12, 255))
            9 -> listOf(Pulse(PRIMITIVE_TICK, 0.5f, 0, 8, 128), Pulse(PRIMITIVE_TICK, 0.5f, 50, 8, 128))
            10 -> listOf(Pulse(PRIMITIVE_CLICK, 0.8f, 0, 15, 204), Pulse(PRIMITIVE_CLICK, 0.8f, 100, 15, 204))
            else -> return
        }
        val vibrator = vibrator() ?: return
        play(vibrator, pulses)
    }

    fun notification(kind: Int) {
        val pulses = when (kind) {
            0, 1 -> listOf(Pulse(PRIMITIVE_CLICK, 0.8f, 0, 15, 204), Pulse(PRIMITIVE_CLICK, 0.8f, 150, 15, 204))
            2 -> listOf(Pulse(PRIMITIVE_CLICK, 0.5f, 0, 12, 128), Pulse(PRIMITIVE_CLICK, 0.7f, 100, 12, 178),
                Pulse(PRIMITIVE_CLICK, 0.9f, 150, 12, 230))
            else -> return
        }
        val vibrator = vibrator() ?: return
        play(vibrator, pulses)
    }

    private fun canCompose(vibrator: Vibrator, pulses: List<Pulse>): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        // THUD, SPIN and LOW_TICK were introduced after Composition itself.
        if (Build.VERSION.SDK_INT < 31 && pulses.any { it.primitive in listOf(PRIMITIVE_THUD, PRIMITIVE_SPIN, PRIMITIVE_LOW_TICK) }) return false
        // Compositions have no automatic fallback: every primitive must be available.
        return pulses.all { vibrator.areAllPrimitivesSupported(it.primitive) }
    }

    private fun play(vibrator: Vibrator, pulses: List<Pulse>, intensity: Double = 1.0) {
        if (!canCompose(vibrator, pulses)) {
            waveform(vibrator, pulses, intensity)
            return
        }
        val composition = VibrationEffect.startComposition()
        pulses.forEach { composition.addPrimitive(it.primitive, (it.scale * intensity).toFloat(), it.delay) }
        vibrator.vibrate(composition.compose())
    }

    private fun waveform(vibrator: Vibrator, pulses: List<Pulse>, intensity: Double) {
        val timings = pulses.flatMap { listOf(it.delay.toLong(), it.duration) }.toLongArray()
        val amplitudes = pulses.flatMap {
            listOf(0, (it.amplitude * intensity).toInt().coerceIn(1, 255))
        }.toIntArray()
        // Waveforms are available since API26, including all supported legacy devices.
        // Without amplitude control Android ignores nonzero amplitudes; pulse timing still survives.
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
