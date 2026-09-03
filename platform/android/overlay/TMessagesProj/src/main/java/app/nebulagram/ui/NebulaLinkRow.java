package app.nebulagram.ui;

import android.content.Context;

import org.json.JSONObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import app.nebulagram.nebulalink.NebulaLink;

/** The user-facing connection entry, without exposing the local SOCKS endpoint. */
public final class NebulaLinkRow extends NebulaRow {
    private final NebulaLink.StatusListener listener = status -> refresh();

    public NebulaLinkRow(Context context) {
        super(context);
        icon(R.drawable.msg_secret);
        title(LocaleController.getString(R.string.NebulaLinkName));
        trailing(TRAIL_CHEVRON);
        refresh();
    }

    public void refresh() {
        JSONObject status = NebulaLink.status();
        String phase = status == null ? "disconnected" : status.optString("state");
        boolean connected = "connected".equals(phase) && NebulaLink.isRoutingThroughTunnel();
        String text = LocaleController.getString(connected ? R.string.NebulaConnected
                : "connecting".equals(phase) ? R.string.NebulaConnecting : R.string.NebulaDisconnected);
        JSONObject server = status == null ? null : status.optJSONObject("server");
        if (connected && server != null) {
            text += " · " + serverLabel(server);
        }
        subtitle(text, false);
        connected(connected);
    }

    public static String serverLabel(JSONObject server) {
        String name = server.optString("name", "").trim();
        if (name.isEmpty()) {
            name = server.optString("address");
        }
        String flag = server.optString("flag", "").trim();
        return flag.isEmpty() || name.startsWith(flag) ? name : flag + "  " + name;
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
