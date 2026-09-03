package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/** Welcome to NebulaGram, with the same typography and surfaces as sign-in. */
public class NebulaIntroFragment extends BaseFragment {
    private NebulaMark mark;
    private Runnable onContinue;

    public static boolean shouldShow() { return true; }

    public NebulaIntroFragment onContinue(Runnable action) {
        onContinue = action;
        return this;
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);
        NebulaOnboardingLayout root = new NebulaOnboardingLayout(context);

        mark = new NebulaMark(theme.primary(), theme.primaryShade());
        ImageView image = new ImageView(context);
        image.setImageDrawable(mark);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable glow = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {theme.primaryContainer(), NebulaTheme.stateLayer(theme.primaryContainer(), 0.15f)});
        glow.setCornerRadius(AndroidUtilities.dp(44));
        image.setBackground(glow);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(144), AndroidUtilities.dp(144));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconParams.bottomMargin = AndroidUtilities.dp(32);
        root.content.addView(image, iconParams);

        TextView title = text(context, 32, theme.onSurface(), true);
        title.setGravity(Gravity.CENTER);
        String message = LocaleController.getString(R.string.NebulaAuthWelcomeTitle);
        SpannableStringBuilder highlighted = new SpannableStringBuilder(message);
        int brand = message.indexOf("NebulaGram");
        if (brand >= 0) highlighted.setSpan(new ForegroundColorSpan(theme.primary()), brand, brand + 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(highlighted);
        root.content.addView(title, width());

        TextView subtitle = text(context, 16, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(LocaleController.getString(R.string.NebulaAuthWelcomeSubtitle));
        LinearLayout.LayoutParams subtitleParams = width();
        subtitleParams.topMargin = AndroidUtilities.dp(14);
        root.content.addView(subtitle, subtitleParams);

        LinearLayout feature = new LinearLayout(context);
        feature.setOrientation(LinearLayout.VERTICAL);
        feature.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(18));
        GradientDrawable card = new GradientDrawable();
        card.setColor(theme.surfaceContainer());
        card.setCornerRadius(AndroidUtilities.dp(24));
        feature.setBackground(card);
        TextView featureTitle = text(context, 16, theme.primary(), true);
        featureTitle.setText(LocaleController.getString(R.string.NebulaAuthWelcomeFeature));
        feature.addView(featureTitle, width());
        TextView featureSubtitle = text(context, 14, theme.onSurfaceVariant(), false);
        featureSubtitle.setText(LocaleController.getString(R.string.NebulaAuthWelcomeFeatureSub));
        LinearLayout.LayoutParams featureTextParams = width();
        featureTextParams.topMargin = AndroidUtilities.dp(6);
        feature.addView(featureSubtitle, featureTextParams);
        LinearLayout.LayoutParams featureParams = width();
        featureParams.topMargin = AndroidUtilities.dp(28);
        root.content.addView(feature, featureParams);

        NebulaButton next = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        next.setText(LocaleController.getString(R.string.NebulaAuthStart));
        NebulaOnboardingLayout.action(next);
        next.setOnClickListener(v -> {
            ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putBoolean("intro_seen", true).apply();
            if (onContinue != null) onContinue.run();
            else presentFragment(new NebulaConnectFragment(), true);
        });
        root.actions.addView(next);
        NebulaButton language = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        language.setText(LocaleController.getString(R.string.NebulaChangeLanguage));
        NebulaOnboardingLayout.action(language);
        language.setOnClickListener(v -> presentFragment(new org.telegram.ui.LanguageSelectActivity()));
        root.actions.addView(language);
        root.actions.addView(NebulaProgress.build(context, 4, 0));
        fragmentView = root;
        return root;
    }

    static TextView text(Context context, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        view.setLineSpacing(AndroidUtilities.dp(2), 1f);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(AndroidUtilities.bold());
        return view;
    }

    static LinearLayout.LayoutParams width() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onFragmentDestroy() {
        if (mark != null) mark.detach();
        mark = null;
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() { return !NebulaTheme.of(getContext()).isDark(); }
}
