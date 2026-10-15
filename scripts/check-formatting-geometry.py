"""Execute real editor clip and native bubble bounds with a separate keyboard accessory."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
base=tree/'TMessagesProj/src/main/java/org/telegram/ui'
editor=(base/'Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
start=editor.index('        boolean clip = child == topView || child == textFieldContainer;')
clip=editor[start:editor.index('        boolean result = super.drawChild(canvas, child, drawingTime);',start)]
container=(base/'Components/chat/ChatInputViewsContainer.java').read_text(encoding='utf-8')
start=container.index('        tmpRect.set(\n            Math.round(inputBubbleOffsetLeft),')
bounds=container[start:container.index('        tmpRect.inset(',start)]
toolbar=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java').read_text(encoding='utf-8')
start=toolbar.index('        LinearLayout.LayoutParams params = ')
button=toolbar[start:toolbar.index('\n    }',start)]
source=r'''
class FormattingGeometryCheck {
 static class View {float y;float getY(){return y;}}
 static class Canvas {float top=Float.NEGATIVE_INFINITY,bottom=Float.POSITIVE_INFINITY;void save(){}void clipRect(float l,float t,float r,float b){top=t;bottom=b;}}
 static class Animator {float height;float getFactor(){return height;}}
 static class Style {boolean active;boolean isActive(){return active;}}
 final View textFieldContainer=new View(),aiButton=new View();View topView=new View();
 final Animator animatorInputFieldHeight=new Animator();final Style nebulaComposerStyle=new Style();
 int height,nebulaFormattingHeight;boolean shownAiButton;int getMeasuredHeight(){return height;}int getMeasuredWidth(){return 360;}
 void draw(View child,Canvas canvas){CLIP}
 static class Rect {int top,bottom;void set(int l,int t,int r,int b){top=t;bottom=b;}}
 final Rect tmpRect=new Rect();int inputBubbleHeightRound,inputAccessoryHeightRound;float inputBubbleOffsetLeft,inputBubbleOffsetRight;
 void bubble(){BOUNDS}
 static class AndroidUtilities {static float density=1;static int dp(float v){return Math.round(v*density);}}
 static class LinearLayout {static class LayoutParams {int width,height,margin;LayoutParams(int w,int h){width=w;height=h;}void setMarginEnd(int m){margin=m;}}}
 static class Row {LinearLayout.LayoutParams captured;void addView(View v,LinearLayout.LayoutParams p){captured=p;}}
 Row row=new Row();View button=new View();void button(){BUTTON}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){int cases=0;
  for(int field:new int[]{44,52,88,176})for(int accessory:new int[]{0,52})for(int reply:new int[]{0,48,96})
  for(boolean ai:new boolean[]{false,true}){
   FormattingGeometryCheck c=new FormattingGeometryCheck();c.height=field+accessory+reply+1;c.nebulaFormattingHeight=accessory;
   c.animatorInputFieldHeight.height=field;c.textFieldContainer.y=reply+1;c.aiButton.y=-20;
   c.nebulaComposerStyle.active=ai;c.shownAiButton=ai;
   Canvas canvas=new Canvas();c.draw(c.textFieldContainer,canvas);
   float fieldTop=c.height-accessory-field,fieldBottom=c.height-accessory;
   check(canvas.top<=fieldTop&&canvas.bottom>=fieldBottom,"formatting row clips native text/buttons");
   if(ai)check(canvas.top<=c.textFieldContainer.y-20,"AI accessory clipped");
   Canvas preview=new Canvas();c.draw(c.topView,preview);check(preview.bottom==fieldTop,"reply separator overlaps composer");
   c.inputBubbleHeightRound=field+reply+accessory;c.inputAccessoryHeightRound=accessory;c.bubble();
   check(c.tmpRect.bottom-c.tmpRect.top==field+reply,"formatting toolbar expands input bubble instead of separate strip");
   check(c.inputBubbleHeightRound==field+reply+accessory,"accessory lost from chat content reserve");cases++;
  }
  for(float density:new float[]{1,1.5f,2.75f,3.5f}){
   AndroidUtilities.density=density;FormattingGeometryCheck c=new FormattingGeometryCheck();c.button();
   check(c.row.captured.width==Math.round(44*density)&&c.row.captured.height==-1,"scroll-view weighted buttons collapse to zero width");
  }
  FormattingGeometryCheck poll=new FormattingGeometryCheck();poll.inputBubbleHeightRound=-7;poll.bubble();
  check(poll.tmpRect.bottom==-7,"disabled toolbar changes native poll transition");
  System.out.println(cases+" native composer clip/bubble cases and fixed-width accessory actions passed");
 }
}
'''.replace('CLIP',clip).replace('BOUNDS',bounds).replace('BUTTON',button)
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-format-geometry-') as directory:
 work=Path(directory);test=work/'FormattingGeometryCheck.java';test.write_text(source,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(test)],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'FormattingGeometryCheck'],check=True)
print('Native container keeps full content reserve while drawing a separate input island; real keyboard/IME acceptance remains required')
