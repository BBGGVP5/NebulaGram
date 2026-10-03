"""Exercise native title slot reuse with rapid switches and late callbacks."""
from pathlib import Path
import subprocess
import sys
import tempfile

source = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
start = source.index('    public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration, Interpolator interpolator)')
opening = source.index('{', start)
depth, end = 1, opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end]
harness = r'''
class TitleAnimationCheck {
 static class Animator {}
 static class AnimatorListenerAdapter { public void onAnimationEnd(Animator a) {} }
 interface Interpolator {}
 static class View { static final int VISIBLE=0, GONE=8; }
 static class ViewGroup { void removeView(SimpleTextView v) { v.parent=null; } }
 static class ViewPropertyAnimator {
  AnimatorListenerAdapter listener;
  ViewPropertyAnimator alpha(float f){return this;}
  ViewPropertyAnimator translationY(float f){return this;}
  ViewPropertyAnimator setDuration(long l){return this;}
  ViewPropertyAnimator setInterpolator(Interpolator i){return this;}
  ViewPropertyAnimator setListener(AnimatorListenerAdapter l){listener=l;return this;}
  void start(){}
  void cancel(){if(listener!=null)listener.onAnimationEnd(new Animator());}
 }
 static class SimpleTextView {
  ViewGroup parent=new ViewGroup();
  CharSequence text;
  final ViewPropertyAnimator animator=new ViewPropertyAnimator();
  SimpleTextView(CharSequence t){text=t;}
  ViewGroup getParent(){return parent;}
  ViewPropertyAnimator animate(){return animator;}
  void setAlpha(float f){}
  void setTranslationY(float f){}
  int getVisibility(){return View.VISIBLE;}
  void setVisibility(int i){}
 }
 static class TextUtils {static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}
 SimpleTextView[] titleTextView={new SimpleTextView("First"),null};
 SimpleTextView subtitleTextView=new SimpleTextView("");
 CharSequence subtitle;
 boolean overlayTitleAnimation, titleAnimationRunning, fromBottom, nebulaHomeTabsGlass=true;
 int dp(int v){return v;}
 void requestLayout(){}
 void setTitle(CharSequence t){if(titleTextView[0]==null)titleTextView[0]=new SimpleTextView(t);else titleTextView[0].text=t;}
''' + method + r'''
 public static void main(String[] args) {
  TitleAnimationCheck t=new TitleAnimationCheck();
  for(int i=0;i<100;i++) {
   t.setTitleAnimated("Folder "+i,false,220,null);
   SimpleTextView incoming=t.titleTextView[0], old=t.titleTextView[1];
   AnimatorListenerAdapter stale=old.animator.listener;
   t.setTitleAnimated("Emoji "+i,false,220,null);
   if(t.titleTextView[1]!=incoming||incoming.parent==null||old.parent!=null)
    throw new AssertionError("Canceled listener removed the wrong title");
   stale.onAnimationEnd(new Animator());
   if(t.titleTextView[1]!=incoming||!t.titleAnimationRunning)
    throw new AssertionError("Late completion damaged a newer transition");
   incoming.animator.listener.onAnimationEnd(new Animator());
   if(t.titleTextView[1]!=null||t.titleAnimationRunning||t.titleTextView[0].parent==null)
    throw new AssertionError("Latest title did not survive completion");
  }
  System.out.println("PASS: 100 rapid native title transitions, cancellation and late completion");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-title-animation-') as directory:
    work = Path(directory)
    test = work / 'TitleAnimationCheck.java'
    test.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(test)], check=True)
    subprocess.run(['java', '-cp', str(work), 'TitleAnimationCheck'], check=True)
