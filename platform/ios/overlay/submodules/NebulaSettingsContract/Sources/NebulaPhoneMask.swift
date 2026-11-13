import Foundation

/// "Hide profile phone number" keeps the row in place with its digits hidden
/// (Android 0297 blurs it). The leading country digits and the last two stay,
/// so the user still recognises which number it is; tapping the row opens
/// Telegram's own phone actions, which show the full number.
public enum NebulaPhoneMask {
    public static func mask(_ formatted: String) -> String {
        let digits = formatted.filter { $0.isNumber }.count
        guard digits > 4 else { return String(formatted.map { $0.isNumber ? "•" : $0 }) }
        var seen = 0
        var result = ""
        for character in formatted {
            if character.isNumber {
                seen += 1
                result.append(seen <= 2 || seen > digits - 2 ? character : "•")
            } else {
                result.append(character)
            }
        }
        return result
    }
}
