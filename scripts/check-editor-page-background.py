#!/usr/bin/env python3
"""Run SectionsScrollView.dispatchDraw: NebulaGram cards must not leave the page unpainted.

На экране «Изменить» группы (ChatEditActivity) карточки рисует NebulaGram
через setSectionBackground. Патч 0013 при этом отключал drawOptimizedBackground,
а заменить его было нечем: серый фон страницы не рисовал никто. В зазорах между
карточками, под подписями (TextInfoPrivacyCell) и под скруглёнными углами
оставались пиксели прошлых кадров — при прокрутке и анимации секций карточки,
обводки и подпись «Темы» размножались со сдвигом.

Проверка вырезает настоящий dispatchDraw из подготовленного дерева, компилирует
его в заглушке с записывающим Canvas и сверяет, что фон страницы закрашен на
весь видимый прямоугольник до карточек.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1] if len(sys.argv) > 1 else root / 'vendor/telegram-android')
java = tree / 'TMessagesProj/src/main/java'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def fail(message):
    raise SystemExit('check-editor-page-background: ' + message)


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                return source[brace + 1:i]
    fail('unterminated ' + signature)


scroll = (java / 'org/telegram/ui/Components/SectionsScrollView.java').read_text(encoding='utf-8')
body = method(scroll, 'protected void dispatchDraw(@NonNull Canvas canvas)')

# Экран действительно идёт по этой ветке: фон включён, карточки свои.
edit = (java / 'org/telegram/ui/ChatEditActivity.java').read_text(encoding='utf-8')
if 'scrollView.setDrawBackground(true);' not in edit:
    fail('ChatEditActivity no longer enables the page background; recheck the editor path')
if 'NebulaProfileStyle.editor(scrollView' not in edit:
    fail('ChatEditActivity no longer calls NebulaProfileStyle.editor')
style = (ui / 'NebulaProfileStyle.java').read_text(encoding='utf-8')
if not re.search(r'scroll\.setSectionBackground\(', method(style, 'public static void editor(')):
    fail('NebulaProfileStyle.editor no longer paints its own cards; recheck this check')

stub = r'''
import java.util.*;
public class PageBackgroundCheck {
  static final List<String> log = new ArrayList<>();
  static class Paint { final String name; Paint(String n) { name = n; } }
  static class Canvas {
    void drawRect(float l, float t, float r, float b, Paint p) { log.add("rect " + (int) l + "," + (int) t + "," + (int) r + "," + (int) b + " " + p.name); }
    void drawColor(int color) { log.add("color"); }
    void drawPaint(Paint p) { log.add("paint " + p.name); }
  }
  interface Section { void run(Canvas c, Object rect, Float a, Float b, Float alpha); }
  static class Scroll {
    boolean drawBackground;
    Section sectionBackground;
    final Paint backgroundPaint = new Paint("page");
    int getScrollX() { return 0; }
    int getScrollY() { return 700; }
    int getWidth() { return 400; }
    int getHeight() { return 900; }
    void updateColors() { }
    void collectSections() { }
    void drawOptimizedBackground(Canvas c) { log.add("optimized"); }
    void drawSectionsBackgrounds(Canvas c) { log.add("sections"); }
    void drawSectionCorners(Canvas c) { log.add("corners"); }
    void superDispatchDraw(Canvas c) { log.add("children"); }
    protected void dispatchDraw(Canvas canvas) {
      BODY
    }
  }
  static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why + ": " + log); }
  static List<String> run(boolean background, boolean cards) {
    log.clear();
    Scroll s = new Scroll();
    s.drawBackground = background;
    s.sectionBackground = cards ? (c, r, a, b, alpha) -> { } : null;
    s.dispatchDraw(new Canvas());
    return new ArrayList<>(log);
  }
  public static void main(String[] args) {
    List<String> editor = run(true, true);
    String full = "rect 0,700,400,1600 page";
    String paint = "paint page";
    int page = editor.indexOf(full) >= 0 ? editor.indexOf(full) : editor.indexOf(paint);
    check(page >= 0, "NebulaGram cards: the page background is not painted over the whole viewport");
    check(page < editor.indexOf("sections"), "NebulaGram cards: page background must be painted before the cards");
    check(editor.indexOf("sections") < editor.indexOf("children"), "NebulaGram cards: cards must be under the rows");
    check(!editor.contains("optimized") && !editor.contains("corners"), "NebulaGram cards keep their own rounded corners");

    List<String> upstream = run(true, false);
    check(upstream.equals(Arrays.asList("optimized", "children", "corners")), "upstream sections path changed");

    List<String> transparent = run(false, true);
    check(transparent.equals(Arrays.asList("sections", "children")), "screens without page background stay transparent");
    System.out.println("editor page background: cards are drawn over a freshly painted page");
  }
}
'''
body = body.replace('super.dispatchDraw(canvas);', 'superDispatchDraw(canvas);')
source = stub.replace('BODY', body)

java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
exe = '.exe' if os.name == 'nt' else ''
javac = java_home / 'bin' / ('javac' + exe)
javabin = java_home / 'bin' / ('java' + exe)
if not javac.exists():
    javac, javabin = Path(shutil.which('javac')), Path(shutil.which('java'))
with tempfile.TemporaryDirectory(prefix='nebula-editor-bg-') as directory:
    directory = Path(directory)
    (directory / 'PageBackgroundCheck.java').write_text(source, encoding='utf-8')
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-d', str(directory), str(directory / 'PageBackgroundCheck.java')], check=True)
    subprocess.run([str(javabin), '-cp', str(directory), 'PageBackgroundCheck'], check=True)
