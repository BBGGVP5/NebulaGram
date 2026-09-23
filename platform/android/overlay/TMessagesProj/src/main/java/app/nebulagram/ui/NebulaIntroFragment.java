package app.nebulagram.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/** A short, native tour of features NebulaGram actually has before sign-in. */
public class NebulaIntroFragment extends BaseFragment {
    private static final int[] TITLES = {
            R.string.NebulaAuthWelcomeTitle, R.string.NebulaIntroDesignTitle,
            R.string.NebulaIntroPrivacyTitle, R.string.NebulaIntroAITitle,
            R.string.NebulaIntroLinkTitle
    };
    private static final int[] SUBTITLES = {
            R.string.NebulaAuthWelcomeSubtitle, R.string.NebulaIntroDesignSubtitle,
            R.string.NebulaIntroPrivacySubtitle, R.string.NebulaIntroAISubtitle,
            R.string.NebulaIntroLinkSubtitle
    };
    private int page;
    private NebulaIntroArt art;
    private TextView title;
    private TextView subtitle;
    private NebulaButton next;
    private NebulaButton language;
    private NebulaButton skip;
    private LinearLayout actions;
    private View progress;
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

        art = new NebulaIntroArt(context);
        LinearLayout.LayoutParams artParams = width();
        artParams.bottomMargin = NebulaLoginStyle.compact(26);
        root.content.addView(art, artParams);

        title = text(context, 26 + 6 * NebulaLoginStyle.vertical(), theme.onSurface(), true);
        title.setGravity(Gravity.CENTER);
        root.content.addView(title, width());

        subtitle = text(context, 16, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = width();
        subtitleParams.topMargin = AndroidUtilities.dp(14);
        root.content.addView(subtitle, subtitleParams);

        next = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        NebulaOnboardingLayout.action(next);
        next.setOnClickListener(v -> {
            if (page < TITLES.length - 1) showPage(page + 1);
            else continueToSignIn();
        });
        root.actions.addView(next);
        language = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        language.setText(LocaleController.getString(R.string.NebulaChangeLanguage));
        NebulaOnboardingLayout.action(language);
        language.setOnClickListener(v -> presentFragment(new org.telegram.ui.LanguageSelectActivity()));
        root.actions.addView(language);
        skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaIntroSkip));
        NebulaOnboardingLayout.action(skip);
        skip.setOnClickListener(v -> continueToSignIn());
        root.actions.addView(skip);
        actions = root.actions;
        showPage(page);
        fragmentView = root;
        return root;
    }

    private void showPage(int index) {
        page = index;
        art.setPage(index);
        NebulaTheme theme = NebulaTheme.of(title.getContext());
        title.setText(highlight(LocaleController.getString(TITLES[index]),
                index == 4 ? "NebulaLink" : "NebulaGram", theme.primary()));
        subtitle.setText(LocaleController.getString(SUBTITLES[index]));
        next.setText(LocaleController.getString(index == TITLES.length - 1
                ? R.string.NebulaAuthStart : R.string.NebulaIntroNext));
        language.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        skip.setVisibility(index > 0 && index < TITLES.length - 1 ? View.VISIBLE : View.GONE);
        if (progress != null) actions.removeView(progress);
        progress = NebulaProgress.build(actions.getContext(), TITLES.length, index);
        actions.addView(progress);
        title.announceForAccessibility(title.getText());
    }

    private void continueToSignIn() {
        ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0)
                .edit().putBoolean("intro_seen", true).apply();
        if (onContinue != null) onContinue.run();
        else presentFragment(new NebulaConnectFragment(), true);
    }

    /** Красит название акцентом: тем же приёмом, что и заголовок приветствия. */
    static CharSequence highlight(String message, String word, int color) {
        SpannableStringBuilder highlighted = new SpannableStringBuilder(message);
        int at = message.indexOf(word);
        if (at >= 0) {
            highlighted.setSpan(new ForegroundColorSpan(color), at, at + word.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return highlighted;
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
    public boolean isLightStatusBar() { return !NebulaTheme.of(getContext()).isDark(); }
}
