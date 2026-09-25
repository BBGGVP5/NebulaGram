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
    private NebulaButton previous;
    private LinearLayout actions;
    private LinearLayout secondaryActions;
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
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        artParams.gravity = Gravity.CENTER_HORIZONTAL;
        artParams.bottomMargin = NebulaLoginStyle.compact(14);
        root.content.addView(art, artParams);

        title = text(context, 24, theme.onSurface(), true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(2);
        title.setIncludeFontPadding(false);
        root.content.addView(title, width());

        subtitle = text(context, 15, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setMaxLines(4);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleParams = width();
        subtitleParams.topMargin = AndroidUtilities.dp(8);
        root.content.addView(subtitle, subtitleParams);

        next = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        NebulaOnboardingLayout.action(next);
        android.graphics.drawable.GradientDrawable gradient = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xff675fe8, 0xff289fc4});
        gradient.setCornerRadius(AndroidUtilities.dp(28));
        android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
        mask.setColor(-1); mask.setCornerRadius(AndroidUtilities.dp(28));
        next.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30ffffff), gradient, mask));
        next.setTextColor(0xffffffff);
        next.setOnClickListener(v -> {
            if (page < TITLES.length - 1) showPage(page + 1);
            else continueToSignIn();
        });
        root.actions.addView(next);
        root.content.setGravity(Gravity.CENTER_VERTICAL);
        // Slightly lower the illustration-and-copy group so the visual center
        // accounts for the anchored pager controls below it.
        root.content.setPadding(0, NebulaLoginStyle.compact(38), 0, 0);
        root.setOnSwipeListener(direction -> showPage(Math.max(0,
                Math.min(TITLES.length - 1, page + direction))));
        language = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        language.setText(LocaleController.getString(R.string.NebulaChangeLanguage));
        NebulaOnboardingLayout.action(language);
        language.setOnClickListener(v -> presentFragment(new org.telegram.ui.LanguageSelectActivity()));
        skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaIntroSkip));
        NebulaOnboardingLayout.action(skip);
        skip.setOnClickListener(v -> continueToSignIn());
        previous = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        previous.setText(NebulaText.text("← Назад", "← Back"));
        NebulaOnboardingLayout.action(previous);
        previous.setOnClickListener(v -> showPage(Math.max(0, page - 1)));
        secondaryActions = new LinearLayout(context);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setGravity(Gravity.CENTER);
        root.actions.addView(secondaryActions);
        addSecondary(previous);
        addSecondary(language);
        addSecondary(skip);
        actions = root.actions;
        showPage(page);
        fragmentView = root;
        return root;
    }

    private void showPage(int index) {
        if (index == page && title.getText().length() > 0) return;
        int previousPage = page;
        page = index;
        int direction = Integer.compare(index, previousPage);
        float offset = AndroidUtilities.dp(14) * (direction == 0 ? 1 : direction);
        art.animate().cancel();
        art.setAlpha(0f);
        art.setTranslationX(offset);
        art.setPage(index);
        art.animate().alpha(1f).translationX(0).setDuration(220).start();
        NebulaTheme theme = NebulaTheme.of(title.getContext());
        title.animate().cancel();
        subtitle.animate().cancel();
        title.setAlpha(0f);
        subtitle.setAlpha(0f);
        title.setTranslationX(offset);
        subtitle.setTranslationX(offset);
        title.setText(new String[]{NebulaText.text("Добро пожаловать\nв NebulaGram", "Welcome to\nNebulaGram"),
                NebulaText.text("Настрой под себя", "Make it yours"), NebulaText.text("Твоё личное пространство", "Your private space"),
                NebulaText.text("Помощник в твоём ритме", "An assistant at your pace"), NebulaText.text("NebulaLink рядом", "NebulaLink, built in")}[index]);
        subtitle.setText(new String[]{
                NebulaText.text("Любимые чаты, близкие люди и больше возможностей для общения.", "Your favorite chats, your people, and more ways to connect."),
                NebulaText.text("Темы, иконки и папки. Собери удобное пространство для своих разговоров.", "Themes, icons and folders. Make room for the way you chat."),
                NebulaText.text("Управляй приватностью и защищай приложение кодом или биометрией.", "Manage your privacy and protect the app with a passcode or biometrics."),
                NebulaText.text("Переводи сообщения и выделяй главное с помощью выбранной тобой модели ИИ.", "Translate messages and find the key points with the AI model you choose."),
                NebulaText.text("Добавь подписку Xray, выбери сервер и управляй подключением прямо здесь.", "Add an Xray subscription, pick a server, and manage your connection right here.")
        }[index]);
        title.animate().alpha(1f).translationX(0).setDuration(220).start();
        subtitle.animate().alpha(1f).translationX(0).setDuration(220).start();
        next.setText(LocaleController.getString(index == TITLES.length - 1
                ? R.string.NebulaAuthStart : R.string.NebulaIntroNext)
                + (LocaleController.isRTL ? "  ←" : "  →"));
        previous.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        language.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        skip.setVisibility(index > 0 && index < TITLES.length - 1 ? View.VISIBLE : View.INVISIBLE);
        if (progress != null) actions.removeView(progress);
        progress = dots(actions.getContext(), index);
        actions.addView(progress, 0);
        title.announceForAccessibility(title.getText());
    }

    private View dots(Context context, int selected) {
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER);
        row.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(18));
        for (int i=0;i<TITLES.length;i++) {
            View dot = new View(context); android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            shape.setColor(i==selected ? 0xff65b9ee : NebulaTheme.of(context).outline()); dot.setBackground(shape);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(AndroidUtilities.dp(6),AndroidUtilities.dp(6));
            size.leftMargin=AndroidUtilities.dp(4);size.rightMargin=AndroidUtilities.dp(4);row.addView(dot,size);
            final int pageIndex = i;
            dot.setContentDescription(NebulaText.text("Экран " + (i + 1), "Page " + (i + 1)));
            dot.setOnClickListener(v -> showPage(pageIndex));
            dot.setClickable(true);
            dot.setFocusable(true);
        }
        return row;
    }

    private void addSecondary(NebulaButton button) {
        button.setSingleLine();
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1f);
        secondaryActions.addView(button, params);
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
