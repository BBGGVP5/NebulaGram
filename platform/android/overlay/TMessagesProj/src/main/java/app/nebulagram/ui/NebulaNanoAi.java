package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
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

    private static GenerativeModelFutures model(boolean preview, boolean fast) {
        int release = preview ? ModelReleaseStage.PREVIEW : ModelReleaseStage.STABLE;
        int preference = fast ? ModelPreference.FAST : ModelPreference.FULL;
        ModelConfig.Builder modelConfigBuilder = new ModelConfig.Builder();
        modelConfigBuilder.setReleaseStage(release);
        modelConfigBuilder.setPreference(preference);
        ModelConfig modelConfig = modelConfigBuilder.build();
        GenerationConfig.Builder generationConfigBuilder = new GenerationConfig.Builder();
        generationConfigBuilder.setModelConfig(modelConfig);
        GenerationConfig config = generationConfigBuilder.build();
        return GenerativeModelFutures.from(Generation.INSTANCE.getClient(config));
    }

    /** Returns a FeatureStatus value; callers must invoke it off the UI thread. */
    public static int checkStatus() throws Exception {
        if (!supportedByOs()) return FeatureStatus.UNAVAILABLE;
        try (Session session = session()) { return session.checkStatus(); }
    }

    public static Session session() {
        return new Session(prefs().getBoolean("nano_preview", false), prefs().getBoolean("nano_fast", false));
    }

    /** One immutable configuration for status, download and inference. */
    public static final class Session implements AutoCloseable {
        private final GenerativeModelFutures client;
        public Session(boolean preview, boolean fast) { client = model(preview, fast); }
        public int checkStatus() throws Exception { return await(client.checkStatus(), 45, TimeUnit.SECONDS); }
        public void download(DownloadCallback callback) throws Exception {
            await(client.download(callback), 30, TimeUnit.MINUTES);
        }
        @Override public void close() { client.getGenerativeModel().close(); }
    }

    private static <T> T await(java.util.concurrent.Future<T> future, long timeout, TimeUnit unit) throws Exception {
        try { return future.get(timeout, unit); }
        catch (InterruptedException e) { future.cancel(true); Thread.currentThread().interrupt(); throw e; }
        catch (java.util.concurrent.TimeoutException e) { future.cancel(true); throw e; }
    }

    private static <T> T await(java.util.concurrent.Future<T> future, long timeout, TimeUnit unit,
            java.util.function.BooleanSupplier cancelled) throws Exception {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        try {
            while (!cancelled.getAsBoolean()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new java.util.concurrent.TimeoutException();
                try { return future.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS); }
                catch (java.util.concurrent.TimeoutException waiting) { }
            }
            throw new java.io.InterruptedIOException();
        } finally { if (!future.isDone()) future.cancel(true); }
    }

    /** Display only our own messages; service errors can contain request text. */
    public static String errorText(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; depth < 8 && cause != null; depth++, cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException)
                return NebulaText.text("AICore не ответил вовремя. Повторите проверку с открытым приложением.", "AICore timed out. Retry with the app open.");
            if ("GEMINI_NANO_BUSY".equals(cause.getMessage()))
                return NebulaText.text("Gemini Nano завершает предыдущий запрос. Повторите через несколько секунд.", "Gemini Nano is finishing the previous request. Try again in a few seconds.");
            if ("GEMINI_NANO_DOWNLOAD_REQUIRED".equals(cause.getMessage()))
                return NebulaText.text("Скачайте модель Gemini Nano: Провайдер и модель → Скачать модель.", "Download Gemini Nano in Provider and model → Download model.");
            if ("GEMINI_NANO_DOWNLOADING".equals(cause.getMessage()))
                return NebulaText.text("Модель Gemini Nano загружается. Дождитесь завершения в настройках провайдера.", "Gemini Nano is downloading. Check its progress in provider settings.");
            if ("GEMINI_NANO_UNAVAILABLE".equals(cause.getMessage()))
                return NebulaText.text("Выбранная модель Gemini Nano пока недоступна в AICore. Проверьте модель и обновление сервиса в настройках провайдера.", "This Gemini Nano model is not available in AICore yet. Check the model and service update in provider settings.");
            // AICore's 606 is nested in ML Kit's service exception, not an SDK error code.
            String detail = cause.getMessage();
            if (detail != null && (detail.contains("FEATURE_NOT_FOUND") || detail.contains("606")))
                return NebulaText.text("AICore ещё не предоставил эту модель (606). Обновите AICore, оставьте устройство в сети и повторите проверку позже.", "AICore has not provided this model yet (606). Update AICore, keep the device online and check again later.");
            if (cause instanceof GenAiException) {
                switch (((GenAiException) cause).getErrorCode()) {
                    case GenAiException.ErrorCode.BUSY:
                        return NebulaText.text("AICore занят. Подождите немного и повторите попытку.", "AICore is busy. Wait a moment and retry.");
                    case GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED:
                        return NebulaText.text("Откройте NebulaGram на экране и повторите проверку: AICore не работает в фоне.", "Keep NebulaGram on screen and retry: AICore cannot run in the background.");
                    case GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE:
                        return NebulaText.text("Недостаточно памяти для модели. Освободите место на устройстве.", "Not enough storage for the model. Free up space on this device.");
                    case GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE:
                    case GenAiException.ErrorCode.AICORE_INCOMPATIBLE:
                        return NebulaText.text("Нужно обновить Android AICore или систему Google Play, затем повторить проверку.", "Update Android AICore or the Google Play system, then check again.");
                    case GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED:
                        return NebulaText.text("Достигнут временный лимит AICore. Повторите запрос позже.", "AICore's temporary usage limit was reached. Try again later.");
                    case GenAiException.ErrorCode.NOT_SUPPORTED:
                        return NebulaText.text("AICore не поддерживает выбранную модель в текущей конфигурации устройства. Попробуйте Stable · Полная и проверьте обновления сервиса.", "AICore does not support this model in the device's current configuration. Try Stable · Full and check service updates.");
                    case GenAiException.ErrorCode.NOT_AVAILABLE:
                        return NebulaText.text("AICore пока не готов предоставить модель. Оставьте устройство в сети и повторите проверку позже.", "AICore is not ready to provide the model. Keep the device online and check again later.");
                }
            }
        }
        return NebulaText.text("Не удалось проверить AICore. Обновите сервис и повторите попытку. Это не означает, что устройство не поддерживается.", "Could not check AICore. Update the service and retry. This does not mean the device is unsupported.");
    }

    /** A chat request needs response guidance, not the setup/check wording. */
    public static String responseErrorText(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.util.concurrent.TimeoutException)
                return NebulaText.text("Gemini Nano не ответил вовремя. Сократите запрос и повторите.",
                        "Gemini Nano did not respond in time. Shorten the request and try again.");
        }
        return errorText(error);
    }

    /** Starts AICore's one-time model download. Progress callbacks are provided by ML Kit. */
    public static void download(DownloadCallback callback) throws Exception {
        if (!supportedByOs()) throw new IllegalStateException("Gemini Nano requires Android 8.0 or newer");
        try (Session session = session()) { session.download(callback); }
    }

    public static String generate(String instructions, String input) throws Exception {
        return generate(instructions, input, () -> false);
    }

    public static String generate(String instructions, String input, java.util.function.BooleanSupplier cancelled) throws Exception {
        if (!supportedByOs()) throw new IllegalStateException("Gemini Nano is unavailable on this Android version");
        if (input == null || input.trim().isEmpty()) throw new IllegalArgumentException("Enter text");
        if (input.length() > 10000 || instructions != null && instructions.length() > 4000)
            throw new IllegalArgumentException("Gemini Nano supports shorter prompts on device");
        NebulaNanoInferenceGate.acquire(cancelled);
        try (Session session = session()) {
            int status = await(session.client.checkStatus(), 45, TimeUnit.SECONDS, cancelled);
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
            GenerateContentResponse response = await(session.client.generateContent(prompt.toString()), 3, TimeUnit.MINUTES, cancelled);
            if (response.getCandidates().isEmpty() || response.getCandidates().get(0).getText() == null) {
                throw new IllegalStateException("Gemini Nano returned no text");
            }
            return response.getCandidates().get(0).getText().trim();
        } finally {
            NebulaNanoInferenceGate.release();
        }
    }
}
