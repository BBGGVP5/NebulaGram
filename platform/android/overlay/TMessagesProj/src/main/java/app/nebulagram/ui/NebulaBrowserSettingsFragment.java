package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaBrowserSettingsFragment extends BaseFragment {
    private LinearLayout content; private boolean destroyed;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Браузер и реклама", "Browser and ads")); NebulaBrowserAdBlock.prepare(); content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
    @Override public void onResume() { super.onResume(); if (content != null) rebuild(); }
    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(new NebulaSettingsHero(c, "🛡️", text("Блокировка рекламы", "Ad blocking"), text("Меньше рекламных запросов и баннеров во встроенном браузере.", "Fewer ad requests and banners in the built-in browser.")));
        NebulaCard card = new NebulaCard(c);
        card.add(new NebulaRow(c).title(text("Блокировать рекламу", "Block ads")).subtitle(text("Сетевые правила и скрытие рекламных элементов", "Network rules and hiding ad elements"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaBrowserAdBlock.enabled()).withClick(v -> NebulaBrowserAdBlock.prefs().edit().putBoolean("adblock", ((NebulaRow) v).toggleChecked()).apply()));
        card.add(new NebulaRow(c).title(text("Исключения сайтов", "Site exceptions")).subtitle(text("Домены, на которых блокировка выключена", "Domains where blocking is disabled"), false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
            EditText field = NebulaFormUi.field(c, "example.org", 4, 10000); field.setText(NebulaBrowserAdBlock.prefs().getString("exclusions", ""));
            showDialog(new NebulaDialog.Builder(c).setTitle(text("Исключения сайтов", "Site exceptions")).setMessage(text("По одному домену в строке, без https:// и пути.", "One domain per line, without https:// or a path.")).setView(field)
                    .setPositiveButton(text("Сохранить", "Save"), (d, w) -> NebulaBrowserAdBlock.exclusions(field.getText().toString())).create());
        }));
        card.add(new NebulaRow(c).title(text("Обновить фильтры EasyList", "Update EasyList filters")).subtitle(NebulaBrowserAdBlock.refreshing() ? text("Загрузка…", "Downloading…") : text("Загрузить сетевые и косметические правила", "Download network and cosmetic rules"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                    if (NebulaBrowserAdBlock.refreshing()) return;
                    NebulaBrowserAdBlock.refresh(success -> { if (destroyed) return; rebuild(); Toast.makeText(c, success ? text("Фильтры обновлены.", "Filters updated.") : text("Не удалось обновить. Прежние фильтры сохранены.", "Update failed. Previous filters are kept."), Toast.LENGTH_LONG).show(); }); rebuild();
                }));
        content.addView(card);
        long updated = NebulaBrowserAdBlock.prefs().getLong("updated", 0);
        content.addView(NebulaFormUi.note(c, text("Поддерживаемых правил: ", "Supported rules: ") + NebulaBrowserAdBlock.ruleCount() + "\n" + text("Заблокировано в этой сессии: ", "Blocked this session: ") + NebulaBrowserAdBlock.blockedCount()
                + (updated == 0 ? "" : "\n" + text("Обновлено: ", "Updated: ") + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(updated)))));
        content.addView(NebulaFormUi.note(c, text("Встроенный базовый список работает без загрузки. EasyList расширяет его поддерживаемыми правилами. Перезагрузите открытую страницу после изменения настроек. Mini Apps не затрагиваются.", "The basic bundled list works without downloading. EasyList adds supported rules. Reload an open page after changing settings. Mini Apps are unaffected.")));
    }
    @Override public void onFragmentDestroy() { destroyed = true; super.onFragmentDestroy(); }
}
