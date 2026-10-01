"""Draw the actual slider methods with distinct global/chat palettes and fake Canvas."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
source = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaZoomSlider.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(method(s) for s in [
    'private float compactWidth()', 'private float fullWidth()', 'private float widthForProgress()',
    'private RectF capsuleBounds()', 'private float pixelsPerOctave()', 'private float xForZoom(',
    'protected void onDraw(Canvas canvas)', 'private String label(', 'private void drawPresets(',
    'private void drawMark(', 'private static int blend('])
java = r'''
import java.util.*;
class ZoomColorsCheck {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static class Theme {
  static int key_chat_messagePanelSend=1,key_chat_messagePanelText=2,key_chat_messagePanelBackground=3,key_chat_messagePanelVoicePressed=4;
  static class ResourcesProvider {int[] colors;ResourcesProvider(int[] c){colors=c;}}
  static int[] global={0,0xff1144dd,0xff123456,0xff667788,0xffeeeedd};
  static int getColor(int key,ResourcesProvider p){return p==null?global[key]:p.colors[key];}
  static int getColor(int key){return global[key];}
 }
 static class NebulaGlass {static float depth(){return 0;}static boolean highlights(){return false;}}
 static class Color {
  static int WHITE=0xffffffff,BLACK=0xff000000;
  static int red(int c){return c>>>16&255;}static int green(int c){return c>>>8&255;}static int blue(int c){return c&255;}
  static int argb(int a,int r,int g,int b){return a<<24|r<<16|g<<8|b;}
 }
 static class Shader {enum TileMode {CLAMP}}
 static class LinearGradient extends Shader {int first,last;LinearGradient(float x,float y,float a,float b,int first,int last,TileMode mode){this.first=first;this.last=last;}}
 static class android {static class graphics {static class Typeface {static Object DEFAULT_BOLD=new Object();}}}
 static class Paint {
  enum Style {FILL,STROKE}enum Align {CENTER}enum Cap {ROUND,BUTT}
  int color;float size;Shader shader;
  static class FontMetrics {float ascent,descent;FontMetrics(float size){ascent=-size*.75f;descent=size*.25f;}}
  void setStyle(Style s){}void setShader(Shader s){shader=s;}void setShadowLayer(float a,float b,float c,int d){}void clearShadowLayer(){}
  void setStrokeWidth(float w){}void setColor(int c){color=c;}void setAlpha(int a){color=(color&0xffffff)|(a<<24);}
  void setTypeface(Object t){}void setTextAlign(Align a){}void setTextSize(float size){this.size=size;}float getTextSize(){return size;}
  float measureText(String text){return text.length()*size*.5f;}FontMetrics getFontMetrics(){return new FontMetrics(size);}void setStrokeCap(Cap c){}
 }
 static class RectF {float left,top,right,bottom;RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}float width(){return right-left;}float centerY(){return (top+bottom)/2;}}
 static class Glass {void setBounds(int l,int t,int r,int b){}void draw(Canvas c){}}
 static class Canvas {
  List<Integer> circles=new ArrayList<>(),texts=new ArrayList<>(),lines=new ArrayList<>(),backgrounds=new ArrayList<>();
  void drawRoundRect(RectF rect,float a,float b,Paint p){if(p.shader instanceof LinearGradient)backgrounds.add(((LinearGradient)p.shader).first);}
  void drawCircle(float x,float y,float radius,Paint p){circles.add(p.color);}
  void drawText(String text,float x,float y,Paint p){texts.add(p.color);}
  void drawLine(float x,float y,float a,float b,Paint p){lines.add(p.color);}
  void save(){}void restore(){}void clipRect(float l,float t,float r,float b){}
 }
 static class Parent {protected void onDraw(Canvas c){}}
 static class Slider extends Parent {
  Paint paint=new Paint();float current=1.4f,minimum=1,maximum=2,expansion;float[] cameraStops={1,2},rulerMarks={1,2};Glass glass;
  Theme.ResourcesProvider resourcesProvider;
  Slider(Theme.ResourcesProvider p){resourcesProvider=p;}
  int getWidth(){return 352;}int getHeight(){return 96;}static int dp(float f){return Math.round(f);}
  METHODS
 }
 static boolean hasRgb(List<Integer> values,int expected){for(int c:values)if((c&0xffffff)==(expected&0xffffff))return true;return false;}
 public static void main(String[] args){
  Theme.ResourcesProvider provider=new Theme.ResourcesProvider(new int[]{0,0xffff8800,0xffeeeeee,0xff151515,0xff111111});
  Slider slider=new Slider(provider);
  for(int[] palette:new int[][]{provider.colors,new int[]{0,0xffa700df,0xff112244,0xfffafafa,0xfffefefe}}){
   provider.colors=palette;
   for(float progress:new float[]{0,.5f,1}){
    slider.expansion=progress;Canvas canvas=new Canvas();slider.onDraw(canvas);
    check(hasRgb(canvas.backgrounds,palette[3]),"material fallback must follow the active chat background on every draw");
    if(progress<1){check(hasRgb(canvas.circles,palette[1]),"preset accent must come from chat provider, not global theme");check(hasRgb(canvas.texts,palette[4])&&hasRgb(canvas.texts,palette[2]),"selected and inactive labels must both use chat colors");}
    if(progress>0){check(hasRgb(canvas.lines,palette[1])&&hasRgb(canvas.lines,palette[2]),"ruler majors, indicator and minor ticks must use chat palette");}
   }
  }
  slider.resourcesProvider=null;slider.expansion=0;Canvas fallback=new Canvas();slider.onDraw(fallback);
  check(hasRgb(fallback.circles,Theme.global[1]),"null provider retains native global fallback");
  System.out.println("Zoom palette: actual draw methods follow two chat themes in compact/expanded modes; global fallback passed");
 }
}
'''.replace('METHODS', methods)
with tempfile.TemporaryDirectory(prefix='nebula-zoom-colors-') as temp:
    file = Path(temp) / 'ZoomColorsCheck.java'
    file.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(file)], check=True)
    subprocess.run(['java', '-cp', temp, 'ZoomColorsCheck'], check=True)
