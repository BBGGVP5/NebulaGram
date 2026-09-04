package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

import app.nebulagram.nebulalink.NebulaLink;

/** Optional connection before login; content and actions scroll together under IME. */
public class NebulaConnectFragment extends BaseFragment {
    private NebulaAuthArt art;
    private EditTextBoldCursor input;
    private NebulaButton connect;
    private TextView status;
    private boolean busy;
    private boolean destroyed;

    @Override
    public View createView(Context context) {
        destroyed = false;
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);
        NebulaOnboardingLayout root = new NebulaOnboardingLayout(context);

        art = new NebulaAuthArt(NebulaAuthArt.KIND_LINK, theme.primary(), theme.onSurfaceVariant());
        ImageView image = new ImageView(context);
        image.setImageDrawable(art);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable badge = new GradientDrawable();
        badge.setColor(theme.primaryContainer());
        badge.setCornerRadius(AndroidUtilities.dp(32));
        image.setBackground(badge);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(104), AndroidUtilities.dp(104));
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        badgeParams.bottomMargin = AndroidUtilities.dp(24);
        root.content.addView(image, badgeParams);

        TextView title = NebulaIntroFragment.text(context, 30, theme.onSurface(), true);
        title.setGravity(Gravity.CENTER);
        title.setText(LocaleController.getString(R.string.NebulaAuthLinkTitle));
        root.content.addView(title, NebulaIntroFragment.width());
        TextView subtitle = NebulaIntroFragment.text(context, 15, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(LocaleController.getString(R.string.NebulaAuthLinkSubtitle));
        LinearLayout.LayoutParams subtitleParams = NebulaIntroFragment.width();
        subtitleParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(subtitle, subtitleParams);

        LinearLayout field = new LinearLayout(context);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(theme.surfaceContainer());
        surface.setCornerRadius(AndroidUtilities.dp(22));
        field.setBackground(surface);
        TextView label = NebulaIntroFragment.text(context, 13, theme.primary(), true);
        label.setText(LocaleController.getString(R.string.NebulaAuthLinkField));
        field.addView(label, NebulaIntroFragment.width());

        input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setCursorColor(theme.primary());
        input.setHint(LocaleController.getString(R.string.NebulaAuthLinkHint));
        input.setContentDescription(LocaleController.getString(R.string.NebulaAuthLinkField));
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setBackground(null);
        input.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(10));
        input.setMinimumHeight(AndroidUtilities.dp(48));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        field.addView(input, NebulaIntroFragment.width());
        LinearLayout.LayoutParams fieldParams = NebulaIntroFragment.width();
        fieldParams.topMargin = AndroidUtilities.dp(28);
        root.content.addView(field, fieldParams);

        TextView hint = NebulaIntroFragment.text(context, 12, theme.onSurfaceVariant(), false);
        hint.setText(LocaleController.getString(R.string.NebulaAuthLinkFormats));
        // Значок папки перед перечислением: строка длинная, и без якоря слева
        // она читается как продолжение подписи поля, а не как отдельная справка.
        android.graphics.drawable.Drawable folder =
                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.files_folder);
        if (folder != null) {
            folder.setColorFilter(theme.onSurfaceVariant(), android.graphics.PorterDuff.Mode.SRC_IN);
            int size = AndroidUtilities.dp(16);
            folder.setBounds(0, 0, size, size);
            hint.setCompoundDrawablesRelative(folder, null, null, null);
            hint.setCompoundDrawablePadding(AndroidUtilities.dp(8));
            hint.setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        LinearLayout.LayoutParams hintParams = NebulaIntroFragment.width();
        hintParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(hint, hintParams);
        status = NebulaIntroFragment.text(context, 14, theme.onSurfaceVariant(), false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = NebulaIntroFragment.width();
        statusParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(status, statusParams);

        connect = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        connect.setText(LocaleController.getString(R.string.NebulaConnect));
        connect.setOnClickListener(v -> submit());
        NebulaOnboardingLayout.action(connect);
        root.actions.addView(connect);
        NebulaButton skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaAuthDirectAction));
        skip.setOnClickListener(v -> openLogin());
        NebulaOnboardingLayout.action(skip);
        root.actions.addView(skip);

        TextView optional = NebulaIntroFragment.text(context, 12, theme.onSurfaceVariant(), false);
        optional.setGravity(Gravity.CENTER);
        optional.setText(LocaleController.getString(R.string.NebulaAuthLinkOptional));
        root.actions.addView(optional, NebulaIntroFragment.width());
        LinearLayout.LayoutParams connectionParams = NebulaIntroFragment.width();
        connectionParams.topMargin = AndroidUtilities.dp(14);
        root.actions.addView(new NebulaAuthStatus(context, false), connectionParams);
        root.actions.addView(NebulaProgress.build(context, 4, 1));
        fragmentView = root;
        return root;
    }

    private void submit() {
        if (busy || destroyed) return;
        String pasted = input.getText().toString().trim();
        if (pasted.isEmpty()) {
            openLogin();
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("input", pasted);
        } catch (JSONException e) { return; }
        busy = true;
        input.setEnabled(false);
        connect.setEnabled(false);
        connect.setText(LocaleController.getString(R.string.NebulaConnecting));
        show(LocaleController.getString(R.string.NebulaConnecting));
        NebulaLink.call("onboarding.connect", payload, result -> {
            if (destroyed) return;
            busy = false;
            input.setEnabled(true);
            connect.setEnabled(true);
            connect.setText(LocaleController.getString(R.string.NebulaConnect));
            if (result.ok) openLogin();
            else {
                show(result.error);
                status.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_text_RedRegular));
            }
        });
    }

    private void show(String message) {
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        status.setTextColor(NebulaTheme.of(getContext()).onSurfaceVariant());
    }

    private void openLogin() {
        if (destroyed) return;
        AndroidUtilities.hideKeyboard(input);
        // Без удаления предыдущего экрана: с параметром true стек оставался
        // пустым, и с ввода номера некуда было вернуться — ни кнопкой, ни
        // жестом от края. Приветствие остаётся позади, как и ожидается.
        presentFragment(new org.telegram.ui.LoginActivity());
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        if (art != null) art.detach();
        art = null;
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() { return !NebulaTheme.of(getContext()).isDark(); }
}
