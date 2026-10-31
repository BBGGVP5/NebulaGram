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
    'app/nebulagram/ui/NebulaGlass.java': 'package app.nebulagram.ui;public class NebulaGlass {public static boolean reduced(){return false;}}',
    'app/nebulagram/ui/NebulaShape.java': 'package app.nebulagram.ui;public class NebulaShape {public static int radius(float dp){return (int)dp;}}',
    'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public class NebulaText { public static String text(String a,String b){return b;} }',
    'app/nebulagram/ui/NebulaTheme.java': 'package app.nebulagram.ui; public class NebulaTheme {public static NebulaTheme of(android.content.Context c){return new NebulaTheme();} public int surfaceContainer(){return 0;}public int onSurface(){return 0;}public int primary(){return 0xff0088cc;}public int surface(){return 0;}public static int stateLayer(int c,float a){return c;}}',
    'org/telegram/messenger/R.java': 'package org.telegram.messenger;public class R {public static class drawable {public static int nebula_format_clear=1,nebula_format_quote=2,nebula_format_spoiler=3,msg_link=4,formatting_underline=5,formatting_strikethrough=6,formatting_code=7,nebula_format_newline=8,nebula_format_link=9,nebula_format_code=10;}}',
    'org/telegram/ui/Components/EditTextCaption.java': '''package org.telegram.ui.Components;
public class EditTextCaption extends android.widget.EditText {
 public EditTextCaption(android.content.Context c){super(c);}
 public void setSelectionOverride(int a,int b){}
 public void makeSelectedBold(){} public void makeSelectedItalic(){} public void makeSelectedMono(){}
 public void makeSelectedQuote(){} public void makeSelectedSpoiler(){} public void makeSelectedUrl(){}
 public void makeSelectedRegular(){} public void makeSelectedUnderline(){} public void makeSelectedStrike(){} public void makeSelectedCode(){}
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
    selection_source = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingSelection.java'
    (path.parent / selection_source.name).write_bytes(selection_source.read_bytes())
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
    test = '''import app.nebulagram.ui.NebulaFormattingSelection;
class SelectionCheck {
 static class Editor {int a,b,length=12,calls,batch; String getText(){return "hello world!";}int getSelectionStart(){return a;}int getSelectionEnd(){return b;}int length(){return length;}
 void beginBatchEdit(){batch++;}void endBatchEdit(){batch--;}void setSelection(int p){a=b=p;}void setSelection(int a,int b){this.a=a;this.b=b;}void setSelectionOverride(int a,int b){if(a>=0&&(a!=this.a||b!=this.b))throw new AssertionError();}void invalidate(){}}
 static class NebulaText {static String text(String a,String b){return b;}}
 Editor editor=new Editor();boolean needsSelection=true,expanded=true;int calls;Runnable action=()->calls++;void announceForAccessibility(String x){}
 void click(){BODY}
 public static void main(String[]args){SelectionCheck t=new SelectionCheck();int cases=0;
 for(int a=-2;a<16;a++)for(int b=-2;b<16;b++){t.editor.a=a;t.editor.b=b;t.calls=0;t.click();
 int[] expected=app.nebulagram.ui.NebulaFormattingSelection.range(t.editor.getText(),a,b,true);
 boolean valid=expected!=null&&expected[0]!=expected[1];
 if(t.calls!=(valid?1:0))throw new AssertionError(a+":"+b);
 if(valid&&(t.editor.a!=(a==b?a:expected[0])||t.editor.b!=(a==b?a:expected[1])))throw new AssertionError("implicit selection replaces the next typed word: "+a+":"+b);if(t.editor.batch!=0)throw new AssertionError("unbalanced IME batch");cases++;}
 range("Hello world",11,11,6,11);range("Hello world",8,8,6,11);range("Привет мир!  ",13,13,7,10);
 range("one\\n你好",6,6,4,6);range("e\\u0301",2,2,0,2);range("a\\uD83D\\uDE80b",2,3,1,3);
 range("word ❤️",7,7,0,4);
 if(app.nebulagram.ui.NebulaFormattingSelection.range("🚀",2,2,true)!=null)throw new AssertionError("emoji is not a word");
 if(app.nebulagram.ui.NebulaFormattingSelection.range("",0,0,true)!=null)throw new AssertionError("empty word");
 System.out.println(cases+" selection bounds/reversal cases passed");}}
'''.replace('BODY', body).replace(' public static void main', '''
 static void range(String text,int a,int b,int start,int end){int[] range=app.nebulagram.ui.NebulaFormattingSelection.range(text,a,b,true);
 if(range==null||range[0]!=start||range[1]!=end)throw new AssertionError(text+":"+java.util.Arrays.toString(range));}
 static class CountingText implements CharSequence {
 String text="a"+"\\u0301".repeat(8000);int reads;public int length(){return text.length();}
 public char charAt(int i){reads++;return text.charAt(i);}public CharSequence subSequence(int a,int z){return text.subSequence(a,z);}}
 public static void main''')
    test=test.replace('System.out.println(cases+', '''CountingText longWord=new CountingText();
 int[] longRange=NebulaFormattingSelection.range(longWord,longWord.length(),longWord.length(),true);
 if(longRange==null||longRange[0]!=0||longRange[1]!=longWord.length()||longWord.reads>longWord.length()*12)throw new AssertionError("Word selection rescans combining marks quadratically: "+longWord.reads);
 System.out.println("8001-unit accented word selected with "+longWord.reads+" character reads");
 System.out.println(cases+''')
    path = tmp / 'SelectionCheck.java'
    path.write_text(test, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tmp / 'test'), str(path), str(tmp / 'app/nebulagram/ui/NebulaFormattingSelection.java')], check=True)
    subprocess.run(['java', '-cp', str(tmp / 'test'), 'SelectionCheck'], check=True)

assert 'setText(' not in source.split('button.setOnClickListener', 1)[1].split('public int prepare', 1)[0]
assert 'unregisterOnSharedPreferenceChangeListener' in source
ios = (root / 'patches/ios/0108-formatting-toolbar.patch').read_text(encoding='utf-8')
for method in ['Bold', 'Italic', 'Monospace', 'Quote', 'Spoiler', 'Link']:
    assert 'self.formatAttributes' + method + '(self)' in ios
assert 'ChatTextInputAttributes.customEmoji' not in ios
assert 'updateTextInputStateAndMode' in ios
print('Android SDK compilation and native formatting/ownership hooks verified; device keyboard QA remains separate')
