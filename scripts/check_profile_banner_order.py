"""Run the real native child hook with visible-child sequences, including GONE overlays."""
from pathlib import Path
import subprocess
import tempfile


def check(native: str) -> None:
    start = native.index('protected boolean drawChild(', native.index('avatarContainer2 = new FrameLayout(context)'))
    opening = native.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (native[end] == '{') - (native[end] == '}')
        end += 1
    method = native[start:end]
    code = '''import java.util.*;
class View {final String name;View(String n){name=n;}}
class Canvas {final List<String> events=new ArrayList<>();}
class Parent {protected boolean drawChild(Canvas c,View v,long time){c.events.add(v.name);return true;}}
class Hero {void drawForeground(Canvas c){c.events.add("fade");}}
public class NativeHeaderCheck extends Parent {
 boolean isInLandscapeMode,nebulaForegroundDrawn;final Hero nebulaHero=new Hero();
 final View giftsView=new View("gifts"),avatarGooey=new View("avatar"),avatarsViewPager=new View("gallery"),avatarsBlurView=new View("blur"),overlaysView=new View("overlays");
 final View actions=new View("actions"),music=new View("music"),title=new View("title");
 METHOD
 void frame(boolean expanded,boolean hasActions,boolean hasMusic,boolean landscape){
  isInLandscapeMode=landscape;nebulaForegroundDrawn=false;Canvas c=new Canvas();
  List<View> visible=new ArrayList<>(List.of(giftsView,avatarGooey));
  if(expanded)visible.add(avatarsViewPager);visible.add(avatarsBlurView);
  if(expanded)visible.add(overlaysView); // collapsed native OverlaysView is GONE
  if(hasActions)visible.add(actions);if(hasMusic)visible.add(music);visible.add(title);
  for(View v:visible)drawChild(c,v,0);
  if(landscape){if(c.events.contains("fade"))throw new AssertionError("portrait fade in landscape");return;}
  if(Collections.frequency(c.events,"fade")!=1)throw new AssertionError("missing/repeated fade: "+c.events);
  int fade=c.events.indexOf("fade");
  for(String bg:List.of("gifts","avatar","gallery","blur","overlays"))if(c.events.contains(bg)&&c.events.indexOf(bg)>fade)throw new AssertionError("photo overwrites fade: "+c.events);
  for(String fg:List.of("actions","music","title"))if(c.events.contains(fg)&&c.events.indexOf(fg)<fade)throw new AssertionError("fade covers foreground: "+c.events);
 }
 public static void main(String[] args){NativeHeaderCheck node=new NativeHeaderCheck();
  for(int repeat=0;repeat<3;repeat++)for(boolean expanded:new boolean[]{false,true})for(boolean actions:new boolean[]{false,true})for(boolean music:new boolean[]{false,true})for(boolean landscape:new boolean[]{false,true})node.frame(expanded,actions,music,landscape);
  System.out.println("48 actual native draw-order cases: hidden overlays, optional controls, repeated frames and landscape passed");
 }
}
'''.replace('METHOD', method)
    with tempfile.TemporaryDirectory(prefix='nebula-profile-order-') as folder:
        work = Path(folder)
        source = work / 'NativeHeaderCheck.java'
        source.write_text(code, encoding='utf-8')
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(source)], check=True)
        subprocess.run(['java', '-cp', str(work), 'NativeHeaderCheck'], check=True)


