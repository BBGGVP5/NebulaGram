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
        return provider + ":" + p.getString("model_" + provider, "") + ":" + p.getString("endpoint", "");
    }
    public static boolean shortcut() { return global().getBoolean("composer_shortcut", false); }
    public static boolean draft(int a, long d) { return prefs(a).getBoolean("draft_" + d, false); }
    public static String draftLanguage(int a, long d) { return prefs(a).getString("draft_language_" + d, "en"); }
    public static int delay(int a, long d) { return Math.max(500, Math.min(2000, prefs(a).getInt("delay_" + d, 1000))); }
    public static String label(String code) {
        String name = org.telegram.ui.Components.TranslateAlert2.languageName(code);
        return name == null ? code : org.telegram.ui.Components.TranslateAlert2.capitalFirst(name);
    }
    public static void choose(BaseFragment host, String selected, Consumer<String> completion) {
        ArrayList<TranslateController.Language> all = TranslateController.getLanguages(), sorted = new ArrayList<>();
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
