package app.nebulagram.nebulalink;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nebulalink.EventSink;
import nebulalink.Nebulalink;

/**
 * The Android side of NebulaLink.
 *
 * <p>Everything that touches the tunnel goes through the Go core: this class
 * only marshals JSON, keeps calls off the main thread, and points Telegram's
 * own proxy settings at the local SOCKS5 endpoint the core opens. Routing the
 * messenger that way is deliberate — Telegram already speaks SOCKS5, so the
 * network stack needs no patching at all, which is the usual reason forks break
 * on a new upstream release.
 */
public final class NebulaLink {

    /** The core listens on loopback only; nothing else on the device can reach it. */
    public static final String PROXY_ADDRESS = "127.0.0.1";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static volatile boolean initialised;
    private static SharedConfig.ProxyInfo installedProxy;

    private NebulaLink() {
    }

    /**
     * Result of one core call. Some methods answer with an object and some with
     * an array — menu.get returns the screens as a list — so both are carried
     * and the caller reads whichever it expects.
     */
    public static final class Result {
        public final boolean ok;
        public final String error;
        public final JSONObject data;
        public final JSONArray array;

        Result(boolean ok, String error, JSONObject data, JSONArray array) {
            this.ok = ok;
            this.error = error;
            this.data = data;
            this.array = array;
        }

        static Result failure(String message) {
            return new Result(false, message, null, null);
        }
    }

    /** Receives a call result on the main thread. */
    public interface Callback {
        void onResult(Result result);
    }

