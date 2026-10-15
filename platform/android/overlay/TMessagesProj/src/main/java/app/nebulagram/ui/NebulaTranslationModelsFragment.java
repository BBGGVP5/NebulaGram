package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.telegram.ui.ActionBar.BaseFragment;

/** Explicit preparation only: opening this screen never downloads a model. */
public final class NebulaTranslationModelsFragment extends BaseFragment {
    private final Map<String, NebulaRow> rows = new HashMap<>();
    private final Set<String> downloaded = new HashSet<>(), pending = new HashSet<>(), failed = new HashSet<>();
    private boolean destroyed, loaded;
    private String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        destroyed = false;
        NebulaFormUi.bar(this, actionBar, context, t("Языки на устройстве", "On-device languages"));
        LinearLayout column = NebulaFormUi.column(context);
        column.addView(NebulaFormUi.note(context, t(
            "Загрузите языки заранее по Wi-Fi, чтобы переводить без интернета. Нужны языки оригинала и перевода; английский уже встроен. Текст остаётся на устройстве; Gemini Nano и Premium не требуются.",
            "Download languages over Wi-Fi before going offline. Both source and target languages are needed; English is built in. Text stays on device; Gemini Nano and Premium are not required.")));
        NebulaCard card = new NebulaCard(context);
        ArrayList<String> languages = new ArrayList<>(TranslateLanguage.getAllLanguages());
        Collator collator = Collator.getInstance();
        languages.sort((a, b) -> a.equals(b) ? 0 : "en".equals(a) ? -1 : "en".equals(b) ? 1
            : collator.compare(NebulaTranslationSettings.label(a), NebulaTranslationSettings.label(b)));
        rows.clear();
        downloaded.add("en");
        for (String language : languages) {
            NebulaRow row = new NebulaRow(context).title(NebulaTranslationSettings.label(language));
            row.withClick(v -> { if (loaded && !downloaded.contains(language) && !pending.contains(language)) download(language); });
            rows.put(language, row); card.add(row);
        }
        column.addView(card);
        refresh();
        WeakReference<NebulaTranslationModelsFragment> reference = new WeakReference<>(this);
        RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel.class).addOnCompleteListener(task -> {
            NebulaTranslationModelsFragment screen = reference.get();
            if (screen == null || screen.destroyed) return;
            screen.loaded = true;
            if (task.isSuccessful()) {
                for (TranslateRemoteModel model : task.getResult()) screen.downloaded.add(model.getLanguage());
            } else screen.failed.addAll(screen.rows.keySet());
            screen.refresh();
        });
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, NebulaFormUi.scroll(context, column));
    }

    private void download(String language) {
        if ("en".equals(language)) return; // ML Kit's English model is built in, not a remote model.
        pending.add(language); failed.remove(language); refresh();
        WeakReference<NebulaTranslationModelsFragment> reference = new WeakReference<>(this);
        RemoteModelManager.getInstance().download(new TranslateRemoteModel.Builder(language).build(),
            new DownloadConditions.Builder().requireWifi().build()).addOnCompleteListener(task -> {
                NebulaTranslationModelsFragment screen = reference.get();
                if (screen == null || screen.destroyed) return;
                screen.pending.remove(language);
                if (task.isSuccessful()) screen.downloaded.add(language);
                else screen.failed.add(language);
                screen.refresh();
            });
    }

    private void refresh() {
        for (Map.Entry<String, NebulaRow> entry : rows.entrySet()) {
            String language = entry.getKey();
            boolean ready = downloaded.contains(language), loading = pending.contains(language);
            entry.getValue().subtitle(!loaded ? t("Проверяем…", "Checking…")
                : ready ? t("Готов к работе без интернета", "Ready offline")
                : loading ? t("Загрузка · нужен Wi-Fi", "Downloading · Wi-Fi required")
                : failed.contains(language) ? t("Не удалось загрузить · нажмите для повтора", "Could not load · tap to retry")
                : t("Нажмите, чтобы загрузить по Wi-Fi", "Tap to download over Wi-Fi"), ready);
        }
    }

    @Override public void onFragmentDestroy() {
        destroyed = true; rows.clear(); super.onFragmentDestroy();
    }
}
