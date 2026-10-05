package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import java.io.*;
import java.util.Map;

/** Packs replace messenger drawables through Resources, independently of the launcher icon. */
public final class NebulaIconPacksFragment extends BaseFragment {
    private static final int IMPORT = 9781, EXPORT = 9782;
    private LinearLayout content; private String exporting; private boolean busy, destroyed;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Паки иконок", "Icon packs")); content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -10);
    }
    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(new NebulaSettingsHero(c, "🎨", text("Паки иконок", "Icon packs"), text("Значки во всём мессенджере: чаты, меню, настройки и вложения.", "Icons throughout the messenger: chats, menus, settings and attachments.")));
        NebulaCard base = new NebulaCard(c); String[] names = {text("Telegram", "Telegram"), "iOS Outline", "Solar Icon Set"};
        for (int i = 0; i < names.length; i++) {
            final int pack = i; base.add(new NebulaRow(c).title(names[i]).radio(NebulaIcons.pack() == i).withClick(v -> { if (busy) return; NebulaIcons.setPack(pack); Theme.reloadAllResources(c); rebuild(); }));
        }
        base.add(new NebulaRow(c).title("Remix Outline").subtitle(text("Единый контурный набор · встроен", "Consistent outlines · bundled"), false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> worker(() -> {
            try (InputStream in = c.getAssets().open("nebulagram/remix-outline.icons")) { return NebulaIconPackStore.install(in); }
        }, pack -> { rebuild(); preview(pack); })));
        NebulaFormUi.group(content, text("Встроенные наборы", "Bundled packs"), base);
        NebulaCard imported = new NebulaCard(c);
        for (NebulaIconPackStore.Pack pack : NebulaIconPackStore.list()) imported.add(new NebulaRow(c).title(pack.name)
                .subtitle(pack.author + (pack.version.isEmpty() ? "" : " · " + pack.version), false)
                .radio(NebulaIcons.pack() == 3 && pack.id.equals(NebulaIconPackStore.selected())).withClick(v -> preview(pack)));
        imported.add(new NebulaRow(c).icon(R.drawable.msg_add).title(text("Импортировать .icons", "Import .icons")).withClick(v -> {
            if (busy) return;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(intent, IMPORT); } catch (Exception e) { notice(text("Не удалось открыть выбор файла.", "Could not open the file picker.")); }
        }));
        NebulaFormUi.group(content, text("Мои наборы", "My packs"), imported);
        content.addView(NebulaFormUi.note(c, text("Формат exteraGram .icons: metadata.json и SVG, PNG или WebP. Значки, которых нет в паке, сохраняют стандартный вид. После выбора откройте нужный экран заново.", "exteraGram .icons format: metadata.json and SVG, PNG or WebP. Unmapped icons keep their native appearance. Reopen the affected screen after selecting a pack.")));
    }
    private void preview(NebulaIconPackStore.Pack pack) {
        worker(() -> NebulaIconPackStore.preview(pack.id), images -> {
            Context c = content.getContext(); LinearLayout view = new LinearLayout(c); view.setPadding(NebulaFormUi.dp(12), NebulaFormUi.dp(16), NebulaFormUi.dp(12), NebulaFormUi.dp(16));
            String[] samples = {"msg_search", "msg_saved", "msg_calls", "msg_delete", "msg_settings"};
            int[] nativeIds = {R.drawable.msg_search, R.drawable.msg_saved, R.drawable.msg_calls, R.drawable.msg_delete, R.drawable.msg_settings};
            for (int i = 0; i < samples.length; i++) {
                ImageView icon = new ImageView(c); Bitmap bitmap = images.get(samples[i]);
                if (bitmap == null) icon.setImageDrawable(NebulaIconResources.originalDrawable(c.getResources(), nativeIds[i])); else icon.setImageBitmap(bitmap);
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setColorFilter(NebulaTheme.of(c).onSurface(), PorterDuff.Mode.SRC_IN);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, NebulaFormUi.dp(32), 1); params.leftMargin = params.rightMargin = NebulaFormUi.dp(8); view.addView(icon, params);
            }
            showDialog(new NebulaDialog.Builder(c).setTitle(pack.name).setView(view)
                    .setPositiveButton(text("Применить", "Use"), (d, w) -> worker(() -> { NebulaIconPackStore.select(pack.id); return true; }, done -> { Theme.reloadAllResources(c); rebuild(); }))
                    .setNeutralButton(text("Экспорт", "Export"), (d, w) -> {
                        exporting = pack.id; Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, pack.id + ".icons");
                        try { startActivityForResult(intent, EXPORT); } catch (Exception e) { notice(text("Не удалось открыть экспорт.", "Could not open export.")); }
                    }).setNegativeButton(text("Удалить", "Delete"), (d, w) -> worker(() -> { NebulaIconPackStore.delete(pack.id); return true; }, done -> { Theme.reloadAllResources(c); rebuild(); })).create());
        });
    }
    private interface Work<T> { T run() throws Exception; }
    private <T> void worker(Work<T> work, java.util.function.Consumer<T> done) {
        if (busy || destroyed) return; busy = true;
        new Thread(() -> { try { T result = work.run(); AndroidUtilities.runOnUIThread(() -> { busy = false; if (!destroyed) done.accept(result); }); }
            catch (Exception e) { AndroidUtilities.runOnUIThread(() -> { busy = false; if (!destroyed) notice(text("Не удалось прочитать пак. Проверьте metadata.json, формат и размер значков.", "Could not read this pack. Check metadata.json, icon formats and sizes.")); }); } }, "NebulaIconImport").start();
    }
    private void notice(String value) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), value, Toast.LENGTH_LONG).show(); }
    @Override public void onActivityResultFragment(int request, int result, Intent data) {
        if (request != IMPORT && request != EXPORT) { super.onActivityResultFragment(request, result, data); return; }
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); Context c = content.getContext(); String id = exporting;
        if (request == IMPORT) worker(() -> { try (InputStream in = c.getContentResolver().openInputStream(uri)) { if (in == null) throw new IOException(); return NebulaIconPackStore.install(in); } }, pack -> { rebuild(); preview(pack); });
        else worker(() -> { try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) { if (out == null) throw new IOException(); NebulaIconPackStore.export(id, out); return true; } }, done -> notice(text("Пак экспортирован.", "Pack exported.")));
    }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }
}
