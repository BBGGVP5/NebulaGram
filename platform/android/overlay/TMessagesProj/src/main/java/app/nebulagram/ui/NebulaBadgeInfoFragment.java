package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/** Expanded details opened from the profile badge dialog. */
public final class NebulaBadgeInfoFragment extends BaseFragment {
    private final String kind;
    public NebulaBadgeInfoFragment(String kind) { this.kind = kind; }
    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(NebulaBadges.title(kind));
        actionBar.setBackgroundColor(theme.surface()); actionBar.setTitleColor(theme.onSurface()); actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setBackgroundColor(theme.surface());
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content);
        int icon = NebulaBadges.iconResource(kind);
        content.addView(new NebulaSettingsHero(context, icon == 0 ? R.drawable.nebula_badge_star : icon,
                NebulaBadges.title(kind), NebulaBadges.description(kind)));
        NebulaCard card = new NebulaCard(context);
        card.add(new NebulaRow(context).artwork(icon == 0 ? R.drawable.nebula_badge_star : icon)
                .title(NebulaText.text("Значки NebulaGram", "NebulaGram badges"))
                .subtitle(NebulaText.text("Значок связан с сообществом NebulaGram и отображается рядом с именем владельца профиля.", "This community badge appears beside its owner's profile name."), false));
        content.addView(card);
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }
}
