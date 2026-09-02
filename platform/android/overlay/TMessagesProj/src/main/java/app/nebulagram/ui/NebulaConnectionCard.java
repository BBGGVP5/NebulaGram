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

    private boolean connected;
    private boolean busy;

    public NebulaConnectionCard(@NonNull Context context, BaseFragment host) {
        super(context);
        this.host = host;
        this.theme = NebulaTheme.of(context);

        setOrientation(VERTICAL);
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
        state.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        state.setTypeface(AndroidUtilities.bold());
        labels.addView(state, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        detail = new TextView(context);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        detail.setTextColor(theme.onSurfaceVariant());
        detail.setVisibility(GONE);
        labels.addView(detail, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LayoutParams labelParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = AndroidUtilities.dp(14);
        header.addView(labels, labelParams);
        addView(header, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        action = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        action.setOnClickListener(v -> toggle());
        LayoutParams actionParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(52));
        actionParams.topMargin = AndroidUtilities.dp(16);
        addView(action, actionParams);

        render(null);
        refresh();
    }

    /** Asks the core where the tunnel currently stands. */
    public void refresh() {
        NebulaLink.call("tunnel.status", null, result -> render(result.ok ? result.data : null));
    }

    private void toggle() {
        if (busy) {
            return;
        }
        busy = true;
        action.setText(LocaleController.getString(R.string.NebulaConnecting));

        NebulaLink.call(connected ? "tunnel.stop" : "tunnel.start", null, result -> {
            busy = false;
            if (result.ok) {
                render(result.data);
            } else {
                render(null);
                detail.setVisibility(VISIBLE);
                detail.setText(result.error);
            }
        });
    }

    private void render(JSONObject status) {
        String phase = status == null ? "disconnected" : status.optString("state", "disconnected");
        connected = "connected".equals(phase);

        int accent = connected ? theme.primary() : theme.onSurfaceVariant();
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setShape(GradientDrawable.OVAL);
        badgeBackground.setColor(NebulaTheme.stateLayer(accent, 0.16f));
        badge.setBackground(badgeBackground);
        badge.setColorFilter(accent, PorterDuff.Mode.SRC_IN);

        state.setTextColor(connected ? theme.onSurface() : theme.onSurfaceVariant());
        state.setText(LocaleController.getString(connected
                ? R.string.NebulaConnected
                : "connecting".equals(phase) ? R.string.NebulaConnecting : R.string.NebulaDisconnected));

        // The server line only means something while a tunnel is up; when it is
        // down, the name of a server we are not using would just be noise.
        JSONObject server = status == null ? null : status.optJSONObject("server");
        if (connected && server != null) {
            detail.setVisibility(VISIBLE);
            int latency = server.optInt("latency_ms");
            String name = server.optString("name");
            detail.setText(latency > 0 ? name + " · " + latency + " ms" : name);
        } else if (detail.getText().length() == 0) {
            detail.setVisibility(GONE);
        }

        action.setText(LocaleController.getString(connected
                ? R.string.NebulaDisconnect : R.string.NebulaConnect));
    }

    /** Lets the host screen refresh the card after a change elsewhere. */
    public BaseFragment host() {
        return host;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }
}
