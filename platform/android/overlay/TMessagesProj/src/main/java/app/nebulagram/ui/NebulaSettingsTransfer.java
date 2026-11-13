package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.*;
import app.nebulagram.nebulalink.NebulaLink;

public final class NebulaSettingsTransfer {
    public static final int EXPORT = 9710, IMPORT = 9711, REPORT = 9712, RESTART = 9713, RESET = 9714;
    private NebulaSettingsTransfer() { }
    public static void menu(BaseFragment f) {
        ActionBarMenuItem menu = f.getActionBar().createMenu().addItem(9700, R.drawable.ic_ab_other);
        menu.addSubItem(EXPORT, R.drawable.msg_share, text("Экспорт настроек", "Export settings"));
        menu.addSubItem(IMPORT, R.drawable.msg_download, text("Импорт настроек", "Import settings"));
        menu.addSubItem(REPORT, R.drawable.msg_copy, text("Скопировать детали отчёта", "Copy report details"));
        menu.addSubItem(RESTART, R.drawable.msg_retry, text("Перезапустить приложение", "Restart app"));
        menu.addSubItem(RESET, R.drawable.msg_clearcache, text("Сбросить переносимые настройки", "Reset transferable settings"));
    }
    public static void action(BaseFragment f, int id) {
        try {
            if (id == EXPORT) {
                confirmExport(f);
            } else if (id == IMPORT) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                f.startActivityForResult(intent, id);
            } else if (id == RESET) {
                f.showDialog(new NebulaDialog.Builder(f.getParentActivity()).setTitle(text("Сбросить настройки?", "Reset settings?"))
                    .setMessage(text("Будут сброшены настройки, включаемые в экспорт, и выбор своего шрифта. Аккаунты, ключи ИИ и уже отложенные сообщения сохранятся.", "Resets settings included in exports and the custom font choice. Accounts, AI keys and already scheduled messages remain."))
                    .setPositiveButton(text("Сбросить", "Reset"), (dialog, which) -> {
                        Context c = ApplicationLoader.applicationContext;
                        NebulaTheme.setMaterialYouEnabled(false);
                        SharedPreferences.Editor editor = c.getSharedPreferences("nebulagram", 0).edit();
                        for (String key : NebulaSettingsSchema.types.keySet()) editor.remove(key);
                        editor.remove("font_custom").remove(NebulaTheme.KEY_PICTURE_SOURCE).remove(NebulaTheme.KEY_PICTURE_SEED).apply();
                        NebulaTheme.refreshAccentSource(c);
                        NebulaIcons.setPack(NebulaIcons.pack()); AndroidUtilities.nebulaClearTypefaces(); Theme.reloadAllResources(c);
                        f.finishFragment();
                    }).setNegativeButton(text("Отмена", "Cancel"), null).create());
            } else if (id == REPORT) {
                AndroidUtilities.addToClipboard("NebulaGram " + BuildVars.BUILD_VERSION_STRING + "\nAndroid " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n" + Build.MANUFACTURER + " " + Build.MODEL + "\nMaterial You: " + NebulaTheme.materialYouEnabled() + "\nIcon pack: " + NebulaIcons.pack());
                notice(f, text("Детали скопированы", "Details copied"));
            } else if (id == RESTART) {
                Context c = ApplicationLoader.applicationContext;
                Intent launch = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
                if (launch != null && launch.getComponent() != null) {
                    c.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        } catch (Exception e) { notice(f, text("Не удалось открыть действие", "Unable to open this action")); }
    }
    /** Prepared before the picker opens: the subscriptions, when the user ticked them. */
    private static List<NebulaSettingsBackup.Connection> pendingConnections = new ArrayList<>();

    private static void confirmExport(BaseFragment f) {
        Context context = f.getParentActivity();
        if (context == null) return;
        NebulaRow keys = new NebulaRow(context).title(text("Включить ключи подключения", "Include connection keys"))
                .subtitle(text("Подписки NebulaLink. С ними файл открывает доступ к вашим серверам — не отправляйте его никому.",
                        "NebulaLink subscriptions. With them the file grants access to your servers — do not share it."), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(false);
        keys.setOnClickListener(v -> keys.toggleChecked());
        f.showDialog(new NebulaDialog.Builder(context).setTitle(text("Экспорт настроек", "Export settings"))
                .setMessage(text("В файл попадут все настройки NebulaGram. Ключи ИИ, токены и состояние устройства не сохраняются никогда.",
                        "The file holds every NebulaGram setting. AI keys, tokens and device state are never written."))
                .setView(keys)
                .setPositiveButton(text("Выбрать файл", "Choose file"), (dialog, which) -> {
                    pendingConnections = new ArrayList<>();
                    if (!keys.isChecked()) { openExport(f); return; }
                    NebulaLink.call("subscription.list", null, listed -> {
                        if (!listed.ok) { notice(f, text("Не удалось получить подписки NebulaLink", "Unable to read NebulaLink subscriptions")); return; }
                        pendingConnections = connections(listed.array);
                        openExport(f);
                    });
                }).setNegativeButton(text("Отмена", "Cancel"), null).create());
    }

    private static void openExport(BaseFragment f) {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            intent.putExtra(Intent.EXTRA_TITLE, "NebulaGram-settings-" + day + ".json");
            f.startActivityForResult(intent, EXPORT);
        } catch (Exception e) { notice(f, text("Не удалось открыть выбор файла", "Unable to open the file picker")); }
    }

    private static List<NebulaSettingsBackup.Connection> connections(JSONArray array) {
        List<NebulaSettingsBackup.Connection> result = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject row = array.optJSONObject(i);
            if (row == null || row.optString("url").isEmpty()) continue;
            result.add(new NebulaSettingsBackup.Connection(row.optString("name"), row.optString("url"), row.optString("kind")));
        }
        return result;
    }

    private static Map<String, Map<String, ?>> currentStores(Context c) {
        Map<String, Map<String, ?>> stores = new HashMap<>();
        for (String store : NebulaSettingsBackup.STORES) stores.put(store, c.getSharedPreferences(store, 0).getAll());
        return stores;
    }

    public static boolean result(BaseFragment f, int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) { pendingConnections = new ArrayList<>(); return true; }
        Uri uri = data.getData();
        Context c = ApplicationLoader.applicationContext;
        if (request == EXPORT) {
            try {
                String json = NebulaSettingsBackup.encode(currentStores(c), pendingConnections, BuildVars.BUILD_VERSION_STRING, System.currentTimeMillis());
                try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException();
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                }
                notice(f, pendingConnections.isEmpty() ? text("Настройки экспортированы", "Settings exported")
                        : text("Настройки и ключи подключения экспортированы. Храните файл в надёжном месте.",
                        "Settings and connection keys exported. Keep the file somewhere safe."));
            } catch (Exception e) {
                notice(f, text("Не удалось записать файл", "Unable to write the file"));
            } finally {
                pendingConnections = new ArrayList<>();
            }
            return true;
        }
        NebulaSettingsBackup.Backup backup;
        try {
            byte[] bytes;
            try (InputStream in = c.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) throw new IOException();
                byte[] buf = new byte[8192]; int count;
                while ((count = in.read(buf)) != -1) {
                    if (out.size() + count > NebulaSettingsBackup.MAX_FILE) throw new IOException();
                    out.write(buf, 0, count);
                }
                bytes = out.toByteArray();
            }
            backup = NebulaSettingsBackup.decode(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            notice(f, text("Это не файл настроек NebulaGram или он повреждён. Ничего не изменено.",
                    "This is not a NebulaGram settings file, or it is damaged. Nothing was changed."));
            return true;
        }
        List<NebulaSettingsBackup.Change> changes = NebulaSettingsBackup.diff(currentStores(c), backup);
        if (backup.connections.isEmpty()) {
            confirmImport(f, backup, changes, new ArrayList<>());
        } else {
            NebulaLink.call("subscription.list", null, listed -> {
                Set<String> urls = new HashSet<>();
                for (NebulaSettingsBackup.Connection known : connections(listed.ok ? listed.array : null)) urls.add(known.url);
                confirmImport(f, backup, changes, NebulaSettingsBackup.newConnections(urls, backup));
            });
        }
        return true;
    }

    private static void confirmImport(BaseFragment f, NebulaSettingsBackup.Backup backup,
                                      List<NebulaSettingsBackup.Change> changes, List<NebulaSettingsBackup.Connection> added) {
        if (f.getParentActivity() == null) return;
        if (changes.isEmpty() && added.isEmpty()) {
            notice(f, text("Файл совпадает с текущими настройками — менять нечего.", "The file matches the current settings — nothing to change."));
            return;
        }
        StringBuilder message = new StringBuilder();
        if (!changes.isEmpty()) {
            message.append(text("Изменится настроек: ", "Settings to change: ")).append(changes.size()).append('\n');
            int shown = Math.min(12, changes.size());
            for (int i = 0; i < shown; i++) {
                NebulaSettingsBackup.Change change = changes.get(i);
                message.append("• ").append(change.key).append(": ")
                        .append(NebulaSettingsBackup.describe(change.before)).append(" → ")
                        .append(NebulaSettingsBackup.describe(change.after)).append('\n');
            }
            if (changes.size() > shown) message.append(text("…и ещё ", "…and ")).append(changes.size() - shown)
                    .append(text("", " more")).append('\n');
        }
        if (!added.isEmpty()) {
            message.append('\n').append(text("Добавятся подписки NebulaLink: ", "NebulaLink subscriptions to add: ")).append(added.size()).append('\n');
            for (NebulaSettingsBackup.Connection connection : added) {
                String host = Uri.parse(connection.url).getHost();
                message.append("• ").append(connection.name.isEmpty() ? (host == null ? "?" : host) : connection.name).append('\n');
            }
        }
        if (backup.ignored > 0) {
            message.append('\n').append(text("Пропущено записей (ключи, состояние устройства, неизвестное): ",
                    "Entries skipped (keys, device state, unknown): ")).append(backup.ignored).append('\n');
        }
        message.append('\n').append(text("Остальные настройки останутся как есть.", "Everything else stays as it is."));
        f.showDialog(new NebulaDialog.Builder(f.getParentActivity()).setTitle(text("Импортировать настройки?", "Import settings?"))
                .setMessage(message.toString().trim())
                .setPositiveButton(text("Импортировать", "Import"), (dialog, which) -> apply(f, backup, added))
                .setNegativeButton(text("Отмена", "Cancel"), null).create());
    }

    @SuppressWarnings("unchecked")
    private static void apply(BaseFragment f, NebulaSettingsBackup.Backup backup, List<NebulaSettingsBackup.Connection> added) {
        Context c = ApplicationLoader.applicationContext;
        try {
            for (Map.Entry<String, Map<String, Object>> store : backup.stores.entrySet()) {
                SharedPreferences.Editor editor = c.getSharedPreferences(store.getKey(), 0).edit();
                for (Map.Entry<String, Object> item : store.getValue().entrySet()) {
                    Object value = item.getValue(); String key = item.getKey();
                    if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                    else if (value instanceof Long) editor.putLong(key, (Long) value);
                    else if (value instanceof Float) editor.putFloat(key, (Float) value);
                    else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
                    else editor.putString(key, (String) value);
                }
                if (!editor.commit()) throw new IOException();
            }
        } catch (Exception e) {
            notice(f, text("Не удалось сохранить настройки", "Unable to save the settings"));
            return;
        }
        SharedPreferences prefs = c.getSharedPreferences(NebulaSettingsBackup.MAIN, 0);
        NebulaIcons.setPack(NebulaIcons.pack());
        NebulaTheme.setMaterialYouEnabled(prefs.getBoolean("material_you", true));
        NebulaTheme.refreshAccentSource(c); // also restores the accent when the file turns the picture off
        NebulaPictureTheme.refresh();
        Theme.reloadAllResources(c);
        addConnections(f, new ArrayList<>(added), 0);
    }

    /** One subscription at a time: the core refreshes each on add, and order keeps errors legible. */
    private static void addConnections(BaseFragment f, List<NebulaSettingsBackup.Connection> added, int failed) {
        if (added.isEmpty()) {
            notice(f, failed == 0 ? text("Настройки импортированы. Откройте нужный экран заново.", "Settings imported. Reopen the affected screen.")
                    : text("Настройки импортированы, но не все подписки добавились: ", "Settings imported, but some subscriptions failed: ") + failed);
            return;
        }
        NebulaSettingsBackup.Connection next = added.remove(0);
        JSONObject payload = new JSONObject();
        try { payload.put("url", next.url).put("name", next.name).put("kind", next.kind); } catch (JSONException ignored) { }
        NebulaLink.call("subscription.add", payload, done -> addConnections(f, added, failed + (done.ok ? 0 : 1)));
    }

    private static void notice(BaseFragment f, String message) {
        if (f.getParentActivity() != null) f.showDialog(new NebulaDialog.Builder(f.getParentActivity()).setTitle("NebulaGram").setMessage(message).setPositiveButton(text("ОК", "OK"), null).create());
    }
}
