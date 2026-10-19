// Copyright 2023–2026 Skip
// SPDX-License-Identifier: MPL-2.0
#if !SKIP_BRIDGE
import Foundation

// note that this needs AndroidManifest.xml permission:
// <uses-permission android:name="android.permission.VIBRATE"/>
public protocol UIFeedbackGenerator {
}

/// UIImpactFeedbackGenerator is used to give user feedback when an impact between UI elements occurs
open class UIImpactFeedbackGenerator : UIFeedbackGenerator {
    private let style: UIImpactFeedbackGenerator.FeedbackStyle

    public init() {
        self.style = .medium
    }

    public init(style: UIImpactFeedbackGenerator.FeedbackStyle) {
        self.style = style
    }

    /// call when your UI element impacts something else
    open func impactOccurred() {
        #if SKIP
        AndroidHapticFeedback.impact(style: style.rawValue, intensity: 1.0)
        #endif
    }

    /// call when your UI element impacts something else with a specific intensity [0.0, 1.0]
    open func impactOccurred(intensity: CGFloat) {
        #if SKIP
        AndroidHapticFeedback.impact(style: style.rawValue, intensity: Double(intensity))
        #endif
    }

    open func impactOccurred(intensity: CGFloat, at location: CGPoint) {
        impactOccurred(intensity: intensity)
    }

    public enum FeedbackStyle : Int {
        case light = 0
        case medium = 1
        case heavy = 2

        case soft = 3
        case rigid = 4

    }
}

/// UINotificationFeedbackGenerator is used to give user feedback when an notification is displayed
open class UINotificationFeedbackGenerator : UIFeedbackGenerator {

    public init() {
    }

    /// call when a notification is displayed, passing the corresponding type
    open func notificationOccurred(_ notificationType: FeedbackType) {
        #if SKIP
        AndroidHapticFeedback.notification(kind: notificationType.rawValue)
        #endif
    }

    /// call when a notification is displayed, passing the corresponding type
    open func notificationOccurred(_ notificationType: FeedbackType, at location: CGPoint) {
        notificationOccurred(notificationType)
    }

    public enum FeedbackType : Int {
        case success = 0
        case warning = 1
        case error = 2

    }
}


/// UINotificationFeedbackGenerator is used to give user feedback when an notification is displayed
open class UISelectionFeedbackGenerator : UIFeedbackGenerator {

    public init() {
    }

    /// call when a notification is displayed, passing the corresponding type
    open func selectionChanged() {
        #if SKIP
        AndroidHapticFeedback.sensory(identifier: 4)
        #endif
    }

    open func selectionChanged(at location: CGPoint) {
        selectionChanged()
    }
}

#endif
