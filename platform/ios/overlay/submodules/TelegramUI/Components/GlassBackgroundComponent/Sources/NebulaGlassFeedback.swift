import UIKit
import NebulaSettingsContract

/// A single opt-in pulse per interaction, shared by overlapping glass surfaces.
public enum NebulaGlassFeedback {
    private static var lastImpact = -Double.greatestFiniteMagnitude
    private static var generator: UIImpactFeedbackGenerator?

    public static func impact() {
        guard Thread.isMainThread, NebulaSettingsStore.shared.glassHaptics else { return }
        let reduced = NebulaGlassPolicy.reduced(mode: NebulaSettingsStore.shared.glassQuality,
            lowPower: ProcessInfo.processInfo.isLowPowerModeEnabled,
            hot: ProcessInfo.processInfo.thermalState.rawValue >= ProcessInfo.ThermalState.serious.rawValue,
            reduceTransparency: UIAccessibility.isReduceTransparencyEnabled)
        guard !reduced else { return }
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastImpact >= 0.08 else { return }
        lastImpact = now
        if generator == nil { generator = UIImpactFeedbackGenerator(style: .soft) }
        generator?.impactOccurred(intensity: CGFloat(NebulaSettingsStore.shared.glassHapticStrength) / 100.0)
    }
}

/// Fails immediately, so scrolling, native highlight gestures and button taps keep ownership.
final class NebulaGlassFeedbackRecognizer: UIGestureRecognizer {
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        NebulaGlassFeedback.impact()
        state = .failed
    }
}
