"""Execute native status drawing at shifted bounds (centered chat subtitle)."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components'
work = root / 'build/status-bounds-check'
work.mkdir(parents=True, exist_ok=True)


def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


names = ['SendingFileDrawable', 'RecordStatusDrawable', 'RoundStatusDrawable', 'PlayingGameDrawable']
classes = []
for name in names:
    text = (native / (name + '.java')).read_text(encoding='utf-8')
    classes.append('static class ' + name + ' extends Status {\n' + '\n'.join(method(text, signature) for signature in [
        'public void draw(', 'public int getIntrinsicWidth(', 'public int getIntrinsicHeight(']) + '\n}')
source = r'''
import java.util.*;
public class CheckStatusBounds {
 static class Rect {float left,top,right,bottom;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
 static class Bounds {int left,top,right,bottom;void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
 static class Paint {float stroke=2;void setAlpha(int a){}void setColor(int c){}void setStrokeWidth(float f){stroke=f;}float getStrokeWidth(){return stroke;}}
 static class AndroidUtilities {static float density=1;static int dp(float f){return (int)Math.ceil(f*density);}}
 static class Theme {static Paint chat_statusRecordPaint=new Paint(),chat_statusPaint=new Paint();
  static int key_chats_actionMessage,key_chat_status,key_windowBackgroundWhite,key_actionBarDefault;
  static int getColor(int key,Object... p){return 0xffffffff;}}
 static class Canvas {
  float x,y;ArrayList<float[]> points=new ArrayList<>();ArrayDeque<float[]> stack=new ArrayDeque<>();
  int save(){int old=stack.size();stack.push(new float[]{x,y});return old;}
  void restore(){float[] p=stack.pop();x=p[0];y=p[1];}
  void restoreToCount(int n){while(stack.size()>n)restore();}
  void translate(float dx,float dy){x+=dx;y+=dy;}
  void point(float a,float b){points.add(new float[]{a+x,b+y});}
  void drawLine(float a,float b,float c,float d,Paint p){point(a,b);point(c,d);}
  void drawCircle(float a,float b,float r,Paint p){point(a-r,b-r);point(a+r,b+r);}
  void drawArc(Rect r,float start,float sweep,boolean center,Paint p){point(r.left,r.top);point(r.right,r.bottom);}
 }
 static abstract class Status {
  boolean isChat,started,isDialogScreen;float progress;int alpha=255;Object resourcesProvider;
  Paint currentPaint=new Paint(),paint=new Paint();Rect rect=new Rect();Bounds bounds=new Bounds();
  Bounds getBounds(){return bounds;}void update(){}void checkUpdate(){}
  abstract void draw(Canvas c);abstract int getIntrinsicWidth();abstract int getIntrinsicHeight();
 }
 CLASSES
 public static void main(String[] args){
  int tested=0;ArrayList<String> failed=new ArrayList<>();
  for(Status s:new Status[]{new SendingFileDrawable(),new RecordStatusDrawable(),new RoundStatusDrawable(),new PlayingGameDrawable()})
  for(float density:new float[]{1,2.75f})for(boolean chat:new boolean[]{false,true})for(float progress:new float[]{0,.25f,.75f}) {
   AndroidUtilities.density=density;s.isChat=chat;s.progress=progress;
   int width=s.getIntrinsicWidth(),height=s.getIntrinsicHeight();
   s.bounds.set(0,0,width,height);Canvas origin=new Canvas();s.draw(origin);
   s.bounds.set(57,13,57+width,13+height);Canvas shifted=new Canvas();s.draw(shifted);
   boolean ok=origin.points.size()==shifted.points.size() && !origin.points.isEmpty();
   for(int i=0;i<origin.points.size();i++){
    float[] a=origin.points.get(i),b=shifted.points.get(i);
    ok &= Math.abs(b[0]-a[0]-57)<.001 && Math.abs(b[1]-a[1]-13)<.001;
   }
   ok &= shifted.x==0 && shifted.y==0 && shifted.stack.isEmpty();
   ok &= width==s.getIntrinsicWidth() && height==s.getIntrinsicHeight();
   if(!ok && !failed.contains(s.getClass().getSimpleName()))failed.add(s.getClass().getSimpleName());
   tested++;
  }
  if(!failed.isEmpty())throw new AssertionError("Drawables ignore centered status bounds: "+failed);
  System.out.println(tested+" native status drawings honor bounds and restore Canvas state");
 }
}
'''.replace('CLASSES', '\n'.join(classes))
target = work / 'CheckStatusBounds.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target)], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckStatusBounds'], check=True)
