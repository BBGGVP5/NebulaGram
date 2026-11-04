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
    private String displayedLanguage;

    public static boolean shouldShow() { return true; }

    public NebulaIntroFragment onContinue(Runnable action) {
        onContinue = action;
        return this;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (title != null) {
            language.setText("🌐  " + LocaleController.getString(R.string.NebulaChangeLanguage));
            previous.setText(LocaleController.getString(R.string.Back));
            skip.setText(LocaleController.getString(R.string.NebulaIntroSkip));
            displayedLanguage = null;
            showPage(page);
        }
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);
        NebulaOnboardingLayout root = new NebulaOnboardingLayout(context);

        // Как в обзоре Telegram: живая картинка сверху, под ней заголовок
        // и описание по центру, внизу шаги и кнопка.
        art = new NebulaIntroArt(context);
        art.setAnimated(NebulaGlass.animations() && !NebulaGlass.reduced()
                && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()));
        root.content.addView(art, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        title = text(context, 26, theme.onSurface(), true);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setMaxLines(4);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = width();
        titleParams.topMargin = NebulaLoginStyle.compact(20);
        root.content.addView(title, titleParams);

        subtitle = text(context, 16, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setMaxLines(8);
        subtitle.setIncludeFontPadding(false);
        LinearLayout.LayoutParams subtitleParams = width();
        subtitleParams.topMargin = AndroidUtilities.dp(10);
        subtitleParams.leftMargin = subtitleParams.rightMargin = AndroidUtilities.dp(8);
        root.content.addView(subtitle, subtitleParams);

        next = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        NebulaOnboardingLayout.action(next);
        next.setOnClickListener(v -> {
            if (page < TITLES.length - 1) showPage(page + 1);
            else continueToSignIn();
        });
        root.actions.addView(next);
        root.content.setGravity(Gravity.TOP);
        root.content.setPadding(0, NebulaLoginStyle.compact(12), 0, NebulaLoginStyle.compact(8));
        root.setOnSwipeListener(direction -> showPage(Math.max(0,
                Math.min(TITLES.length - 1, page + direction))));
        language = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        language.setText("🌐  " + LocaleController.getString(R.string.NebulaChangeLanguage));
        language.setContentDescription(LocaleController.getString(R.string.NebulaChangeLanguage));
        language.setOnClickListener(v -> presentFragment(new NebulaLanguageFragment()));
        skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaIntroSkip));
        skip.setOnClickListener(v -> continueToSignIn());
        previous = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        previous.setText(LocaleController.getString(R.string.Back));
        previous.setOnClickListener(v -> showPage(Math.max(0, page - 1)));
        secondaryActions = new LinearLayout(context);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        secondaryActions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondaryParams.topMargin = AndroidUtilities.dp(6);
        secondaryActions.setLayoutParams(secondaryParams);
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
        String currentLanguage = LocaleController.getInstance().getCurrentLocale().getLanguage();
        if (index == page && title.getText().length() > 0
                && currentLanguage.equals(displayedLanguage)) return;
        displayedLanguage = currentLanguage;
        int previousPage = page;
        page = index;
        int direction = Integer.compare(index, previousPage);
        float offset = AndroidUtilities.dp(14) * direction * (LocaleController.isRTL ? -1 : 1);
        boolean animate = direction != 0 && NebulaGlass.animations() && !NebulaGlass.reduced()
                && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled());
        for (View view : new View[]{art, title, subtitle}) {
            view.animate().cancel();
            view.setAlpha(animate ? Math.max(.6f, view.getAlpha()) : 1f);
            view.setTranslationX(animate ? offset : 0f);
        }
        art.setPage(index);
        title.setText(new String[]{NebulaText.text("Добро пожаловать\nв NebulaGram", "Welcome to\nNebulaGram"),
                NebulaText.text("Оформление\nпод вас", "Designed\naround you"),
                NebulaText.text("Ваше личное\nпространство", "Your private\nspace"),
                NebulaText.text("ИИ-помощник\nвсегда рядом", "An AI assistant\nat hand"),
                NebulaText.text("NebulaLink\nуже внутри", "NebulaLink,\nbuilt in")}[index]);
        subtitle.setText(new String[]{
                NebulaText.text("Ваши чаты и близкие люди — и возможности, которых нет в обычном Telegram.", "Your chats and your people — plus features regular Telegram doesn't have."),
                NebulaText.text("Цвет акцента, иконка, темы и папки. Настройте всё так, как удобно именно вам.", "Accent color, app icon, themes and folders. Set everything up the way you like."),
                NebulaText.text("Код-пароль и биометрия закроют приложение и отдельные чаты от чужих глаз.", "A passcode and biometrics keep the app and individual chats away from prying eyes."),
                NebulaText.text("Переводите сообщения и выделяйте главное с моделью, которую выберете сами.", "Translate messages and pull out the key points with a model you choose."),
                NebulaText.text("Добавьте подписку Xray, выберите сервер и управляйте подключением прямо в приложении.", "Add an Xray subscription, pick a server and manage the connection right in the app.")
        }[index]);
        if (animate) {
            for (View view : new View[]{art, title, subtitle}) {
                view.animate().alpha(1f).translationX(0).setDuration(220)
                        .setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
        }
        next.setText(LocaleController.getString(index == TITLES.length - 1
                ? R.string.NebulaAuthStart : R.string.NebulaIntroNext));
        previous.setVisibility(index == 0 ? View.GONE : View.VISIBLE);
        language.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        skip.setVisibility(index > 0 && index < TITLES.length - 1 ? View.VISIBLE : View.INVISIBLE);
        if (index == 0) {
            skip.setVisibility(View.GONE);
            setSecondaryParams(language, false, true);
        } else if (index == TITLES.length - 1) {
            skip.setVisibility(View.GONE);
            setSecondaryParams(previous, false, true);
        } else {
            setSecondaryParams(previous, true, false);
            setSecondaryParams(skip, true, false);
        }
        if (progress != null) actions.removeView(progress);
        progress = dots(actions.getContext(), index);
        actions.addView(progress, 0);
        title.announceForAccessibility(title.getText());
    }

    /** Тот же вид шагов, что и на экранах входа: вытянутая текущая полоска. */
    private View dots(Context context, int selected) {
        NebulaTheme theme = NebulaTheme.of(context);
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER);
        row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(22));
        for (int i=0;i<TITLES.length;i++) {
            View dot = new View(context);
            android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
            shape.setCornerRadius(AndroidUtilities.dp(3));
            shape.setColor(i == selected ? theme.primary() : theme.outline());
            dot.setBackground(shape);
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(i == selected ? 24 : 6), AndroidUtilities.dp(6));
            size.leftMargin = size.rightMargin = AndroidUtilities.dp(3);
            row.addView(dot, size);
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
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(44));
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(4);
        secondaryActions.addView(button, params);
    }

    private void setSecondaryParams(NebulaButton button, boolean weighted, boolean centered) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                weighted ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
                AndroidUtilities.dp(44), weighted ? 1f : 0f);
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(4);
        if (centered) {
            params.gravity = Gravity.CENTER_HORIZONTAL;
        }
        button.setLayoutParams(params);
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
