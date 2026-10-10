package app.nebulagram.ui;

import java.util.Locale;

/** Explicit local engines; Advanced audio support is distinct from text Gemini Nano. */
public final class NebulaLocalAudioPolicy {
    public static final String NANO = "nano", BASIC = "device-recognition";
    public static final int SAMPLE_RATE = 16000, BYTES_PER_SECOND = 32000, MAX_SECONDS = 600;
    public static final String[] LOCALES = {"ru-RU", "en-US", "fr-FR", "it-IT", "de-DE", "es-ES", "hi-IN", "ja-JP", "pt-BR", "tr-TR", "pl-PL", "cmn-Hans-CN", "ko-KR", "cmn-Hant-TW", "vi-VN", "nl-NL", "da-DK", "sv-SE", "id-ID", "ar-SA", "th-TH"};
    private NebulaLocalAudioPolicy() { }
    public static boolean local(String id) { return NANO.equals(id) || BASIC.equals(id); }
    public static boolean advancedDevice(String manufacturer, String model, int sdk) {
        // The SDK's preferred-mode setting may select Basic elsewhere. Do not label that Nano.
        return sdk >= 31 && "google".equalsIgnoreCase(manufacturer) && model != null && model.matches("(?i)^Pixel (10|11)(?: .*)?$");
    }
    public static String locale(String value) {
        for (String allowed : LOCALES) if (allowed.equalsIgnoreCase(value)) return allowed;
        String language = Locale.forLanguageTag(value == null ? "" : value.replace('_','-')).getLanguage();
        for (String allowed : LOCALES) if (Locale.forLanguageTag(allowed).getLanguage().equals(language)) return allowed;
        return "en-US";
    }
    public static long playbackNanos(long bytes) {
        if (bytes < 0 || bytes > (long)BYTES_PER_SECOND * MAX_SECONDS) throw new IllegalArgumentException("PCM duration limit");
        return bytes * 1_000_000_000L / BYTES_PER_SECOND;
    }
}
