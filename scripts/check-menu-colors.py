"""Run actual menu colour traversal, including nested menus and tinted dark text."""
from pathlib import Path
import subprocess

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
public class CheckMenuColors {
 static int background;
 static boolean enabled(){return true;}
 static int surface(Theme.ResourcesProvider p){return background;}
 static class Theme {
  static final int key_actionBarDefaultSubmenuItem=1;
  static class ResourcesProvider{public int getColor(int key){return 0xff211a16;}}
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
 static class View {}
 static class ViewGroup extends View {
  List<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
 }
 static class Text {int color;int getCurrentTextColor(){return color;}void setTextColor(int c){color=c;}}
 static class CheckBox {
  Theme.ResourcesProvider provider;
  CheckBox getCheckBoxBase(){return this;}void setResourcesProvider(Theme.ResourcesProvider p){provider=p;}
 }
 static class ActionBarMenuSubItem extends ViewGroup {
  CheckBox checkView=new CheckBox();
  Text text=new Text();int cached=Color.WHITE,icon;
  Text getTextView(){return text;}void setTextColor(int c){if(cached!=c){cached=c;text.color=c;}}
  void setIconColor(int c){icon=c;}void setSelectorColor(int c){}
 }
 STYLE
 public static void main(String[] args){
  int count=0;
  for(int bg:new int[]{0xff332b26,0xff171218,0xff203746,0xfffaf8f5,0xffdbe6ee})
  for(int fg:new int[]{0xff211a16,0xff000000,0xffeeeeee,0xffff7777,0xffaa1616,0x88000000}){
   background=bg;
   ViewGroup root=new ViewGroup(),nested=new ViewGroup();root.children.add(nested);
   ActionBarMenuSubItem row=new ActionBarMenuSubItem();row.text.color=fg;nested.children.add(row);
   boolean readable=ColorUtils.calculateContrast(fg,bg)>=4.5;
   styleRows(root,null);
   if(ColorUtils.calculateContrast(row.text.color,bg)<4.5||row.text.color!=row.icon)
    throw new AssertionError("Invisible nested menu text/icons: "+Integer.toHexString(bg));
   if(row.checkView.provider.getColor(Theme.key_actionBarDefaultSubmenuItem)!=row.text.color)
    throw new AssertionError("Invisible folder selection check mark");
   if(readable&&fg!=row.text.color)throw new AssertionError("Readable semantic colour discarded");
   // A theme update bypasses the row cache, as ThemeDescription does.
   row.text.color=0xff211a16;styleRows(root,null);
   if(ColorUtils.calculateContrast(row.text.color,bg)<4.5)throw new AssertionError("Stale row colour cache");
   count++;
  }
  System.out.println(count+" nested menu palette cases passed, including direct theme updates");
 }
}
'''.replace('FOREGROUND', method(overlay/'NebulaChatColors.java','public static int foreground(')).replace(
    'STYLE', method(overlay/'NebulaMenuStyle.java','public static void styleRows('))
work=root/'build/menu-colors-check';work.mkdir(parents=True,exist_ok=True)
target=work/'CheckMenuColors.java';target.write_text(source,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target)],check=True)
subprocess.run(['java','-cp',str(work),'CheckMenuColors'],check=True)
