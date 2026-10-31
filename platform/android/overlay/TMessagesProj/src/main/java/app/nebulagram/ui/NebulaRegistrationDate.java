package app.nebulagram.ui;

import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.*;
import static app.nebulagram.ui.NebulaText.text;

/** Telegram's own month first; offline cache/range and history bounds are labelled estimates. */
public final class NebulaRegistrationDate {
    private NebulaRegistrationDate() { }
    private static android.content.SharedPreferences cache() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_registration_months", 0);
    }
    public static boolean validMonth(String value) { return value != null && value.matches("20[0-9]{2}-(0[1-9]|1[0-2])"); }
    public static void observe(long id, String month) {
        if (id <= 0 || !validMonth(month)) return;
        android.content.SharedPreferences.Editor edit = cache().edit();
        if (cache().getAll().size() >= 512 && !cache().contains(Long.toString(id))) {
            String first = cache().getAll().keySet().iterator().next(); edit.remove(first);
        }
        edit.putString(Long.toString(id), month).apply();
    }
    private static String estimate(long id) {
        String exact = cache().getString(Long.toString(id), null);
        if (validMonth(exact)) return text("Месяц из локального кэша Telegram: ", "Telegram month from local cache: ") + exact;
        long lower = 0, upper = Long.MAX_VALUE; String from = null, to = null;
        for (Map.Entry<String, ?> entry : cache().getAll().entrySet()) try {
            long peer = Long.parseLong(entry.getKey()); String month = (String) entry.getValue();
            if (!validMonth(month)) continue;
            if (peer < id && peer > lower) { lower = peer; from = month; }
            if (peer > id && peer < upper) { upper = peer; to = month; }
        } catch (RuntimeException ignored) { }
        if (from != null && to != null && from.compareTo(to) <= 0)
            return text("Ориентировочно: ", "Estimated: ") + from + " — " + to
                + text(". Оценка по соседним ID, сохранённым на устройстве; она может быть неточной.", ". Based on neighbouring IDs cached on this device; it may be inaccurate.");
        return null;
    }
    public static void show(BaseFragment host, int account, long id) {
        if (host.getParentActivity() == null || id <= 0) return;
        final long owner = UserConfig.getInstance(account).getClientUserId();
        final boolean[] active = {true}; final int[] request = {0};
        android.widget.TextView value = new android.widget.TextView(host.getParentActivity());
        value.setText(text("Запрашиваю сведения Telegram…", "Loading Telegram account information…"));
        value.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack));
        value.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));
        android.app.Dialog dialog = new NebulaDialog.Builder(host.getParentActivity()).setTitle(text("Дата регистрации", "Registration date"))
            .setView(value).setPositiveButton(text("Закрыть", "Close"), null).create();
        host.showDialog(dialog, d -> { active[0] = false; if (request[0] != 0) ConnectionsManager.getInstance(account).cancelRequest(request[0], true); });
        TLRPC.TL_users_getFullUser query = new TLRPC.TL_users_getFullUser(); query.id = MessagesController.getInstance(account).getInputUser(id);
        request[0] = ConnectionsManager.getInstance(account).sendRequest(query, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!active[0] || owner != UserConfig.getInstance(account).getClientUserId()) return;
            if (response instanceof TLRPC.TL_users_userFull) {
                TLRPC.UserFull full = ((TLRPC.TL_users_userFull) response).full_user;
                String month = full.settings == null ? null : full.settings.registration_month;
                if (validMonth(month)) { observe(id, month); value.setText(text("По данным Telegram: ", "According to Telegram: ") + month); return; }
            }
            String cached = estimate(id);
            if (cached != null) { value.setText(cached); return; }
            TLRPC.TL_messages_getHistory history = new TLRPC.TL_messages_getHistory();
            history.peer = MessagesController.getInstance(account).getInputPeer(id); history.offset_id = 1; history.add_offset = -1; history.limit = 1;
            request[0] = ConnectionsManager.getInstance(account).sendRequest(history, (result, failure) -> AndroidUtilities.runOnUIThread(() -> {
                if (!active[0] || owner != UserConfig.getInstance(account).getClientUserId()) return;
                if (result instanceof TLRPC.messages_Messages && !((TLRPC.messages_Messages) result).messages.isEmpty()) {
                    int date = ((TLRPC.messages_Messages) result).messages.get(0).date;
                    if (date > 0) {
                        String bound = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(new Date(date * 1000L));
                        value.setText(text("Аккаунт существовал не позднее ", "Account existed no later than ") + bound
                            + text(". Это граница по самой ранней доступной переписке, а не точная дата регистрации.", ". This is a bound from the earliest available history, not an exact registration date.")); return;
                    }
                }
                value.setText(text("Telegram не сообщил месяц, а локальных данных недостаточно для оценки.", "Telegram did not provide a month and there is not enough local data to estimate it."));
            }));
        }));
    }
}
