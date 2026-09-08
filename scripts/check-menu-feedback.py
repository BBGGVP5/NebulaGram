"""Run real anchor/haptic methods in JVM fakes, then check native wiring."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
work = root / 'build/menu-feedback-check'
work.mkdir(parents=True, exist_ok=True)


def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


reveal = (ui / 'NebulaMenuReveal.java').read_text()
haptics = (ui / 'NebulaHaptics.java').read_text()
source = r'''
import java.lang.ref.WeakReference;
import app.nebulagram.ui.NebulaMenuMotion;
public class CheckMenuFeedback {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Observer {
  Runnable listener;
  boolean isAlive(){return true;}
  void addOnPreDrawListener(Runnable r){listener=r;}
  void removeOnPreDrawListener(Runnable r){listener=null;}
 }
 static class View {
  int x,y,w=200,h=300; boolean attached,shownFromBottom,feedback=true;
  float sx=1,sy=1,alpha=1,px,py;
  Observer observer=new Observer();
  boolean isAttachedToWindow(){return attached;}
  boolean isHapticFeedbackEnabled(){return feedback;}
  Context getContext(){return new Context();}
  boolean performHapticFeedback(int c){Vibrator.calls++;return true;}
  int getWidth(){return w;} int getHeight(){return h;}
  void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}
  void setScaleX(float f){sx=f;} void setScaleY(float f){sy=f;}
  void setAlpha(float f){alpha=f;} void setPivotX(float f){px=f;} void setPivotY(float f){py=f;}
  Observer getViewTreeObserver(){return observer;} void invalidate(){}
 }
 static class Reveal {
  View host=new View(); WeakReference<View> anchor;
  float progress=1,originX,originY; boolean began,originResolved;
  int[] location=new int[2]; Runnable originListener=()->resolveOrigin();
  void stopTouch(){}
  REVEAL_METHODS
 }
 static class Context {static String VIBRATOR_SERVICE="v";Object getSystemService(String s){return new Vibrator();}}
 static class Vibrator {static int calls,amplitude;static long duration;static boolean available=true;
  boolean hasVibrator(){return available;} void vibrate(Object e){calls++;}}
 static class VibrationEffect {static Object createOneShot(long d,int a){Vibrator.duration=d;Vibrator.amplitude=a;return new Object();}}
 static class Build {static class VERSION {static int SDK_INT=30;}}
 static long now; static long uptimeMillis(){return now;}
 static boolean enabled; static boolean enabled(){return enabled;}
 static int power; static class Prefs {int getInt(String key,int fallback){return power;}}
 static Prefs prefs(){return new Prefs();}
 STRENGTH_METHOD
 static long lastTick=-100;
 HAPTIC_METHODS
 public static void main(String[] args){
  Reveal r=new Reveal();View anchor=new View();anchor.attached=true;anchor.x=240;anchor.y=20;anchor.w=40;anchor.h=40;
  r.setAnchor(anchor);r.host.x=100;r.host.y=80;r.host.shownFromBottom=true;
  r.begin();r.setProgress(.3f);r.resolveOrigin();
  check(!r.originResolved && r.host.alpha==0,"must wait for popup attachment");
  r.host.attached=true;r.resolveOrigin();
  check(r.host.px==160 && r.host.py==0,"top anchor must override bottom flag");
  check(r.host.observer.listener==null,"pre-draw listener removed");
  float previous=r.host.sx;
  for(int i=31;i<=100;i++){r.setProgress(i/100f);check(r.host.sx>=previous && r.host.sx<=1,"monotonic scale");previous=r.host.sx;}
  r.setProgress(.45f);float pivot=r.host.py;r.setProgress(.2f);
  check(r.host.py==pivot && r.host.sx<previous,"closing retains origin and current progress");
  anchor.y=450;r.host.shownFromBottom=false;r.begin();r.resolveOrigin();
  check(r.host.py==300,"bottom anchor must override top flag");
  r.reset();check(r.host.alpha==1 && r.host.sx==1 && !r.began,"detach reset");
  View v=new View();power=35;now=100;tick(v);check(Vibrator.calls==0,"toggle off");
  enabled=true;v.feedback=false;tick(v);check(Vibrator.calls==0,"view feedback disabled");
  v.feedback=true;Vibrator.available=false;tick(v);check(Vibrator.calls==0,"no vibrator");
  Vibrator.available=true;power=-5;tick(v);check(Vibrator.calls==1 && Vibrator.amplitude==27,"minimum strength");
  now=139;tick(v);check(Vibrator.calls==1,"duplicate tick suppressed");
  now=140;power=150;tick(v);check(Vibrator.calls==2 && Vibrator.amplitude==225 && Vibrator.duration==20,"maximum strength and debounce boundary");
  Build.VERSION.SDK_INT=25;now=180;tick(v);check(Vibrator.calls==3,"legacy feedback");
  System.out.println("Anchors, delayed attachment, interrupted scale, reset and haptic policy passed");
 }
}
'''
source = source.replace('REVEAL_METHODS', '\n'.join(method(reveal, sig) for sig in [
    'public void setAnchor(', 'public void begin(', 'private boolean resolveOrigin(',
    'private void removeOriginListener(', 'private void applyMotion(', 'public void setProgress(', 'public void reset(']))
source = source.replace('HAPTIC_METHODS', method(haptics, 'public static void tick(') + '\n' + method(haptics, 'public static boolean accept('))
source = source.replace('STRENGTH_METHOD', method(haptics, 'public static int strength()'))
source = source.replace('android.os.SystemClock.uptimeMillis()', 'uptimeMillis()').replace('android.view.HapticFeedbackConstants.KEYBOARD_TAP', '1')
target = work / 'CheckMenuFeedback.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target), str(ui / 'NebulaMenuMotion.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckMenuFeedback'], check=True)

style = (ui / 'NebulaMenuStyle.java').read_text()
opening = method(style, 'public static AnimatorSet opening(')
assert 'if (!cancelled)' in opening and 'child.setTranslationY(0)' in opening
assert 'getProgress(), 0f' in method(style, 'public static AnimatorSet closing(')
bar = (native / 'ActionBar/ActionBar.java').read_text()
width = bar[bar.index('final int nebulaBackWidth'):bar.index('final int menuWidthA')]
assert 'actionModeFactor == 0f && searchFactor == 0f' in width
assert 'NebulaHaptics.tick(v)' in (native / 'MainTabsActivity.java').read_text()
folders = (native / 'Components/FilterTabsView.java').read_text()
assert folders.count('NebulaHaptics.tick(view)') >= 2
assert 'nebulaLens.drawOutline(canvas, outline)' in folders
lens = (ui / 'NebulaTabLens.java').read_text()
assert 'paintedBounds.set(bounds)' in lens and 'outlineBounds.set(paintedBounds)' in lens
emoji = (native / 'Components/EmojiView.java').read_text()
assert 'nebulaPanelClip.addRoundRect' in emoji and 'bottomTabContainer.getY()' in emoji
assert 'createNebulaEmojiBackground(this)' in emoji
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
about = method(section, 'private void buildAbout(')
assert all(f'{card}.addView(NebulaCard.header' not in about for card in ['identity', 'links', 'components'])
print('Native menu/header/folder/sticker wiring and About section layout passed')
