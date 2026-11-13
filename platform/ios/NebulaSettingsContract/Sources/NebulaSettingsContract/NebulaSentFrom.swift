import Foundation

/// Guesses which client recorded a voice note or round video from its document
/// attributes alone (Android `NebulaSentFromRules`). Telegram sends no platform
/// field; these are fingerprints of how the official apps build the upload.
///
/// Round video: Android sets supports_streaming and adds no file name; iOS adds
/// the file name "video.mp4" and no streaming flag. Voice notes look alike on
/// both, so only an official recorder is told apart from other uploads.
public enum NebulaSentFrom: Equatable {
    case unknown, android, ios, officialApp, nonStandard

    public static func round(fileName: String?, supportsStreaming: Bool) -> NebulaSentFrom {
        let named = !(fileName ?? "").isEmpty
        if !named && supportsStreaming { return .android }
        if fileName == "video.mp4" && !supportsStreaming { return .ios }
        return .unknown
    }

    public static func voice(mimeType: String?, fileName: String?, waveform: Data?) -> NebulaSentFrom {
        let named = !(fileName ?? "").isEmpty
        let ogg = (mimeType ?? "").lowercased().hasPrefix("audio/ogg")
        if (waveform ?? Data()).isEmpty || named || !ogg { return .nonStandard }
        return .officialApp
    }

    public func label(russian: Bool) -> String {
        switch self {
        case .android: return russian ? "Telegram для Android (или форк), по атрибутам файла" : "Telegram for Android (or a fork), by file attributes"
        case .ios: return russian ? "Telegram для iOS (или форк), по атрибутам файла" : "Telegram for iOS (or a fork), by file attributes"
        case .officialApp: return russian ? "Запись в приложении Telegram (Android и iOS неотличимы)" : "Recorded in a Telegram app (Android and iOS look alike)"
        case .nonStandard: return russian ? "Нестандартная загрузка: бот, скрипт или сторонний клиент" : "Non-standard upload: a bot, script or third-party client"
        case .unknown: return russian ? "Не удалось определить" : "Could not be determined"
        }
    }
}
