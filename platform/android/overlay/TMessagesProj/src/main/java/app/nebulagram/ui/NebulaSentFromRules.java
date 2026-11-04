package app.nebulagram.ui;

/**
 * Guesses which client recorded a voice note or round video from its document
 * attributes alone. Telegram sends no platform field; these are fingerprints of
 * how the official sources build the upload (Android 13.1.1, iOS at our pin):
 *
 * Round video — Android (SendMessagesHelper) sets supports_streaming and adds no
 * file name; iOS (VideoMessageCameraScreen, LegacyInstantVideoController) adds
 * FileName "video.mp4" and no streaming flag. Anything else is unknown.
 *
 * Voice note — Android and iOS both send audio/ogg, a waveform and no file name,
 * so the platform cannot be told apart; we only separate "an official app
 * recorder" from uploads that lack the waveform or carry a file name (bots,
 * scripts, some third-party clients). Forks of either app inherit its fingerprint.
 */
public final class NebulaSentFromRules {
    public static final int UNKNOWN = 0, ANDROID = 1, IOS = 2, OFFICIAL_APP = 3, NON_STANDARD = 4;

    private NebulaSentFromRules() {}

    public static int round(String fileName, boolean supportsStreaming) {
        boolean named = fileName != null && !fileName.isEmpty();
        if (!named && supportsStreaming) return ANDROID;
        if ("video.mp4".equals(fileName) && !supportsStreaming) return IOS;
        return UNKNOWN;
    }

    public static int voice(String mime, String fileName, byte[] waveform) {
        boolean named = fileName != null && !fileName.isEmpty();
        boolean ogg = mime != null && mime.toLowerCase(java.util.Locale.ROOT).startsWith("audio/ogg");
        if (waveform == null || waveform.length == 0 || named || !ogg) return NON_STANDARD;
        return OFFICIAL_APP;
    }

    public static String label(int verdict, boolean russian) {
        switch (verdict) {
            case ANDROID: return russian ? "Telegram для Android (или форк), по атрибутам файла" : "Telegram for Android (or a fork), by file attributes";
            case IOS: return russian ? "Telegram для iOS (или форк), по атрибутам файла" : "Telegram for iOS (or a fork), by file attributes";
            case OFFICIAL_APP: return russian ? "Запись в приложении Telegram (Android и iOS неотличимы)" : "Recorded in a Telegram app (Android and iOS look alike)";
            case NON_STANDARD: return russian ? "Нестандартная загрузка: бот, скрипт или сторонний клиент" : "Non-standard upload: a bot, script or third-party client";
            default: return russian ? "Не удалось определить" : "Could not be determined";
        }
    }
}
