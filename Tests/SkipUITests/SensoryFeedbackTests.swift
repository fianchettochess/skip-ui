// Copyright 2026 Skip
// SPDX-License-Identifier: MPL-2.0
import SkipUI
import XCTest

final class SensoryFeedbackTests: XCTestCase {
    func testImpactWeightsSurviveRawValueRoundTrip() {
        let effects = [SensoryFeedback.Weight.light, .medium, .heavy].map {
            SensoryFeedback.impact(weight: $0)
        }
        XCTAssertEqual(Set(effects.map { $0.rawValue }).count, 3,
            "The integer passed through the Android bridge must preserve the requested weight")
        for effect in effects {
            XCTAssertEqual(SensoryFeedback(rawValue: effect.rawValue), effect)
        }
    }

    func testImpactFlexibilitySurvivesRawValueRoundTrip() {
        let effects = [SensoryFeedback.Flexibility.soft, .solid, .rigid].map {
            SensoryFeedback.impact(flexibility: $0)
        }
        XCTAssertEqual(Set(effects.map { $0.rawValue }).count, 3,
            "Soft, solid and rigid impacts must remain distinct across the bridge")
        for effect in effects {
            XCTAssertEqual(SensoryFeedback(rawValue: effect.rawValue), effect)
        }
    }

    func testImpactIntensitySurvivesRawValueRoundTrip() {
        let effects = [0.0, 0.25, 0.5, 1.0].map {
            SensoryFeedback.impact(weight: .medium, intensity: $0)
        }
        XCTAssertEqual(Set(effects.map { $0.rawValue }).count, 4,
            "A zero or reduced impact must not become a full-strength impact at the bridge")
        for effect in effects {
            XCTAssertEqual(SensoryFeedback(rawValue: effect.rawValue), effect)
        }
    }

    func testExistingFeedbackConstantsKeepTheirBridgeValues() {
        XCTAssertEqual(SensoryFeedback.success.rawValue, 1)
        XCTAssertEqual(SensoryFeedback.warning.rawValue, 2)
        XCTAssertEqual(SensoryFeedback.error.rawValue, 3)
        XCTAssertEqual(SensoryFeedback.impact.rawValue, 11)
        XCTAssertEqual(SensoryFeedback.impact(weight: .medium), .impact)
    }
}
