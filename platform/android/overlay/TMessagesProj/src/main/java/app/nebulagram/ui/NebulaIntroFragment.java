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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * The welcome screen: "Welcome to NebulaGram", one primary action, and the
 * progress of the first-run flow underneath it.
 *
 * <p>The copy and the order of the steps come from the Go core
 * ({@code core/onboarding}), so this screen, the iOS one and the desktop one
 * say the same thing. What lives here is only how it looks on Android.
 */
public class NebulaIntroFragment extends BaseFragment {

    /** How many steps the first-run flow has, for the progress row. */
    private static final int STEPS = 4;
    private static final int STEP_INDEX = 0;

    private static final String PREFS = "nebulagram";
    private static final String KEY_SEEN = "intro_seen";

    private NebulaMark mark;
    private Runnable onContinue;

    /**
     * Whether the welcome screen still has to be shown. Once it is passed, the
     * app falls back to Telegram's own intro, so the hook in LaunchActivity
     * stays a single line and the upstream flow is left alone.
     */
    public static boolean shouldShow() {
        return !prefs().getBoolean(KEY_SEEN, false);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    /** What to do when the user taps Next; defaults to closing the screen. */
    public NebulaIntroFragment onContinue(Runnable action) {
        this.onContinue = action;
        return this;
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        // The welcome screen carries its own layout end to end, so the action
        // bar would only take space away from it.
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(AndroidUtilities.dp(22), 0, AndroidUtilities.dp(22), AndroidUtilities.dp(10));
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        column.addView(buildMark(context, theme));
        column.addView(buildTitle(context, theme));
        column.addView(buildSubtitle(context, theme));

        View spacer = new View(context);
        column.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        column.addView(buildPrimaryAction(context));
        column.addView(buildLanguageAction(context));
        // Material 3 puts the progress under the actions, not above the copy:
        // it is a status line, not a heading.
        column.addView(NebulaProgress.build(context, STEPS, STEP_INDEX));

        return root;
    }

    private View buildMark(Context context, NebulaTheme theme) {
        mark = new NebulaMark(theme.primary(), theme.primaryShade());
        ImageView view = new ImageView(context);
        view.setImageDrawable(mark);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(132), AndroidUtilities.dp(132));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = AndroidUtilities.dp(40);
        params.bottomMargin = AndroidUtilities.dp(24);
        view.setLayoutParams(params);
        return view;
    }

    private View buildTitle(Context context, NebulaTheme theme) {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        title.setTextColor(theme.onSurface());
        title.setLineSpacing(AndroidUtilities.dp(2), 1f);

        // The product name is the accent in the sentence, the way the Material 3
        // display style is meant to be used.
        String full = LocaleController.getString(R.string.NebulaWelcomeTitle);
        String product = LocaleController.getString(R.string.NebulaAppName);
        SpannableStringBuilder text = new SpannableStringBuilder(full);
        int at = full.indexOf(product);
        if (at >= 0) {
            text.setSpan(new ForegroundColorSpan(theme.primary()), at, at + product.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        title.setText(text);
        return title;
    }

    private View buildSubtitle(Context context, NebulaTheme theme) {
        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setTextColor(theme.onSurfaceVariant());
        subtitle.setLineSpacing(AndroidUtilities.dp(3), 1f);
        subtitle.setText(LocaleController.getString(R.string.NebulaWelcomeSubtitle));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(10);
        subtitle.setLayoutParams(params);
        return subtitle;
    }

    private View buildPrimaryAction(Context context) {
        NebulaButton next = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        next.setText(LocaleController.getString(R.string.NebulaNext));
        next.setOnClickListener(v -> {
            prefs().edit().putBoolean(KEY_SEEN, true).apply();
            if (onContinue != null) {
                onContinue.run();
            } else {
                // Дальше наш же шаг с туннелем; карусель Telegram пропускаем —
                // её работу только что сделал этот экран.
                presentFragment(new NebulaConnectFragment(), true);
            }
        });
        next.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(52)));
        return next;
    }

    private View buildLanguageAction(Context context) {
        NebulaButton language = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        language.setText(LocaleController.getString(R.string.NebulaChangeLanguage));
        language.setOnClickListener(v -> presentFragment(new org.telegram.ui.LanguageSelectActivity()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44));
        params.topMargin = AndroidUtilities.dp(2);
        language.setLayoutParams(params);
        return language;
    }


    @Override
    public void onFragmentDestroy() {
        if (mark != null) {
            mark.detach();
            mark = null;
        }
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() {
        return !NebulaTheme.of(getContext()).isDark();
    }
}
