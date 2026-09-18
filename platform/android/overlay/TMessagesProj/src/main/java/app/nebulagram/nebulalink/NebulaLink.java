package app.nebulagram.nebulalink;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

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
import java.util.ArrayList;

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

    /** The core listens on loopback only; it is not exposed to the local network. */
    public static final String PROXY_ADDRESS = "127.0.0.1";

    private static final NebulaCommandQueue EXECUTOR = new NebulaCommandQueue();

    private static volatile boolean initialised;
    private static SharedConfig.ProxyInfo installedProxy;
    private static JSONObject tunnelStatus;
    private static final ArrayList<StatusListener> statusListeners = new ArrayList<>();

    /** Status and listeners are accessed on the main thread. */
    public interface StatusListener {
        void onStatus(JSONObject status);
    }

    public interface ProbeListener { void onProgress(JSONObject progress); }
    private static final ArrayList<ProbeListener> probeListeners = new ArrayList<>();
    public static void addProbeListener(ProbeListener listener) {
        if (!probeListeners.contains(listener)) probeListeners.add(listener);
    }
    public static void removeProbeListener(ProbeListener listener) { probeListeners.remove(listener); }

    public static JSONObject status() {
        return tunnelStatus;
    }

    public static void addStatusListener(StatusListener listener) {
        if (!statusListeners.contains(listener)) {
            statusListeners.add(listener);
        }
        listener.onStatus(tunnelStatus);
    }

    public static void removeStatusListener(StatusListener listener) {
        statusListeners.remove(listener);
    }

    /**
     * Наш ли это прокси.
     *
     * <p>По ссылке на статику судить нельзя: после перезапуска процесса она
     * пуста, а запись в списке и включённый прокси остаются с прошлого раза.
     * Записанный порт тоже не опора — он пропадает ровно в том случае, ради
     * которого проверка и заведена: снятие туннеля стирает его последним, и
     * повторный проход уже не узнавал собственную запись.
     *
     * <p>Адрес обратной петли без логина и пароля считаем своим: чужой прокси
     * живёт на другой машине, а локальный SOCKS без нашего ядра всё равно мёртв.
     */
    public static boolean isTunnelProxy(SharedConfig.ProxyInfo proxy) {
        return proxy != null && (proxy == installedProxy
                || isTunnelEndpoint(proxy.address, proxy.username, proxy.password, proxy.secret));
    }

    /** Тот же признак для сырых значений из настроек Telegram. */
    private static boolean isTunnelEndpoint(String address, String user, String password, String secret) {
        return isLoopback(address) && isEmpty(user) && isEmpty(password) && isEmpty(secret);
    }

    private static boolean isLoopback(String address) {
        return PROXY_ADDRESS.equals(address) || "localhost".equals(address) || "::1".equals(address);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    public static boolean isRoutingThroughTunnel() {
        return isTunnelProxy(SharedConfig.currentProxy)
                && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
    }

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

        // The application hook precedes Telegram's handler/native initialization.
        // Enqueue after onCreate returns so proxy cleanup and status events cannot be dropped.
        new Handler(context.getMainLooper()).post(() -> {
            // Withdraw the recorded dead endpoint before core.init, even if the Go core fails.
            // Otherwise a disabled tunnel can leave Telegram reconnecting to a closed local port.
            SharedPreferences saved = context.getSharedPreferences(PREFS, 0);
            clearPreviousProxy(saved.getInt(KEY_PROXY_PORT, 0));
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
                JSONObject settings = result.data == null ? null : result.data.optJSONObject("settings");
                SharedPreferences preferences = context.getSharedPreferences(PREFS, 0);
                // Older builds remembered the last connection instead of an explicit choice.
                boolean previouslyConnected = preferences.getBoolean(KEY_WAS_CONNECTED, false);
                int previousPort = preferences.getInt(KEY_PROXY_PORT,
                        previouslyConnected && settings != null ? settings.optInt("socks_port") : 0);
                if (previousPort > 0) AndroidUtilities.runOnUIThread(() -> clearPreviousProxy(previousPort));
                if (settings != null && preferences.contains(KEY_WAS_CONNECTED)) {
                    JSONObject migration = new JSONObject();
                    migration.put("auto_connect", previouslyConnected);
                    Result migrated = callBlocking("settings.set", migration);
                    if (migrated.ok && migrated.data != null) {
                        settings = migrated.data;
                        preferences.edit().remove(KEY_WAS_CONNECTED).apply();
                    }
                }
                Nebulalink.setEventSink(new EventSink() {
                    @Override
                    public void onEvent(String json) {
                        handleEvent(json);
                    }
                });
                if (settings != null && settings.optBoolean("auto_connect")
                        && !settings.optString("selected_server_id").isEmpty()) {
                    Result started = callBlocking("tunnel.start", null);
                    if (!started.ok) {
                        FileLog.e("NebulaLink: automatic connection failed: " + started.error);
                    }
                }
            } catch (JSONException e) {
                FileLog.e(e);
            }
            });
        });
    }

    /** Runs a core method and delivers the result on the main thread. */
    public static void call(final String method, final JSONObject payload, final Callback callback) {
        EXECUTOR.execute(method, () -> {
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

    private static final String PREFS = "nebulagram";
    private static final String KEY_WAS_CONNECTED = "tunnel_was_connected";
    private static final String KEY_PROXY_PORT = "tunnel_proxy_port";
    private static final String KEY_ROUTE_CALLS = "tunnel_route_calls";

    /** A killed process leaves its SOCKS entry behind, even when startup is disabled. */
    private static void clearPreviousProxy(int port) {
        if (port > 0) {
            SharedConfig.loadProxyList();
            for (SharedConfig.ProxyInfo proxy : new ArrayList<>(SharedConfig.proxyList)) {
                if (isTunnelProxy(proxy)) {
                    SharedConfig.deleteProxy(proxy);
                }
            }
            // Also handle a missing/stale proxy-list entry before forgetting the recorded port.
            disableStaleProxy();
        }
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().remove(KEY_PROXY_PORT).apply();
    }

    /** Version of the linked core, for the About screen. */
    public static String version() {
        return Nebulalink.version();
    }

    private static void handleEvent(String json) {
        try {
            JSONObject envelope = new JSONObject(json);
            if ("probe.progress".equals(envelope.optString("event"))) {
                JSONObject progress = envelope.optJSONObject("data");
                if (progress != null) AndroidUtilities.runOnUIThread(() -> {
                    for (ProbeListener listener : new ArrayList<>(probeListeners)) listener.onProgress(progress);
                });
                return;
            }
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
                tunnelStatus = status;
                if ("connected".equals(state) && socksPort > 0) {
                    useTunnelAsProxy(socksPort);
                } else if ("disconnected".equals(state) || "failed".equals(state)) {
                    stopUsingTunnel();
                }
                for (StatusListener listener : new ArrayList<>(statusListeners)) {
                    listener.onStatus(status);
                }
                // Об изменении прокси сообщают те две ветки выше, и только
                // когда он действительно изменился. Безусловное извещение
                // заставляло Telegram пересобирать сессии на каждое событие
                // туннеля, включая «подключаюсь», которое ничего не меняет.
            });
        } catch (JSONException e) {
            // Ignore malformed events without logging their payload.
        }
    }

    /**
     * Points Telegram at the running tunnel, following the same steps the
     * built-in proxy screen takes, so the app sees an ordinary SOCKS5 proxy.
     */
    public static void useTunnelAsProxy(int socksPort) {
        // Повторная установка того же прокси рвёт соединения: Telegram на
        // setProxySettings поднимает сессии заново. Событие «подключено»
        // приходит не только при первом подключении, и каждый раз это стоило
        // пользователю обрыва — отсюда и вечное «Соединение».
        if (installedProxy != null && installedProxy.port == socksPort && isRoutingThroughTunnel()) {
            return;
        }
        SharedConfig.ProxyInfo proxy = SharedConfig.addProxy(
                new SharedConfig.ProxyInfo(PROXY_ADDRESS, socksPort, "", "", ""));
        if (installedProxy != null && installedProxy != proxy) {
            stopUsingTunnel();
        }
        SharedConfig.currentProxy = proxy;
        installedProxy = proxy;
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().putInt(KEY_PROXY_PORT, socksPort).apply();

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
        // Прокси снова существует — только теперь выбор «звонки через NebulaLink»
        // снова что-то значит для Telegram.
        setCallsThroughTunnel(callsThroughTunnel());
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    /**
     * Stops routing through the tunnel. A proxy the user configured themselves
     * is left alone: only the entry we installed is withdrawn.
     */
    public static void stopUsingTunnel() {
        // deleteProxy also clears the saved endpoint when this is the current
        // proxy. Clearing currentProxy first would resurrect it on the next launch.
        if (installedProxy != null) {
            SharedConfig.deleteProxy(installedProxy);
            installedProxy = null;
        }
        // Проходим по списку в любом случае, а не только когда ссылки нет:
        // Telegram мог пересобрать список сам, и тогда наш объект в нём уже
        // не тот, что мы держали, — запись оставалась и проксировала в пустоту.
        SharedConfig.loadProxyList();
        for (SharedConfig.ProxyInfo proxy : new ArrayList<>(SharedConfig.proxyList)) {
            if (isTunnelProxy(proxy)) {
                SharedConfig.deleteProxy(proxy);
            }
        }
        disableStaleProxy();
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().remove(KEY_PROXY_PORT).apply();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
    }

    /**
     * Последняя проверка после снятия: настройки Telegram не должны остаться
     * указывающими на локальный порт, которого больше нет.
     *
     * <p>Здесь и ломалось соединение после выключения NebulaLink.
     * {@code deleteProxy} снимает настройки, только если удаляемая запись —
     * текущая; во всех прочих случаях {@code proxy_enabled} оставался
     * включённым с адресом 127.0.0.1, и клиент бесконечно стучался в мёртвый
     * порт. Отсюда «не работает и без него».
     */
    private static void disableStaleProxy() {
        SharedPreferences settings = MessagesController.getGlobalMainSettings();
        if (!isTunnelEndpoint(settings.getString("proxy_ip", ""), settings.getString("proxy_user", ""),
                settings.getString("proxy_pass", ""), settings.getString("proxy_secret", ""))) {
            return;
        }
        if (SharedConfig.currentProxy != null && isTunnelProxy(SharedConfig.currentProxy)) {
            SharedConfig.currentProxy = null;
        }
        settings.edit().putBoolean("proxy_enabled", false).putBoolean("proxy_enabled_calls", false)
                .putString("proxy_ip", "").putString("proxy_user", "").putString("proxy_pass", "")
                .putString("proxy_secret", "").putInt("proxy_port", 1080).apply();
        ConnectionsManager.setProxySettings(false, "", 0, "", "", "");
    }

    /**
     * Sends call media through the tunnel as well. Telegram's own VoIP service
     * reads this preference, so this is a setting rather than a code change.
     */
    public static void setCallsThroughTunnel(boolean enabled) {
        // Выбор пользователя живёт у нас, а в настройках Telegram он стоит,
        // только пока прокси действительно есть. Раньше он возвращался туда и
        // после снятия туннеля: клиент считал, что звонки идут через прокси,
        // которого уже нет.
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .edit().putBoolean(KEY_ROUTE_CALLS, enabled).apply();
        MessagesController.getGlobalMainSettings().edit()
                .putBoolean("proxy_enabled_calls", enabled && isRoutingThroughTunnel()).commit();
    }

    /** Whether call media currently goes through the tunnel. */
    public static boolean callsThroughTunnel() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0)
                .getBoolean(KEY_ROUTE_CALLS, MessagesController.getGlobalMainSettings()
                        .getBoolean("proxy_enabled_calls", false));
    }

    /** Применяются ли звонки через туннель прямо сейчас, а не только выбраны. */
    public static boolean callsRoutedNow() {
        return isRoutingThroughTunnel()
                && MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled_calls", false);
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
