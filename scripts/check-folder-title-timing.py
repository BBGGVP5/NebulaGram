"""Exercise the patched native folder switch before page loading/settling occurs."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
source = tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java'
text = source.read_text(encoding='utf-8')

def method(signature):
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

methods = method('private void updateNebulaFolderTitle()')
if 'private void updateNebulaFolderTitle(int page)' in text:
    methods += '\n' + method('private void updateNebulaFolderTitle(int page)')
methods += '\n' + method('public void switchToCurrentSelectedMode(boolean animated)')
work = root / 'build/folder-title-timing/check'
work.mkdir(parents=True, exist_ok=True)
recorder = work / 'app/nebulagram/ui/NebulaDialogsTitle.java'
recorder.parent.mkdir(parents=True, exist_ok=True)
recorder.write_text('''package app.nebulagram.ui;
public class NebulaDialogsTitle {
 public static int title = -1;
 public static void apply(Object bar, Object controller, int selected, Object status) { title = selected; }
}''', encoding='utf-8')
harness = work / 'CheckTitleTiming.java'
harness.write_text('''import app.nebulagram.ui.NebulaDialogsTitle;
public class CheckTitleTiming {
 Object actionBar = new Object(), statusDrawable = new Object();
 ViewPage[] viewPages = {new ViewPage(), new ViewPage()};
 int initialDialogsType, expectedTitle, loads;
 static final int DIALOGS_TYPE_DEFAULT=0, ARCHIVE_ITEM_STATE_HIDDEN=0;
 float scrollYOffset;
 MessagesController controller = new MessagesController();
 MessagesController getMessagesController() { return controller; }
 boolean hasHiddenArchive() { return false; }
 void checkListLoad(ViewPage page) {
  if (NebulaDialogsTitle.title != expectedTitle) throw new AssertionError("Title waited for page settling: expected " + expectedTitle + ", got " + NebulaDialogsTitle.title);
  loads++;
 }
 static class MessagesController {
  static class DialogFilter { boolean locked, home; boolean isDefault(){return home;} }
  java.util.ArrayList<DialogFilter> filters = new java.util.ArrayList<>();
  MessagesController() { for(int i=0;i<5;i++){ DialogFilter f=new DialogFilter();f.home=i==0;filters.add(f);} }
  java.util.ArrayList<DialogFilter> getDialogFilters(){return filters;}
  void selectDialogFilter(DialogFilter f,int slot){}
 }
 static class ListView { void stopScroll(){} void updatePullState(){} void setScrollEnabled(boolean b){} }
 static class Adapter {void setDialogsType(int type){}}
 static class Layout {void scrollToPositionWithOffset(int p,int offset){}}
 static class ViewPage {int selectedType,dialogsType,archivePullViewState;boolean isLocked;
  ListView listView=new ListView();Adapter dialogsAdapter=new Adapter();Layout layoutManager=new Layout();}
''' + methods + '''
 public static void main(String[] args) {
  CheckTitleTiming t=new CheckTitleTiming();
  for(int from=0;from<5;from++)for(int to=0;to<5;to++)if(from!=to){
   t.viewPages[0].selectedType=from;t.viewPages[1].selectedType=to;
   NebulaDialogsTitle.title=from;t.expectedTitle=to;
   t.switchToCurrentSelectedMode(true);
   // A canceled swipe returns to the visible page without a settling callback.
   t.updateNebulaFolderTitle();
   if(NebulaDialogsTitle.title!=from)throw new AssertionError("Canceled swipe retained target");
   t.controller.filters.get(to).locked=true;t.expectedTitle=from;t.switchToCurrentSelectedMode(true);
   t.controller.filters.get(to).locked=false;
   t.expectedTitle=from;t.switchToCurrentSelectedMode(false);
  }
  int loads=t.loads;t.viewPages[1].selectedType=99;t.switchToCurrentSelectedMode(true);
  if(loads!=t.loads)throw new AssertionError("Invalid folder loaded");
  System.out.println("Folder timing passed: 20 transitions update before page loading, cancel restores current, locked/invalid targets stay unchanged");
 }
}''', encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(recorder), str(harness)], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckTitleTiming'], check=True)
