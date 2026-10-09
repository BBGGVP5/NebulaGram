#if canImport(LocalAuthentication) && !SWIFT_PACKAGE
import Foundation
import LocalAuthentication

/// A fresh device-owner challenge for each destructive flow; cancellation never continues it.
public enum NebulaDeletionAuthentication {
    public static func authorize(account: String, reason: String, force: Bool = false, completion: @escaping (Bool) -> Void) {
        guard force || NebulaBehaviorPreferences.shared.enabled("biometric_delete", account: account) else { completion(true); return }
        let context = LAContext()
        context.localizedCancelTitle = Locale.preferredLanguages.first?.hasPrefix("ru") == true ? "Отмена" : "Cancel"
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else { completion(false); return }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
            DispatchQueue.main.async { completion(success) }
        }
    }
}
#endif
