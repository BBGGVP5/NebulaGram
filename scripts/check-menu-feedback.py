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
import app.nebulagram.ui.NebulaMenuBubble;
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
  int getWidth(){return w;} int getHeight(){return h;}OutlineProvider getOutlineProvider(){return null;}
  void getWindowVisibleDisplayFrame(Rect r){r.left=r.top=-2000;r.right=r.bottom=2000;}
  void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}
  void setScaleX(float f){sx=f;} void setScaleY(float f){sy=f;}
  float getScaleX(){return sx;} float getScaleY(){return sy;}float getAlpha(){return alpha;}
  void setAlpha(float f){alpha=f;} void setPivotX(float f){px=f;} void setPivotY(float f){py=f;}
  Observer getViewTreeObserver(){return observer;} void invalidate(){}
  int getItemsCount(){return 0;}View getItemAt(int i){return this;}Object getParent(){return this;}
 }
 static class Reveal {
  View host=new View(); WeakReference<View> anchor;
  float progress=1,originX,originY,seed,seedWidth,seedHeight,seedRadius,closeProgress,closeStart,clockStart;
  final NebulaMenuBubble.Frame frame=new NebulaMenuBubble.Frame(),closeFrame=new NebulaMenuBubble.Frame();
  View focusContent;boolean morphEnabled=true;
  boolean began,originResolved,closing,viewportReady=true;int focusStep=-1;
  Rect screen=new Rect();int[] location=new int[2]; Runnable originListener=()->resolveOrigin();
  void stopTouch(){}
  REVEAL_METHODS
 }
 static class Rect {int left=-2000,top=-2000,right=2000,bottom=2000;Rect(){}Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
  boolean isEmpty(){return width()<=0||height()<=0;}void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;}
  int width(){return right-left;}int height(){return bottom-top;}float exactCenterX(){return (left+right)/2f;}float exactCenterY(){return (top+bottom)/2f;}
  void offset(int x,int y){left+=x;right+=x;top+=y;bottom+=y;}}
 static class Outline {boolean isEmpty(){return true;}boolean getRect(Rect r){return false;}float getRadius(){return 0;}}
 static class OutlineProvider {void getOutline(View v,Outline o){}}
 static class AndroidUtilities {static int dp(int n){return n;}}
 static class NebulaMenuFocus {static int step;static void apply(View v,int n){step=n;}static void clear(View v){step=0;}}
 static class NebulaMenuStyle {static float radius(){return 24;}}
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
  check(r.host.px==160 && r.host.py== -40,"top anchor must override bottom flag");
  check(Math.abs(r.frame.width*(r.host.w-16)/r.host.w-40)<.001 && Math.abs(r.frame.height*(r.host.h-16)/r.host.h-40)<.001 && r.frame.radius==20 && r.frame.content==0,"visible seed matches the source after proportional native drawable padding");
  check(r.host.observer.listener==null,"pre-draw listener removed");
  float peak=0;
  for(int i=31;i<=100;i++){r.setProgress(i/100f);check(r.host.sx==1 && r.host.sy==1,"surface geometry must not scale the whole window");peak=Math.max(peak,r.frame.width);}
  check(peak>200 && r.frame.width==200 && r.frame.height==300 && r.frame.x==100 && r.frame.y==150,"independent growth settles exactly at native bounds");
  r.setProgress(.45f);float pivot=r.host.py,previous=r.frame.width,alpha=r.host.alpha,center=r.frame.x;r.prepareClose();check(r.getCloseDuration()>=350,"grown popup retains a soft close duration");r.setCloseProgress(0);
  check(r.frame.width==previous && r.host.alpha==alpha && r.frame.x==center,"interrupt close starts at displayed frame");
  for(int i=1;i<=100;i++){r.setCloseProgress(i/100f);check(r.host.py==pivot && r.frame.width<=previous+.001f,"reverse reveal never jumps origin or grows");previous=r.frame.width;}
  check(r.host.alpha==0 && Math.abs(r.frame.width-r.seedWidth)<.001 && Math.abs(r.frame.height-r.seedHeight)<.001,"close ends at source bounds");
  anchor.y=450;r.host.shownFromBottom=false;r.begin();r.resolveOrigin();
  check(r.host.py==390,"bottom anchor must override top flag");
  r.reset();check(r.host.alpha==1 && r.host.sx==1 && !r.began,"detach reset");
  for(int side=0;side<4;side++)for(int cycle=0;cycle<25;cycle++){
   anchor.x=(side%2==0?20:500);anchor.y=(side<2?20:500);r.setAnchor(anchor);r.viewportReady=true;r.begin();
   float px=r.host.px,py=r.host.py;r.setProgress(.1f);
   check(r.originResolved && r.host.observer.listener==null,"resolve attached anchor before first visible frame");
   check(px==(side%2==0?-60:420)&&py==(side<2?-40:440),"correct source corner");
   check(Math.abs(r.seedWidth*(r.host.w-16)/r.host.w-anchor.w)<.001 && Math.abs(r.seedHeight*(r.host.h-16)/r.host.h-anchor.h)<.001,"initial visible shape follows source button dimensions");
   r.prepareClose();r.setCloseProgress(.5f);r.reset();check(NebulaMenuFocus.step==0,"cancel releases focus");
  }
  r.viewportReady=false;r.begin();r.setProgress(.1f);
  check(r.frame.x==100&&r.frame.y==150&&r.frame.width==200&&r.frame.height==300&&r.frame.content==1,"unsupported drawing surfaces fade at native bounds");
  r.reset();check(!r.viewportReady,"detach forgets old drawing viewport");
  r.setMorphEnabled(false);r.reset();r.setAnchor(anchor);check(!r.isMorphEnabled(),"native message policy survives reuse/reset");
  check(anchor.alpha==1,"trigger remains visible throughout all cycles");
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
    'private void removeOriginListener(', 'private void applyMotion(', 'private void updateFrame(', 'private void clearFocus(', 'public void setProgress(', 'public void reset(',
    'public void prepareClose(', 'public void setCloseProgress(', 'public int getCloseDuration(', 'public boolean isMorphEnabled(', 'public void setMorphEnabled(']))
# The no-entry caller path only needs an endpoint in this lifecycle fixture.
source = source.replace('void stopTouch(){}', 'void stopTouch(){} void finish(){closing=false;setProgress(1);}')
source = source.replace('HAPTIC_METHODS', method(haptics, 'public static void tick(') + '\n' + method(haptics, 'public static boolean accept('))
source = source.replace('STRENGTH_METHOD', method(haptics, 'public static int strength()'))
source = source.replace('android.os.SystemClock.uptimeMillis()', 'uptimeMillis()').replace('android.view.HapticFeedbackConstants.KEYBOARD_TAP', '1')
source = source.replace('android.graphics.Outline','Outline').replace('android.os.Build.VERSION','Build.VERSION')
target = work / 'CheckMenuFeedback.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target), str(ui / 'NebulaMenuMotion.java'), str(ui / 'NebulaMenuBubble.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckMenuFeedback'], check=True)

style = (ui / 'NebulaMenuStyle.java').read_text()
opening = method(style, 'public static AnimatorSet opening(')
assert 'if (!cancelled)' in opening and 'child.setTranslationY(0)' in opening
assert 'content.clipChildren = false;' in opening, 'reusing a native-fallback popup must clear its old canvas clip'
assert 'setCloseProgress(' in method(style, 'public static AnimatorSet closing(')
assert 'ValueAnimator.areAnimatorsEnabled()' in style and '!NebulaGlass.reduced()' in style
bar = (native / 'ActionBar/ActionBar.java').read_text(encoding='utf-8')
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
