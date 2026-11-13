package app.nebulagram.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.recorder.DraftsController;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Отложенные истории, которые публикует сам телефон. У {@code stories.sendStory} в схеме
 * Telegram 13.2.1 нет времени публикации, поэтому история сохраняется черновиком Telegram, а в
 * назначенное время будильник {@link NebulaStoryScheduleReceiver} поднимает приложение и
 * отправляет её обычным {@code StoriesController.uploadStory}. Если телефон был выключен,
 * история уходит при следующем включении или запуске. Правила — {@link NebulaStorySchedulePlan}.
 */
public final class NebulaStorySchedule {
    public static final String ACTION_PUBLISH = "app.nebulagram.story.PUBLISH";
    private static final String PREFS = "nebula_story_schedule";
    private static final String KEY = "items";
    private static final int ALARM_REQUEST = 0x5707;

    /** Черновики отложенных историй по {@code draftId}; в общем списке черновиков их нет. */
    private static final HashMap<Long, StoryEntry> adopted = new HashMap<>();
    private static final HashSet<Integer> loadedAccounts = new HashSet<>();
    private static final HashMap<Integer, ArrayList<Runnable>> waitingForDrafts = new HashMap<>();
    private static Runnable listener;

    private NebulaStorySchedule() { }

    // ---- хранилище ----

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<NebulaStorySchedulePlan.Item> items() {
        return NebulaStorySchedulePlan.decodeAll(prefs().getString(KEY, ""));
    }

    private static synchronized void save(List<NebulaStorySchedulePlan.Item> items) {
        NebulaStorySchedulePlan.sort(items);
        prefs().edit().putString(KEY, NebulaStorySchedulePlan.encodeAll(items)).apply();
        arm(items);
        notifyListener();
    }

    private static void notifyListener() {
        Runnable current = listener;
        if (current != null) {
            AndroidUtilities.runOnUIThread(current);
        }
    }

    public static void setListener(Runnable value) {
        listener = value;
    }

    public static StoryEntry entry(NebulaStorySchedulePlan.Item item) {
        return item == null ? null : adopted.get(item.draftId);
    }

    // ---- выбор времени в окне «Опубликовать» ----

    public static String buttonText() {
        return NebulaText.text("Запланировать публикацию", "Schedule for later");
    }

    /** Отложить можно новую историю себе, в канал или группу; не правку, не превью бота и не репост сообщения. */
    public static boolean schedulable(StoryEntry entry) {
        return entry != null && !entry.isEdit && !entry.isRepostMessage && entry.botId == 0;
    }

    public static void pick(Context context, Theme.ResourcesProvider resources, Runnable post) {
        if (context == null || post == null) {
            return;
        }
        long self = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        AlertsCreator.createScheduleDatePickerDialog(context, NebulaText.text("Опубликовать историю", "Publish story"), self, -1, 0, true,
                (notify, date, repeat) -> {
                    long now = System.currentTimeMillis();
                    long publishAt = date * 1000L;
                    int verdict = NebulaStorySchedulePlan.validate(publishAt, now);
                    if (verdict == NebulaStorySchedulePlan.TOO_SOON) {
                        toast(NebulaText.text("Выберите время хотя бы через минуту", "Pick a time at least a minute from now"));
                        return;
                    }
                    if (verdict == NebulaStorySchedulePlan.TOO_LATE) {
                        toast(NebulaText.text("Историю можно отложить не больше чем на 6 дней: черновики Telegram живут неделю",
                                "A story can wait at most 6 days: Telegram keeps drafts for a week"));
                        return;
                    }
                    NebulaStorySchedulePlan.arm(publishAt, now);
                    post.run();
                }, null, new AlertsCreator.ScheduleDatePickerColors(resources), resources);
    }

    // ---- постановка в очередь ----

