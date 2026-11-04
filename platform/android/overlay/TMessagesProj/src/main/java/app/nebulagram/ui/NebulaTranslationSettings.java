package app.nebulagram.ui;

import android.content.SharedPreferences;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.TranslateController;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Translation consent is scoped to the signed-in account and chat, never imported. */
public final class NebulaTranslationSettings {
    public static SharedPreferences prefs(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_translate_" + NebulaTasks.user(account), 0);
    }
    public static SharedPreferences global() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings", 0);
    }
    public static String connectionIdentity() {
        SharedPreferences p = global(); int provider = p.getInt("provider", 0);
        if (provider == NebulaAiClient.NANO) return provider + ":" + p.getBoolean("nano_preview", false) + ":" + p.getBoolean("nano_fast", false);
        return provider + ":" + p.getString("model_" + provider, "") + ":" + p.getString("endpoint", "")
                + ":" + p.getString("selected_service", "") + ":" + p.getInt("connection_revision", 0);
    }
    public static boolean local() { return global().getBoolean("live_translation_local", false); }
    /** A keyless web translator, or "" when on-device or AI translation is selected. */
    public static String web() {
        String value = local() ? "" : global().getString("live_translation_web", "");
        return NebulaWebTranslation.known(value) ? value : "";
    }
    public static boolean webEngine() { return !web().isEmpty(); }
    public static String webName() { return webEngine() ? NebulaWebTranslation.name(web()) : null; }
    /** 0 on device, 1 AI provider, then one per web translator in NebulaWebTranslation.PROVIDERS. */
    public static int engine() {
        if (local()) return 0;
        String value = web();
        for (int i = 0; i < NebulaWebTranslation.PROVIDERS.length; i++) if (NebulaWebTranslation.PROVIDERS[i].equals(value)) return 2 + i;
        return 1;
    }
    public static void setEngine(int engine) {
        global().edit().putBoolean("live_translation_local", engine == 0)
            .putString("live_translation_web", engine >= 2 ? NebulaWebTranslation.PROVIDERS[engine - 2] : "").apply();
    }
    public static String translationIdentity() { return local() ? "local:mlkit" : webEngine() ? "web:" + web() : connectionIdentity(); }
    public static boolean translationAvailable() { return local() || webEngine() || NebulaAiAvailability.available(); }
    public static boolean automatic(int a, long d) { return prefs(a).getBoolean("draft_automatic_" + d, true); }
    public static boolean shortcut() { return global().getBoolean("composer_shortcut", false); }
    public static boolean outgoing(int a, long d) { return prefs(a).getBoolean("outgoing_" + d, false); }
    public static boolean draft(int a, long d) { return prefs(a).getBoolean("draft_" + d, false); }
    public static String draftLanguage(int a, long d) { return prefs(a).getString("draft_language_" + d, "en"); }
    public static int delay(int a, long d) { return Math.max(150, Math.min(2000, prefs(a).getInt("delay_" + d, 150))); }
    public static String label(String code) {
        String name = org.telegram.ui.Components.TranslateAlert2.languageName(code);
        return name == null ? code : org.telegram.ui.Components.TranslateAlert2.capitalFirst(name);
    }
    public static void choose(BaseFragment host, String selected, Consumer<String> completion) {
        ArrayList<TranslateController.Language> all = TranslateController.getLanguages(), sorted = new ArrayList<>();
        all = new ArrayList<>(all);
        if (local()) all.removeIf(language -> com.google.mlkit.nl.translate.TranslateLanguage.fromLanguageTag(language.code) == null);
        for (String code : new String[]{"ru", "en"}) for (TranslateController.Language l : all) if (code.equals(l.code)) sorted.add(l);
        int quick = sorted.size();
        for (TranslateController.Language l : all) if (!"ru".equals(l.code) && !"en".equals(l.code)) sorted.add(l);
        CharSequence[] names = new CharSequence[sorted.size()], subtitles = new CharSequence[sorted.size()]; int index = -1;
        for (int i = 0; i < sorted.size(); i++) {
            TranslateController.Language l = sorted.get(i); names[i] = l.displayName; subtitles[i] = l.ownDisplayName;
            if (l.code.equals(selected)) index = i;
        }
        host.showDialog(new NebulaDialog.Builder(host.getContext(), host.getResourceProvider())
                .setTitle(NebulaText.text("Язык перевода", "Translation language")).setSelectedIndex(index)
                .setSection(quick, NebulaText.text("Другие языки", "Other languages")).setDescriptions(subtitles)
                .setItems(names, (d, i) -> completion.accept(sorted.get(i).code))
                .setNegativeButton(NebulaText.text("Отмена", "Cancel"), null).create());
    }
}
