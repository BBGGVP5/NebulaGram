"""Run actual menu colour traversal, including nested menus and tinted dark text."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
def method(file, signature):
    text = file.read_text(encoding='utf-8')
    start = text.index(signature); brace = text.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}'); end += 1
    return text[start:end]

source = r'''
import java.util.*;
import app.nebulagram.ui.NebulaMenuPalette;
public class CheckMenuColors {
 static int background;
 static final WeakHashMap<View,Integer> rowColors=new WeakHashMap<>();
 static boolean enabled(){return true;}
 static int surface(Theme.ResourcesProvider p){return p==null?background:NebulaMenuPalette.surface(p.dark);}
 static float opacity(){return .78f;}
 MENU_FOREGROUND
 static class Theme {
  static final int key_actionBarDefaultSubmenuItem=1;
  static class ResourcesProvider{boolean dark;public int getColor(int key){return 0xff211a16;}}
  static int getColor(int key,ResourcesProvider p){return 0xff211a16;}
  static int multAlpha(int c,float a){return c;}
 }
 static class Color {static final int WHITE=0xffffffff,BLACK=0xff000000;}
 static class ColorUtils {
  static double channel(int n){double v=n/255.0;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
  static double luminance(int c){return .2126*channel((c>>>16)&255)+.7152*channel((c>>>8)&255)+.0722*channel(c&255);}
  static double calculateContrast(int a,int b){
   double alpha=(a>>>24)/255.0;
   int composite=0xff000000;
   for(int shift:new int[]{0,8,16})composite|=((int)Math.round(((a>>>shift)&255)*alpha+((b>>>shift)&255)*(1-alpha)))<<shift;
   double x=luminance(composite)+.05,y=luminance(b)+.05;return Math.max(x,y)/Math.min(x,y);
  }
 }
 static class NebulaChatColors { FOREGROUND }
 interface ViewParent {ViewParent getParent();}
 static class View implements ViewParent {ViewParent parent; public ViewParent getParent(){return parent;}}
 static class Canvas {}
 static class ViewGroup extends View {
  List<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  protected void dispatchDraw(Canvas canvas){}
 }
 static class ActionBarPopupWindowLayout extends ViewGroup {
  Theme.ResourcesProvider provider;
  Theme.ResourcesProvider getNebulaResourcesProvider(){return provider;}
 }
 static class Text extends View {int color;int getCurrentTextColor(){return color;}void setTextColor(int c){color=c;}}
 static class SimpleText extends View {int color;int getTextColor(){return color;}void setTextColor(int c){color=c;}}
 static class CheckBox {
  Theme.ResourcesProvider provider;
  CheckBox getCheckBoxBase(){return this;}void setResourcesProvider(Theme.ResourcesProvider p){provider=p;}
 }
 static class ActionBarMenuSubItem extends ViewGroup {
  CheckBox checkView=new CheckBox();
  Text text=new Text(),subtextView=new Text();int cached=Color.WHITE,icon;
  Text getTextView(){return text;}void setTextColor(int c){if(cached!=c){cached=c;text.color=c;}}
  void setIconColor(int c){icon=c;}void setSelectorColor(int c){}
  Theme.ResourcesProvider resourcesProvider;
  NATIVE_DRAW
 }
 OWNER_PROVIDER
 STYLE
 public static void main(String[] args){
  int count=0;
  for(int bg:new int[]{0xff332b26,0xff171218,0xff203746,0xfffaf8f5,0xffdbe6ee})
  for(int fg:new int[]{0xff211a16,0xff000000,0xffeeeeee,0xffff7777,0xffaa1616,0x88000000}){
   background=bg;
   ViewGroup root=new ViewGroup(),nested=new ViewGroup();root.children.add(nested);
   ActionBarMenuSubItem row=new ActionBarMenuSubItem();row.text.color=fg;nested.children.add(row);
   Text account=new Text();account.color=fg;nested.children.add(account);
   SimpleText update=new SimpleText();update.color=fg;nested.children.add(update);
   boolean readable=ColorUtils.calculateContrast(fg,NebulaMenuPalette.contrastSurface(bg,opacity()))>=4.5;
   styleRows(root,null);
   if(ColorUtils.calculateContrast(row.text.color,bg)<4.5||row.text.color!=row.icon)
    throw new AssertionError("Invisible nested menu text/icons: "+Integer.toHexString(bg));
   if(row.checkView.provider.getColor(Theme.key_actionBarDefaultSubmenuItem)!=row.text.color)
    throw new AssertionError("Invisible folder selection check mark");
   if(readable&&fg!=row.text.color)throw new AssertionError("Readable semantic colour discarded");
   if(ColorUtils.calculateContrast(account.color,bg)<4.5||ColorUtils.calculateContrast(update.color,bg)<4.5)
    throw new AssertionError("Custom account/update rows have unreadable text");
   Theme.ResourcesProvider cached=row.checkView.provider;
   styleRows(root,null);
   if(cached!=row.checkView.provider)throw new AssertionError("Provider allocated every draw");
   // A theme update bypasses the row cache, as ThemeDescription does.
   row.text.color=0xff211a16;styleRows(root,null);
   if(ColorUtils.calculateContrast(row.text.color,bg)<4.5)throw new AssertionError("Stale row colour cache");
   count++;
  }
  int translucent=0;
  for(boolean dark:new boolean[]{false,true})for(float requested:new float[]{.25f,.5f,.72f,.9f,1f})
  for(int backdrop:new int[]{0xff000000,0xffffffff,0xff805025})for(int original:new int[]{0xff000000,0xffffffff,0xffff6666}) {
   int base=NebulaMenuPalette.surface(dark);float alpha=NebulaMenuPalette.opacity(requested);
   int actual=0xff000000;
   for(int shift:new int[]{0,8,16})actual|=Math.round(((base>>>shift)&255)*alpha+((backdrop>>>shift)&255)*(1-alpha))<<shift;
   int text=NebulaChatColors.foreground(original,NebulaMenuPalette.contrastSurface(base,alpha));
   if(ColorUtils.calculateContrast(text,actual)<4.5)throw new AssertionError("Unreadable real translucent menu");
   translucent++;
  }
  System.out.println(translucent+" translucent palette cases passed, including unready black and white blur sources");
  System.out.println(count+" nested menu palette cases passed, including direct theme updates");
  NATIVE_TEST
 }
}
'''.replace('MENU_FOREGROUND', method(overlay/'NebulaMenuStyle.java','public static int foreground(')).replace('FOREGROUND', method(overlay/'NebulaChatColors.java','public static int foreground(')).replace(
    'STYLE', method(overlay/'NebulaMenuStyle.java','public static void styleRows(').replace(
        'android.widget.TextView', 'Text').replace('org.telegram.ui.ActionBar.SimpleTextView', 'SimpleText'))
source = source.replace('OWNER_PROVIDER', method(overlay/'NebulaMenuStyle.java', 'public static Theme.ResourcesProvider ownerProvider(').replace('android.view.ViewParent', 'ViewParent'))
if len(sys.argv) > 1:
    native = Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar'
    draw = method(native/'ActionBarMenuSubItem.java', 'protected void dispatchDraw(')
    source = source.replace('NATIVE_DRAW', draw.replace('android.graphics.Canvas', 'Canvas').replace('app.nebulagram.ui.NebulaMenuStyle.', ''))
    source = source.replace('NATIVE_TEST', r'''
      ActionBarPopupWindowLayout popup=new ActionBarPopupWindowLayout();
      popup.provider=new Theme.ResourcesProvider();popup.provider.dark=true;
      ActionBarMenuSubItem item=new ActionBarMenuSubItem();item.resourcesProvider=new Theme.ResourcesProvider();
      ViewGroup scroll=new ViewGroup();scroll.parent=popup;item.parent=scroll;
      item.text.color=Color.BLACK;item.subtextView.color=Color.BLACK;item.icon=Color.BLACK;
      item.dispatchDraw(new Canvas());
      if(item.text.color!=Color.WHITE || item.icon!=Color.WHITE || item.subtextView.color!=Color.WHITE)
        throw new AssertionError("Late black theme update or light row provider overrode dark popup");
      popup.provider.dark=false;item.dispatchDraw(new Canvas());
      if(item.text.color!=Color.BLACK || item.icon!=Color.BLACK)throw new AssertionError("Live light theme not applied");
      System.out.println("Actual native row draw hook repairs late colors using the owning popup palette");
    ''')
else:
    source = source.replace('NATIVE_DRAW', '').replace('NATIVE_TEST', '')
work=root/'build/menu-colors-check';work.mkdir(parents=True,exist_ok=True)
target=work/'CheckMenuColors.java';target.write_text(source,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target),str(overlay/'NebulaMenuPalette.java')],check=True)
subprocess.run(['java','-cp',str(work),'CheckMenuColors'],check=True)
if len(sys.argv) > 1:
    native = Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar'
    popup = (native/'ActionBarPopupWindow.java').read_text(encoding='utf-8')
    assert 'addOnPreDrawListener(nebulaPaletteListener)' in popup
    assert 'removeOnPreDrawListener(nebulaPaletteListener)' in popup
    assert 'setForceDarkAllowed(false)' in popup
    row = method(native/'ActionBarMenuSubItem.java', 'protected void dispatchDraw(')
    assert row.index('NebulaMenuStyle.styleRows') < row.index('super.dispatchDraw')
    print('Native popup pre-draw, platform recoloring opt-out and row draw-time palette hooks passed')
