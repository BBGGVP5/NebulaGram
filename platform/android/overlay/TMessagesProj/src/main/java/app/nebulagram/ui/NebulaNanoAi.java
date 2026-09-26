package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.ModelConfig;
import com.google.mlkit.genai.prompt.ModelPreference;
import com.google.mlkit.genai.prompt.ModelReleaseStage;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.telegram.messenger.ApplicationLoader;

import java.util.concurrent.TimeUnit;

/** On-device Gemini Nano access. No request in this class opens a network connection. */
public final class NebulaNanoAi {
    private static final String PREFS = "nebula_ai_settings";
    private NebulaNanoAi() { }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean supportedByOs() {
        return Build.VERSION.SDK_INT >= 26;
    }

    private static GenerativeModelFutures model() {
        int release = prefs().getBoolean("nano_preview", false) ? ModelReleaseStage.PREVIEW : ModelReleaseStage.STABLE;
        int preference = prefs().getBoolean("nano_fast", true) ? ModelPreference.FAST : ModelPreference.FULL;
        ModelConfig modelConfig = new ModelConfig.Builder()
                .setReleaseStage(release)
                .setPreference(preference)
                .build();
        GenerationConfig config = new GenerationConfig.Builder()
                .setModelConfig(modelConfig)
                .build();
        return GenerativeModelFutures.from(Generation.INSTANCE.getClient(config));
    }

    /** Returns a FeatureStatus value; callers must invoke it off the UI thread. */
    public static int checkStatus() throws Exception {
        if (!supportedByOs()) return FeatureStatus.UNAVAILABLE;
        try {
            return model().checkStatus().get(45, TimeUnit.SECONDS);
        } catch (Exception e) {
            return FeatureStatus.UNAVAILABLE;
        }
    }

    /** Starts AICore's one-time model download. Progress callbacks are provided by ML Kit. */
    public static void download(DownloadCallback callback) throws Exception {
        if (!supportedByOs()) throw new IllegalStateException("Gemini Nano requires Android 8.0 or newer");
        model().download(callback).get(30, TimeUnit.MINUTES);
    }

    public static String generate(String instructions, String input) throws Exception {
        if (!supportedByOs()) throw new IllegalStateException("Gemini Nano is unavailable on this Android version");
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("Enter text");
        if (input.length() > 10000 || instructions != null && instructions.length() > 4000)
            throw new IllegalArgumentException("Gemini Nano supports shorter prompts on device");
        int status = checkStatus();
        if (status == FeatureStatus.DOWNLOADABLE)
            throw new IllegalStateException("GEMINI_NANO_DOWNLOAD_REQUIRED");
        if (status == FeatureStatus.DOWNLOADING)
            throw new IllegalStateException("GEMINI_NANO_DOWNLOADING");
        if (status != FeatureStatus.AVAILABLE)
            throw new IllegalStateException("GEMINI_NANO_UNAVAILABLE");
        StringBuilder prompt = new StringBuilder();
        if (instructions != null && !instructions.trim().isEmpty()) {
            prompt.append("Instructions: ").append(instructions.trim()).append('\n');
        }
        prompt.append("User request: ").append(input.trim());
        return model().generateContent(prompt.toString()).get(3, TimeUnit.MINUTES).getText().trim();
    }
}
