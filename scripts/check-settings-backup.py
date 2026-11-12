#!/usr/bin/env python3
"""Settings backup file: compile the real NebulaSettingsBackup and round-trip it.

Covers the versioned JSON (every preference type survives export and import
exactly), secrets and device state never leaving the device, validation of
damaged or hostile files, version 1 compatibility, the change list shown before
import, and the opt-in for NebulaLink connection keys. The Android half
(NebulaSettingsTransfer) is checked for the SAF picker and the confirmation flow.
"""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

CHECK = r'''
import app.nebulagram.ui.NebulaSettingsBackup;
import app.nebulagram.ui.NebulaSettingsBackup.*;
import java.util.*;
public class CheckSettingsBackup {
    static int checks;
    static void ok(boolean value, String what) { checks++; if (!value) throw new AssertionError(what); }
    static void rejects(String json, String what) {
        checks++;
        try { NebulaSettingsBackup.decode(json); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("accepted: " + what);
    }
    static String wrap(String stores) { return "{\"format\":\"NebulaGram-settings\",\"version\":2,\"stores\":" + stores + "}"; }
    public static void main(String[] args) {
        Map<String, Map<String, Object>> raw = new HashMap<>();
        Map<String, Object> main = new HashMap<>();
        main.put("avatar_round", 42);                 // schema int
        main.put("hide_send_as", true);               // schema key that contains "send"/"as": kept
        main.put("bottom_bar_order", "chats,contacts,settings");
        main.put("glass_opacity", 0.7f);              // float
        main.put("menu_seen_at", 1731400000000L);     // long
        main.put("muted_in_tab_counters", new HashSet<>(Arrays.asList("b", "a", "ж")));
        main.put("home_chats_title", "Привет \"мир\"\n\t\\   😀");
        // Never exported:
        main.put("api_key", "sk-123"); main.put("openai_token", "t"); main.put("tunnel_proxy_port", 1080);
        main.put("accent_before_material_you:day:0", 5); main.put("proxy_secret", "s"); main.put("font_custom", "/data/x.ttf");
        main.put("picture_accent_seed", 0xff112233); main.put("bot_password", "p"); main.put("session_id", "s");
        main.put("pending_payload_1", "x"); main.put("settings_search_history", "a,b");
        raw.put("nebulagram", main);
        Map<String, Object> ai = new HashMap<>();
        ai.put("temperature", 0.25f); ai.put("selected_service", "svc"); ai.put("model_openai", "gpt"); ai.put("provider_api_key", "nope");
        raw.put("nebula_ai_settings", ai);
        Map<String, Object> badges = new HashMap<>(); badges.put("host", "h"); badges.put("admin_token", "secret");
        raw.put("nebulagram_badges", badges);         // store not in the allowlist
        Map<String, Object> pins = new HashMap<>(); pins.put("pins", "[]");
        raw.put("nebula_local_pins", pins);

        String plain = NebulaSettingsBackup.encode(raw, null, "13.2.1", 1L);
        for (String secret : new String[]{"sk-123", "api_key", "openai_token", "tunnel_proxy_port", "accent_before", "proxy_secret",
                "font_custom", "picture_accent_seed", "bot_password", "session_id", "admin_token", "nebulagram_badges",
                "nebula_local_pins", "provider_api_key", "pending_payload", "settings_search_history", "connections"}) {
            ok(!plain.contains(secret), "exported " + secret);
        }
        Backup back = NebulaSettingsBackup.decode(plain);
        ok(back.version == 2 && back.ignored == 0 && back.connections.isEmpty(), "clean file");
        Map<String, Object> got = back.stores.get("nebulagram");
        ok(Integer.valueOf(42).equals(got.get("avatar_round")), "int stays int");
        ok(Boolean.TRUE.equals(got.get("hide_send_as")), "schema key kept despite its words");
        ok(Float.valueOf(0.7f).equals(got.get("glass_opacity")), "float stays float: " + got.get("glass_opacity"));
        ok(Long.valueOf(1731400000000L).equals(got.get("menu_seen_at")), "long stays long");
        ok(new HashSet<>(Arrays.asList("a", "b", "ж")).equals(got.get("muted_in_tab_counters")), "string set");
        ok("Привет \"мир\"\n\t\\   😀".equals(got.get("home_chats_title")), "string escapes round-trip");
        ok(got.size() == 7, "only the seven settings: " + got.keySet());
        ok(Float.valueOf(0.25f).equals(back.stores.get("nebula_ai_settings").get("temperature")), "second store");
        ok(!back.stores.get("nebula_ai_settings").containsKey("provider_api_key"), "AI key never exported");
        ok(NebulaSettingsBackup.diff(raw, back).isEmpty(), "re-importing the same file changes nothing");
        // Encoding is stable: same input, same bytes (sorted keys).
        ok(plain.equals(NebulaSettingsBackup.encode(raw, null, "13.2.1", 1L)), "deterministic");

        // Connections only when the user asked for them.
        List<Connection> subs = new ArrayList<>();
        subs.add(new Connection("Home", "https://panel.example/sub/abc", "remnawave"));
        subs.add(new Connection("", "javascript:alert(1)", "x"));
        String keyed = NebulaSettingsBackup.encode(raw, subs, "13.2.1", 1L);
        Backup withKeys = NebulaSettingsBackup.decode(keyed);
        ok(withKeys.connections.size() == 1 && withKeys.connections.get(0).url.equals("https://panel.example/sub/abc"), "connections");
        ok(NebulaSettingsBackup.newConnections(Collections.singleton("https://panel.example/sub/abc"), withKeys).isEmpty(), "known subscription skipped");
        ok(NebulaSettingsBackup.newConnections(new HashSet<>(), withKeys).size() == 1, "new subscription offered");

        // Change list: only what differs, before and after.
        Map<String, Map<String, Object>> now = new HashMap<>();
        Map<String, Object> current = new HashMap<>(main);
        current.put("avatar_round", 10); current.remove("bottom_bar_order");
        current.put("muted_in_tab_counters", new LinkedHashSet<>(Arrays.asList("ж", "a", "b")));
        current.put("unrelated_setting", true);
        now.put("nebulagram", current); now.put("nebula_ai_settings", ai);
        List<Change> changes = NebulaSettingsBackup.diff(now, back);
        ok(changes.size() == 2, "two changes: " + changes.size());
        Map<String, Change> byKey = new HashMap<>();
        for (Change change : changes) byKey.put(change.key, change);
        ok(Integer.valueOf(10).equals(byKey.get("avatar_round").before) && Integer.valueOf(42).equals(byKey.get("avatar_round").after), "before/after");
        ok(byKey.get("bottom_bar_order").before == null, "new key has no before");
        ok(NebulaSettingsBackup.describe(null).equals("—") && NebulaSettingsBackup.describe(new HashSet<>(Arrays.asList("a"))).equals("[1]"), "describe");
        ok(NebulaSettingsBackup.describe("x".repeat(100)).length() == 40, "describe trims");

        // Secrets smuggled into a file are skipped, not imported.
        Backup smuggled = NebulaSettingsBackup.decode(wrap("{\"nebulagram\":{\"api_key\":{\"type\":\"string\",\"value\":\"k\"},"
                + "\"avatar_round\":{\"type\":\"int\",\"value\":5}},\"nebulagram_badges\":{\"admin_token\":{\"type\":\"string\",\"value\":\"t\"}}}"));
        ok(smuggled.ignored == 2 && smuggled.stores.get("nebulagram").size() == 1, "smuggled secrets skipped: " + smuggled.ignored);

        // Version 1: the old flat allowlist file still imports.
        Backup v1 = NebulaSettingsBackup.decode("{\"format\":\"NebulaGram-settings\",\"version\":1,\"settings\":{\"avatar_round\":40,\"hide_dividers\":true,\"unknown\":1}}");
        ok(v1.version == 1 && Integer.valueOf(40).equals(v1.stores.get("nebulagram").get("avatar_round")) && v1.ignored == 1, "v1");

        // Damaged and hostile files.
        rejects("", "empty");
        rejects("[]", "array root");
        rejects("{\"format\":\"Other\",\"version\":2,\"stores\":{}}", "format");
        rejects("{\"format\":\"NebulaGram-settings\",\"version\":3,\"stores\":{}}", "future version");
        rejects("{\"format\":\"NebulaGram-settings\",\"version\":2.5,\"stores\":{}}", "fractional version");
        rejects("{\"format\":\"NebulaGram-settings\",\"version\":2}", "no stores");
        rejects(wrap("{}") + "x", "trailing data");
        rejects(wrap("{\"nebulagram\":{\"avatar_round\":{\"type\":\"string\",\"value\":\"5\"}}}"), "schema type mismatch");
        rejects(wrap("{\"nebulagram\":{\"x_count\":{\"type\":\"int\",\"value\":3000000000}}}"), "int overflow");
        rejects(wrap("{\"nebulagram\":{\"x_count\":{\"type\":\"int\",\"value\":1.5}}}"), "fractional int");
        rejects(wrap("{\"nebulagram\":{\"x_count\":{\"type\":\"double\",\"value\":1}}}"), "unknown type");
        rejects(wrap("{\"nebulagram\":{\"x_count\":5}}"), "untyped value");
        rejects(wrap("{\"nebulagram\":{\"x\":{\"type\":\"string_set\",\"value\":[1]}}}"), "set of numbers");
        rejects(wrap("{\"nebulagram\":{\"x\":{\"type\":\"string\",\"value\":\"" + "a".repeat(70000) + "\"}}}"), "huge string");
        rejects(wrap("{\"nebulagram\":{\"x\":{\"type\":\"boolean\",\"value\":true},\"x\":{\"type\":\"boolean\",\"value\":false}}}"), "duplicate key");
        rejects(wrap("{\"nebulagram\":{\"x\":{\"type\":\"float\",\"value\":1e400}}}"), "infinite float");
        rejects(wrap("{\"nebulagram\":" + "[".repeat(40) + "]".repeat(40) + "}"), "deep nesting");
        rejects(wrap("{\"nebulagram\":{\"x\":{\"type\":\"string\",\"value\":\"a\u0001\"}}}"), "raw control character");
        rejects(wrap("{}").replace("}}", "},\"connections\":[{\"url\":\"file:///etc/passwd\"}]}"), "non-http connection");
        rejects(wrap("{}").replace("}}", "},\"connections\":[{\"url\":\"https://a.b/c\",\"kind\":\"../x\"}]}"), "bad kind");
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 65; i++) many.append(i == 0 ? "" : ",").append("{\"url\":\"https://a.b/").append(i).append("\"}");
        rejects(wrap("{}").replace("}}", "},\"connections\":" + many + "]}"), "too many connections");
        System.out.println(checks + " settings backup checks passed");
        System.out.print(plain);
    }
}
'''


