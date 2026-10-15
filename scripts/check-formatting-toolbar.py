"""Compile the production toolbar against Android SDK and exercise its selection guard."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
source = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java').read_text(encoding='utf-8')
sdk = Path(os.environ.get('ANDROID_HOME') or str(Path.home() / 'AppData/Local/Android/Sdk'))
jars = [p for p in (sdk / 'platforms').glob('android-*/android.jar')
        if re.fullmatch(r'android-\d+(?:\.\d+)*', p.parent.name)]
if not jars:
    raise SystemExit('Install a numeric Android SDK platform and set ANDROID_HOME before running this check')
jar = max(jars, key=lambda p: tuple(int(x) for x in p.parent.name.removeprefix('android-').split('.')))
stubs = {
    'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities { public static int dp(float x){return (int)x;} }',
    'org/telegram/messenger/ApplicationLoader.java': 'package org.telegram.messenger; public class ApplicationLoader { public static android.content.Context applicationContext; }',
    'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public class NebulaText { public static String text(String a,String b){return b;} }',
    'app/nebulagram/ui/NebulaTheme.java': 'package app.nebulagram.ui; public class NebulaTheme {public static NebulaTheme of(android.content.Context c){return new NebulaTheme();} public int surfaceContainer(){return 0;}public int onSurface(){return 0;}}',
    'org/telegram/ui/Components/EditTextCaption.java': '''package org.telegram.ui.Components;
public class EditTextCaption extends android.widget.EditText {
 public EditTextCaption(android.content.Context c){super(c);}
 public void setSelectionOverride(int a,int b){}
 public void makeSelectedBold(){} public void makeSelectedItalic(){} public void makeSelectedMono(){}
 public void makeSelectedQuote(){} public void makeSelectedSpoiler(){} public void makeSelectedUrl(){}
 public void makeSelectedRegular(){}
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-formatting-') as tmp:
    tmp = Path(tmp)
    for name, text in stubs.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding='utf-8')
    path = tmp / 'app/nebulagram/ui/NebulaFormattingToolbar.java'
    path.write_text(source, encoding='utf-8')
    # Compile the actual native callsite inside a method, not a constructor:
    # createEditText has no local Context parameter.
    patch = (root / 'patches/android/0212-formatting-toolbar.patch').read_text(encoding='utf-8')
    callsite = next(line[1:].strip() for line in patch.splitlines()
                    if line.startswith('+') and 'new app.nebulagram.ui.NebulaFormattingToolbar(' in line)
    (tmp / 'NativeToolbarHook.java').write_text('''
class NativeToolbarHook extends android.widget.FrameLayout {
 app.nebulagram.ui.NebulaFormattingToolbar nebulaFormattingToolbar;
 org.telegram.ui.Components.EditTextCaption messageEditText;
 NativeToolbarHook(android.content.Context context){super(context);}
 void createEditText(){CALLSITE}
}'''.replace('CALLSITE', callsite), encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(tmp / 'classes'), *map(str, tmp.rglob('*.java'))], check=True)
    body = source.split('button.setOnClickListener(v -> {', 1)[1].split('\n        });', 1)[0]
    test = '''class SelectionCheck {
 static class Editor {int a,b,length=12,calls; int getSelectionStart(){return a;}int getSelectionEnd(){return b;}int length(){return length;}
 void setSelection(int a,int b){this.a=a;this.b=b;}void setSelectionOverride(int a,int b){if(a!=this.a||b!=this.b)throw new AssertionError();}void invalidate(){}}
 static class NebulaText {static String text(String a,String b){return b;}}
 Editor editor=new Editor();int calls;Runnable action=()->calls++;void announceForAccessibility(String x){}
 void click(){BODY}
 public static void main(String[]args){SelectionCheck t=new SelectionCheck();int cases=0;
 for(int a=-2;a<16;a++)for(int b=-2;b<16;b++){t.editor.a=a;t.editor.b=b;t.calls=0;t.click();
 boolean valid=a>=0&&b>=0&&a!=b&&a<=12&&b<=12;
 if(t.calls!=(valid?1:0))throw new AssertionError(a+":"+b);
 if(valid&&(t.editor.a!=Math.min(a,b)||t.editor.b!=Math.max(a,b)))throw new AssertionError("reversed selection");cases++;}
 System.out.println(cases+" selection bounds/reversal cases passed");}}
'''.replace('BODY', body)
    path = tmp / 'SelectionCheck.java'
    path.write_text(test, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp / 'test'), str(path)], check=True)
    subprocess.run(['java', '-cp', str(tmp / 'test'), 'SelectionCheck'], check=True)

assert 'setText(' not in source.split('button.setOnClickListener', 1)[1].split('public int prepare', 1)[0]
assert 'unregisterOnSharedPreferenceChangeListener' in source
ios = (root / 'patches/ios/0108-formatting-toolbar.patch').read_text(encoding='utf-8')
for method in ['Bold', 'Italic', 'Monospace', 'Quote', 'Spoiler', 'Link']:
    assert 'self.formatAttributes' + method + '(self)' in ios
assert 'ChatTextInputAttributes.customEmoji' not in ios
assert 'updateTextInputStateAndMode' in ios
print('Android SDK compilation and native formatting/ownership hooks verified; device keyboard QA remains separate')
