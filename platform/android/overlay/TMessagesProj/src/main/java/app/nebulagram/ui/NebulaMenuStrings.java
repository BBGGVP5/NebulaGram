package app.nebulagram.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Подписи экранов NebulaLink. Файл создаёт scripts/gen-menu-strings.py: правьте
 * схему в core/settings/menu.go и словарь в скрипте, а не этот файл.
 *
 * <p>Таблица в коде, а не ресурсы. Сборка Telegram переносит строки в бинарную
 * локализацию и выписывает исходным ресурсам tools:discard, после чего шринкер
 * их удаляет. Искать такой ресурс по имени через getIdentifier нельзя — он
 * вернёт ноль, и весь экран откатится на английский из схемы.
 */
public final class NebulaMenuStrings {
    private NebulaMenuStrings() { }

    private static final Map<String, String[]> TEXTS = new HashMap<>();

    static {
        TEXTS.put("nl_about", new String[]{"О NebulaLink", "About NebulaLink"});
        TEXTS.put("nl_add_key", new String[]{"Добавить ключ сервера", "Add server key"});
        TEXTS.put("nl_add_key_sub", new String[]{"VLESS, VMess, Trojan, Shadowsocks, Hysteria2 или TUIC", "VLESS, VMess, Trojan, Shadowsocks, Hysteria2 or TUIC"});
        TEXTS.put("nl_add_sub", new String[]{"Добавить подписку", "Add subscription"});
        TEXTS.put("nl_add_sub_sub", new String[]{"Ссылка на панель Remnawave или обычную подписку", "HTTP or HTTPS link to a Remnawave panel"});
        TEXTS.put("nl_advanced", new String[]{"Настройки соединения", "Connection settings"});
        TEXTS.put("nl_advanced_sub", new String[]{"Автоподключение, пинг и звонки", "Automatic connection, ping and calls"});
        TEXTS.put("nl_all_protocols", new String[]{"Все протоколы", "All protocols"});
        TEXTS.put("nl_auto_connect", new String[]{"Автоподключение", "Connect automatically"});
        TEXTS.put("nl_auto_connect_sub", new String[]{"Подключаться к выбранному серверу при запуске Telegram", "Connect to the selected server when Telegram starts"});
        TEXTS.put("nl_auto_ping", new String[]{"Автопроверка задержки", "Automatic latency check"});
        TEXTS.put("nl_auto_ping_sub", new String[]{"Периодически и всегда по TCP, чтобы не рвать соединение", "Periodic, always over TCP so the tunnel is never interrupted"});
        TEXTS.put("nl_call_state", new String[]{"Состояние звонков", "Call state"});
        TEXTS.put("nl_call_state_sub", new String[]{"Маршрут, время в эфире, отправлено и получено", "Route, airtime, sent and received"});
        TEXTS.put("nl_check_page", new String[]{"Проверить задержку", "Check page"});
        TEXTS.put("nl_check_page_sub", new String[]{"Измеряет серверы на текущей странице", "Measures the servers currently visible"});
        TEXTS.put("nl_clear", new String[]{"Очистить серверы", "Clear servers"});
        TEXTS.put("nl_clear_sub", new String[]{"Подписки сохранятся, туннель отключится", "Subscriptions are kept, the tunnel is stopped"});
        TEXTS.put("nl_connection", new String[]{"Соединение", "Connection"});
        TEXTS.put("nl_current_server", new String[]{"Текущий сервер", "Current server"});
        TEXTS.put("nl_dns", new String[]{"DNS внутри туннеля", "DNS inside the tunnel"});
        TEXTS.put("nl_dual_core", new String[]{"Оба ядра сразу", "Both cores at once"});
        TEXTS.put("nl_dual_core_sub", new String[]{"Xray и sing-box загружены вместе, переключение мгновенное", "Keeps Xray and sing-box loaded, so switching protocols is instant"});
        TEXTS.put("nl_export_logs", new String[]{"Выгрузить журнал", "Export logs"});
        TEXTS.put("nl_guard", new String[]{"NebulaGuard", "NebulaGuard"});
        TEXTS.put("nl_guard_sub", new String[]{"Продление, пополнение и поддержка", "Top up, renew and get support"});
        TEXTS.put("nl_hwid", new String[]{"Идентификатор устройства", "HWID"});
        TEXTS.put("nl_hwid_sub", new String[]{"Отправляется панели с лимитом устройств", "Device id sent to a device-limited panel"});
        TEXTS.put("nl_mode", new String[]{"Режим туннеля", "Tunnel mode"});
        TEXTS.put("nl_mode_proxy", new String[]{"Только мессенджер", "Messenger only (local proxy)"});
        TEXTS.put("nl_mode_sub", new String[]{"Только мессенджер или всё устройство", "Messenger only, or the whole device"});
        TEXTS.put("nl_mode_vpn", new String[]{"Всё устройство (VPN)", "Whole device (VPN)"});
        TEXTS.put("nl_open_provider", new String[]{"Профиль подписки", "Subscription profile"});
        TEXTS.put("nl_open_provider_sub", new String[]{"Оплата и поддержка вашего провайдера", "Your provider's payment and support page"});
        TEXTS.put("nl_per_page", new String[]{"Серверов на странице", "Servers per page"});
        TEXTS.put("nl_per_page_sub", new String[]{"Больше сразу — меньше перелистывания", "More servers at once, less paging"});
        TEXTS.put("nl_ping_http", new String[]{"HTTP GET", "HTTP GET"});
        TEXTS.put("nl_ping_nimbo", new String[]{"Nimbo Ping", "Nimbo Ping"});
        TEXTS.put("nl_ping_tcp", new String[]{"TCP", "TCP"});
        TEXTS.put("nl_ping_type", new String[]{"Тип проверки", "Ping type"});
        TEXTS.put("nl_ping_url", new String[]{"URL", "URL"});
        TEXTS.put("nl_protocol", new String[]{"Протокол", "Protocol"});
        TEXTS.put("nl_protocol_sub", new String[]{"Показывать только выбранный", "Show only the selected protocol"});
        TEXTS.put("nl_provider", new String[]{"Провайдер", "Provider"});
        TEXTS.put("nl_provider_sub", new String[]{"Встроенный или свой источник", "Pick a built-in or custom source"});
        TEXTS.put("nl_refresh", new String[]{"Обновить подписки", "Refresh subscriptions"});
        TEXTS.put("nl_refresh_on_start", new String[]{"Обновлять подписки при запуске", "Refresh subscriptions on launch"});
        TEXTS.put("nl_refresh_on_start_sub", new String[]{"При открытии, если списки устарели", "On open, when the lists are stale"});
        TEXTS.put("nl_refresh_sub", new String[]{"Загрузить свежие серверы из сохранённых источников", "Fetch fresh servers from saved sources"});
        TEXTS.put("nl_reset", new String[]{"Сбросить настройки NebulaLink", "Reset NebulaLink settings"});
        TEXTS.put("nl_route_calls", new String[]{"Звонки через NebulaLink", "Calls through NebulaLink"});
        TEXTS.put("nl_route_calls_sub", new String[]{"Медиа звонка идёт через ваш сервер, без сторонних релеев", "Call media goes through your server, with no third-party relays"});
        TEXTS.put("nl_search", new String[]{"Поиск", "Search"});
        TEXTS.put("nl_search_sub", new String[]{"По имени сервера или источника", "By server or source name"});
        TEXTS.put("nl_sec_actions", new String[]{"ДЕЙСТВИЯ", "ACTIONS"});
        TEXTS.put("nl_sec_calls", new String[]{"ЗВОНКИ", "CALLS"});
        TEXTS.put("nl_sec_connection", new String[]{"ПОДКЛЮЧЕНИЕ", "CONNECTION"});
        TEXTS.put("nl_sec_core", new String[]{"ЯДРО", "CORE"});
        TEXTS.put("nl_sec_servers", new String[]{"СЕРВЕРЫ", "SERVERS"});
        TEXTS.put("nl_sec_source", new String[]{"ИСТОЧНИК И ФИЛЬТРЫ", "SOURCE AND FILTERS"});
        TEXTS.put("nl_server_list", new String[]{"Список серверов", "Server list"});
        TEXTS.put("nl_servers", new String[]{"Серверы NebulaLink", "NebulaLink servers"});
        TEXTS.put("nl_socks_port", new String[]{"Локальный порт SOCKS", "Local SOCKS port"});
        TEXTS.put("nl_source", new String[]{"Подписки и ключи", "Subscriptions and keys"});
        TEXTS.put("nl_source_sub", new String[]{"Добавление, обновление и управление серверами", "Import, refresh and manage servers"});
        TEXTS.put("nl_switch_on_failure", new String[]{"Менять сервер при обрыве", "Switch server on failure"});
        TEXTS.put("nl_title", new String[]{"NebulaLink", "NebulaLink"});
        TEXTS.put("nl_versions", new String[]{"Версии компонентов", "Component versions"});
    }

    /** Перевод по ключу схемы; fallback — английский текст из самой схемы. */
    public static String text(String key, String fallback) {
        String[] pair = key == null || key.isEmpty() ? null : TEXTS.get(key);
        return pair == null ? fallback : NebulaText.text(pair[0], pair[1]);
    }
}
