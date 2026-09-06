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

public final class NebulaSettingsTransfer {
    public static final int EXPORT = 9710, IMPORT = 9711, REPORT = 9712, RESTART = 9713;
    private NebulaSettingsTransfer() { }
    public static void menu(BaseFragment f) {
        ActionBarMenuItem menu = f.getActionBar().createMenu().addItem(9700, R.drawable.ic_ab_other);
        menu.addSubItem(EXPORT, R.drawable.msg_share, text("Экспорт настроек", "Export settings"));
        menu.addSubItem(IMPORT, R.drawable.msg_download, text("Импорт настроек", "Import settings"));
        menu.addSubItem(REPORT, R.drawable.msg_copy, text("Скопировать детали отчёта", "Copy report details"));
        menu.addSubItem(RESTART, R.drawable.msg_retry, text("Перезапустить приложение", "Restart app"));
    }
    public static void action(BaseFragment f, int id) {
        try {
            if (id == EXPORT || id == IMPORT) {
                Intent intent = new Intent(id == EXPORT ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("application/json");
                if (id == EXPORT) intent.putExtra(Intent.EXTRA_TITLE, "NebulaGram-settings.json");
                f.startActivityForResult(intent, id);
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
    public static boolean result(BaseFragment f, int request, int result, Intent data) {
        if (request != EXPORT && request != IMPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        try {
            Context c = ApplicationLoader.applicationContext;
            SharedPreferences prefs = c.getSharedPreferences("nebulagram", 0);
            if (request == EXPORT) {
                JSONObject values = new JSONObject();
                for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) if (NebulaSettingsSchema.types.containsKey(item.getKey())) values.put(item.getKey(), item.getValue());
                JSONObject json = new JSONObject().put("format", "NebulaGram-settings").put("version", 1).put("settings", values);
                try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
                    if (out == null) throw new IOException();
                    out.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                notice(f, text("Настройки экспортированы", "Settings exported"));
            } else {
                byte[] bytes;
                try (InputStream in = c.getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new IOException();
                    byte[] buf = new byte[4096]; int count;
                    while ((count = in.read(buf)) != -1) { if (out.size() + count > 200000) throw new IOException(); out.write(buf, 0, count); }
                    bytes = out.toByteArray();
                }
                JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (!"NebulaGram-settings".equals(json.optString("format")) || json.optInt("version") != 1) throw new IOException();
                JSONObject values = json.getJSONObject("settings");
                Map<String, Object> checked = new HashMap<>();
                Iterator<String> keys = values.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); Class<?> type = NebulaSettingsSchema.types.get(key);
                    if (type == null) continue;
                    Object value = values.get(key);
                    if (!type.isInstance(value) || value instanceof String && ((String) value).length() > 1024) throw new IOException();
                    checked.put(key, value);
                }
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, Object> item : checked.entrySet()) {
                    Object value = item.getValue(); String key = item.getKey();
                    if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                    else if (value instanceof Integer) editor.putInt(key, (Integer) value);
                    else editor.putString(key, (String) value);
                }
                if (!editor.commit()) throw new IOException();
                NebulaIcons.setPack(NebulaIcons.pack());
                NebulaTheme.setMaterialYouEnabled(prefs.getBoolean("material_you", true));
                NebulaTheme.applyMaterialYou(c); Theme.reloadAllResources(c);
                notice(f, text("Настройки импортированы. Откройте нужный экран заново.", "Settings imported. Reopen the affected screen."));
            }
        } catch (Exception e) { notice(f, text("Не удалось прочитать или записать настройки. Проверьте формат и доступ к файлу.", "Unable to read or write settings. Check the format and file access.")); }
        return true;
    }
    private static void notice(BaseFragment f, String message) {
        if (f.getParentActivity() != null) f.showDialog(new AlertDialog.Builder(f.getParentActivity()).setTitle("NebulaGram").setMessage(message).setPositiveButton(text("ОК", "OK"), null).create());
    }
}