def check_animation(native: str) -> None:
    """Execute native listeners, including Android's cancel-then-end dispatch."""
    scope = native.index('if (h > headerExtraHeight || isPulledDown)')
    expressions = []
    start = native.index('private void collapseAvatarInstant()')
    opening = native.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (native[end] == '{') - (native[end] == '}')
        end += 1
    instant = native[start:end]
    for target in ['1f', '0f']:
        start = native.index('new AnimatorListenerAdapter() {', native.index('expandAnimatorValues[1] = ' + target + ';', scope))
        opening = native.index('{', start)
        end, depth = opening + 1, 1
        while depth:
            depth += (native[end] == '{') - (native[end] == '}')
            end += 1
        expressions.append(native[start:end])
    code = '''class Animator {}
class AnimatorListenerAdapter {
 public void onAnimationStart(Animator a){}public void onAnimationCancel(Animator a){}public void onAnimationEnd(Animator a){}
}
class View {
 static final int VISIBLE=0,GONE=8;int visibility=7,background=19;float alpha=.37f;
 void setVisibility(int v){visibility=v;}void setAlpha(float a){alpha=a;}void setBackgroundColor(int c){background=c;}
 void setAnimatedFileMaybe(Object a){}void resetCurrentItem(){}
}
class Color {static int BLACK=0xff000000;}
class Theme {static int key_windowBackgroundGray=1;}
class ListView {boolean canStopFlinger;int getPaddingTop(){return 0;}void post(Runnable action){action.run();}}
class LayoutManager {void scrollToPositionWithOffset(int position,int offset){}}
class Image {Receiver receiver=new Receiver();Receiver getImageReceiver(){return receiver;}}
class Receiver {Object getAnimation(){return null;}}
class ExpandAnimator {int removals;boolean running;AnimatorListenerAdapter listener;
 void removeListener(AnimatorListenerAdapter a){removals++;}boolean isRunning(){return running;}
 void cancel(){running=false;listener.onAnimationCancel(new Animator());listener.onAnimationEnd(new Animator());}}
public class NativeAnimationCheck {
 boolean isPulledDown,justFullyExpanded;final ListView listView=new ListView();
 boolean allowPullingDown=true,targetExpanded;float currentExpandAnimatorValue=1;final LayoutManager layoutManager=new LayoutManager();
 final ExpandAnimator expandAnimator=new ExpandAnimator();final View topView=new View(),avatarContainer=new View(),avatarsViewPager=new View();final Image avatarImage=new Image();
 void setForegroundImage(boolean value){}int getThemedColor(int key){return 12;}
 int getHeaderExtraHeight(){return 250;}void needLayout(boolean instant){isPulledDown=targetExpanded;}
 void setAvatarExpandProgress(float progress){currentExpandAnimatorValue=isPulledDown?1:0;}
 INSTANT
 AnimatorListenerAdapter opening(){return OPEN;}
 AnimatorListenerAdapter closing(){return CLOSE;}
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
  for(boolean opening:new boolean[]{false,true}){
   for(boolean target:new boolean[]{false,true}){
    NativeAnimationCheck root=new NativeAnimationCheck();root.isPulledDown=target;
    AnimatorListenerAdapter listener=opening?root.opening():root.closing();listener.onAnimationStart(new Animator());
    listener.onAnimationCancel(new Animator());listener.onAnimationEnd(new Animator());listener.onAnimationEnd(new Animator());
    check(root.avatarContainer.visibility==7&&root.avatarsViewPager.visibility==7&&root.avatarsViewPager.alpha==.37f&&root.topView.background==19,"cancelled callback changes native photo visibility");
    check(root.expandAnimator.removals==2,"completed listener is retained");cases++;
   }
   NativeAnimationCheck stale=new NativeAnimationCheck();stale.isPulledDown=!opening;
   (opening?stale.opening():stale.closing()).onAnimationEnd(new Animator());
   check(stale.avatarContainer.visibility==7&&stale.avatarsViewPager.visibility==7,"stale opposite-state completion changes visibility");cases++;
   NativeAnimationCheck complete=new NativeAnimationCheck();complete.isPulledDown=opening;
   (opening?complete.opening():complete.closing()).onAnimationEnd(new Animator());
   if(opening)check(complete.avatarContainer.visibility==View.GONE&&complete.avatarsViewPager.visibility==View.VISIBLE&&complete.avatarsViewPager.alpha==1f&&complete.topView.background==Color.BLACK,"normal opening fails to show gallery");
   else check(complete.avatarsViewPager.visibility==View.GONE&&complete.avatarsViewPager.alpha==1f,"normal closing fails to hide gallery");cases++;
  }
  for(boolean target:new boolean[]{false,true})for(boolean running:new boolean[]{false,true}){
   NativeAnimationCheck root=new NativeAnimationCheck();root.targetExpanded=target;
   root.avatarsViewPager.visibility=View.VISIBLE;root.avatarsViewPager.alpha=0f;
   root.expandAnimator.running=running;root.expandAnimator.listener=target?root.opening():root.closing();
   root.collapseAvatarInstant();
   check(root.avatarsViewPager.visibility==(target?View.VISIBLE:View.GONE)&&root.avatarsViewPager.alpha==1f
     &&root.avatarContainer.visibility==(target?View.GONE:View.VISIBLE),"instant endpoint leaves a transparent/incorrect gallery after cancellation");cases++;
  }
  System.out.println(cases+" actual native animator cases: cancel/end, repeated callback, reversed state and normal completion passed");
 }
}
'''.replace('OPEN', expressions[0]).replace('CLOSE', expressions[1]).replace('INSTANT', instant)
    with tempfile.TemporaryDirectory(prefix='nebula-profile-animation-') as folder:
        work = Path(folder)
        source = work / 'NativeAnimationCheck.java'
        source.write_text(code, encoding='utf-8')
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(source)], check=True)
        subprocess.run(['java', '-cp', str(work), 'NativeAnimationCheck'], check=True)
