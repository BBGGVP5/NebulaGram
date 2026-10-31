package app.nebulagram.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import static app.nebulagram.ui.NebulaText.text;

public final class NebulaFontFragment extends BaseFragment {
    private static final int IMPORT = 0x4e46;
    private LinearLayout content;
    private boolean busy, destroyed;
    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, text("Шрифт приложения", "App font"));
        content = NebulaFormUi.column(context);
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.addView(content);
        fragmentView = scroll; rebuild(); return scroll;
    }
    private void rebuild() {
        if (content == null || destroyed) return;
        Context c = content.getContext(); content.removeAllViews();
        NebulaCard card = new NebulaCard(c);
        String[] titles = {"Telegram", text("Системный", "System"), text("Свой шрифт", "Custom font")};
        int current = NebulaFont.customEnabled() ? 2 : NebulaFont.system() ? 1 : 0;
        for (int mode = 0; mode < titles.length; mode++) {
            final int choice = mode;
            NebulaRow row = new NebulaRow(c).title(titles[mode]).trailing(NebulaRow.TRAIL_SWITCH).checked(current == mode);
            row.setEnabled(!busy && (mode != 2 || NebulaFont.hasCustom()));
            row.withClick(v -> { if (!busy) { NebulaFont.choose(choice); refresh(); } }); card.add(row);
        }
        card.add(new NebulaRow(c).title(busy ? text("Загрузка…", "Importing…") : text("Добавить TTF / OTF", "Import TTF / OTF"))
            .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                if (busy) return;
                try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), IMPORT); }
                catch (RuntimeException e) { notice(text("Не удалось открыть файлы", "Could not open documents")); }
            }));
        content.addView(card);
        TextView preview = new TextView(c); preview.setText("Аа Бб Вв · Aa Bb Cc\nСъешь ещё этих мягких французских булок\nThe quick brown fox jumps over the lazy dog\n0123456789");
        preview.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)); preview.setTextSize(20);
        preview.setTypeface(NebulaFont.substitute("fonts/rregular.ttf"));
        preview.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24)); content.addView(preview);
        content.addView(NebulaFormUi.note(c, text("TTF или OTF до 8 МБ. Код остаётся моноширинным. Некоторые уже открытые экраны обновятся после повторного открытия.", "TTF or OTF up to 8 MB. Code stays monospace. Some open screens update when reopened.")));
    }
    private void refresh() { AndroidUtilities.nebulaClearTypefaces(); Theme.reloadAllResources(content.getContext()); rebuild(); }
    private void notice(String value) { if (!destroyed && getParentActivity() != null) Toast.makeText(getParentActivity(), value, Toast.LENGTH_LONG).show(); }
    @Override public void onActivityResultFragment(int request, int result, Intent data) {
        if (request != IMPORT) { super.onActivityResultFragment(request, result, data); return; }
        if (result != Activity.RESULT_OK || data == null || data.getData() == null || busy || destroyed) return;
        android.net.Uri uri = data.getData(); busy = true; rebuild();
        new Thread(() -> {
            try (java.io.InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                NebulaFont.install(input);
                AndroidUtilities.runOnUIThread(() -> { busy = false; if (!destroyed) { NebulaFont.choose(2); refresh(); } });
            } catch (Exception error) {
                AndroidUtilities.runOnUIThread(() -> { busy = false; if (!destroyed) { rebuild(); notice(text("Не удалось загрузить шрифт. Нужен исправный TTF/OTF до 8 МБ.", "Import failed. Choose a valid TTF/OTF up to 8 MB.")); } });
            }
        }, "NebulaFontImport").start();
    }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }
}
