"""Execute native sheet opt-out, editor colors and header policy."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
sheet = (native / 'ActionBar/BottomSheet.java').read_text(encoding='utf-8')
bar = (native / 'ActionBar/ActionBar.java').read_text(encoding='utf-8')
editor = (native / 'Components/AIEditorAlert.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

gate_start = sheet.index('if (useNebulaSheetGlass()') + len('if (')
gate_end = sheet.index(') {', gate_start)
gate = sheet[gate_start:gate_end]
setup_start = editor.index('        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray)')
setup_end = editor.index('\n', editor.index('actionBar.setTitleRightMargin(', setup_start))
setup = editor[setup_start:setup_end]
sources = {
    'app/nebulagram/ui/NebulaAppearance.java': 'package app.nebulagram.ui; public class NebulaAppearance {public static boolean center;public static boolean centerHome(){return center;}}',
    'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui; public class NebulaMenuStyle {public static boolean enabled=true;public static boolean enabled(){return enabled;}}',
    'app/nebulagram/ui/NebulaSheetDrawable.java': 'package app.nebulagram.ui; public class NebulaSheetDrawable {}',
    'org/telegram/ui/ChatActivity.java': 'package org.telegram.ui; public class ChatActivity {}',
    'org/telegram/ui/DialogsActivity.java': 'package org.telegram.ui; public class DialogsActivity {public boolean expanded;public boolean nebulaIsHeaderExpanded(){return expanded;}}',
    'EditorCheck.java': '''class EditorCheck {
 static float density=1;static int dp(float v){return (int)Math.ceil(v*density);}
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static class Theme {static int key_windowBackgroundGray=1;}
 static class Header {
  Object parentFragment;boolean nebulaHomeTabsGlass,nebulaCommunityGlass,isSearchFieldVisible;float mode;int color,rightMargin;
  float getActionModeFactor(){return mode;}void setBackgroundColor(int c){color=c;}void setTitleRightMargin(int m){rightMargin=m;}
  HEADER
 }
 static class Sheet {
  Object shadowDrawable=new Object();
  SHEET
  boolean wrapsGlass(){return GATE;}
 }
 static class Editor extends Sheet {
  Header actionBar=new Header();int color,providedColor;
  EDITOR
  int getThemedColor(int key){return providedColor;}void setBackgroundColor(int c){color=c;}
  void setup(){SETUP}
 }
 public static void main(String[] args){
  for(boolean enabled:new boolean[]{false,true}){
   app.nebulagram.ui.NebulaMenuStyle.enabled=enabled;Sheet ordinary=new Sheet();Editor editor=new Editor();
   check(ordinary.wrapsGlass()==enabled,"other sheet preference preserved");check(!editor.wrapsGlass(),"dense editor remains opaque with glass preference on/off");
   ordinary.shadowDrawable=new app.nebulagram.ui.NebulaSheetDrawable();check(!ordinary.wrapsGlass(),"already wrapped sheet not wrapped twice");
  }
  for(float d:new float[]{1,2,2.75f})for(int c:new int[]{0xff171717,0xfff6f4f2,0x2028394a}){
   density=d;Editor e=new Editor();e.providedColor=c;e.setup();
   check(e.color>>>24==255&&(e.color&0xffffff)==(c&0xffffff),"opaque editor preserves Telegram RGB");
   check(e.actionBar.color==e.color,"header and body share Telegram surface");
   check(e.actionBar.rightMargin==dp(62),"title reserves 54dp close plus 8dp end gap");
  }
  for(boolean center:new boolean[]{false,true}){
   app.nebulagram.ui.NebulaAppearance.center=center;Header h=new Header();
   check(!h.nebulaCenterTitle(),"sheet title keeps native sliding alignment");
   h.parentFragment=new Object();check(h.nebulaCenterTitle()==center,"fragment title preference retained");
   h.parentFragment=new org.telegram.ui.ChatActivity();check(!h.nebulaCenterTitle(),"chat avatar title owns positioning");
   org.telegram.ui.DialogsActivity dialogs=new org.telegram.ui.DialogsActivity();h.parentFragment=dialogs;
   check(!h.nebulaCenterTitle(),"collapsed home title unaffected");dialogs.expanded=true;check(h.nebulaCenterTitle()==center,"expanded home title preference retained");
   h.isSearchFieldVisible=true;check(!h.nebulaCenterTitle(),"search keeps title policy");h.isSearchFieldVisible=false;h.mode=1;check(!h.nebulaCenterTitle(),"selection keeps title policy");
  }
  System.out.println("Native editor opacity/palette/close inset and sheet/home title ownership passed");
 }
}'''.replace('HEADER', method(bar, 'private boolean nebulaCenterTitle()'))
         .replace('SHEET', method(sheet, 'protected boolean useNebulaSheetGlass()'))
         .replace('GATE', gate)
         .replace('EDITOR', method(editor, 'protected boolean useNebulaSheetGlass()'))
         .replace('SETUP', setup),
}
with tempfile.TemporaryDirectory(prefix='nebula-editor-surface-') as temp:
    work = Path(temp)
    for name, source in sources.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, *map(str, work.rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', temp, 'EditorCheck'], check=True)
