package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import app.nebulagram.nebulalink.NebulaLink;

/** The current route, with attachment-scoped subscription and no network polling. */
public final class NebulaAuthStatus extends TextView {
    private final boolean telegramTheme;
    private final NebulaLink.StatusListener listener = this::render;

    public NebulaAuthStatus(Context context, boolean telegramTheme) {
        super(context);
        this.telegramTheme = telegramTheme;
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        setGravity(Gravity.CENTER);
        setMaxLines(2);
        setEllipsize(TextUtils.TruncateAt.END);
        setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        render(NebulaLink.status());
    }

    public void refreshColors() {
        render(NebulaLink.status());
    }

    private void render(JSONObject status) {
        String phase = status == null ? "disconnected" : status.optString("state");
        boolean connected = "connected".equals(phase) && NebulaLink.isRoutingThroughTunnel();
        boolean connecting = "connecting".equals(phase);
        // Плашка сообщает о туннеле. Когда его нет, сообщать нечего: строка
        // "Прямое подключение" выглядела кнопкой, которая никуда не ведёт, и
        // занимала место под настоящими действиями экрана.
        if (!connected && !connecting) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        NebulaTheme palette = NebulaTheme.of(getContext());
        int color = connected ? (telegramTheme
                ? (Theme.isCurrentThemeDark() ? 0xFF81D99A : 0xFF236C3D) : palette.success())
                : telegramTheme ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6) : palette.onSurfaceVariant();
        String text = connected ? LocaleController.getString(R.string.NebulaAuthRouteConnected)
                : connecting ? LocaleController.getString(R.string.NebulaConnecting)
                : LocaleController.getString(R.string.NebulaAuthRouteDirect);
        JSONObject server = status == null ? null : status.optJSONObject("server");
        if (connected && server != null) {
            text += " · " + NebulaLinkRow.serverLabel(server);
        }
        setText(text);
        setTextColor(color);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(NebulaTheme.stateLayer(color, connected ? 0.10f : 0.055f));
        shape.setCornerRadius(AndroidUtilities.dp(20));
        setBackground(shape);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NebulaLink.addStatusListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        NebulaLink.removeStatusListener(listener);
        super.onDetachedFromWindow();
    }
}
