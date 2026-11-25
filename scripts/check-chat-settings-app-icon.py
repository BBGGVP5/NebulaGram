#!/usr/bin/env python3
"""«Настройки чатов»: вместо нативного выбора иконок — строка, открывающая карусель NebulaGram.

1. В подготовленном дереве ThemeActivity строит для TYPE_APP_ICON
   NebulaAppIconRow, а не AppIconsSelectorCell, обновляет её при привязке,
   по нажатию открывает NebulaIconPickerFragment и не ставит отдельный
   заголовок секции.
2. Строка и карусель берут иконки из LauncherIconController NebulaGram.
3. NebulaAppIconRow компилируется против android.jar (если SDK найден) с
   заглушками Telegram, сигнатуры которых сверены с настоящими исходниками.
"""
from pathlib import Path
import glob
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
    raise SystemExit('check-chat-settings-app-icon: ' + message)


def need(source, pattern, why):
    if not re.search(pattern, source, re.S):
        fail(why)


theme = (java / 'org/telegram/ui/ThemeActivity.java').read_text(encoding='utf-8')
need(theme, r'case TYPE_APP_ICON:\s*view = new app\.nebulagram\.ui\.NebulaAppIconRow\(mContext, getResourceProvider\(\)\);',
     'TYPE_APP_ICON must create NebulaAppIconRow')
if re.search(r'new AppIconsSelectorCell\(', theme):
    fail('ThemeActivity still builds the native AppIconsSelectorCell')
need(theme, r'case TYPE_APP_ICON: \{\s*\(\(app\.nebulagram\.ui\.NebulaAppIconRow\) holder\.itemView\)\.update\(\);',
     'the row must re-read the current icon when bound (after the carousel, onResume rebinds)')
need(theme, r'setOnItemClickListener\(\(view, position, x, y\) -> \{\s*if \(position == appIconSelectorRow\) \{\s*presentFragment\(new app\.nebulagram\.ui\.NebulaIconPickerFragment\(\)\);',
     'tapping the row must open NebulaIconPickerFragment')
if re.search(r'appIconHeaderRow = rowCount\+\+', theme):
    fail('the app icon section keeps a separate header; the row names itself')
need(theme, r'appIconSelectorRow = rowCount\+\+;\s*appIconShadowRow = rowCount\+\+;', 'app icon row and its shadow must stay in place')
need(theme, r'type == TYPE_APP_ICON', 'the app icon row must stay clickable (isEnabled)')

row = (ui / 'NebulaAppIconRow.java').read_text(encoding='utf-8')
need(row, r'NebulaBrandPalette\.current\(\)', 'the row must show the launcher icon that is enabled now')
need(row, r'art\.setIcon\(current\)', 'the row preview must draw the current LauncherIcon')
picker = (ui / 'NebulaIconPickerFragment.java').read_text(encoding='utf-8')
need(picker, r'LauncherIconController\.LauncherIcon\.values\(\)', 'the carousel must list NebulaGram LauncherIconController icons')
palette = (ui / 'NebulaBrandPalette.java').read_text(encoding='utf-8')
need(palette, r'public static LauncherIconController\.LauncherIcon current\(\)', 'NebulaBrandPalette.current() signature changed')
need(palette, r'LauncherIconController\.isEnabled\(icon\)', 'current() must ask LauncherIconController')
launcher = (java / 'org/telegram/ui/LauncherIconController.java').read_text(encoding='utf-8')
need(launcher, r'DEFAULT\("DefaultIcon", R\.drawable\.nebula_launcher_', 'LauncherIconController is not the NebulaGram one')

