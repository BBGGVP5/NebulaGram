#!/usr/bin/env python3
"""Собирает строки экранов NebulaLink из схемы ядра.

Схема в core/settings/menu.go задаёт для каждой строки ключ и английский текст.
Android ищет перевод по этому ключу, и если ресурса нет — показывает английский
из схемы. Скрипт вынимает ключи из Go и раскладывает их в ресурсы, чтобы русский
язык не приходилось поддерживать вручную в двух местах.

    python scripts/gen-menu-strings.py

Новый ключ без перевода попадёт в русский файл с английским текстом, и скрипт
об этом скажет — так его видно сразу, а не после сборки.
"""

from __future__ import annotations

import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEMA = os.path.join(ROOT, "core", "settings", "menu.go")
OVERLAY = os.path.join(ROOT, "platform", "android", "overlay",
                      "TMessagesProj", "src", "main", "res")

RUSSIAN = {
    "nl_title": "NebulaLink",
    "nl_connection": "Соединение",
    "nl_current_server": "Текущий сервер",
    "nl_auto_connect": "Автоподключение",
    "nl_auto_connect_sub": "Подключаться к выбранному серверу при запуске Telegram",
    "nl_sec_connection": "ПОДКЛЮЧЕНИЕ",
    "nl_source": "Источник серверов",
    "nl_source_sub": "Подписки, ключи и фильтры",
    "nl_open_provider": "Страница провайдера",
    "nl_open_provider_sub": "Пополнить, продлить, прочитать объявления",
    "nl_advanced": "Дополнительно",
    "nl_advanced_sub": "Способ замера, идентификатор и ядра",
    "nl_servers": "Серверы NebulaLink",
    "nl_sec_source": "ИСТОЧНИК И ФИЛЬТРЫ",
    "nl_provider": "Провайдер",
    "nl_provider_sub": "Встроенный или свой источник",
    "nl_search": "Поиск",
    "nl_search_sub": "По имени сервера или источника",
    "nl_protocol": "Протокол",
    "nl_protocol_sub": "Показывать только выбранный",
    "nl_all_protocols": "Все протоколы",
    "nl_sec_actions": "ДЕЙСТВИЯ",
    "nl_refresh": "Обновить подписки",
    "nl_refresh_sub": "Загрузить свежие серверы из сохранённых источников",
    "nl_check_page": "Проверить задержку",
    "nl_check_page_sub": "Измеряет серверы на текущей странице",
    "nl_add_key": "Добавить ключ сервера",
    "nl_add_key_sub": "VLESS, VMess, Trojan, Shadowsocks, Hysteria2 или TUIC",
    "nl_add_sub": "Добавить подписку",
    "nl_add_sub_sub": "Ссылка на панель Remnawave или обычную подписку",
    "nl_clear": "Очистить серверы",
    "nl_clear_sub": "Подписки сохранятся, туннель отключится",
    "nl_sec_servers": "СЕРВЕРЫ",
    "nl_server_list": "Список серверов",
    "nl_per_page": "Серверов на странице",
    "nl_per_page_sub": "Больше сразу — меньше перелистывания",
    "nl_mode": "Режим туннеля",
    "nl_mode_sub": "Только мессенджер или всё устройство",
    "nl_mode_proxy": "Только мессенджер",
    "nl_mode_vpn": "Всё устройство (VPN)",
    "nl_ping_type": "Тип проверки",
    "nl_ping_tcp": "TCP",
    "nl_ping_url": "URL",
    "nl_hwid": "Идентификатор устройства",
    "nl_hwid_sub": "Отправляется панели с лимитом устройств",
    "nl_refresh_on_start": "Обновлять подписки при запуске",
    "nl_refresh_on_start_sub": "При открытии, если списки устарели",
    "nl_auto_ping": "Автопроверка задержки",
    "nl_auto_ping_sub": "Периодически и всегда по TCP, чтобы не рвать соединение",
    "nl_sec_calls": "ЗВОНКИ",
    "nl_call_state": "Состояние звонков",
    "nl_call_state_sub": "Перехваты, запросы, отправлено, получено",
    "nl_route_calls": "Звонки через NebulaLink",
    "nl_route_calls_sub": "Медиа звонка идёт через ваш сервер, без сторонних релеев",
    "nl_sec_core": "ЯДРО",
    "nl_switch_on_failure": "Менять сервер при обрыве",
    "nl_dual_core": "Оба ядра сразу",
    "nl_dual_core_sub": "Xray и sing-box загружены вместе, переключение мгновенное",
    "nl_socks_port": "Локальный порт SOCKS",
    "nl_dns": "DNS внутри туннеля",
    "nl_versions": "Версии компонентов",
    "nl_about": "О NebulaLink",
    "nl_export_logs": "Выгрузить журнал",
    "nl_reset": "Сбросить настройки NebulaLink",
    "nl_proto_vless": "VLESS",
    "nl_proto_vmess": "VMess",
    "nl_proto_trojan": "Trojan",
    "nl_proto_ss": "Shadowsocks",
    "nl_proto_hy2": "Hysteria2",
    "nl_proto_tuic": "TUIC",
}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Строки экранов NebulaLink. Сгенерировано scripts/gen-menu-strings.py из
  core/settings/menu.go — руками не править, правка потеряется.
-->
<resources>
"""


def collect() -> dict[str, str]:
    """Вынимает пары ключ → английский текст из схемы."""
    source = open(SCHEMA, encoding="utf-8").read()
    found: dict[str, str] = {}
    for key_field, text_field in (("TitleKey", "Title"), ("SubtitleKey", "Subtitle")):
        pattern = re.compile(
            key_field + r': "([a-z0-9_]+)",\s*' + text_field + r': "((?:[^"\\]|\\.)*)"')
        for key, text in pattern.findall(source):
            found.setdefault(key, text)
    return found


def escape(value: str) -> str:
    return (value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("'", "\\'").replace('"', "\\\""))


def write(path: str, strings: dict[str, str]) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(HEADER)
        for key in sorted(strings):
            handle.write(f'    <string name="{key}">{escape(strings[key])}</string>\n')
        handle.write("</resources>\n")
    print("  ", os.path.relpath(path, ROOT), f"({len(strings)})")


def main() -> None:
    english = collect()
    if not english:
        raise SystemExit("в схеме не нашлось ни одного ключа — проверьте путь")

    russian = {}
    missing = []
    for key, text in english.items():
        if key in RUSSIAN:
            russian[key] = RUSSIAN[key]
        else:
            russian[key] = text
            missing.append(key)

    write(os.path.join(OVERLAY, "values", "strings_nebula_menu.xml"), english)
    write(os.path.join(OVERLAY, "values-ru", "strings_nebula_menu.xml"), russian)

    if missing:
        print("без перевода (пока по-английски):", ", ".join(sorted(missing)))


if __name__ == "__main__":
    main()