    /**
     * Brings the core up. Safe to call more than once; later calls do nothing.
     * Runs off the main thread because the first call opens the state file.
     */
    public static void init(final Context context) {
        if (initialised) {
            return;
        }
        initialised = true;
        followSystemPalette(context);
        final File directory = new File(context.getFilesDir(), "nebulalink");

        EXECUTOR.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("dir", directory.getAbsolutePath());
                payload.put("os", "Android");
                payload.put("os_version", Build.VERSION.RELEASE);
                payload.put("model", Build.MANUFACTURER + " " + Build.MODEL);
                payload.put("user_agent", "NebulaGram/Android");

                Result result = callBlocking("core.init", payload);
                if (!result.ok) {
                    FileLog.e("NebulaLink: core.init failed: " + result.error);
                    return;
                }
                Nebulalink.setEventSink(new EventSink() {
                    @Override
                    public void onEvent(String json) {
                        handleEvent(json);
                    }
                });
            } catch (JSONException e) {
                FileLog.e(e);
            }
        });
    }

    /** Runs a core method and delivers the result on the main thread. */
    public static void call(final String method, final JSONObject payload, final Callback callback) {
        EXECUTOR.execute(() -> {
            final Result result = callBlocking(method, payload);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
            }
        });
    }

    /**
     * Runs a core method on the calling thread. Only call this from a
     * background thread: a subscription refresh talks to the network.
     */
    public static Result callBlocking(String method, JSONObject payload) {
        try {
            String response = Nebulalink.call(method, payload == null ? "" : payload.toString());
            JSONObject envelope = new JSONObject(response);
            if (!envelope.optBoolean("ok", false)) {
                return Result.failure(envelope.optString("error", "unknown error"));
            }
            Object body = envelope.opt("data");
            return new Result(true, null,
                    body instanceof JSONObject ? (JSONObject) body : null,
                    body instanceof JSONArray ? (JSONArray) body : null);
        } catch (Throwable e) {
            FileLog.e(e);
            return Result.failure(String.valueOf(e.getMessage()));
        }
    }

    /** Version of the linked core, for the About screen. */
    public static String version() {
        return Nebulalink.version();
    }

    private static void handleEvent(String json) {
        try {
            JSONObject envelope = new JSONObject(json);
            if (!"tunnel.status".equals(envelope.optString("event"))) {
                return;
            }
            JSONObject status = envelope.optJSONObject("data");
            if (status == null) {
                return;
            }
            final String state = status.optString("state");
            final int socksPort = status.optInt("socks_port");
            AndroidUtilities.runOnUIThread(() -> {
                if ("connected".equals(state) && socksPort > 0) {
                    useTunnelAsProxy(socksPort);
                } else if ("disconnected".equals(state) || "failed".equals(state)) {
                    stopUsingTunnel();
                }
            });
        } catch (JSONException e) {
            FileLog.e(e);
        }
    }

    /**
     * Points Telegram at the running tunnel, following the same steps the
     * built-in proxy screen takes, so the app sees an ordinary SOCKS5 proxy.
     */
    public static void useTunnelAsProxy(int socksPort) {
        SharedConfig.ProxyInfo proxy = new SharedConfig.ProxyInfo(PROXY_ADDRESS, socksPort, "", "", "");
        SharedConfig.addProxy(proxy);
        SharedConfig.currentProxy = proxy;
        installedProxy = proxy;

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("proxy_enabled", true);
        editor.putString("proxy_ip", proxy.address);
        editor.putInt("proxy_port", proxy.port);
        editor.putString("proxy_user", proxy.username);
        editor.putString("proxy_pass", proxy.password);
        editor.putString("proxy_secret", proxy.secret);
        editor.commit();

        ConnectionsManager.setProxySettings(true, proxy.address, proxy.port, proxy.username, proxy.password, proxy.secret);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    /**
     * Stops routing through the tunnel. A proxy the user configured themselves
     * is left alone: only the entry we installed is withdrawn.
     */
    public static void stopUsingTunnel() {
        if (installedProxy == null) {
            return;
        }
        if (SharedConfig.currentProxy == installedProxy) {
            SharedConfig.currentProxy = null;
            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("proxy_enabled", false);
            editor.putBoolean("proxy_enabled_calls", false);
            editor.commit();
            ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        }
        SharedConfig.deleteProxy(installedProxy);
        installedProxy = null;
    }

    /**
     * Sends call media through the tunnel as well. Telegram's own VoIP service
     * reads this preference, so this is a setting rather than a code change.
     */
    public static void setCallsThroughTunnel(boolean enabled) {
        MessagesController.getGlobalMainSettings().edit().putBoolean("proxy_enabled_calls", enabled).commit();
    }

    /** Whether call media currently goes through the tunnel. */
    public static boolean callsThroughTunnel() {
        return MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled_calls", false);
    }

    /**
     * Applies the wallpaper palette once the first screen appears. It cannot be
     * done from Application.onCreate: Telegram's theme is not loaded yet at
     * that point, and there would be nothing to recolour.
     */
    private static void followSystemPalette(Context context) {
        if (!(context instanceof Application)) {
            return;
        }
        ((Application) context).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                // On resume rather than on create: the wallpaper, and therefore
                // the palette, can change while the app sits in the background.
                app.nebulagram.ui.NebulaTheme.applyMaterialYou(activity);
                watchThemeChanges();
            }

            @Override
            public void onActivityCreated(Activity activity, Bundle state) {
                // Раньше красили только при возврате к экрану, и запуск из
                // уведомления успевал показать чужие цвета.
                app.nebulagram.ui.NebulaTheme.applyMaterialYou(activity);
                watchThemeChanges();
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
    }

    private static boolean watchingTheme;

    /**
     * Пересобирать палитру приходится не только при появлении экрана: Telegram
     * применяет свою тему при старте и при смене день/ночь, и наш акцент при
     * этом затирается. Отсюда и жалоба на "иногда старые цвета Telegram".
     */
    private static void watchThemeChanges() {
        if (watchingTheme) {
            return;
        }
        watchingTheme = true;

        NotificationCenter.NotificationCenterDelegate watcher = (id, account, args) -> {
            // applyMaterialYou выходит сразу, если акцент уже наш, поэтому
            // повторный вызов из-за собственного уведомления не зациклится.
            app.nebulagram.ui.NebulaTheme.applyMaterialYou(ApplicationLoader.applicationContext);
        };
        NotificationCenter center = NotificationCenter.getGlobalInstance();
        center.addObserver(watcher, NotificationCenter.didSetNewTheme);
        center.addObserver(watcher, NotificationCenter.needSetDayNightTheme);
    }
}
