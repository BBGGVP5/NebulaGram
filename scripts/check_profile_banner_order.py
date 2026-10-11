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
