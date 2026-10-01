"""Run the native menu-to-sheet fade and backdrop draw outside the message clip."""
from pathlib import Path
import subprocess
import tempfile
import sys

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
text = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')

def method(signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

close_menu = method('private void closeMenu(boolean hideDim)')
end = text.index('            if (scrimView != null || messageEnterTransitionContainer.isRunning())')
start = text.rfind('            canvas.restoreToCount(nebulaScrimSave);', 0, end)
backdrop = text[start:end]
assert backdrop.index('restoreToCount') < backdrop.index('canvas.drawRect'), 'backdrop still drawn through lifted-message clip'

harness = r'''
import java.util.*;
class TranslationLiftCheck {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static class View {int invalidations;void invalidate(){invalidations++;}}
 static class Animator {}
 static class AnimatorListenerAdapter {public void onAnimationCancel(Animator a){}public void onAnimationEnd(Animator a){}}
 static class ValueAnimator extends Animator {
  List<AnimatorListenerAdapter> listeners=new ArrayList<>();float value;boolean cancelled;
  static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
  void removeAllListeners(){listeners.clear();}void cancel(){cancelled=true;for(var l:listeners)l.onAnimationCancel(this);}
  void addListener(AnimatorListenerAdapter l){listeners.add(l);}void addUpdateListener(java.util.function.Consumer<ValueAnimator> c){}
  Object getAnimatedValue(){return value;}void setDuration(int ms){}void start(){}
  void end(){for(var l:listeners)l.onAnimationEnd(this);}
 }
 static class Popup {void dismiss(){}}
 static class Canvas {boolean lifted=true;int draws;void restoreToCount(int n){lifted=false;}void drawRect(int l,int t,int r,int b,Paint p){check(!lifted,"translation backdrop retains the message clip/scale");draws++;}}
 static class Paint {void setAlpha(int a){}}
 static class Matrix {void reset(){}void postScale(float x,float y){}}
 static class Bitmap {int getWidth(){return 40;}}
 static class Shader {void setLocalMatrix(Matrix m){}}
 static class Chat {
  boolean scrimPopupWindowHideDimOnDismiss;Popup scrimPopupWindow=new Popup();ValueAnimator scrimViewAlphaAnimator;
  View scrimView=new View(),contentView=new View(),chatListView=new View();Object scrimViewReaction;
  float scrimViewAlpha=1,scrimViewProgress=1,scrimPaintAlpha=.2f,nebulaScrimScale=.6f,nebulaScrimTop=10,nebulaScrimSourceTop=20,nebulaScrimClipBottom=30;
  int nebulaBlurGeneration=1,nebulaScrimSave;Paint scrimBlurBitmapPaint=new Paint(),scrimPaint=new Paint();Matrix scrimBlurMatrix=new Matrix();Bitmap scrimBlurBitmap=new Bitmap();Shader scrimBlurBitmapShader=new Shader();
  void setScrimView(View v){scrimView=v;}int getMeasuredWidth(){return 400;}int getMeasuredHeight(){return 800;}
  CLOSE
  void drawBackdrop(Canvas canvas){BACKDROP}
 }
 public static void main(String[] args){
  Chat c=new Chat();c.closeMenu(false);c.scrimViewAlpha=.5f;Canvas canvas=new Canvas();c.drawBackdrop(canvas);check(canvas.draws==1,"blurred backdrop must draw once at full-screen coordinates");
  c.scrimBlurBitmapPaint=null;canvas=new Canvas();c.drawBackdrop(canvas);check(canvas.draws==1,"plain dim backdrop must also escape lift clip");
  c.scrimViewAlphaAnimator.end();check(c.scrimView==null&&c.nebulaScrimScale==1&&c.nebulaScrimClipBottom==0,"completed handoff must restore message and clear lift bounds");
  c=new Chat();c.closeMenu(false);c.scrimViewAlphaAnimator.cancel();c.scrimViewAlphaAnimator.end();check(c.scrimView!=null,"cancelled fade must not clear a live preview");
  c=new Chat();c.closeMenu(false);c.nebulaBlurGeneration++;c.scrimViewAlphaAnimator.end();check(c.scrimView!=null,"new backdrop generation must ignore stale completion");
  c=new Chat();c.closeMenu(false);View next=c.scrimView=new View();c.scrimViewAlphaAnimator.end();check(c.scrimView==next,"new preview must survive old fade completion");
  c=new Chat();c.closeMenu(true);check(c.scrimViewAlphaAnimator==null,"ordinary menu close remains native");
  System.out.println("Translation lift: screen-coordinate backdrop, completed handoff, stale/cancel guards passed");
 }
}
'''.replace('CLOSE', close_menu).replace('BACKDROP', backdrop)
with tempfile.TemporaryDirectory(prefix='nebula-translation-lift-') as temp:
    file = Path(temp) / 'TranslationLiftCheck.java'
    file.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(file)], check=True)
    subprocess.run(['java', '-cp', temp, 'TranslationLiftCheck'], check=True)
