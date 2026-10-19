// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0

/// Shared impact representation for the native Swift and transpiled Android feedback bridge.
/// This wire format is an implementation detail, separate from SwiftUI's feedback API.
public struct HapticFeedbackImpact : RawRepresentable, Equatable, Sendable {
    public enum Style : Int, Sendable {
        case light = 0
        case medium = 1
        case heavy = 2
        case soft = 3
        case rigid = 4
    }

    public let style: Style
    public let intensity: Double

    public init(style: Style, intensity: Double) {
        self.style = style
        let bounded = intensity.isNaN ? 0.0 : min(1.0, max(0.0, intensity))
        self.intensity = Double(Int(bounded * 65535.0 + 0.5)) / 65535.0
    }

    public init?(rawValue: Int) {
        if rawValue == 11 {
            self.init(style: .medium, intensity: 1.0)
        } else {
            // Positive, Int32-safe identifiers leave the existing 1...11 constants intact.
            guard rawValue >= 16777216, rawValue < 16777216 + 5 * 65536 else { return nil }
            let packed = rawValue - 16777216
            guard let style = Style(rawValue: packed / 65536) else { return nil }
            self.init(style: style, intensity: Double(packed % 65536) / 65535.0)
        }
    }

    public var rawValue: Int {
        if style == .medium && intensity == 1.0 { return 11 }
        return 16777216 + style.rawValue * 65536 + Int(intensity * 65535.0 + 0.5)
    }
}
