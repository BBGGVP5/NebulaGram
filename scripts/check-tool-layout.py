"""Execute production tool-grid geometry without Android."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
grid = (ui / 'NebulaToolGrid.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

java = r'''
import java.util.*;
class ToolLayoutCheck {
 static float density=1;
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static int dp(float v){return (int)Math.ceil(v*density);}
 static class Config {float fontScale;}
 static class Metrics {int heightPixels=800;}
 static class Resources {Config config=new Config();Metrics metrics=new Metrics();Config getConfiguration(){return config;}Metrics getDisplayMetrics(){return metrics;}}
 static class MeasureSpec {
  static int EXACTLY=0x40000000,AT_MOST=0x80000000,UNSPECIFIED=0;
  static int getSize(int s){return s&0x3fffffff;}static int getMode(int s){return s&0xc0000000;}
  static int makeMeasureSpec(int s,int mode){return s|mode;}
 }
 static class LinearLayout {static class LayoutParams {int topMargin,bottomMargin;}}
 static class View {
  int width,height,natural=88,l,t,r,b;String tag;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams();
  void measure(int w,int h){width=MeasureSpec.getSize(w);height=natural;}
  int getMeasuredHeight(){return height;}Object getTag(){return tag;}Object getLayoutParams(){return params;}
  void layout(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
 }
 static class Parent {
  static int LAYOUT_DIRECTION_RTL=1;boolean rtl;int width,height;Resources resources=new Resources();ArrayList<View> children=new ArrayList<>();
  int dp(float n){return ToolLayoutCheck.dp(n);}int getWidth(){return width;}
  Resources getResources(){return resources;}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  int getLayoutDirection(){return rtl?1:0;}void setMeasuredDimension(int w,int h){width=w;height=h;}
 }
 static class Grid extends Parent {
  int columns,cellWidth;int[] rowHeights;
  MEASURE
  LAYOUT
 }
 public static void main(String[] args){
  int cases=0;
  for(float d:new float[]{1,2,2.75f})for(float font:new float[]{1,1.3f,2})for(int width:new int[]{240,328,440})for(boolean rtl:new boolean[]{false,true})for(int count:new int[]{1,2,3,4,5,6,7,8}){
   density=d;Grid g=new Grid();g.rtl=rtl;g.resources.config.fontScale=font;
   for(int i=0;i<count;i++){View v=new View();v.natural=dp((i%2==0?88:102)*font);g.children.add(v);}
   g.onMeasure(MeasureSpec.makeMeasureSpec(dp(width),MeasureSpec.EXACTLY),0);g.onLayout(true,0,0,g.width,g.height);
   for(int i=0;i<count;i++){
    View v=g.children.get(i);check(v.l>=0&&v.r<=g.width&&v.t>=0&&v.b<=g.height,"tile inside grid");
    check(v.b-v.t>=v.natural,"wrapping labels fit tallest-row layout");
    for(int j=0;j<i;j++){View p=g.children.get(j);check(v.r<=p.l||p.r<=v.l||v.b<=p.t||p.b<=v.t,"tiles do not overlap");}
   }
   for(int start=0;start<count;start+=g.columns){
    int first=g.width,last=0;
    for(int i=start;i<Math.min(count,start+g.columns);i++){View v=g.children.get(i);first=Math.min(first,v.l);last=Math.max(last,v.r);}
    check(Math.abs(first-(g.width-last))<=1,"each full/partial row has balanced edge space");
   }
   if(font==1&&width==328)check(g.columns==3,"normal phone displays three tools per row");
   if(font==2&&width==240)check(g.columns==1,"large font on narrow phone collapses grid");cases++;
  }
  System.out.println(cases+" tool grid density/font/RTL cases passed");
 }
}
'''.replace('  MEASURE\n', method(grid, 'protected void onMeasure(int widthSpec, int heightSpec)')).replace(
    '  LAYOUT\n', method(grid, 'protected void onLayout(boolean changed, int l, int t, int r, int b)'))
with tempfile.TemporaryDirectory(prefix='nebula-tool-layout-') as temp:
    path = Path(temp) / 'ToolLayoutCheck.java'
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'ToolLayoutCheck'], check=True)

tools = (ui / 'NebulaMessageToolsFragment.java').read_text(encoding='utf-8')
for callback in ('request(false)', 'request(true)', 'transcribe()', 'speechRequested = true; speak()',
                 'new NebulaTaskEditorFragment', 'AndroidUtilities.addToClipboard(lastResult)',
                 'id==generation', 'client.cancel()', 'speech.shutdown()'):
    assert callback in tools, callback
assert 'new NebulaToolGrid(c)' in tools and 'preferences.setVisibility(View.GONE)' in tools
print('Message action/cancellation wiring and collapsed settings retained')

# Run the actual language chooser: displayed names are localized, requests keep
# stable language codes, choosing stops prior work, and a destroyed host is inert.
language_java = '''import java.util.*;
class LanguageCheck {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static class TranslateController {
  static class Language {String code,displayName,ownDisplayName;Language(String c,String n,String own){code=c;displayName=n;ownDisplayName=own;}}
  static ArrayList<Language> getLanguages(){return new ArrayList<>(Arrays.asList(new Language("en","English","English"),new Language("ru","Russian","Русский"),new Language("es","Spanish","Español")));}
 }
 static class NebulaRow {CharSequence name;void subtitle(CharSequence s,boolean value){name=s;}}
 static class NebulaDialog {
  interface Click {void onClick(Object dialog,int index);}
  static class Dialog {int selected;CharSequence[] names,descriptions;Click click;}
  static class Builder {
   Dialog dialog=new Dialog();Builder(Object context,Object provider){}
   Builder setTitle(String s){return this;}Builder setSelectedIndex(int i){dialog.selected=i;return this;}
   Builder setDescriptions(CharSequence[] d){dialog.descriptions=d;return this;}
   Builder setItems(CharSequence[] n,Click c){dialog.names=n;dialog.click=c;return this;}
   Builder setNegativeButton(String label,Object listener){return this;}Dialog create(){return dialog;}
  }
 }
 static class Tools {
  String targetLanguage="ru",cancelledLanguage;NebulaRow target=new NebulaRow();boolean destroyed;int cancelled;NebulaDialog.Dialog dialog;
  String t(String ru,String en){return en;}Object getContext(){return this;}Object getResourceProvider(){return this;}
  void showDialog(NebulaDialog.Dialog d){dialog=d;}void cancel(){cancelled++;cancelledLanguage=targetLanguage;}
  LABEL
  CHOOSER
 }
 public static void main(String[] args){
  Tools t=new Tools();t.chooseLanguage();
  check(t.dialog.selected==1,"current language selected by code");
  check(t.dialog.names[1].equals("Russian")&&t.dialog.descriptions[1].equals("Русский"),"localized/native names retained");
  check(t.dialog.descriptions[0]==null,"duplicate native label omitted");
  t.dialog.click.onClick(t.dialog,2);
  check(t.targetLanguage.equals("es")&&t.target.name.equals("Spanish"),"selecting stores code and updates displayed name");
  check(t.cancelled==1&&t.cancelledLanguage.equals("ru"),"previous request cancelled before changing destination");
  t.destroyed=true;t.dialog.click.onClick(t.dialog,0);check(t.cancelled==1&&t.targetLanguage.equals("es"),"destroyed host ignores language selection");
  t.targetLanguage="fr";check(t.languageLabel().equals("fr"),"unknown display label falls back to stable code");
  System.out.println("Actual language selection, code/display separation and stale-host cancellation passed");
 }
}'''.replace('LABEL', method(tools, 'private String languageLabel()')).replace(
    'CHOOSER', method(tools, 'private void chooseLanguage()'))
with tempfile.TemporaryDirectory(prefix='nebula-language-check-') as temp:
    work = Path(temp)
    test = work / 'LanguageCheck.java'
    test.write_text(language_java, encoding='utf-8')
    names = work / 'org/telegram/ui/Components/TranslateAlert2.java'
    names.parent.mkdir(parents=True)
    names.write_text('''package org.telegram.ui.Components;public class TranslateAlert2 {
     public static String languageName(String c){return c.equals("en")?"English":c.equals("ru")?"Russian":c.equals("es")?"Spanish":null;}
     public static String capitalFirst(String s){return s;}}
    ''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(test), str(names)], check=True)
    subprocess.run(['java', '-cp', temp, 'LanguageCheck'], check=True)
assert 'private NebulaRow target;' in tools and 'String language=targetLanguage;' in tools
assert 'target.getText()' not in tools
