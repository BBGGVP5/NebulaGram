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
     * Показывать ли наш экран приветствия.
     *
     * <p>Всегда, пока пользователь не вошёл: хук в LaunchActivity срабатывает
     * только в этом случае. Раньше здесь стоял флажок "уже видел", и при
     * перезапуске вместо нашего экрана появлялась карусель Telegram — то есть
     * ровно то, что мы заменяем.
     */
    public static boolean shouldShow() {
        return true;
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

        // Содержимое живёт по центру, кнопки прижаты к низу. Раньше всё висело
        // сверху, и между текстом и кнопками зияла пустая половина экрана.
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(160));
        content.addView(buildMark(context, theme));
        content.addView(buildTitle(context, theme));
        content.addView(buildSubtitle(context, theme));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        actions.addView(buildPrimaryAction(context));
        actions.addView(buildLanguageAction(context));
        actions.addView(NebulaProgress.build(context, STEPS, STEP_INDEX));

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.gravity = Gravity.BOTTOM;
        root.addView(actions, actionParams);

        return root;
    }

    private View buildMark(Context context, NebulaTheme theme) {
        mark = new NebulaMark(theme.primary(), theme.primaryShade());
        ImageView view = new ImageView(context);
        view.setImageDrawable(mark);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(120), AndroidUtilities.dp(120));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = AndroidUtilities.dp(28);
        view.setLayoutParams(params);
        return view;
    }

    private View buildTitle(Context context, NebulaTheme theme) {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
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
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitle.setTextColor(theme.onSurfaceVariant());
        subtitle.setLineSpacing(AndroidUtilities.dp(3), 1f);
        subtitle.setText(LocaleController.getString(R.string.NebulaWelcomeSubtitle));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(12);
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
