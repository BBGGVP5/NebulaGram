package app.nebulagram.ui;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

import app.nebulagram.nebulalink.NebulaLink;

/** A latency batch belongs to the process, not to the server-list screen. Main-thread only. */
public final class NebulaProbeSession {
    public interface Listener { void onChange(JSONObject progress); }

    private static final ArrayList<Listener> listeners = new ArrayList<>();
    private static final LinkedHashSet<String> pending = new LinkedHashSet<>();
    private static final NebulaLink.ProbeListener progressListener = NebulaProbeSession::onProgress;
    private static String requestId;
    private static int completed;
    private static int total;
    private static boolean cancelling;
    private static boolean failed;

    private NebulaProbeSession() { }

    public static boolean active() { return requestId != null; }
    public static boolean cancelling() { return cancelling; }
    public static boolean failed() { return failed; }
    public static int completed() { return completed; }
    public static int total() { return total; }
    public static Collection<String> pendingIds() { return new ArrayList<>(pending); }

    public static void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static void removeListener(Listener listener) { listeners.remove(listener); }

    public static boolean start(JSONArray ids) {
        if (active() || ids == null || ids.length() == 0) return false;
        String id = UUID.randomUUID().toString();
        JSONObject payload = new JSONObject();
        try {
            payload.put("ids", ids);
            payload.put("timeout", 5);
            payload.put("request_id", id);
        } catch (JSONException e) { return false; }

        requestId = id;
        completed = 0;
        total = ids.length();
        cancelling = false;
        failed = false;
        pending.clear();
        for (int i = 0; i < ids.length(); i++) pending.add(ids.optString(i));
        NebulaLink.addProbeListener(progressListener);
        notifyListeners(null);
        NebulaLink.call("probe.servers", payload, result -> {
            if (!id.equals(requestId)) return;
            failed = !result.ok && !cancelling;
            requestId = null;
            cancelling = false;
            pending.clear();
            NebulaLink.removeProbeListener(progressListener);
            notifyListeners(null);
        });
        return true;
    }

    public static void cancel() {
        if (!active() || cancelling) return;
        JSONObject payload = new JSONObject();
        try { payload.put("request_id", requestId); }
        catch (JSONException e) { return; }
        cancelling = true;
        notifyListeners(null);
        NebulaLink.call("probe.cancel", payload, null);
    }

    private static void onProgress(JSONObject progress) {
        if (!active() || !requestId.equals(progress.optString("request_id"))) return;
        completed = progress.optInt("completed", completed);
        total = progress.optInt("total", total);
        if (progress.optBoolean("cancelled")) cancelling = true;
        String serverId = progress.optString("id");
        if (!serverId.isEmpty() && progress.has("latency_ms")) pending.remove(serverId);
        notifyListeners(progress);
    }

    private static void notifyListeners(JSONObject progress) {
        for (Listener listener : new ArrayList<>(listeners)) listener.onChange(progress);
    }
}
