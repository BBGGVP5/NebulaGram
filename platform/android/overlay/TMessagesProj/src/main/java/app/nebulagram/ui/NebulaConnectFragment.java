package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * The second step of the first run: paste a subscription and connect.
 *
 * <p>It comes before the phone number on purpose. On a censored network the
 * login code never arrives until something carries the traffic, so asking for a
 * server first is the difference between an app that can be signed into and one
 * that cannot.
 */
public class NebulaConnectFragment extends BaseFragment {

    private static final int STEPS = 4;
    private static final int STEP_INDEX = 1;

    private NebulaMark mark;
    private EditTextBoldCursor input;
    private NebulaButton connect;
    private TextView status;

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());

        // Как и на приветствии: содержимое по центру, действия внизу.
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(160));
        content.addView(buildMark(context, theme));
        content.addView(buildTitle(context, theme));
        content.addView(buildSubtitle(context, theme));
        content.addView(buildInput(context, theme));
        content.addView(buildHint(context, theme));
        content.addView(buildStatus(context, theme));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        actions.addView(buildConnect(context));
        actions.addView(buildSkip(context));
        actions.addView(NebulaProgress.build(context, STEPS, STEP_INDEX));

        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.gravity = Gravity.BOTTOM;
        root.addView(actions, actionParams);

        return root;
    }

    private View buildMark(Context context, NebulaTheme theme) {
        mark = new NebulaMark(theme.primary(), theme.primaryShade());
        android.widget.ImageView view = new android.widget.ImageView(context);
        view.setImageDrawable(mark);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                AndroidUtilities.dp(104), AndroidUtilities.dp(104));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = AndroidUtilities.dp(26);
        view.setLayoutParams(params);
        return view;
    }

    private View buildTitle(Context context, NebulaTheme theme) {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        title.setTextColor(theme.onSurface());
        title.setText(LocaleController.getString(R.string.NebulaConnectTitle));
        return title;
    }

    private View buildSubtitle(Context context, NebulaTheme theme) {
        TextView subtitle = new TextView(context);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        subtitle.setTextColor(theme.onSurfaceVariant());
        subtitle.setLineSpacing(AndroidUtilities.dp(3), 1f);
        subtitle.setText(LocaleController.getString(R.string.NebulaConnectSubtitle));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(10);
        subtitle.setLayoutParams(params);
        return subtitle;
    }

    private View buildInput(Context context, NebulaTheme theme) {
        input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setHint(LocaleController.getString(R.string.NebulaConnectHint));
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        GradientDrawable box = new GradientDrawable();
        box.setCornerRadius(NebulaTheme.cornerSmall());
        box.setColor(theme.surfaceContainer());
        box.setStroke(Math.max(1, AndroidUtilities.dp(1)), theme.outline());
        input.setBackground(box);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(20);
        input.setLayoutParams(params);
        return input;
    }

    /** Что принимает поле — иначе непонятно, откуда брать ссылку. */
    private View buildHint(Context context, NebulaTheme theme) {
        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(theme.onSurfaceVariant());
        hint.setText("Remnawave и обычные подписки · VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC");
        hint.setLineSpacing(AndroidUtilities.dp(2), 1f);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(12);
        params.leftMargin = AndroidUtilities.dp(4);
        hint.setLayoutParams(params);
        return hint;
    }

    private View buildStatus(Context context, NebulaTheme theme) {
        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        status.setTextColor(theme.onSurfaceVariant());
        status.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(10);
        status.setLayoutParams(params);
        return status;
    }

    private View buildConnect(Context context) {
        connect = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        connect.setText(LocaleController.getString(R.string.NebulaConnect));
        connect.setOnClickListener(v -> submit());
        connect.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(52)));
        return connect;
    }

    private View buildSkip(Context context) {
        NebulaButton skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaSkip));
        skip.setOnClickListener(v -> openLogin());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(44));
        params.topMargin = AndroidUtilities.dp(2);
        skip.setLayoutParams(params);
        return skip;
    }

    /** Hands whatever was pasted to the core, which works out what it is. */
    private void submit() {
        String pasted = input.getText().toString().trim();
        if (pasted.isEmpty()) {
            openLogin();
            return;
        }

        connect.setEnabled(false);
        connect.setText(LocaleController.getString(R.string.NebulaConnecting));
        show(LocaleController.getString(R.string.NebulaConnecting));

        JSONObject payload = new JSONObject();
        try {
            payload.put("input", pasted);
        } catch (JSONException e) {
            return;
        }
        NebulaLink.call("onboarding.connect", payload, result -> {
            connect.setEnabled(true);
            connect.setText(LocaleController.getString(R.string.NebulaConnect));
            if (result.ok) {
                openLogin();
            } else {
                show(result.error);
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), result.error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void show(String message) {
        if (status == null) {
            return;
        }
        status.setVisibility(View.VISIBLE);
        status.setText(message);
    }

    /**
     * Goes straight to Telegram's login. Its own intro carousel is skipped: the
     * welcome screen this flow started with already did that job.
     */
    private void openLogin() {
        presentFragment(new org.telegram.ui.LoginActivity(), true);
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
