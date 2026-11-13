package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Правила отложенных историй. У {@code stories.sendStory} в схеме Telegram 13.2.1 нет
 * {@code schedule_date}, поэтому история ждёт на телефоне: черновик Telegram хранит медиа,
 * а здесь — когда и куда его опубликовать. Черновики живут 7 дней, поэтому дальше
 * {@link #MAX_AHEAD_MS} запланировать нельзя. Чистая логика без Android:
 * проверяется {@code scripts/check-story-schedule.py}.
 */
public final class NebulaStorySchedulePlan {
    public static final long MIN_LEAD_MS = 60 * 1000L;
    /** Черновик истории Telegram удаляет через 7 дней; оставляем час запаса. */
    public static final long MAX_AHEAD_MS = 7 * 24 * 60 * 60 * 1000L - 60 * 60 * 1000L;
    /** Выбор времени в окне «Опубликовать» действует только на ближайшую отправку. */
    static final long PENDING_MS = 5 * 60 * 1000L;

    public static final int OK = 0;
    public static final int TOO_SOON = 1;
    public static final int TOO_LATE = 2;

    private static long pendingAt;
    private static long pendingArmedAt;

    private NebulaStorySchedulePlan() { }

    public static int validate(long publishAt, long now) {
        if (publishAt - now < MIN_LEAD_MS) {
            return TOO_SOON;
        }
        if (publishAt - now > MAX_AHEAD_MS) {
            return TOO_LATE;
        }
        return OK;
    }

    public static synchronized void arm(long publishAt, long now) {
        pendingAt = publishAt;
        pendingArmedAt = now;
    }

    public static synchronized void disarm() {
        pendingAt = 0;
        pendingArmedAt = 0;
    }

    /** Время, выбранное в окне публикации, или 0 — тогда история уходит сразу. */
    public static synchronized long take(long now) {
        long value = pendingAt;
        boolean fresh = value != 0 && now >= pendingArmedAt && now - pendingArmedAt <= PENDING_MS;
        disarm();
        return fresh ? value : 0;
    }

    public static final class Item {
        public int account;
        public long draftId;
        public long publishAt;
        public long dialogId;
        public boolean pinned;
        public boolean allowScreenshots;
        public int period;
        /** 0 — ждёт, 1 — отправляется, 2 — не удалось (черновик пропал или ошибка). */
        public int state;

        public String encode() {
            return account + ":" + draftId + ":" + publishAt + ":" + dialogId + ":" + (pinned ? 1 : 0) + ":"
                    + (allowScreenshots ? 1 : 0) + ":" + period + ":" + state;
        }

        public static Item decode(String line) {
            if (line == null) {
                return null;
            }
            String[] p = line.trim().split(":");
            if (p.length != 8) {
                return null;
            }
            try {
                Item item = new Item();
                item.account = Integer.parseInt(p[0]);
                item.draftId = Long.parseLong(p[1]);
                item.publishAt = Long.parseLong(p[2]);
                item.dialogId = Long.parseLong(p[3]);
                item.pinned = "1".equals(p[4]);
                item.allowScreenshots = "1".equals(p[5]);
                item.period = Integer.parseInt(p[6]);
                item.state = Integer.parseInt(p[7]);
                return item.draftId == 0 || item.publishAt <= 0 ? null : item;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static String encodeAll(List<Item> items) {
        StringBuilder out = new StringBuilder();
        for (Item item : items) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(item.encode());
        }
        return out.toString();
    }

    public static List<Item> decodeAll(String value) {
        ArrayList<Item> items = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return items;
        }
        for (String line : value.split("\n")) {
            Item item = Item.decode(line);
            if (item != null && find(items, item.account, item.draftId) == null) {
                items.add(item);
            }
        }
        sort(items);
        return items;
    }

    public static void sort(List<Item> items) {
        Collections.sort(items, (a, b) -> Long.compare(a.publishAt, b.publishAt));
    }

    public static Item find(List<Item> items, int account, long draftId) {
        for (Item item : items) {
            if (item.account == account && item.draftId == draftId) {
                return item;
            }
        }
        return null;
    }

    /** Ждущие истории, чьё время пришло (в том числе пропущенные, пока телефон был выключен). */
    public static List<Item> due(List<Item> items, long now) {
        ArrayList<Item> out = new ArrayList<>();
        for (Item item : items) {
            if (item.state == 0 && item.publishAt <= now) {
                out.add(item);
            }
        }
        return out;
    }

    /** Ближайшее время будильника или 0, если ждать нечего. */
    public static long nextAlarm(List<Item> items) {
        long next = 0;
        for (Item item : items) {
            if (item.state == 0 && (next == 0 || item.publishAt < next)) {
                next = item.publishAt;
            }
        }
        return next;
    }
}
