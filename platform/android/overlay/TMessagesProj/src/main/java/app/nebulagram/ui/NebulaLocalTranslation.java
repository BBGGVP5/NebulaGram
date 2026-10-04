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
    private NebulaLocalTranslation() { }
    private static final class Session {
        final Translator client;
        final Task<Void> prepared;
        int leases;
        long lastUse;
        Session(String from, String to) {
            client = Translation.getClient(new TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build());
            prepared = client.downloadModelIfNeeded(new DownloadConditions.Builder().requireWifi().build());
            prepared.addOnCompleteListener(cleanup, task -> cleanup.schedule(NebulaLocalTranslation::closeIdle, 60, TimeUnit.SECONDS));
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
        cleanup.schedule(NebulaLocalTranslation::closeIdle, 60, TimeUnit.SECONDS);
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
        String source;
        try (LanguageIdentifier identifier = LanguageIdentification.getClient()) {
            source = TranslateLanguage.fromLanguageTag(await(identifier.identifyLanguage(text), cancelled, 10));
        }
        if (source == null) {
            boolean latin = false, cyrillic = false, letters = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i); if (!Character.isLetter(c)) continue; letters = true;
                if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) cyrillic = true;
                else if (c < 128) latin = true;
            }
            if (!letters) return text;
            if (cyrillic && !latin) source = "ru";
            else if (latin && !cyrillic) source = "en";
            else throw new IllegalArgumentException("LOCAL_LANGUAGE_UNKNOWN");
        }
        if (source.equals(target)) return text;
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        Session session = acquire(source, target);
        try {
            if (!session.prepared.isComplete()) progress.accept(NebulaText.text("Подготовка языкового пакета · нужен Wi-Fi", "Preparing language pack · Wi-Fi required"));
            await(session.prepared, cancelled, 120);
            progress.accept(NebulaText.text("Перевод на устройстве…", "Translating on device…"));
            return await(session.client.translate(text), cancelled, 30);
        } finally { release(session); }
    }
    public static String errorText(Exception error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if ("LOCAL_LANGUAGE_UNSUPPORTED".equals(cause.getMessage()))
                return NebulaText.text("Этот язык недоступен в быстром режиме. Выберите перевод через ИИ.", "This language is unavailable in fast mode. Select AI translation.");
            if ("LOCAL_LANGUAGE_UNKNOWN".equals(cause.getMessage()))
                return NebulaText.text("Не удалось определить язык текста. Попробуйте более длинную фразу или перевод через ИИ.", "Could not identify the language. Try a longer phrase or AI translation.");
        }
        return NebulaText.text("Языковой пакет не готов. Подключитесь к Wi-Fi и повторите перевод.", "Language pack is not ready. Connect to Wi-Fi and retry translation.");
    }
}