# Сигнатуры, которыми пользуется строка, — в настоящих исходниках.
art = (ui / 'NebulaIconArt.java').read_text(encoding='utf-8')
need(art, r'public NebulaIconArt\(Context context\)', 'NebulaIconArt(Context) changed')
need(art, r'public void setIcon\(LauncherIconController\.LauncherIcon value\)', 'NebulaIconArt.setIcon changed')
need(art, r'public void setCornerFraction\(float value\)', 'NebulaIconArt.setCornerFraction changed')
labels = (ui / 'NebulaLauncherIconLabels.java').read_text(encoding='utf-8')
need(labels, r'public static String title\(Context context, LauncherIconController\.LauncherIcon icon\)', 'labels signature changed')
theme_api = (java / 'org/telegram/ui/ActionBar/Theme.java').read_text(encoding='utf-8')
for key in ('key_windowBackgroundWhiteBlackText', 'key_windowBackgroundWhiteValueText', 'key_windowBackgroundWhiteGrayIcon'):
    need(theme_api, r'public static final int ' + key + r'\b', 'Theme.' + key + ' missing')
need(theme_api, r'public static int getColor\(int key, ResourcesProvider \w+\)', 'Theme.getColor signature changed')
need(theme_api, r'public static Drawable getSelectorDrawable\(boolean whiteBackground\)', 'Theme.getSelectorDrawable changed')
res = tree / 'TMessagesProj/src/main/res'
if not list(res.glob('drawable*/msg_arrowright.*')):
    fail('R.drawable.msg_arrowright is missing')

sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk')
jars = sorted(glob.glob(str(Path(sdk) / 'platforms/android-*/android.jar')))
if not jars:
    print('chat settings app icon: hooks verified; android.jar not found, compile step skipped')
    sys.exit(0)

stubs = {
    'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities { public static int dp(float v) { return (int) v; } }',
    'org/telegram/messenger/LocaleController.java': 'package org.telegram.messenger; public class LocaleController { public static boolean isRTL; }',
    'org/telegram/messenger/R.java': 'package org.telegram.messenger; public final class R { public static final class drawable { public static final int msg_arrowright = 1; } }',
    'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar;
public class Theme { public interface ResourcesProvider { }
 public static final int key_windowBackgroundWhiteBlackText = 1, key_windowBackgroundWhiteValueText = 2, key_windowBackgroundWhiteGrayIcon = 3;
 public static int getColor(int key, ResourcesProvider resourcesProvider) { return 0; }
 public static android.graphics.drawable.Drawable getSelectorDrawable(boolean whiteBackground) { return null; } }''',
    'org/telegram/ui/LauncherIconController.java': 'package org.telegram.ui; public class LauncherIconController { public enum LauncherIcon { DEFAULT } }',
    'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public final class NebulaText { public static String text(String ru, String en) { return ru; } }',
    'app/nebulagram/ui/NebulaIconArt.java': '''package app.nebulagram.ui;
public class NebulaIconArt extends android.view.View { public NebulaIconArt(android.content.Context context) { super(context); }
 public void setIcon(org.telegram.ui.LauncherIconController.LauncherIcon value) { } public void setCornerFraction(float value) { } }''',
    'app/nebulagram/ui/NebulaBrandPalette.java': 'package app.nebulagram.ui; public final class NebulaBrandPalette { public static org.telegram.ui.LauncherIconController.LauncherIcon current() { return null; } }',
    'app/nebulagram/ui/NebulaLauncherIconLabels.java': 'package app.nebulagram.ui; public final class NebulaLauncherIconLabels { public static String title(android.content.Context c, org.telegram.ui.LauncherIconController.LauncherIcon i) { return ""; } }',
}
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = java_home / 'bin' / ('javac' + ('.exe' if os.name == 'nt' else ''))
if not javac.exists():
    javac = Path(shutil.which('javac'))
with tempfile.TemporaryDirectory(prefix='nebula-app-icon-row-') as directory:
    directory = Path(directory)
    files = []
    for path, text in stubs.items():
        target = directory / 'src' / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8')
        files.append(str(target))
    target = directory / 'src/app/nebulagram/ui/NebulaAppIconRow.java'
    target.write_text(row, encoding='utf-8')
    files.append(str(target))
    subprocess.run([str(javac), '-encoding', 'UTF-8', '--release', '17', '-proc:none', '-nowarn', '-cp', jars[-1],
                    '-d', str(directory / 'out'), *files], check=True)
print('chat settings app icon: the row opens the NebulaGram carousel and compiles against android.jar')
