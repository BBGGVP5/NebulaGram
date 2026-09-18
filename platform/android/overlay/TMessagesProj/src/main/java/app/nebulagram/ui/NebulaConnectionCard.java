package app.nebulagram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * The connection card: what state the tunnel is in, which server it is on, and
 * the one button that changes it.
 *
 * <p>It reads {@code tunnel.status} rather than keeping state of its own, so a
 * tunnel started from anywhere — a deep link, the first-run flow, a reconnect
 * after a failure — shows up here correctly.
 */
public class NebulaConnectionCard extends LinearLayout {

    private final NebulaTheme theme;
    private final BaseFragment host;

    private final ImageView badge;
    private final TextView state;
    private final TextView detail;
    private final NebulaButton action;

    private JSONObject lastStatus;
    private boolean connected;
    private boolean busy;
    private final GradientDrawable statusBackground = new GradientDrawable();
    private final NebulaLink.StatusListener statusListener = this::render;

    public NebulaConnectionCard(@NonNull Context context, BaseFragment host) {
        super(context);
        this.host = host;
        this.theme = NebulaTheme.of(context);

        setOrientation(VERTICAL);
        statusBackground.setOrientation(GradientDrawable.Orientation.TL_BR);
        statusBackground.setCornerRadius(AndroidUtilities.dp(16));
        setBackground(statusBackground);
        setClipToOutline(true);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        badge = new ImageView(context);
        badge.setImageResource(R.drawable.msg_secret);
        badge.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        badge.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        header.addView(badge, new LayoutParams(AndroidUtilities.dp(48), AndroidUtilities.dp(48)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);

        state = new TextView(context);
        state.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        state.setTypeface(AndroidUtilities.bold());
        labels.addView(state, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detail = new TextView(context);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        detail.setTextColor(theme.onSurfaceVariant());
        detail.setMaxLines(3);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        detail.setVisibility(GONE);
        labels.addView(detail, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams labelParams = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.setMarginStart(AndroidUtilities.dp(14));
        header.addView(labels, labelParams);
        addView(header, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        action = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        action.setOnClickListener(v -> toggle());
        action.setSingleLine(false);
        action.setMaxLines(2);
        LayoutParams actionParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = AndroidUtilities.dp(16);
        addView(action, actionParams);

        render(null);
        refresh();
    }

    /** Asks the core where the tunnel currently stands. */
    public void refresh() {
        NebulaLink.call("tunnel.status", null, result -> { if (result.ok) render(result.data); });
    }

    private void toggle() {
        if (busy) {
            return;
        }
        busy = true;
        action.setEnabled(false);
        action.setAlpha(.65f);
        action.setText(NebulaText.text("Подождите…", "Please wait…"));

        NebulaLink.call(connected ? "tunnel.stop" : "tunnel.start", null, result -> {
            busy = false;
            if (result.ok) {
                render(result.data);
            } else {
                render(lastStatus);
                detail.setVisibility(VISIBLE);
                // Errors can contain subscription credentials: never echo raw core output.
                detail.setText(NebulaText.text("Не удалось выполнить действие. Проверьте сервер и повторите.",
                        "Could not complete the action. Check the server and try again."));
            }
        });
    }

    private void render(JSONObject status) {
        lastStatus = status;
        String phase = status == null ? "disconnected" : status.optString("state", "disconnected");
        connected = "connected".equals(phase);

        int accent = connected ? theme.success() : theme.primary();
        statusBackground.setColors(new int[]{theme.surfaceContainer(),
                androidx.core.graphics.ColorUtils.blendARGB(theme.surfaceContainer(), accent, connected ? .10f : .02f)});
        statusBackground.setStroke(AndroidUtilities.dp(1), NebulaTheme.stateLayer(accent, .2f));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(NebulaTheme.stateLayer(accent, 0.16f));
        badge.setBackground(badgeBackground);
        badge.setColorFilter(accent, PorterDuff.Mode.SRC_IN);

        state.setTextColor(accent);
        state.setText(LocaleController.getString(connected
                ? R.string.NebulaConnected
                : "connecting".equals(phase) ? R.string.NebulaConnecting : R.string.NebulaDisconnected));

        // The server line only means something while a tunnel is up; when it is
        // down, the name of a server we are not using would just be noise.
        JSONObject server = status == null ? null : status.optJSONObject("server");
        if (connected && server != null) {
            detail.setVisibility(VISIBLE);
            int latency = server.optInt("latency_ms");
            String name = NebulaLinkRow.serverLabel(server);
            detail.setTextColor(theme.success());
            detail.setText(NebulaLatency.isMeasured(latency, server.optLong("checked_at"))
                    ? name + " · " + NebulaLatency.format(latency, server.optString("latency_method"),
                            server.optLong("checked_at"), LocaleController.getString(R.string.NebulaMs), "", "") : name);
        } else {
            detail.setText("");
            detail.setTextColor(theme.onSurfaceVariant());
            detail.setVisibility(GONE);
        }

        action.setEnabled(!busy);
        action.setAlpha(busy ? .65f : 1f);
        action.setText(busy ? NebulaText.text("Подождите…", "Please wait…") : LocaleController.getString(connected
                ? R.string.NebulaDisconnect : R.string.NebulaConnect));
    }

    /** Lets the host screen refresh the card after a change elsewhere. */
    public BaseFragment host() {
        return host;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NebulaLink.addStatusListener(statusListener);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        NebulaLink.removeStatusListener(statusListener);
        super.onDetachedFromWindow();
    }
}
