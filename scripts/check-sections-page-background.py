#!/usr/bin/env python3
"""Lists with NebulaGram cards must paint the page under them and keep rows transparent.

«Настройки чатов» (ThemeActivity) и участники группы (ChatUsersActivity) стоят
списком прямо на окне. В стиле NebulaGram карточки рисуются только при
выключенном setSectionsDrawBackground, и тогда серый фон между ними не рисовал
никто: в зазорах и по краям оставались прошлые кадры, края карточек и голубая
обводка размножались при прокрутке. Кроме того, при смене темы ThemeDescription
с FLAG_CELLBACKGROUNDCOLOR заливал видимые строки непрозрачным
windowBackgroundWhite, и строка («Настройки темы») выделялась на карточке
другим тоном.

Проверка:
1. компилирует настоящий NebulaProfileStyle.SectionsPage в заглушке и сверяет,
   что он закрашивает весь bounds непрозрачным windowBackgroundGray,
   а sectionsPage/cellBackgrounds зависят от стиля;
2. в подготовленном дереве каждый экран, где NebulaProfileStyle.sections стоит
   на окне, сразу ставит sectionsPage, а заливка строк темой выключена.
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
style_path = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProfileStyle.java'


def fail(message):
    raise SystemExit('check-sections-page-background: ' + message)


def block(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 0
    for i in range(brace, len(source)):
        if source[i] == '{':
            depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0:
                return source[start:i + 1]
    fail('unterminated ' + signature)


style = style_path.read_text(encoding='utf-8')
page_class = block(style, 'static final class SectionsPage extends Drawable')
page_method = block(style, 'public static void sectionsPage(')
cells_method = block(style, 'public static boolean cellBackgrounds()')

# 2. Экраны, где список на окне. ProfileActivity и SharedMediaLayout красят
# страницу сами (PageBackground / цвет профиля) и сюда не входят.
screens = {
    'ThemeActivity.java': 'currentType != THEME_TYPE_BASIC || app.nebulagram.ui.NebulaProfileStyle.cellBackgrounds()',
    'ChatUsersActivity.java': 'app.nebulagram.ui.NebulaProfileStyle.cellBackgrounds()',
}
for name, guard in screens.items():
    source = (java / 'org/telegram/ui' / name).read_text(encoding='utf-8')
    lines = source.splitlines()
    calls = [i for i, line in enumerate(lines) if 'NebulaProfileStyle.sections(listView' in line]
    if len(calls) != 1:
        fail(f'{name}: expected one NebulaProfileStyle.sections call, found {len(calls)}')
    following = '\n'.join(lines[calls[0] + 1:calls[0] + 4])
    if 'NebulaProfileStyle.sectionsPage(listView' not in following:
        fail(f'{name}: the page under the cards is not painted (sectionsPage missing after sections)')
    for line in lines:
        if 'FLAG_CELLBACKGROUNDCOLOR' in line and 'listView' in line and 'key_windowBackgroundWhite' in line:
            if guard not in line.split('themeDescriptions.add')[0]:
                fail(f'{name}: theme still fills card rows with windowBackgroundWhite: {line.strip()[:120]}')

stub = r'''
package app.nebulagram.ui;
import java.util.*;
class Rect { int left, top, right, bottom; Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; } }
class Paint { int color; void setColor(int c) { color = c; } }
class ColorFilter { }
class PixelFormat { static final int OPAQUE = -1; }
class Canvas {
  final List<String> log = new ArrayList<>();
  void drawRect(Rect r, Paint p) { log.add(r.left + "," + r.top + "," + r.right + "," + r.bottom + " " + Integer.toHexString(p.color)); }
}
abstract class Drawable {
  Rect bounds = new Rect(0, 0, 0, 0);
  Rect getBounds() { return bounds; }
  Object background;
  public abstract void draw(Canvas c);
  public abstract void setAlpha(int a);
  public abstract void setColorFilter(ColorFilter f);
  public abstract int getOpacity();
}
class Theme {
  interface ResourcesProvider { }
  static final int key_windowBackgroundGray = 7;
  static int gray = 0x40121314;
  static int getColor(int key, ResourcesProvider p) { if (key != key_windowBackgroundGray) throw new AssertionError("wrong key " + key); return gray; }
}
class NebulaAppearance { static boolean style; static boolean profileStyle() { return style; } }
class RecyclerListView { Drawable background; void setBackground(Drawable d) { background = d; } }
public class SectionsPageCheck {
  PAGE_METHOD
  CELLS_METHOD
  PAGE_CLASS
  static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
  public static void main(String[] args) {
    RecyclerListView list = new RecyclerListView();
    NebulaAppearance.style = false;
    sectionsPage(list, null);
    check(list.background == null, "without NebulaGram cards the upstream decoration paints the page");
    check(cellBackgrounds(), "without NebulaGram cards rows keep the theme fill");
    NebulaAppearance.style = true;
    check(!cellBackgrounds(), "on NebulaGram cards the theme must not fill rows");
    sectionsPage(list, null);
    check(list.background instanceof SectionsPage, "NebulaGram cards: list gets a page background");
    Drawable page = list.background;
    check(page.getOpacity() == PixelFormat.OPAQUE, "page background is opaque");
    page.bounds = new Rect(0, 0, 400, 1200);
    Canvas canvas = new Canvas();
    page.draw(canvas);
    check(canvas.log.equals(Arrays.asList("0,0,400,1200 ff121314")), "page covers the whole list with opaque gray: " + canvas.log);
    Theme.gray = 0xff202122;
    canvas.log.clear();
    page.draw(canvas);
    check(canvas.log.equals(Arrays.asList("0,0,400,1200 ff202122")), "page follows a theme change without being recreated: " + canvas.log);
    System.out.println("sections page background: cards lie on a painted page, rows stay transparent");
  }
}
'''
source = (stub.replace('PAGE_METHOD', page_method)
              .replace('CELLS_METHOD', cells_method)
              .replace('PAGE_CLASS', page_class))

java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
exe = '.exe' if os.name == 'nt' else ''
javac = java_home / 'bin' / ('javac' + exe)
javabin = java_home / 'bin' / ('java' + exe)
if not javac.exists():
    javac, javabin = Path(shutil.which('javac')), Path(shutil.which('java'))
with tempfile.TemporaryDirectory(prefix='nebula-sections-page-') as directory:
    directory = Path(directory)
    package = directory / 'app/nebulagram/ui'
    package.mkdir(parents=True)
    (package / 'SectionsPageCheck.java').write_text(source, encoding='utf-8')
    subprocess.run([str(javac), '-encoding', 'UTF-8', '-d', str(directory), str(package / 'SectionsPageCheck.java')], check=True)
    subprocess.run([str(javabin), '-cp', str(directory), 'app.nebulagram.ui.SectionsPageCheck'], check=True)
