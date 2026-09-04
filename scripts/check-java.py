#!/usr/bin/env python3
"""Быстрые проверки нашего Java-кода до сборки.

Компилятор локально недоступен: ему нужен Android SDK и всё дерево Telegram.
Но самые дорогие ошибки — грубые: переменная, объявленная дважды в одной
области видимости. Такая проверка стоит секунду и экономит двадцать пять
минут сборки, потраченных впустую.

    python scripts/check-java.py
"""

from __future__ import annotations

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(ROOT, "platform", "android", "overlay",
                       "TMessagesProj", "src", "main", "java", "app", "nebulagram")

# Объявление локальной переменной: тип, имя, одиночное «равно». Обобщения и
# массивы входят в тип, поэтому угловые и квадратные скобки допускаются.
DECLARATION = re.compile(
    r'^\s*(?:final\s+)?([A-Za-z_][\w.]*(?:<[^;=]*>)?(?:\[\])?)\s+([a-z]\w*)\s*=[^=]')

# Слова, после которых идёт не объявление, а выражение.
KEYWORDS = {"return", "new", "case", "else", "throw", "assert", "break", "continue"}

STRING = re.compile(r'"(?:[^"\\]|\\.)*"')
CHAR = re.compile(r"'(?:[^'\\]|\\.)'")
COMMENT = re.compile(r'//.*$')


def duplicates(text: str):
    """Ищет повторные объявления, следя за вложенностью блоков.

    Одинаковые имена в разных блоках Java разрешает — в двух ветках if это
    обычное дело. Значит важна не принадлежность методу, а перекрытие
    областей: ошибка только если имя уже занято в этом же блоке или в
    объемлющем.
    """
    stack = [set()]
    for number, raw in enumerate(text.splitlines(), 1):
        # Литералы и комментарии убираем: скобки внутри них блоков не открывают.
        line = COMMENT.sub("", CHAR.sub("' '", STRING.sub('""', raw)))

        match = DECLARATION.match(line)
        if match:
            kind, variable = match.groups()
            if kind not in KEYWORDS and variable not in KEYWORDS:
                if any(variable in scope for scope in stack):
                    yield number, variable
                stack[-1].add(variable)

        for character in line:
            if character == "{":
                stack.append(set())
            elif character == "}" and len(stack) > 1:
                stack.pop()


# Классы, которые есть всегда: пакет java.lang и примитивы.
BUILTIN = {
    "String", "Integer", "Boolean", "Float", "Double", "Long", "Math", "Object",
    "System", "Character", "Byte", "Short", "Number", "Thread", "Throwable",
    "Exception", "RuntimeException", "Class", "Runnable", "StringBuilder",
    "CharSequence", "Void", "Override", "Deprecated", "SuppressWarnings",
}


# Вложенные типы, видимые по наследству от View и его родителей: импорта не
# требуют, но выглядят как обращение к чужому классу.
INHERITED = {"MeasureSpec", "LayoutParams", "OnClickListener", "OnTouchListener",
             "Align", "Style", "Cap", "Join", "Direction", "Mode", "Op", "Config",
             "ScaleType", "TruncateAt", "Editor", "FontMetrics", "FontMetricsInt"}


def unresolved_classes(text: str, own: set):
    """Ищет обращения вида Класс.метод без импорта.

    Компилятор такое ловит мгновенно, а мы узнавали через двадцать пять минут
    сборки. Проверка простая: если имя начинается с заглавной, за ним точка, и
    оно не импортировано, не наше и не из java.lang — значит забыт импорт.
    """
    imported = set(re.findall(r'^import\s+(?:static\s+)?[\w.]*?(\w+);', text, re.M))
    declared = set(re.findall(r'(?:class|interface|enum)\s+(\w+)', text))
    used = set(re.findall(r'(?<![\w.])([A-Z][A-Za-z0-9]+)\.[a-zA-Z]', text))
    for name in sorted(used - imported - own - BUILTIN - declared - INHERITED):
        # Константы пишутся заглавными и классами не являются.
        if name.upper() == name:
            continue
        yield name

def main() -> int:
    problems = []
    # Соседние классы видны без импорта: они в том же пакете.
    own = set()
    for root, _, files in os.walk(SOURCES):
        for name in files:
            if not name.endswith(".java"):
                continue
            own.add(name[:-5])
            body = io.open(os.path.join(root, name), encoding="utf-8").read()
            own |= set(re.findall(r'(?:class|interface|enum)\s+(\w+)', body))
    for root, _, files in os.walk(SOURCES):
        for filename in sorted(files):
            if not filename.endswith(".java"):
                continue
            text = io.open(os.path.join(root, filename), encoding="utf-8").read()
            for number, variable in duplicates(text):
                problems.append("%s:%d — %s уже объявлена в этой области"
                                % (filename, number, variable))
            for name in unresolved_classes(text, own):
                problems.append("%s — %s используется без импорта" % (filename, name))
    if problems:
        print("Повторные объявления:")
        for problem in problems:
            print("  " + problem)
        return 1
    print("Java-проверка: повторных объявлений нет")
    return 0


if __name__ == "__main__":
    sys.exit(main())
