"""Execute native history methods and pager geometry; check routing hooks, not pixels."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
ui = tree / 'TMessagesProj/src/main/java/org/telegram/ui'
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

profile = (ui / 'ProfileActivity.java').read_text(encoding='utf-8')
load = method(profile, 'private void updateSearchArray()')
add = method(profile, 'public void addRecent(Object object)')
clear = method(profile, 'public void clearRecent()')
assert load.index('recentSearches.clear()') < load.index('settingsSearchRecent2')
assert 'NebulaAppearance.settingsSearchHistory()' in load
assert 'NebulaAppearance.settingsSearchHistory()' in add
helper = (overlay / 'NebulaSettingsHistory.java').read_text(encoding='utf-8')
assert '.remove("settingsSearchRecent2").apply()' in helper
assert 'MAX_ACCOUNT_COUNT' in helper and 'NotificationCenter.updateSearchSettings' in helper
appearance = (overlay / 'NebulaAppearance.java').read_text(encoding='utf-8')
assert 'NebulaSettingsHistory.refresh();' in appearance
assert 'NotificationCenter.storiesUpdated' in appearance
stories = method((ui / 'DialogsActivity.java').read_text(encoding='utf-8'), 'public void updateStoriesVisibility(boolean animated)')
start = stories.index('final boolean newVisibility = app.nebulagram.ui.NebulaAppearance.showStories()')
end = stories.index('hasOnlySlefStories =', start)
story_gate = stories[start:end].replace('app.nebulagram.ui.NebulaAppearance.showStories()', 'show')
assert 'show && rawVisibility' in story_gate and 'onlySelfStories = false' in story_gate
activity = (ui / 'MainTabsActivity.java').read_text(encoding='utf-8')
assert 'tabsView.setNebulaPagerSelection(from, to, viewPager.getNextPositionAlpha())' in activity
assert 'tabsView.clearNebulaPagerSelection()' in method(activity, 'protected void onViewPagerScrollEnd()')
layout = (ui / 'MainTabsLayout.java').read_text(encoding='utf-8')
segment = layout[layout.index('if (nebulaPagerFrom != null && nebulaPagerTo != null'):layout.index('} else if (selected != null)', layout.index('if (nebulaPagerFrom != null && nebulaPagerTo != null'))]
assert 'nebulaLens.drawDrag(' in segment and 'nebulaLens.draw(' not in segment
assert segment.count('NebulaPagerMotion.edge(') == 2
assert 'nebulaSnapSelection = true' in method(layout, 'public void clearNebulaPagerSelection()')

methods = '\n'.join([load, add, clear]).replace('app.nebulagram.ui.NebulaAppearance.settingsSearchHistory()', 'enabled')
methods = methods.replace('MessagesController.getGlobalMainSettings()', 'prefs')
with tempfile.TemporaryDirectory(prefix='nebula-search-pager-') as work:
    path = Path(work)
    java = path / 'SearchPagerCheck.java'
    java.write_text("""import java.util.*;
import app.nebulagram.ui.NebulaPagerMotion;
class SearchPagerCheck {
 boolean enabled=true,searchWas=false; int notified;
 ArrayList<Object> recentSearches=new ArrayList<>();
 SearchResult[] searchArray={new SearchResult(1),new SearchResult(2)};
 static class SearchResult {int guid,num; SearchResult(int id){guid=id;}public String toString(){return "item"+guid;}}
 static class MessagesController {static class FaqSearchResult {int num;FaqSearchResult(String t,String[] p,String u){}}}
 static class Utilities {static byte[] hexToBytes(String s){return new byte[]{Byte.parseByte(s)};}}
 static class SerializedData {int next;byte[] bytes;SerializedData(byte[] b){bytes=b;}int readInt32(boolean e){return next++==0?0:1;}String readString(boolean e){return "faq";}}
 static class Prefs {Set<String> saved=new HashSet<>(Arrays.asList("1"));int reads,writes;String unrelated="keep";
 Set<String> getStringSet(String key,Object fallback){reads++;return saved;}
 Prefs edit(){return this;}Prefs putStringSet(String k,Set<String> v){saved=v;writes++;return this;}
 Prefs remove(String k){saved=null;writes++;return this;}boolean commit(){return true;}}
 Prefs prefs=new Prefs();
 void notifyDataSetChanged(){notified++;}int getNum(Object o){return o instanceof SearchResult?((SearchResult)o).num:0;}
 static void check(boolean ok){if(!ok)throw new AssertionError();}
""" + methods + """
 static boolean[] stories(boolean show,boolean rawVisibility,boolean onlySelfStories){
""" + story_gate + """
 return new boolean[]{newVisibility,onlySelfStories};}
 public static void main(String[] args){SearchPagerCheck x=new SearchPagerCheck();
 x.updateSearchArray();check(x.recentSearches.size()==1);
 x.updateSearchArray();check(x.recentSearches.size()==1); // no duplicates on refresh
 x.enabled=false;int reads=x.prefs.reads;x.updateSearchArray();check(x.recentSearches.isEmpty()&&x.prefs.reads==reads);
 x.addRecent(new SearchResult(2));check(x.prefs.writes==0&&x.recentSearches.isEmpty());
 x.enabled=true;x.updateSearchArray();check(x.recentSearches.size()==1); // hiding did not erase
 x.addRecent(x.searchArray[1]);check(x.recentSearches.get(0)==x.searchArray[1]&&x.prefs.writes==1);
 x.clearRecent();check(x.recentSearches.isEmpty()&&x.prefs.saved==null&&x.prefs.unrelated.equals("keep"));
 x.updateSearchArray();check(x.recentSearches.isEmpty());
 for(boolean visible:new boolean[]{false,true})for(boolean self:new boolean[]{false,true}){
  boolean[] off=stories(false,visible,self);check(!off[0]&&!off[1]);
  boolean[] on=stories(true,visible,self);check(on[0]==visible&&on[1]==self);
 }
 for(int i=0;i<=100;i++){float p=i/100f;
  check(Math.abs(NebulaPagerMotion.edge(16,260,p)-(16+244*p))<0.001f);
  check(Math.abs(NebulaPagerMotion.edge(260,16,p)-(260-244*p))<0.001f);
  check(Math.abs(NebulaPagerMotion.edge(16,260,1-p)-(260-244*p))<0.001f);
 }
 check(NebulaPagerMotion.edge(10,40,-1)==10&&NebulaPagerMotion.edge(10,40,2)==40);
 check(NebulaPagerMotion.edge(10,40,Float.NaN)==10);
 System.out.println("PASS: history load/hide/no-write/restore/clear, 8 story states, 303 pager positions and native hooks");
 }
}
""", encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(path),str(overlay/'NebulaPagerMotion.java'),str(java)],check=True)
    subprocess.run(['java','-cp',str(path),'SearchPagerCheck'],check=True)
