package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import java.io.IOException;

/** Audio choices are independent of the selected text-chat model. Keys stay in Keystore. */
public final class NebulaAudioPreferences {
    private NebulaAudioPreferences() { }
    public static SharedPreferences prefs() {
        SharedPreferences p = ApplicationLoader.applicationContext.getSharedPreferences("nebula_audio_settings", 0);
        if (!p.getBoolean("initialized", false)) {
            NebulaAiServices.Service active = NebulaAiServices.find(NebulaAiServices.selected());
            SharedPreferences.Editor edit = p.edit().putBoolean("initialized", true).putString("speech_service", "device");
            if (active != null && supported(active)) edit.putString("transcription_service", active.id);
            edit.apply();
        }
        return p;
    }
    public static boolean supported(NebulaAiServices.Service service) { return service.provider == 0 || service.provider == 2; }
    public static String serviceId(boolean speech) { return prefs().getString(speech ? "speech_service" : "transcription_service", speech ? "device" : ""); }
    public static boolean localTranscription() { return NebulaLocalAudioPolicy.local(serviceId(false)); }
    public static boolean hasTranscriptionService() { NebulaAiServices.Service s=NebulaAiServices.find(serviceId(false)); return localTranscription() || s!=null && supported(s); }
    public static String localLocale() { return NebulaLocalAudioPolicy.locale(prefs().getString("local_audio_locale", org.telegram.messenger.LocaleController.getInstance().getCurrentLocale().toLanguageTag())); }
    public static String title(boolean speech) {
        String id = serviceId(speech);
        if (!speech && NebulaLocalAudioPolicy.NANO.equals(id)) return "Gemini Nano · " + NebulaText.text("на устройстве", "on device");
        if (!speech && NebulaLocalAudioPolicy.BASIC.equals(id)) return NebulaText.text("Android · на устройстве", "Android · on device");
        if (speech && "device".equals(id)) return NebulaText.text("На устройстве", "On device");
        NebulaAiServices.Service service = NebulaAiServices.find(id);
        return service == null ? NebulaText.text("Выбрать сервис", "Choose service") : service.name;
    }
    public static String model(boolean speech, NebulaAiServices.Service service) {
        String key = (speech ? "speech_model_" : "transcription_model_") + service.provider;
        return prefs().getString(key, speech ? service.provider == 0 ? "gpt-4o-mini-tts" : "gemini-3.8-flash-tts" : service.provider == 0 ? "gpt-4o-mini-transcribe" : service.model);
    }
    public static String voice(int provider) { return prefs().getString("voice_" + provider, provider == 0 ? "marin" : "Kore"); }
    public static NebulaAudioClient.Configuration capture(boolean speech) throws Exception {
        NebulaAiServices.Service service = NebulaAiServices.find(serviceId(speech));
        if (service == null || !supported(service)) throw new IOException(NebulaText.text("Выберите сервис в «ИИ → Аудио и голоса»", "Choose a service in AI → Audio & voices"));
        return new NebulaAudioClient.Configuration(service.provider, NebulaAiServices.key(service.id), model(speech, service), voice(service.provider), prefs().getString("style", "neutral"), Math.round(prefs().getFloat("speed", 1) * 10) / 10.0);
    }
}
