package app.nebulagram.ui;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import java.io.InterruptedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Language-pack translation. Only model preparation uses the network. */
public final class NebulaLocalTranslation {
    private static final LinkedHashMap<String, Session> sessions = new LinkedHashMap<>(4, .75f, true);
    private static final java.util.concurrent.ScheduledExecutorService cleanup =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nebula-translation-idle"); t.setDaemon(true); return t;
        });
    private static java.util.concurrent.ScheduledFuture<?> idleSweep;
    private NebulaLocalTranslation() { }
    private static final class Session {
        final Translator client;
        final Task<Void> prepared;
        int leases;
        long lastUse;
        Session(String from, String to) {
            client = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build());
            try { prepared = client.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build()); }
            catch (RuntimeException error) { client.close(); throw error; }
        }
    }
    private static synchronized Session acquire(String from, String to) {
        String key = from + ":" + to;
        Session session = sessions.get(key);
        if (session == null) { session = new Session(from, to); sessions.put(key, session); }
        session.leases++; session.lastUse = System.nanoTime();
        return session;
    }
    private static synchronized void release(Session session) {
        session.leases--; session.lastUse = System.nanoTime();
        if (session.leases == 0 && session.prepared.isComplete() && !session.prepared.isSuccessful()) {
            sessions.values().remove(session); session.client.close(); return;
        }
        closeIdle();
        scheduleSweep();
    }
    private static synchronized void scheduleSweep() {
        if (idleSweep != null || sessions.isEmpty()) return;
        idleSweep = cleanup.schedule(() -> {
            synchronized (NebulaLocalTranslation.class) {
                idleSweep = null;
                closeIdle();
                if (!sessions.isEmpty()) scheduleSweep();
            }
        }, 60, TimeUnit.SECONDS);
    }
    private static synchronized void closeIdle() {
        java.util.Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (session.leases == 0 && session.prepared.isComplete() &&
                (sessions.size() > 4 || System.nanoTime() - session.lastUse >= TimeUnit.SECONDS.toNanos(59))) {
                iterator.remove(); session.client.close();
            }
        }
    }
    private static <T> T await(Task<T> task, BooleanSupplier cancelled, int seconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (!cancelled.getAsBoolean()) {
            try { return Tasks.await(task, 50, TimeUnit.MILLISECONDS); }
            catch (TimeoutException waiting) { if (System.nanoTime() >= deadline) throw waiting; }
        }
        throw new InterruptedIOException();
    }
    public static String translate(String text, String language, BooleanSupplier cancelled, Consumer<String> progress) throws Exception {
        String target = TranslateLanguage.fromLanguageTag(language);
        if (target == null) throw new IllegalArgumentException("LOCAL_LANGUAGE_UNSUPPORTED");
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        String source;
        LanguageIdentifier identifier = LanguageIdentification.getClient();
        Task<String> identification = null;
        try {
            identification = identifier.identifyLanguage(text);
            source = TranslateLanguage.fromLanguageTag(await(identification, cancelled, 10));
        } finally {
            if (identification != null && !identification.isComplete())
                identification.addOnCompleteListener(cleanup, task -> identifier.close());
            else identifier.close();
        }
        if (source == null) {
            boolean letters = false;
            for (int i = 0; i < text.length(); i++) {
                if (Character.isLetter(text.charAt(i))) { letters = true; break; }
            }
            if (!letters) return text;
            // An alphabet is not a language: Latin is not necessarily English,
            // and Cyrillic is also used by Ukrainian, Bulgarian and others.
            throw new IllegalArgumentException("LOCAL_LANGUAGE_UNKNOWN");
        }
        if (source.equals(target)) return text;
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        Session session = acquire(source, target);
        Task<String> translation = null;
        try {
            if (!session.prepared.isComplete()) progress.accept(NebulaText.text("Подготовка языкового пакета · нужен Wi-Fi", "Preparing language pack · Wi-Fi required"));
            try { await(session.prepared, cancelled, 120); }
            catch (InterruptedIOException error) { throw error; }
            catch (Exception error) { throw new Exception("LOCAL_MODEL_UNAVAILABLE", error); }
            progress.accept(NebulaText.text("Перевод на устройстве…", "Translating on device…"));
            translation = session.client.translate(text);
            return await(translation, cancelled, 30);
        } finally {
            // Tasks.await cancellation does not cancel ML Kit. Keep the lease
            // until inference finishes, including when another caller shares it.
            if (translation != null && !translation.isComplete())
                translation.addOnCompleteListener(cleanup, task -> release(session));
            else release(session);
        }
    }
    public static String errorText(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ("LOCAL_LANGUAGE_UNSUPPORTED".equals(cause.getMessage()))
                return NebulaText.text("Этот язык недоступен в быстром режиме. Выберите перевод через ИИ.", "This language is unavailable in fast mode. Select AI translation.");
            if ("LOCAL_LANGUAGE_UNKNOWN".equals(cause.getMessage()))
                return NebulaText.text("Не удалось определить язык текста. Попробуйте более длинную фразу или перевод через ИИ.", "Could not identify the language. Try a longer phrase or AI translation.");
            if ("LOCAL_MODEL_UNAVAILABLE".equals(cause.getMessage()))
                return NebulaText.text("Языковой пакет не готов. Загрузите языки по Wi-Fi в настройках перевода.", "Language pack is not ready. Download languages over Wi-Fi in translation settings.");
        }
        return NebulaText.text("Не удалось перевести на устройстве. Повторите запрос. Текст не отправлялся в облако.", "On-device translation failed. Retry the request. Text was not sent to the cloud.");
    }
}
