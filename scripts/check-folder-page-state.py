"""Regression: a same-folder scrub must never move the only visible page offscreen."""
from pathlib import Path
import subprocess, sys
r=Path(__file__).resolve().parent.parent
n=Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui'
def method(s, signature):
 a=s.index(signature);b=s.index('{',a)+1;depth=1
 while depth:depth+=(s[b]=='{')-(s[b]=='}');b+=1
 return s[a:b]
tabs=(n/'Components/FilterTabsView.java').read_text(encoding='utf-8')
dialogs=(n/'DialogsActivity.java').read_text(encoding='utf-8')
scroll=method(tabs,'public void scrollToTab(Tab tab, int position)')
finish=method(tabs,'private void finishNebulaTabTransition()')
progress=method(dialogs,'public void onPageScrolled(float progress)')
source='''import java.util.*;
class FolderPageCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class View {static final int VISIBLE=0,GONE=8;}
 static class Adapter {void resume(){}void pause(){}}
 static class ViewPage {int selectedType,visibility;float x;Adapter dialogsAdapter=new Adapter();
 void setTranslationX(float v){x=v;}void setVisibility(int v){visibility=v;}int getVisibility(){return visibility;}int getMeasuredWidth(){return 400;}}
 static class Tab {int id;boolean isLocked;Tab(int i){id=i;}}
 static class SystemClock {static long elapsedRealtime(){return 1;}}
 static class Host {
 ViewPage[] viewPages={new ViewPage(),new ViewPage()};boolean animatingForward;Tabs filterTabsView;
 void showScrollbars(boolean b){}void updateCounters(boolean b){}void checkListLoad(ViewPage p){}void updateNebulaFolderTitle(){}
 void onPageSelected(Tab tab,boolean forward){
 if(viewPages[0].selectedType==tab.id||tab.isLocked)return;
 viewPages[1].selectedType=tab.id;viewPages[1].setVisibility(View.VISIBLE);animatingForward=forward;
 }
 PROGRESS
 }
 static class Tabs {
 ArrayList<Tab> tabs=new ArrayList<>();Host delegate;int currentPosition,selectedTabId,scrollingToChild,previousPosition,previousId,posts;
 boolean animatingIndicator,nebulaReleaseFromDrag,enabled=true;float animationTime,animatingIndicatorProgress;long lastAnimationTime;
 Runnable animationRunnable=()->{};void removeCallbacks(Runnable r){}void postOnAnimation(Runnable r){posts++;}
 void setEnabled(boolean b){enabled=b;}void scrollToChild(int i){}
 void setAnimationIdicatorProgress(float p){animatingIndicatorProgress=p;delegate.onPageScrolled(p);}
 void stopAnimatingIndicator(){animatingIndicator=false;enabled=true;}
 FINISH
 SCROLL
 }
 static Tabs setup(){Tabs t=new Tabs();t.delegate=new Host();t.delegate.filterTabsView=t;t.delegate.viewPages[1].visibility=View.GONE;
 for(int i=0;i<3;i++)t.tabs.add(new Tab(i));return t;}
 static void stable(Tabs t,int selected){ViewPage p=t.delegate.viewPages[0];check(p.visibility==View.VISIBLE&&p.x==0&&p.selectedType==selected);check(t.delegate.viewPages[1].visibility==View.GONE);}
 public static void main(String[] args){
 Tabs t=setup();t.scrollToTab(t.tabs.get(0),0);check(t.posts==0);stable(t,0);
 // A late progress callback without a prepared destination must do nothing.
 t.delegate.onPageScrolled(.4f);t.delegate.onPageScrolled(1f);stable(t,0);
 for(int k=0;k<60;k++){
 int dest=(k%2)+1;t.scrollToTab(t.tabs.get(dest),dest);t.setAnimationIdicatorProgress(.35f);
 int posts=t.posts;t.scrollToTab(t.tabs.get(dest),dest);check(t.posts==posts); // Repeated target
 t.scrollToTab(t.tabs.get(0),0); // Interrupt halfway, complete prior destination first
 t.setAnimationIdicatorProgress(.2f);t.finishNebulaTabTransition();stable(t,0);
 t.scrollToTab(t.tabs.get(0),0);stable(t,0);
 }
 t.scrollToTab(t.tabs.get(1),1);t.setAnimationIdicatorProgress(.6f);t.finishNebulaTabTransition();stable(t,1); // Detach settlement
 t.tabs.get(2).isLocked=true;t.scrollToTab(t.tabs.get(2),2);stable(t,1);
 t.scrollToTab(null,0);t.scrollToTab(t.tabs.get(0),-1);stable(t,1);
 System.out.println("60 interrupted folder cycles, same-folder no-op, hidden-page fence, detach and locked target passed");
 }
}'''.replace('PROGRESS',progress).replace('FINISH',finish).replace('SCROLL',scroll)
w=r/'build/folder-page-state-check';w.mkdir(exist_ok=True);p=w/'FolderPageCheck.java';p.write_text(source,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(w),str(p)],check=True)
subprocess.run(['java','-cp',str(w),'FolderPageCheck'],check=True)
assert 'nebulaTracking = isEnabled() && !animatingIndicator' in tabs
assert 'nebulaHover != currentPosition' in tabs
assert 'finishNebulaTabTransition();' in method(tabs,'@Override protected void onDetachedFromWindow()')
print('No new scrub during settling; same destination and detach are wired')
