"""Check installed/preview artwork and execute native launcher switching without Android."""
from pathlib import Path
import hashlib
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
TREE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'vendor/telegram-android'
RES = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'
source = (TREE / 'TMessagesProj/src/main/java/org/telegram/ui/LauncherIconController.java').read_text(encoding='utf-8')
variants = [('DEFAULT', 'DefaultIcon', 'blue'), ('VINTAGE', 'VintageIcon', 'ocean'),
            ('AQUA', 'AquaIcon', 'aurora'), ('PREMIUM', 'PremiumIcon', 'sunset'),
            ('TURBO', 'TurboIcon', 'graphite'), ('NOX', 'NoxIcon', 'pearl')]
for enum, component, key in variants:
    expected = f'{enum}("{component}", R.drawable.nebula_launcher_{key}_background, R.mipmap.nebula_launcher_{key}_foreground, R.string.NebulaLauncher{key.title()})'
    assert expected in source, f'Native selector still uses upstream artwork: {enum}'
    adaptive = ET.parse(RES / f'mipmap-anydpi-v26/nebula_launcher_{key}.xml').getroot()
    assert adaptive.find('background').get(A + 'drawable') == f'@drawable/nebula_launcher_{key}_background'
    assert adaptive.find('foreground').get(A + 'drawable') == f'@mipmap/nebula_launcher_{key}_foreground'
    assert adaptive.find('monochrome').get(A + 'drawable') == '@drawable/nebula_launcher_monochrome'
    for density, factor in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
        for suffix, dp in [('', 48), ('_foreground', 108)]:
            png = (RES / f'mipmap-{density}/nebula_launcher_{key}{suffix}.png').read_bytes()
            assert png[:8] == b'\x89PNG\r\n\x1a\n'
            assert struct.unpack('>II', png[16:24]) == (round(dp * factor),) * 2
    for locale in ['values', 'values-ru']:
        labels = ET.parse(RES / locale / 'nebula_launcher.xml').getroot()
        assert labels.find(f"string[@name='NebulaLauncher{key.title()}']").text

assert len({hashlib.sha256((RES / f'mipmap-xxxhdpi/nebula_launcher_{k}.png').read_bytes()).digest() for _, _, k in variants}) == 6
main = ET.parse(TREE / 'TMessagesProj/src/main/AndroidManifest.xml').getroot().find('application')
standalone = ET.parse(TREE / 'TMessagesProj/config/release/AndroidManifest_standalone.xml').getroot().find('application')
for app in [main, standalone]:
    for _, component, key in variants:
        alias = app.find(f"activity-alias[@{A}name='org.telegram.messenger.{component}']")
        if alias is None:
            assert component == 'DefaultIcon' and app is standalone
            alias = app
        for attr in ['icon', 'roundIcon']:
            assert alias.get(A + attr) == f'@mipmap/nebula_launcher_{key}', (component, attr)

# Compile the actual controller, not a reimplementation of its transitions.
with tempfile.TemporaryDirectory(prefix='nebula-launcher-') as tmp:
    work = Path(tmp)
    files = {
        'org/telegram/ui/LauncherIconController.java': source,
        'android/content/ComponentName.java': 'package android.content; public class ComponentName { public final String key; public ComponentName(String pkg,String name){key=pkg+name;} }',
        'android/content/Context.java': 'package android.content; public class Context { public final android.content.pm.PackageManager pm = new android.content.pm.PackageManager(); public String getPackageName(){return "app.nebulagram";} public android.content.pm.PackageManager getPackageManager(){return pm;} }',
        'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {
 public static final int COMPONENT_ENABLED_STATE_DEFAULT=0, COMPONENT_ENABLED_STATE_ENABLED=1, COMPONENT_ENABLED_STATE_DISABLED=2, DONT_KILL_APP=1;
 public final java.util.Map<String,Integer> states=new java.util.HashMap<>();
 public int getComponentEnabledSetting(android.content.ComponentName c){return states.getOrDefault(c.key,0);}
 public void setComponentEnabledSetting(android.content.ComponentName c,int state,int flags){if(flags!=DONT_KILL_APP)throw new AssertionError(); states.put(c.key,state);}
}''',
        'org/telegram/messenger/ApplicationLoader.java': 'package org.telegram.messenger; public class ApplicationLoader { public static android.content.Context applicationContext = new android.content.Context(); }',
        'LauncherCheck.java': '''import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;
import org.telegram.messenger.ApplicationLoader;
public class LauncherCheck {
 static void check(LauncherIcon chosen){int enabled=0;for(LauncherIcon i:LauncherIcon.values()){if(LauncherIconController.isEnabled(i)){enabled++;if(i!=chosen)throw new AssertionError();}if(i.premium)throw new AssertionError("Original icons must be free");}if(enabled!=1)throw new AssertionError("Missing/duplicate alias");}
 public static void main(String[] args){
  check(LauncherIcon.DEFAULT);
  for(LauncherIcon from:LauncherIcon.values())for(LauncherIcon to:LauncherIcon.values()){
   LauncherIconController.setIcon(from);LauncherIconController.setIcon(to);check(to);
   LauncherIconController.tryFixLauncherIconIfNeeded();check(to);
  }
  for(LauncherIcon i:LauncherIcon.values())ApplicationLoader.applicationContext.pm.setComponentEnabledSetting(i.getComponentName(ApplicationLoader.applicationContext),2,1);
  LauncherIconController.tryFixLauncherIconIfNeeded();check(LauncherIcon.DEFAULT);
  System.out.println("PASS: 36 switches, retained choice, recovery, no Premium gate");
 }
}''',
    }
    r = 'package org.telegram.messenger; public class R {'
    for kind in ['drawable', 'mipmap', 'string']:
        names = sorted(set(re.findall(r'R\.' + kind + r'\.(\w+)', source)))
        r += 'public static class ' + kind + '{' + ''.join(f'public static final int {n}={i};' for i, n in enumerate(names)) + '}'
    files['org/telegram/messenger/R.java'] = r + '}'
    for name, content in files.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    javac = shutil.which('javac')
    if not javac:
        candidates = list((Path.home() / '.gradle/jdks').glob('*/bin/javac.exe'))
        assert candidates, 'JDK required for native controller regression'
        javac = str(candidates[0])
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work)] + [str(work / f) for f in files], check=True)
    java = str(Path(javac).with_name('java.exe' if javac.endswith('.exe') else 'java'))
    subprocess.run([java, '-cp', str(work), 'LauncherCheck'], check=True)
print('PASS: six branded previews/aliases, legacy densities, adaptive layers and RU/EN titles')