def main():
    javac, java = shutil.which('javac'), shutil.which('java')
    if not javac or not java:
        raise SystemExit('A JDK is required for the settings backup check')
    with tempfile.TemporaryDirectory(prefix='nebula-backup-') as temp:
        tree = Path(temp)
        (tree / 'CheckSettingsBackup.java').write_text(CHECK, encoding='utf-8')
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(tree / 'classes'), str(UI / 'NebulaSettingsBackup.java'),
                        str(UI / 'NebulaSettingsSchema.java'), str(tree / 'CheckSettingsBackup.java')], check=True)
        run = subprocess.run([java, '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-cp', str(tree / 'classes'),
                              'CheckSettingsBackup'], check=True, capture_output=True)
    out = run.stdout.decode('utf-8')
    summary, document = out.split('\n', 1)
    print(summary)
    # The file is real JSON that other tools can read, and versioned.
    parsed = json.loads(document)
    assert parsed['format'] == 'NebulaGram-settings' and parsed['version'] == 2
    assert parsed['stores']['nebulagram']['avatar_round'] == {'type': 'int', 'value': 42}
    assert parsed['stores']['nebulagram']['muted_in_tab_counters']['value'] == ['a', 'b', 'ж']

    transfer = (UI / 'NebulaSettingsTransfer.java').read_text(encoding='utf-8')
    assert 'Intent.ACTION_CREATE_DOCUMENT' in transfer and 'Intent.ACTION_OPEN_DOCUMENT' in transfer, 'SAF picker'
    assert 'NebulaSettingsBackup.encode(currentStores(c), pendingConnections' in transfer
    assert 'if (!keys.isChecked()) { openExport(f); return; }' in transfer, 'connection keys are opt-in'
    assert '.checked(false)' in transfer, 'the keys switch starts off'
    assert 'Включить ключи подключения' in transfer and 'Include connection keys' in transfer
    assert 'NebulaSettingsBackup.diff(currentStores(c), backup)' in transfer, 'import shows what will change'
    assert 'Импортировать настройки?' in transfer and '(dialog, which) -> apply(f, backup, added)' in transfer, 'import waits for confirmation'
    assert 'putInt(key, (Integer) value)' in transfer and 'putLong(key, (Long) value)' in transfer and 'putFloat(key, (Float) value)' in transfer
    # The only writer of preferences in the import path is apply(), after confirmation.
    before_confirm = transfer[transfer.index('public static boolean result('):transfer.index('private static void confirmImport(')]
    assert '.edit()' not in before_confirm, 'nothing is written before the user confirms'
    print('settings backup flow checked')


if __name__ == '__main__':
    main()
