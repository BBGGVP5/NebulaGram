#!/usr/bin/env python3
"""Переопределяет отображаемое имя приложения во всех локализациях.

Манифест ссылается на `@string/AppName`, а Telegram определяет эту строку в
каждой языковой папке отдельно. Ресурсы модуля приложения перекрывают ресурсы
библиотечного модуля, но только для той же квалификации: файл в `values/` не
перебьёт `values-ru/`. Поэтому override нужен для каждой папки, где апстрим
объявил AppName.

Скрипт сканирует апстрим и раскладывает файлы в оверлей. Запускать после
обновления Telegram: если появится новый язык, имя в нём вернётся к исходному,
пока скрипт не прогонят заново.

    python scripts/gen-app-name.py
"""

from __future__ import annotations

import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM = os.path.join(ROOT, "vendor", "telegram-android", "TMessagesProj", "src", "main", "res")
OVERLAY = os.path.join(ROOT, "platform", "android", "overlay",
                       "TMessagesProj_AppStandalone", "src", "main", "res")

APP_NAME = "NebulaGram"

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!--
  Имя приложения. Лежит в модуле приложения, потому что его ресурсы перекрывают
  библиотечные; файл создаётся для каждой языковой папки, где апстрим объявил
  AppName. Сгенерировано scripts/gen-app-name.py, руками не править.
-->
<resources>
    <string name="AppName">{name}</string>
    <string name="NebulaAppName">{name}</string>
</resources>
"""


def main() -> None:
    pattern = re.compile(r'<string name="AppName">')
    written = 0

    for path in sorted(glob.glob(os.path.join(UPSTREAM, "values*", "strings.xml"))):
        with open(path, encoding="utf-8") as handle:
            if not pattern.search(handle.read()):
                continue

        bucket = os.path.basename(os.path.dirname(path))
        target_dir = os.path.join(OVERLAY, bucket)
        os.makedirs(target_dir, exist_ok=True)
        target = os.path.join(target_dir, "strings_nebula_name.xml")
        with open(target, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(TEMPLATE.format(name=APP_NAME))
        print("  ", os.path.relpath(target, ROOT))
        written += 1

    if written == 0:
        raise SystemExit("апстрим нигде не объявляет AppName — проверьте путь")
    print(f"имя приложения переопределено в {written} локализациях")


if __name__ == "__main__":
    main()
