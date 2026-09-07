"""Exercise the real drag handler with the same click exclusions as MainTabsActivity."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
native = tree / 'TMessagesProj/src/main/java/org/telegram/ui/MainTabsLayout.java'
text = native.read_text(encoding='utf-8')
start = text.index('    private void checkLongMove(')
brace = text.index('{', start)
depth, end = 1, brace + 1
while depth:
    depth += (text[end] == '{') - (text[end] == '}')
    end += 1
handler = text[start:end]
source = r'''
import java.util.*;
public class CheckTabRelease {
 static class View {
  int index, clicks; CheckTabRelease owner;
  View(int i, CheckTabRelease o){index=i;owner=o;}
  float getX(){return index*80;} int getWidth(){return 80;}
  boolean performClick(){clicks++;owner.page=index;return true;}
 }
 static class Spring {void animateToFinalPosition(float n){} void cancel(){}}
 View[] tabs=new View[4]; Set<View> tabsWithIgnoreClick=new HashSet<>();
 View selected,lastLongSelectedView; int page;
 boolean nebulaDragging=true;
 float animatedLongSelectedViewCenterX,animatedLongSelectedViewOffsetX,lastLongSelectedViewWidth,lastLongSelectedViewCenterX;
 Spring selectedTabPositionOffsetX=new Spring(),selectedTabPositionX=new Spring();
 CheckTabRelease(){for(int i=0;i<4;i++){tabs[i]=new View(i,this);tabsWithIgnoreClick.add(tabs[i]);}selected=tabs[0];}
 View findSelectedTab(){return selected;} void setTabSelected(View v,boolean a){selected=v;}
 void invalidate(){} float clampXToChildrenCenters(float x,CheckTabRelease p){return Math.max(40,Math.min(280,x));}
 View findNearestVisibleChildByX(float x,CheckTabRelease p){return tabs[(int)x/80];}
 HANDLER
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int origin=0;origin<4;origin++)for(int target=0;target<4;target++)if(origin!=target){
   CheckTabRelease c=new CheckTabRelease();c.page=origin;c.selected=c.tabs[origin];
   c.checkLongMove(target*80+40,30,true,false);
   check(c.page==target,"drag changed highlight without navigating "+origin+" -> "+target);
   c.checkLongMove(target*80+42,30,false,false);
   c.checkLongMove(target*80+42,30,false,true);
   check(c.page==target&&c.selected==c.tabs[target],"release lost target");
   check(c.tabs[target].clicks==1,"release repeated click (scroll-to-top)");
   c.checkLongMove(origin*80+40,30,false,false);
   c.checkLongMove(origin*80+40,30,false,true);
   check(c.page==origin,"return to origin did not navigate");
  }
  System.out.println("12 drag/release/return sequences passed with all tabs excluded from long-press clicks");
 }
}
'''.replace('HANDLER', handler)
work = root / 'build/tab-release-check'
work.mkdir(parents=True, exist_ok=True)
target = work / 'CheckTabRelease.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target)], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckTabRelease'], check=True)
