package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LauncherIconController;

/**
 * "About NebulaGram": the launcher icon on a backdrop of its own color, the
 * version chips, the update button and the project's real links.
 *
 * Every link here exists. The detailed build card (ABI, build date, component
 * versions) stays on {@link NebulaSectionFragment#SECTION_ABOUT}, one row away.
 */
public final class NebulaAboutFragment extends BaseFragment {
    static final String REPOSITORY = "https://github.com/BBGGVP5/NebulaGram";
    static final String NEWS = "ngram_official";

    private NebulaBrandBackdrop backdrop;
    private NebulaIconArt art;
    private FrameLayout bar;
    private View barScrim;
    private TextView channelChip, iconValue;
    private NebulaUpdatesButton updates;
    private LauncherIconController.LauncherIcon shown;

    @Override public View createView(Context context) {
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);
        shown = NebulaBrandPalette.current();
        NebulaBrandPalette palette = NebulaBrandPalette.of(context, shown);

        FrameLayout root = new FrameLayout(context);
        backdrop = new NebulaBrandBackdrop(context);
        backdrop.setGeometry(0.2f, 0.85f, 0.75f);
        backdrop.setColors(palette.top, palette.glow);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(16), AndroidUtilities.statusBarHeight + dp(72), dp(16), dp(28));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));

        art = new NebulaIconArt(context);
        art.setIcon(shown);
        art.setElevation(dp(18));
        art.setContentDescription(text("Иконка приложения", "App icon"));
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        art.setOnClickListener(v -> presentFragment(new NebulaIconPickerFragment()));
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        artParams.topMargin = dp(8);
        artParams.bottomMargin = dp(26);
        column.addView(art, artParams);

        TextView name = NebulaBrandUi.text(context, 34, NebulaBrandUi.TEXT, true);
        name.setText("NebulaGram");
        name.setGravity(Gravity.CENTER);
        column.addView(name, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.addView(chip(context, versionName(context)));
        chips.addView(chip(context, "Telegram " + org.telegram.messenger.BuildVars.BUILD_VERSION_STRING));
        channelChip = chip(context, channel(context));
        chips.addView(channelChip);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(-2, -2);
        chipsParams.topMargin = dp(18);
        column.addView(chips, chipsParams);

        updates = new NebulaUpdatesButton(context, text("Обновления", "Updates"));
        updates.setOnClickListener(v -> NebulaUpdateSettingsSheet.show(this));
        LinearLayout.LayoutParams updatesParams = new LinearLayout.LayoutParams(-2, dp(50));
        updatesParams.topMargin = dp(18);
        updatesParams.bottomMargin = dp(10);
        column.addView(updates, updatesParams);

        column.addView(header(context, text("Сообщество", "Community")), wide());
        LinearLayout community = card(context);
        community.addView(row(context, R.drawable.msg_channel, text("Новости и обновления", "News and updates"), "@" + NEWS,
                v -> Browser.openUrl(context, "https://t.me/" + NEWS)));
        divider(context, community);
        community.addView(row(context, R.drawable.msg_download, text("Сборки", "Builds"), "@" + NebulaRelease.CHANNEL,
                v -> Browser.openUrl(context, "https://t.me/" + NebulaRelease.CHANNEL)));
        divider(context, community);
        community.addView(row(context, R.drawable.msg_groups, "NebulaHub", "@" + NebulaCommunityCard.SOURCE_USERNAME,
                v -> Browser.openUrl(context, "https://t.me/" + NebulaCommunityCard.SOURCE_USERNAME)));
        column.addView(community, wide());

        column.addView(header(context, text("Проект", "Project")), wide());
        LinearLayout project = card(context);
        project.addView(row(context, R.drawable.msg_link, text("Исходный код", "Source code"), "GitHub",
                v -> Browser.openUrl(context, REPOSITORY)));
        divider(context, project);
        project.addView(row(context, R.drawable.msg_report, text("Сообщить об ошибке", "Report a bug"), "Issues",
                v -> Browser.openUrl(context, REPOSITORY + "/issues")));
        divider(context, project);
        View icon = row(context, R.drawable.msg_customize, text("Иконка приложения", "App icon"),
                NebulaLauncherIconLabels.title(context, shown), v -> presentFragment(new NebulaIconPickerFragment()));
        iconValue = icon.findViewWithTag("value");
        project.addView(icon);
        divider(context, project);
        project.addView(row(context, R.drawable.nebula_settings_support, text("Поддержать проект", "Support the project"), null,
                v -> presentFragment(new NebulaSupportFragment())));
        divider(context, project);
        project.addView(row(context, R.drawable.msg_info, text("О сборке", "Build details"), buildCode(context),
                v -> presentFragment(new NebulaSectionFragment(NebulaSectionFragment.SECTION_ABOUT))));
        column.addView(project, wide());

        TextView footer = NebulaBrandUi.text(context, 15, NebulaBrandUi.TEXT_SECONDARY, false);
        footer.setIncludeFontPadding(true);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(dp(2), 1f);
        footer.setText(text("NebulaGram основан на Telegram для Android и распространяется по лицензии GPL. Спасибо всем, чей открытый код помог проекту.",
                "NebulaGram is based on Telegram for Android and is distributed under the GPL. Thanks to everyone whose open source code helped the project."));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(22);
        footerParams.leftMargin = footerParams.rightMargin = dp(16);
        column.addView(footer, footerParams);

        LinearLayout pills = new LinearLayout(context);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        pills.setGravity(Gravity.CENTER);
        pills.addView(pill(context, R.drawable.msg_policy, text("Лицензия", "License"),
                v -> Browser.openUrl(context, REPOSITORY + "/blob/main/LICENSE")));
        pills.addView(pill(context, R.drawable.msg_openin, "Telegram",
                v -> Browser.openUrl(context, "https://github.com/DrKLO/Telegram")));
        LinearLayout.LayoutParams pillsParams = new LinearLayout.LayoutParams(-2, -2);
        pillsParams.topMargin = dp(18);
        column.addView(pills, pillsParams);

        barScrim = new View(context);
        barScrim.setAlpha(0f);
        root.addView(barScrim, new FrameLayout.LayoutParams(-1, AndroidUtilities.statusBarHeight + dp(60)));
        bar = NebulaBrandUi.topBar(context, text("О NebulaGram", "About NebulaGram"), this::finishFragment);
        root.addView(bar, new FrameLayout.LayoutParams(-1, AndroidUtilities.statusBarHeight + dp(60)));
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
                barScrim.setAlpha(Math.min(1f, y / (float) dp(56))));
        applyPalette(palette, false);
        return fragmentView = root;
    }

    private int dp(float value) { return AndroidUtilities.dp(value); }

    private void applyPalette(NebulaBrandPalette palette, boolean animate) {
        if (animate) backdrop.animateColors(palette.top, palette.glow);
        else backdrop.setColors(palette.top, palette.glow);
        barScrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{palette.top, NebulaBrandPalette.withAlpha(palette.top, 0.92f)}));
        channelChip.setBackground(NebulaBrandUi.rounded(NebulaBrandPalette.withAlpha(palette.accent, 0.55f), 100));
        updates.setColor(palette.accent);
    }

    @Override public void onResume() {
        super.onResume();
        if (art == null) return;
        LauncherIconController.LauncherIcon current = NebulaBrandPalette.current();
        if (current == shown) return;
        shown = current;
        art.setIcon(current);
        if (iconValue != null) iconValue.setText(NebulaLauncherIconLabels.title(art.getContext(), current));
        applyPalette(NebulaBrandPalette.of(art.getContext(), current), true);
    }

    @Override public boolean isLightStatusBar() { return false; }

    @Override public int getNavigationBarColor() { return NebulaBrandPalette.FLOOR; }

    // --- pieces -------------------------------------------------------------

    static String versionName(Context context) {
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            if (name != null) return name.replace("-beta", " beta");
        } catch (Exception ignored) { }
        return org.telegram.messenger.BuildVars.BUILD_VERSION_STRING;
    }

    private static String buildCode(Context context) {
        try {
            return "#" + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Dev for debug builds, Beta for "-beta" versions, Release otherwise. */
    static String channel(Context context) {
        if (org.telegram.messenger.BuildConfig.DEBUG) return "Dev";
        try {
            String name = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            if (name != null && name.contains("-beta")) return "Beta";
        } catch (Exception ignored) { }
        return "Release";
    }

    private TextView chip(Context context, String label) {
        TextView chip = NebulaBrandUi.text(context, 15, NebulaBrandUi.TEXT, true);
        chip.setText(label);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setBackground(NebulaBrandUi.rounded(0x1FFFFFFF, 100));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.leftMargin = params.rightMargin = dp(4);
        chip.setLayoutParams(params);
        return chip;
    }

    private TextView header(Context context, String label) {
        TextView header = NebulaBrandUi.text(context, 14, NebulaBrandUi.TEXT_SECONDARY, true);
        header.setText(label.toUpperCase(java.util.Locale.getDefault()));
        header.setLetterSpacing(0.04f);
        header.setPadding(dp(12), dp(24), dp(12), dp(10));
        return header;
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(NebulaBrandUi.rounded(NebulaBrandUi.CARD, 24));
        card.setClipToOutline(true);
        return card;
    }

    private LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void divider(Context context, LinearLayout card) {
        View line = new View(context);
        line.setBackgroundColor(NebulaBrandUi.DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f)));
        params.leftMargin = dp(64);
        card.addView(line, params);
    }

    private View row(Context context, int iconRes, String title, String value, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(58));
        row.setPadding(dp(20), dp(8), dp(14), dp(8));
        row.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x1FFFFFFF, 2));
        row.setOnClickListener(click);

        ImageView icon = new ImageView(context);
        icon.setImageResource(NebulaSettingsIcons.resource(iconRes));
        icon.setColorFilter(new PorterDuffColorFilter(NebulaBrandUi.TEXT, PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        iconParams.rightMargin = dp(18);
        row.addView(icon, iconParams);

        TextView label = NebulaBrandUi.text(context, 17, NebulaBrandUi.TEXT, false);
        label.setText(title);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        TextView trailing = NebulaBrandUi.text(context, 16, NebulaBrandUi.TEXT_SECONDARY, false);
        trailing.setTag("value");
        trailing.setSingleLine(true);
        trailing.setEllipsize(TextUtils.TruncateAt.START);
        trailing.setMaxWidth(dp(170));
        trailing.setText(value == null ? "" : value);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.leftMargin = dp(8);
        row.addView(trailing, valueParams);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.msg_arrowright);
        chevron.setColorFilter(new PorterDuffColorFilter(NebulaBrandUi.TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        chevron.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        chevronParams.leftMargin = dp(4);
        row.addView(chevron, chevronParams);
        return row;
    }

    private View pill(Context context, int iconRes, String title, View.OnClickListener click) {
        LinearLayout pill = new LinearLayout(context);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), 0, dp(20), 0);
        pill.setBackground(NebulaBrandUi.pressable(NebulaBrandUi.CARD, 100));
        pill.setOnClickListener(click);
        ImageView icon = new ImageView(context);
        icon.setImageResource(NebulaSettingsIcons.resource(iconRes));
        icon.setColorFilter(new PorterDuffColorFilter(NebulaBrandUi.TEXT_SECONDARY, PorterDuff.Mode.SRC_IN));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconParams.rightMargin = dp(8);
        pill.addView(icon, iconParams);
        TextView label = NebulaBrandUi.text(context, 16, NebulaBrandUi.TEXT_SECONDARY, true);
        label.setText(title);
        label.setSingleLine(true);
        pill.addView(label, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
        params.leftMargin = params.rightMargin = dp(5);
        pill.setLayoutParams(params);
        return pill;
    }

    /** Accent pill with a refresh glyph that turns once on every tap. */
    static final class NebulaUpdatesButton extends LinearLayout {
        private final ImageView glyph;

        NebulaUpdatesButton(Context context, String title) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setPadding(AndroidUtilities.dp(26), 0, AndroidUtilities.dp(30), 0);
            glyph = new ImageView(context);
            glyph.setImageResource(R.drawable.msg_retry);
            glyph.setColorFilter(new PorterDuffColorFilter(NebulaBrandUi.TEXT, PorterDuff.Mode.SRC_IN));
            glyph.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LayoutParams glyphParams = new LayoutParams(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
            glyphParams.rightMargin = AndroidUtilities.dp(10);
            addView(glyph, glyphParams);
            TextView label = NebulaBrandUi.text(context, 17, NebulaBrandUi.TEXT, true);
            label.setText(title);
            addView(label, new LayoutParams(-2, -2));
        }

        void setColor(int color) {
            setBackground(NebulaBrandUi.pressable(color, 100));
        }

        @Override public boolean performClick() {
            glyph.animate().cancel();
            glyph.setRotation(0);
            glyph.animate().rotation(360).setDuration(600)
                    .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
            return super.performClick();
        }
    }
}