    /** Вызывается из {@code StoryRecorder.uploadInternal} вместо отправки. */
    public static boolean schedule(int account, StoryEntry entry, long publishAt) {
        if (!schedulable(entry)) {
            return false;
        }
        DraftsController drafts = MessagesController.getInstance(account).getStoriesController().getDraftsController();
        if (entry.isDraft && entry.draftId != 0) {
            drafts.edit(entry);
        } else {
            drafts.append(entry);
        }
        drafts.drafts.remove(entry);
        adopted.put(entry.draftId, entry);

        NebulaStorySchedulePlan.Item item = new NebulaStorySchedulePlan.Item();
        item.account = account;
        item.draftId = entry.draftId;
        item.publishAt = publishAt;
        item.dialogId = dialogId(account, entry.peer);
        item.pinned = entry.pinned;
        item.allowScreenshots = entry.allowScreenshots;
        item.period = entry.period;
        List<NebulaStorySchedulePlan.Item> items = items();
        NebulaStorySchedulePlan.Item old = NebulaStorySchedulePlan.find(items, account, entry.draftId);
        if (old != null) {
            items.remove(old);
        }
        items.add(item);
        save(items);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.storiesDraftsUpdated);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                BulletinFactory.global().createSimpleBulletin(R.raw.chats_infotip,
                        NebulaText.text("История будет опубликована ", "Story will be published ") + when(publishAt)
                                + NebulaText.text(". Телефон должен быть включён.", ". Keep the phone on."),
                        NebulaText.text("Список", "List"), NebulaStorySchedule::openList).show();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, 450);
        return true;
    }

    public static void openList() {
        BaseFragment last = LaunchActivity.getSafeLastFragment();
        if (last != null) {
            last.presentFragment(new NebulaScheduledStoriesFragment());
        }
    }

    /** Снимает историю с публикации; черновик возвращается в обычный список и не теряется. */
    public static void cancel(NebulaStorySchedulePlan.Item item) {
        List<NebulaStorySchedulePlan.Item> items = items();
        NebulaStorySchedulePlan.Item stored = NebulaStorySchedulePlan.find(items, item.account, item.draftId);
        if (stored != null) {
            items.remove(stored);
        }
        StoryEntry entry = adopted.remove(item.draftId);
        if (entry != null && UserConfig.isValidAccount(item.account)) {
            DraftsController drafts = MessagesController.getInstance(item.account).getStoriesController().getDraftsController();
            if (!drafts.drafts.contains(entry)) {
                drafts.drafts.add(0, entry);
            }
            NotificationCenter.getInstance(item.account).postNotificationName(NotificationCenter.storiesDraftsUpdated);
        }
        save(items);
    }

    // ---- хуки DraftsController ----

    /** {@code DraftsController.load}: черновик отложенной истории не попадает в общий список. */
    public static boolean adopt(int account, StoryEntry entry) {
        if (entry == null || NebulaStorySchedulePlan.find(items(), account, entry.draftId) == null) {
            return false;
        }
        adopted.put(entry.draftId, entry);
        return true;
    }

    /** {@code DraftsController.load} дочитал базу аккаунта. */
    public static void draftsLoaded(int account) {
        loadedAccounts.add(account);
        ArrayList<Runnable> waiting = waitingForDrafts.remove(account);
        if (waiting != null) {
            for (Runnable runnable : waiting) {
                runnable.run();
            }
        }
        notifyListener();
    }

    public static void loadDrafts(int account, Runnable after) {
        if (!UserConfig.isValidAccount(account) || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        if (loadedAccounts.contains(account)) {
            if (after != null) {
                after.run();
            }
            return;
        }
        if (after != null) {
            ArrayList<Runnable> waiting = waitingForDrafts.get(account);
            if (waiting == null) {
                waiting = new ArrayList<>();
                waitingForDrafts.put(account, waiting);
            }
            waiting.add(after);
        }
        MessagesController.getInstance(account).getStoriesController().getDraftsController().load();
    }

    // ---- публикация ----

    /** Запуск приложения, будильник или включение телефона. */
    public static void onWake() {
        AndroidUtilities.runOnUIThread(() -> {
            List<NebulaStorySchedulePlan.Item> items = items();
            arm(items);
            for (NebulaStorySchedulePlan.Item item : NebulaStorySchedulePlan.due(items, System.currentTimeMillis())) {
                final int account = item.account;
                final long draftId = item.draftId;
                loadDrafts(account, () -> publish(account, draftId));
            }
        });
    }

    private static void publish(int account, long draftId) {
        List<NebulaStorySchedulePlan.Item> items = items();
        NebulaStorySchedulePlan.Item item = NebulaStorySchedulePlan.find(items, account, draftId);
        if (item == null || item.state != 0 || item.publishAt > System.currentTimeMillis()) {
            return;
        }
        StoryEntry entry = adopted.remove(draftId);
        if (entry == null) {
            item.state = 2;
            save(items);
            return;
        }
        try {
            if (item.dialogId != 0) {
                if (item.dialogId == UserConfig.getInstance(account).getClientUserId()) {
                    entry.peer = new TLRPC.TL_inputPeerSelf();
                } else {
                    TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(item.dialogId);
                    if (peer != null) {
                        entry.peer = peer;
                    }
                }
            }
            entry.pinned = item.pinned;
            entry.allowScreenshots = item.allowScreenshots;
            if (item.period > 0) {
                entry.period = item.period;
            }
            StoriesController stories = MessagesController.getInstance(account).getStoriesController();
            stories.uploadStory(entry, true);
            // Как в StoryRecorder: черновик удаляется сразу после передачи в загрузку.
            stories.getDraftsController().delete(entry);
            items.remove(item);
        } catch (Throwable e) {
            FileLog.e(e);
            item.state = 2;
        }
        save(items);
    }

    // ---- будильник ----

    private static void arm(List<NebulaStorySchedulePlan.Item> items) {
        Context context = ApplicationLoader.applicationContext;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) {
            return;
        }
        Intent intent = new Intent(context, NebulaStoryScheduleReceiver.class).setAction(ACTION_PUBLISH);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_REQUEST, intent, flags);
        long next = NebulaStorySchedulePlan.nextAlarm(items);
        try {
            if (next == 0) {
                alarms.cancel(pending);
            } else if (!exactAlarms(alarms)) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, next, pending);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Без точных будильников Android может сдвинуть публикацию на несколько минут. */
    public static boolean exactAlarms() {
        return exactAlarms((AlarmManager) ApplicationLoader.applicationContext.getSystemService(Context.ALARM_SERVICE));
    }

    private static boolean exactAlarms(AlarmManager alarms) {
        return Build.VERSION.SDK_INT < 31 || alarms != null && alarms.canScheduleExactAlarms();
    }

    // ---- мелочи ----

    static long dialogId(int account, TLRPC.InputPeer peer) {
        if (peer == null || peer instanceof TLRPC.TL_inputPeerSelf) {
            return UserConfig.getInstance(account).getClientUserId();
        }
        return DialogObject.getPeerDialogId(peer);
    }

    static String when(long publishAt) {
        return LocaleController.formatDateAudio(publishAt / 1000, false);
    }

    private static void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_LONG).show();
    }
}
