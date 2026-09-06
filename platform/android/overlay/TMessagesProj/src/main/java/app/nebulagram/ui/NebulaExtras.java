package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.widget.LinearLayout;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;

/** Additional controls shared by the settings sections. */
public final class NebulaExtras {
    private NebulaExtras() { }
    public interface Changed { void set(boolean value); }
    public static NebulaRow toggle(Context c, int icon, String title, String info, boolean checked, Changed change) {
        NebulaRow row = new NebulaRow(c).icon(icon).title(title).subtitle(info, false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(checked);
        row.setOnClickListener(v -> change.set(row.toggleChecked()));
        return row;
    }
    public static void appearance(BaseFragment f, LinearLayout content) {
        Context c = content.getContext();
        NebulaCard card = new NebulaCard(c);
        card.add(new NebulaRow(c).icon(R.drawable.nebula_cupertino_photo).title(text("Наборы иконок", "Icon packs"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> f.presentFragment(new NebulaDesignFragment(false))));
        card.add(new NebulaRow(c).icon(R.drawable.nebula_cupertino_person).title(text("Закругление аватарок", "Avatar corners"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> f.presentFragment(new NebulaDesignFragment(true))));
        card.add(toggle(c, R.drawable.nebula_cupertino_list, text("Центрировать заголовки", "Center titles"),
                text("В настройках и развёрнутой шапке главной", "In settings and the expanded home header"), NebulaAppearance.centerHome(), NebulaAppearance::setCenterHome));
        card.add(toggle(c, R.drawable.nebula_cupertino_sliders, text("Анимации Liquid Glass", "Liquid Glass animations"),
                text("Меню, шапка чата и пузырь нижней панели", "Menus, chat header and bottom bar bubble"), NebulaAppearance.liquidAnimations(), NebulaAppearance::setLiquidAnimations));
        content.addView(card);
    }
    public static void messages(BaseFragment f, LinearLayout content) {
        Context c = content.getContext();
        String[] labels = {text("Реакция", "Reaction"), text("Редактировать", "Edit"), text("Ответить", "Reply"), text("Копировать текст", "Copy text"), text("Ничего", "Nothing")};
        NebulaRow row = new NebulaRow(c).icon(R.drawable.nebula_cupertino_edit).title(text("Двойной тап по своему сообщению", "Double tap your message"))
                .subtitle(labels[NebulaAppearance.ownDoubleTap()], false).trailing(NebulaRow.TRAIL_CHEVRON);
        row.setOnClickListener(v -> new AlertDialog.Builder(c).setTitle(text("Действие двойного нажатия", "Double tap action"))
                .setItems(labels, (d, i) -> { NebulaAppearance.setOwnDoubleTap(i); row.subtitle(labels[i], false); }).show());
        NebulaCard card = new NebulaCard(c); card.add(row); content.addView(card);
    }
}
