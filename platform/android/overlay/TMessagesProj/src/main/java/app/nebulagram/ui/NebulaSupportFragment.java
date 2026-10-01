package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/** Project support is separate from Telegram purchases and NebulaLink subscriptions. */
public final class NebulaSupportFragment extends BaseFragment {
    private LinearLayout content;
    private ScrollView scroll;
    private int surface, accent;

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(NebulaText.text("Поддержать NebulaGram", "Support NebulaGram"));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }

    private void rebuild() {
        Context context = content.getContext();
        NebulaTheme theme = NebulaTheme.of(context);
        surface = theme.surface(); accent = theme.primary();
        scroll.setBackgroundColor(surface);
        actionBar.setBackgroundColor(surface);
        actionBar.setTitleColor(theme.onSurface()); actionBar.setItemsColor(theme.onSurface(), false);
        content.removeAllViews();
        ImageView logo = new ImageView(context);
        logo.setImageResource(R.drawable.nebula_badge_supporter);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(80));
        art.gravity = Gravity.CENTER_HORIZONTAL; art.bottomMargin = AndroidUtilities.dp(14);
        content.addView(logo, art);
        TextView introduction = new TextView(context);
        introduction.setText(NebulaText.text("Помогите развитию NebulaGram", "Help NebulaGram grow"));
        introduction.setTextSize(22); introduction.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        introduction.setTextColor(theme.onSurface()); introduction.setGravity(Gravity.CENTER);
        content.addView(introduction, new LinearLayout.LayoutParams(-1, -2));

        header(NebulaText.text("Поддержка за донат", "Donation support"));
        NebulaCard donation = new NebulaCard(context);
        donation.add(new NebulaRow(context).artwork(R.drawable.nebula_badge_supporter)
                .title(NebulaText.text("Значок поддержки", "Supporter badge"))
                .subtitle(NebulaText.text("После подтверждения доната команда выдаст значок. Он появится после премиум-эмодзи рядом с именем и будет виден другим пользователям NebulaGram.",
                        "After confirming your donation, the team grants a badge. It appears after your premium emoji beside your name and is visible to other NebulaGram users."), false));
        String link = NebulaDonation.link();
        donation.add(new NebulaRow(context).icon(R.drawable.nebula_settings_support)
                .title(link.isEmpty() ? NebulaText.text("Узнать варианты поддержки", "Ask about support options")
                        : NebulaText.text("Поддержать проект", "Donate to the project"))
                .subtitle(link.isEmpty() ? NebulaText.text("Официальный канал NebulaGram", "Official NebulaGram channel")
                        : NebulaText.text("Открыть страницу поддержки", "Open the support page"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> Browser.openUrl(context, link.isEmpty() ? "https://t.me/ngram_official" : link)));
        long id = UserConfig.getInstance(currentAccount).getClientUserId();
        donation.add(new NebulaRow(context).icon(R.drawable.msg_openprofile)
                .title(NebulaText.text("Мой Telegram ID", "My Telegram ID"))
                .subtitle(Long.toString(id), true)
                .withClick(v -> AndroidUtilities.addToClipboard(Long.toString(id))));
        content.addView(donation, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context, NebulaText.text(
                "Для выдачи значка передайте команде подтверждение платежа и ваш Telegram ID.",
                "Send the team your payment confirmation and Telegram ID to receive a badge.")));

        header(NebulaText.text("Поддержка без доната", "Support without donating"));
        NebulaCard community = new NebulaCard(context);
        community.add(new NebulaCommunityCard(context, this));
        content.addView(community, cardParams());
    }

    private void header(String title) { content.addView(NebulaCard.header(content.getContext(), title)); }
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = AndroidUtilities.dp(6); p.bottomMargin = AndroidUtilities.dp(8); return p;
    }
    @Override public void onResume() {
        super.onResume();
        if (content != null) {
            NebulaTheme theme = NebulaTheme.of(content.getContext());
            if (surface != theme.surface() || accent != theme.primary()) rebuild();
        }
    }
    @Override public boolean isLightStatusBar() { return !NebulaTheme.of(getContext()).isDark(); }
}
