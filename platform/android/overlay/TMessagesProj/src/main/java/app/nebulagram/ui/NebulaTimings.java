package app.nebulagram.ui;

import android.os.SystemClock;
import android.view.Choreographer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Тайминги загрузки для журнала Telegram. Пишет только при включённом журнале
 * (отладочное меню → «Включить журнал») и ничего не делает без него.
 *
 * <p>Зачем: «бывает быстро, а бывает ждать» на устройстве пользователя нельзя
 * воспроизвести без замеров. Строки с префиксом {@code NebulaTimings:} отвечают на
 * вопросы, где ушло время: старт процесса и наши инициализаторы; сколько длились
 * «Соединение…» и «Обновление…»; сколько ждала очередь базы (её занимают и
 * Telegram, и наши архивы); были ли зависания UI-потока; через сколько после
 * открытия чата пришли сообщения из кэша и с сервера. Текстов и id чатов нет.
 */
public final class NebulaTimings {
    private NebulaTimings() { }

    private static final String TAG = "NebulaTimings: ";
    private static final long SLOW_STEP_MS = 15, SLOW_QUEUE_MS = 250, SLOW_FRAME_MS = 700, SLOW_STATE_MS = 1500;

    private static long processStart, lastMark;
    // FileLog ещё не готов в Application.onCreate: шаги копим и пишем при первом показе.
    private static final ArrayList<String> pending = new ArrayList<>();
    private static boolean watching, framesRunning;
    private static long resumedAt;
    private static final Map<Integer, int[]> lastState = new HashMap<>();
    private static final Map<Integer, Long> stateSince = new HashMap<>();
    private static final Set<Integer> firstDialogs = new HashSet<>();
    private static final Map<Integer, long[]> chats = new HashMap<>(); // classGuid → {openedAt, cacheAt, serverAt}

    /** Первая строка Application.onCreate. */
    public static void processStart() {
        processStart = lastMark = SystemClock.elapsedRealtime();
    }

    /** Время с прошлой отметки; в журнал попадает только заметное. */
    public static void step(String what) {
        long now = SystemClock.elapsedRealtime(), spent = now - lastMark;
        lastMark = now;
        if (spent >= SLOW_STEP_MS) synchronized (pending) { pending.add(what + " " + spent + "ms"); }
    }

    private static boolean on() {
        return BuildVars.LOGS_ENABLED;
    }

    private static long sinceStart() {
        return processStart == 0 ? -1 : SystemClock.elapsedRealtime() - processStart;
    }

    private static void log(String message) {
        FileLog.d(TAG + "+" + sinceStart() + "ms " + message);
    }

    /** LaunchActivity.onResume: каждый показ приложения, в том числе из фона. */
    public static void onResume() {
        if (!on()) return;
        resumedAt = SystemClock.elapsedRealtime();
        synchronized (pending) {
            for (String step : pending) log("startup " + step);
            pending.clear();
        }
        if (!watching) {
            watching = true;
            log("first resume");
        } else {
            log("resume");
        }
        if (!framesRunning) watchFrames();
        for (int account : UserConfig.getAccountIds()) {
            if (!UserConfig.getInstance(account).isClientActivated()) continue;
            watchAccount(account);
            probeStorage(account);
        }
    }

    private static final Set<Integer> watchedAccounts = new HashSet<>();

    private static void watchAccount(int account) {
        if (!watchedAccounts.add(account)) return;
        NotificationCenter center = NotificationCenter.getInstance(account);
        center.addObserver((id, acc, args) -> connectionChanged(account), NotificationCenter.didUpdateConnectionState);
        center.addObserver((id, acc, args) -> {
            if (firstDialogs.add(account)) log("account " + account + " first dialogs list " + sinceResume());
        }, NotificationCenter.dialogsNeedReload);
        center.addObserver((id, acc, args) -> messagesLoaded(args), NotificationCenter.messagesDidLoad);
        connectionChanged(account);
    }

    private static String sinceResume() {
        return "(" + (SystemClock.elapsedRealtime() - resumedAt) + "ms after resume)";
    }

    private static String stateName(int state) {
        switch (state) {
            case ConnectionsManager.ConnectionStateConnecting: return "connecting";
            case ConnectionsManager.ConnectionStateWaitingForNetwork: return "waiting-for-network";
            case ConnectionsManager.ConnectionStateConnected: return "connected";
            case ConnectionsManager.ConnectionStateConnectingToProxy: return "connecting-to-proxy";
            case ConnectionsManager.ConnectionStateUpdating: return "updating";
            default: return "state-" + state;
        }
    }

    private static void connectionChanged(int account) {
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        int[] previous = lastState.get(account);
        long now = SystemClock.elapsedRealtime();
        if (previous != null && previous[0] == state) return;
        Long since = stateSince.get(account);
        if (previous != null && since != null) {
            long spent = now - since;
            // Каждый переход подробно, если прежнее состояние было не «подключено»:
            // именно они видны пользователю как «Соединение…» и «Обновление…».
            if (previous[0] != ConnectionsManager.ConnectionStateConnected || spent >= SLOW_STATE_MS) {
                log("account " + account + " " + stateName(previous[0]) + " " + spent + "ms -> " + stateName(state) + " " + sinceResume());
            }
        } else {
            log("account " + account + " starts " + stateName(state) + " " + sinceResume());
        }
        lastState.put(account, new int[]{state});
        stateSince.put(account, now);
    }

    /** Сколько задача ждала своей очереди в базе аккаунта. */
    private static void probeStorage(int account) {
        final long posted = SystemClock.elapsedRealtime();
        MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
            long waited = SystemClock.elapsedRealtime() - posted;
            if (waited >= SLOW_QUEUE_MS) AndroidUtilities.runOnUIThread(() -> log("account " + account + " storage queue busy " + waited + "ms"));
        });
    }

    /** ChatActivity.onFragmentCreate. */
    public static void chatOpened(int account, int classGuid) {
        if (!on()) return;
        watchAccount(account);
        chats.put(classGuid, new long[]{SystemClock.elapsedRealtime(), 0, 0});
        probeStorage(account);
    }

    private static void messagesLoaded(Object[] args) {
        if (args == null || args.length < 11 || !(args[3] instanceof Boolean) || !(args[10] instanceof Integer)) return;
        long[] chat = chats.get((Integer) args[10]);
        if (chat == null) return;
        boolean cache = (Boolean) args[3];
        int slot = cache ? 1 : 2;
        if (chat[slot] != 0) return;
        chat[slot] = SystemClock.elapsedRealtime();
        int count = args[1] instanceof Integer ? (Integer) args[1] : -1;
        log("chat messages from " + (cache ? "cache" : "server") + " " + (chat[slot] - chat[0]) + "ms after open, count=" + count);
        if (chat[1] != 0 && chat[2] != 0) chats.remove((Integer) args[10]);
        if (chats.size() > 32) chats.clear();
    }

    private static void watchFrames() {
        framesRunning = true;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            long last;
            @Override public void doFrame(long frameTimeNanos) {
                long now = SystemClock.elapsedRealtime();
                // В фоне сторож останавливается (кадры там не нужны и стоят
                // батареи) и запускается снова при следующем показе.
                if (org.telegram.messenger.ApplicationLoader.mainInterfacePaused || !on()) { framesRunning = false; return; }
                if (last != 0 && now - last >= SLOW_FRAME_MS && now - resumedAt > SLOW_FRAME_MS) {
                    log("ui thread stalled " + (now - last) + "ms");
                }
                last = now;
                Choreographer.getInstance().postFrameCallback(this);
            }
        });
    }
}
