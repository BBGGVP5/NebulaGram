package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** Expanded details opened from the profile badge dialog. */
public final class NebulaBadgeInfoFragment extends BaseFragment {
    private final String kind;
    public NebulaBadgeInfoFragment(String kind) { this.kind = kind; }
    @Override public View createView(Context context) {
        int background = Theme.getColor(Theme.key_windowBackgroundGray, getResourceProvider());
        int cardColor = Theme.getColor(Theme.key_windowBackgroundWhite, getResourceProvider());
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, getResourceProvider());
        int secondaryColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, getResourceProvider());
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(NebulaBadges.title(kind));
        actionBar.setBackgroundColor(background); actionBar.setTitleColor(textColor); actionBar.setItemsColor(textColor, false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setBackgroundColor(background);
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content);
        int icon = NebulaBadges.iconResource(kind);
        TextView summary = new TextView(context);
        summary.setText(NebulaBadges.description(kind)); summary.setTextColor(secondaryColor); summary.setTextSize(14);
        summary.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), AndroidUtilities.dp(14));
        content.addView(summary);
        LinearLayout card = new LinearLayout(context); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        GradientDrawable cardBackground = new GradientDrawable(); cardBackground.setColor(cardColor);
        cardBackground.setCornerRadius(AndroidUtilities.dp(18)); card.setBackground(cardBackground);
        ImageView artwork = new ImageView(context);
        artwork.setImageResource(icon == 0 ? R.drawable.nebula_badge_star : icon);
        artwork.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        iconParams.rightMargin = AndroidUtilities.dp(14); card.addView(artwork, iconParams);
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context); title.setText(NebulaText.text("Значки NebulaGram", "NebulaGram badges"));
        title.setTextColor(textColor); title.setTextSize(16); labels.addView(title);
        TextView detail = new TextView(context);
        detail.setText(NebulaText.text("Рядом с именем в профиле", "Shown beside the profile name"));
        detail.setTextColor(secondaryColor); detail.setTextSize(13); labels.addView(detail);
        card.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(card);
        View layout = NebulaSettingsLayout.wrap(context, actionBar, scroll);
        layout.setBackgroundColor(background);
        return fragmentView = layout;
    }
}
