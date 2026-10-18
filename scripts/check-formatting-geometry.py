"""Execute actual accessory padding/layout, parent clipping and native composer bounds."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
base=tree/'TMessagesProj/src/main/java/org/telegram/ui'
editor=(base/'Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
start=editor.index('        boolean clip = child == topView || child == textFieldContainer;')
clip=editor[start:editor.index('        boolean result = super.drawChild(canvas, child, drawingTime);',start)]
padding_start=editor.index('        if (nebulaFormattingHeight != formattingHeight) {')
padding=editor[padding_start:editor.index('        boolean compactBotMenu',padding_start)]
layout_start=editor.index('            int toolbarBottom =')
layout=editor[layout_start:editor.index('\n        }',layout_start)]
measure_start=editor.index('            nebulaFormattingToolbar.measure(')
measure=editor[measure_start:editor.index(';',measure_start)+1]
container=(base/'Components/chat/ChatInputViewsContainer.java').read_text(encoding='utf-8')
start=container.index('        tmpRect.set(\n            Math.round(inputBubbleOffsetLeft),')
bounds=container[start:container.index('        tmpRect.inset(',start)]
toolbar=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFormattingToolbar.java').read_text(encoding='utf-8')
start=toolbar.index('        LinearLayout.LayoutParams params = ')
button=toolbar[start:toolbar.index('\n    }',start)]
position_start=editor.index('            topView.setTranslationY(')
position=editor[position_start:editor.index(';',position_start)+1]
source=r'''
class FormattingGeometryCheck {
 static class View {float y,translationY;int top,bottom,measuredWidth,measuredHeight;int getTop(){return top;}int getMeasuredHeight(){return measuredHeight;}void setTranslationY(float y){translationY=y;}void measure(int w,int h){measuredWidth=w;measuredHeight=h;}float getY(){return y;}void layout(int l,int t,int r,int b){top=t;bottom=b;}}
 static class Canvas {float top=Float.NEGATIVE_INFINITY,bottom=Float.POSITIVE_INFINITY;void save(){}void clipRect(float l,float t,float r,float b){top=Math.max(top,t);bottom=Math.min(bottom,b);}}
 static class Animator {float height;float getFactor(){return height;}}
 static class Style {boolean active;boolean isActive(){return active;}}
 final View textFieldContainer=new View(),aiButton=new View();View topView=new View();
 final Animator animatorInputFieldHeight=new Animator();final Style nebulaComposerStyle=new Style();
 int height,nebulaFormattingHeight,paddingTop,paddingBottom;boolean shownAiButton,clipPadding=true;View nebulaFormattingToolbar=new View();int getMeasuredHeight(){return height;}int getMeasuredWidth(){return 360;}
 int getPaddingLeft(){return 0;}int getPaddingRight(){return 0;}int getPaddingTop(){return paddingTop;}int getPaddingBottom(){return paddingBottom;}
 void setPadding(int l,int t,int r,int b){paddingTop=t;paddingBottom=b;}void setClipToPadding(boolean value){clipPadding=value;}
 static class MeasureSpec {static int EXACTLY=0;static int makeMeasureSpec(int value,int mode){return value;}}
 static class app {static class nebulagram {static class ui {static class NebulaFormattingToolbar {static int HEIGHT_DP=52,GAP_DP=8;}}}}
 static int dp(int value){return value;}void measure(){MEASURE}void padding(int formattingHeight){PADDING}void layout(){LAYOUT}
 void position(float y,float visibility){POSITION}
 void parentClip(Canvas canvas){if(clipPadding&&(paddingTop!=0||paddingBottom!=0))canvas.clipRect(0,paddingTop,360,height-paddingBottom);}
 void draw(View child,Canvas canvas){CLIP}
 static class Rect {int top,bottom;void set(int l,int t,int r,int b){top=t;bottom=b;}}
 final Rect tmpRect=new Rect();int inputBubbleHeightRound,inputAccessoryHeightRound;float inputBubbleOffsetLeft,inputBubbleOffsetRight;
 void bubble(){BOUNDS}
 static class AndroidUtilities {static float density=1;static int dp(float v){return Math.round(v*density);}}
 static class LinearLayout {static class LayoutParams {int width,height,margin;LayoutParams(int w,int h){width=w;height=h;}void setMarginEnd(int m){margin=m;}}}
 static class Row {int getChildCount(){return 0;}LinearLayout.LayoutParams captured;void addView(View v,LinearLayout.LayoutParams p){captured=p;}}
 Row row=new Row();View button=new View();void button(){BUTTON}
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){int cases=0;
  for(int field:new int[]{44,52,88,176})for(int accessory:new int[]{0,60})for(int reply:new int[]{0,48,96})
  for(boolean ai:new boolean[]{false,true}){
   FormattingGeometryCheck c=new FormattingGeometryCheck();c.height=field+accessory+reply+1;c.padding(accessory);c.layout();
   if(accessory>0){
    c.measure();check(c.nebulaFormattingToolbar.measuredWidth==344&&c.nebulaFormattingToolbar.measuredHeight==52,"accessory overlay has zero measured content");
    Canvas bar=new Canvas();c.parentClip(bar);
    check(bar.top<=c.nebulaFormattingToolbar.top&&bar.bottom>=c.nebulaFormattingToolbar.bottom,"FrameLayout clipToPadding hides formatting actions");
    check(c.nebulaFormattingToolbar.top==0&&c.nebulaFormattingToolbar.bottom==accessory-8,"formatting toolbar is not above native composer");
   }
   c.topView.top=accessory;c.topView.measuredHeight=reply;
   for(float visibility:new float[]{0,.25f,.5f,1}){
    float separator=accessory+reply*visibility;
    c.position(separator,visibility);
    check(Math.abs(c.topView.top+c.topView.translationY+reply*visibility-separator)<.01f,"reply preview translated twice by toolbar padding");
   }
   c.animatorInputFieldHeight.height=field;c.textFieldContainer.y=reply+accessory+1;c.aiButton.y=-20;
   c.nebulaComposerStyle.active=ai;c.shownAiButton=ai;
   Canvas canvas=new Canvas();c.parentClip(canvas);c.draw(c.textFieldContainer,canvas);
   float fieldTop=c.height-field,fieldBottom=c.height;
   check(canvas.top<=fieldTop&&canvas.bottom>=fieldBottom,"formatting row clips native text/buttons");
   if(ai)check(canvas.top<=c.textFieldContainer.y-20,"AI accessory clipped");
   Canvas preview=new Canvas();c.parentClip(preview);c.draw(c.topView,preview);check(preview.bottom==fieldTop,"reply separator overlaps composer");
   c.inputBubbleHeightRound=field+reply+accessory;c.inputAccessoryHeightRound=accessory;c.bubble();
   check(c.tmpRect.bottom-c.tmpRect.top==field+reply,"formatting toolbar expands input bubble instead of separate strip");
   check(c.inputBubbleHeightRound==field+reply+accessory,"accessory lost from chat content reserve");
   if(accessory>0){
    check(c.tmpRect.top==accessory&&c.tmpRect.bottom==field+reply+accessory,"input drawable overlaps the separate top toolbar");
   }
   c.padding(0);check(c.paddingTop==0&&c.paddingBottom==0&&c.clipPadding,"keyboard close leaks reserved height or parent clipping");cases++;
  }
  for(float density:new float[]{1,1.5f,2.75f,3.5f}){
   AndroidUtilities.density=density;FormattingGeometryCheck c=new FormattingGeometryCheck();c.button();
   check(c.row.captured.width==Math.round(44*density)&&c.row.captured.height==-1,"scroll-view weighted buttons collapse to zero width");
  }
  FormattingGeometryCheck poll=new FormattingGeometryCheck();poll.inputBubbleHeightRound=-7;poll.bubble();
  check(poll.tmpRect.bottom==-7,"disabled toolbar changes native poll transition");
  System.out.println(cases+" native composer parent-clip/padding/top-accessory cases and fixed-width accessory actions passed");
 }
}
'''.replace('POSITION',position).replace('CLIP',clip).replace('BOUNDS',bounds).replace('BUTTON',button).replace('PADDING',padding).replace('LAYOUT',layout).replace('MEASURE',measure)
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-format-geometry-') as directory:
 work=Path(directory);test=work/'FormattingGeometryCheck.java';test.write_text(source,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(test)],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'FormattingGeometryCheck'],check=True)
print('Native container keeps full content reserve while drawing a separate input island; real keyboard/IME acceptance remains required')
