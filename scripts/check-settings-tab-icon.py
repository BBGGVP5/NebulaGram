"""Execute the real Settings icon switch, including live preference/account changes."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
ui = tree / 'TMessagesProj/src/main/java/org/telegram/ui'
source = (ui / 'Components/glass/GlassTabView.java').read_text(encoding='utf-8')

def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

factory = method(source, 'public static GlassTabView createMainTab(Context context, Theme.ResourcesProvider resourcesProvider, TabAnimation tabAnimation, int stringRes, boolean accountAvatar)')
assert 'return createAvatar(' not in factory
assert 'nebulaSettingsTab = accountAvatar && tabAnimation == TabAnimation.SETTINGS' in factory
assert 'tab.nebulaUpdateSettingsIcon(UserConfig.selectedAccount)' in factory
refresh = method(source, 'public void nebulaApplyLabel()')
assert 'nebulaUpdateSettingsIcon(nebulaSettingsAccount)' in refresh
assert 'backupImageView.getVisibility() == VISIBLE' in refresh
activity = (ui / 'MainTabsActivity.java').read_text(encoding='utf-8')
assert activity.count('tabs[INDEX_SETTINGS].nebulaUpdateSettingsIcon(currentAccount)') == 2
assert 'checkUi_callTabVisible(getUserConfig().showCallsTab, false)' in method(activity, 'public void onResume()')
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaBottomBar.java'
assert 'tab.nebulaApplyLabel()' in overlay.read_text(encoding='utf-8')
actual = method(source, 'public void nebulaUpdateSettingsIcon(int account)').replace('app.nebulagram.ui.NebulaBottomBar.', 'Bar.')
with tempfile.TemporaryDirectory(prefix='nebula-tab-icon-') as folder:
    work = Path(folder)
    java = r'''
class SettingsIconCheck {
 static final int VISIBLE=0, GONE=8;
 boolean nebulaSettingsTab=true; int nebulaSettingsAccount, children;
 final View imageView=new View(), textView=new View(); BackupImageView backupImageView;
 static class View { int visibility=VISIBLE; void setVisibility(int v){visibility=v;} }
 static class BackupImageView extends View { TLRPC.User user; BackupImageView(Object c){} void setRoundRadius(int r){} void clearImage(){user=null;} void setForUserOrChat(TLRPC.User u,AvatarDrawable a){user=u;} }
 static class AvatarDrawable { AvatarDrawable(TLRPC.User u){} }
 static class TLRPC { static class Photo {long photo_id=1;} static class User { Photo photo=new Photo(); } }
 static class UserConfig { static UserConfig[] accounts={new UserConfig(),new UserConfig()}; TLRPC.User user=new TLRPC.User(); static UserConfig getInstance(int a){return accounts[a];} TLRPC.User getCurrentUser(){return user;} }
 static class Gravity { static int CENTER_HORIZONTAL=1,TOP=2; }
 static class LayoutHelper { static Object createFrame(int a,int b,int c,int d,int e,int f,int g){return null;} }
 static class Bar {static boolean profile; static View lastIcon; static boolean settingsUsesAvatar(boolean photo){return photo&&!profile;} static void applyTabLabel(View icon,View label){lastIcon=icon;} }
 static int dp(int n){return n;} Object getContext(){return null;} void addView(View v,Object p){children++;} void checkVisualWidth(){}
 ACTUAL
 static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
 static void expect(SettingsIconCheck tab,boolean avatar) {
   check(tab.imageView.visibility==(avatar?GONE:VISIBLE),"gear visibility");
   check(tab.backupImageView==null?!avatar:tab.backupImageView.visibility==(avatar?VISIBLE:GONE),"avatar visibility");
   check(Bar.lastIcon==(avatar?tab.backupImageView:tab.imageView),"label alignment uses hidden icon");
 }
 public static void main(String[] args){
  SettingsIconCheck tab=new SettingsIconCheck();
  // Toggling Profile repeatedly must update the same view, not replace click/gesture handlers.
  for(int i=0;i<20;i++){
   Bar.profile=(i%2==0);tab.nebulaUpdateSettingsIcon(0);expect(tab,!Bar.profile);
  }
  check(tab.children==1,"duplicated avatar children");
  Bar.profile=false;tab.nebulaUpdateSettingsIcon(1);expect(tab,true);
  check(tab.backupImageView.user==UserConfig.accounts[1].user,"wrong account photo");
  UserConfig.accounts[1].user.photo=null;tab.nebulaUpdateSettingsIcon(1);expect(tab,false);
  check(tab.backupImageView.user==null,"stale account photo retained");
  UserConfig.accounts[1].user=null;tab.nebulaUpdateSettingsIcon(1);expect(tab,false);
  tab.nebulaUpdateSettingsIcon(0);expect(tab,true);
  SettingsIconCheck preview=new SettingsIconCheck();preview.nebulaSettingsTab=false;
  preview.nebulaUpdateSettingsIcon(0);check(preview.children==0,"preview/profile modified by Settings hook");
  System.out.println("Settings icon: gear/avatar live transitions, no photo, account switch, preview isolation passed");
 }
}
'''.replace('ACTUAL', actual)
    (work / 'SettingsIconCheck.java').write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(work / 'SettingsIconCheck.java')], check=True)
    subprocess.run(['java', '-cp', str(work), 'SettingsIconCheck'], check=True)
