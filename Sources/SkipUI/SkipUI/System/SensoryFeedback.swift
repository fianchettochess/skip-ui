// Copyright 2023–2026 Skip
// SPDX-License-Identifier: MPL-2.0
#if !SKIP_BRIDGE
#if SKIP
import android.os.VibrationEffect
#endif

public struct SensoryFeedback : RawRepresentable, Equatable {
    public let rawValue: Int

    public init(rawValue: Int) {
        self.rawValue = rawValue
    }

    public static let success = SensoryFeedback(rawValue: 1) // For bridging
    public static let warning = SensoryFeedback(rawValue: 2) // For bridging
    public static let error = SensoryFeedback(rawValue: 3) // For bridging
    public static let selection = SensoryFeedback(rawValue: 4) // For bridging
    public static let increase = SensoryFeedback(rawValue: 5) // For bridging
    public static let decrease = SensoryFeedback(rawValue: 6) // For bridging
    public static let start = SensoryFeedback(rawValue: 7) // For bridging
    public static let stop = SensoryFeedback(rawValue: 8) // For bridging
    public static let alignment = SensoryFeedback(rawValue: 9) // For bridging
    public static let levelChange = SensoryFeedback(rawValue: 10) // For bridging
    public static let impact = SensoryFeedback(rawValue: 11) // For bridging

    public static func impact(weight: SensoryFeedback.Weight = .medium, intensity: Double = 1.0) -> SensoryFeedback {
        let style: HapticFeedbackImpact.Style
        switch weight {
        case .light: style = .light
        case .medium: style = .medium
        case .heavy: style = .heavy
        }
        return SensoryFeedback(rawValue: HapticFeedbackImpact(style: style, intensity: intensity).rawValue)
    }

    public static func impact(flexibility: SensoryFeedback.Flexibility, intensity: Double = 1.0) -> SensoryFeedback {
        let style: HapticFeedbackImpact.Style
        switch flexibility {
        case .soft: style = .soft
        case .solid: style = .medium
        case .rigid: style = .rigid
        }
        return SensoryFeedback(rawValue: HapticFeedbackImpact(style: style, intensity: intensity).rawValue)
    }

    public enum Weight : Equatable {
        case light
        case medium
        case heavy
    }

    public enum Flexibility : Equatable {
        case rigid
        case solid
        case soft
    }

    public func activate() {
        #if SKIP
        if let impact = HapticFeedbackImpact(rawValue: rawValue) {
            AndroidHapticFeedback.impact(style: impact.style.rawValue, intensity: impact.intensity)
        } else {
            AndroidHapticFeedback.sensory(identifier: rawValue)
        }
        #endif
    }
}

extension View {
    public func sensoryFeedback<T>(_ feedback: SensoryFeedback, trigger: T) -> any View {
        return onChange(of: trigger) {
            feedback.activate()
        }
    }

    // SKIP @bridge
    public func sensoryFeedback(bridgedFeedback: Int, trigger: Any?) -> any View {
        let feedback = SensoryFeedback(rawValue: bridgedFeedback)
        return self.sensoryFeedback(feedback, trigger: trigger)
    }

    public func sensoryFeedback<T>(_ feedback: SensoryFeedback, trigger: T, condition: @escaping (_ oldValue: T, _ newValue: T) -> Bool) -> any View {
        return onChange(of: trigger) { oldValue, newValue in
            if condition(oldValue, newValue) {
                feedback.activate()
            }
        }
    }

    public func sensoryFeedback<T>(trigger: T, _ feedback: @escaping (_ oldValue: T, _ newValue: T) -> SensoryFeedback?) -> any View {
        return onChange(of: trigger) { oldValue, newValue in
            feedback(oldValue, newValue)?.activate()
        }
    }

    // SKIP @bridge
    public func sensoryFeedbackClosure(trigger: Any?, bridgedFeedback: @escaping (_ oldValue: Any?, _ newValue: Any?) -> Int?) -> any View {
        return self.sensoryFeedback(trigger: trigger) { oldValue, newValue in
            if let feedback = bridgedFeedback(oldValue, newValue) {
                return SensoryFeedback(rawValue: feedback)
            } else {
                return nil
            }
        }
    }
}

#endif
