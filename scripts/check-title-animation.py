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
def extract(signature):
    begin = source.index(signature)
    opening = source.index('{', begin)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[begin:end]

folder_method = extract('    public void setNebulaTitle(')
restore = extract('                    if (!overlayTitleAnimationInProgress && !titleOverlayShown')
harness = r'''
class TitleAnimationCheck {
 static class Drawable {}
 static class ValueAnimator { static boolean areAnimatorsEnabled(){return true;} }
 static class CubicBezierInterpolator { static Interpolator EASE_OUT_QUINT=null; }
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
  CharSequence getText(){return text;}
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
 boolean titleOverlayShown, overlayTitleAnimationInProgress, attached=true;
 Object parentFragment=new org.telegram.ui.DialogsActivity();
 CharSequence lastTitle;
 Drawable lastRightDrawable;
 static final int VISIBLE=View.VISIBLE;
 int getMeasuredWidth(){return 400;}
 int dp(int v){return v;}
 void requestLayout(){}
 void setTitle(CharSequence t){if(titleTextView[0]==null)titleTextView[0]=new SimpleTextView(t);else titleTextView[0].text=t;}
 void setTitle(CharSequence t,Drawable d){lastTitle=t;lastRightDrawable=d;setTitle(t);}
''' + method + folder_method + ' void restoreFolderAfterNetworkTransition(){' + restore + '}' + r'''
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
  // Selecting another folder while the network title exits must not leave
  // the previous title visible after the native null-overlay deduplication.
  t.titleOverlayShown=false;t.overlayTitleAnimationInProgress=true;
  t.setNebulaTitle("Latest folder",null,true);
  if(t.titleTextView[0].text.toString().equals("Latest folder"))
   throw new AssertionError("Folder interrupted active network transition");
  t.overlayTitleAnimationInProgress=false;
  t.restoreFolderAfterNetworkTransition();
  if(!t.titleTextView[0].text.toString().equals("Latest folder"))
   throw new AssertionError("Network completion retained stale folder");
  System.out.println("PASS: 100 rapid native title transitions, cancellation and late completion");
  System.out.println("PASS: latest folder restored after network-title transition");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-title-animation-') as directory:
    work = Path(directory)
    test = work / 'TitleAnimationCheck.java'
    test.write_text(harness, encoding='utf-8')
    support = {
        'android/os/Build.java': 'package android.os; public class Build { public static class VERSION { public static int SDK_INT=36; } }',
        'org/telegram/ui/DialogsActivity.java': 'package org.telegram.ui; public class DialogsActivity {}',
        'app/nebulagram/ui/NebulaDialogsTitle.java': 'package app.nebulagram.ui; public class NebulaDialogsTitle { public static boolean sameTitle(CharSequence a,CharSequence b){ return a==b || a!=null && a.equals(b); } }',
    }
    paths = [str(test)]
    for name, content in support.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
        paths.append(str(path))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *paths], check=True)
    subprocess.run(['java', '-cp', str(work), 'TitleAnimationCheck'], check=True)
