"""Execute arithmetic/account/snapshot/gesture preferences, then inspect their native wiring."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work=root/'build/extera-preferences-check';work.mkdir(parents=True,exist_ok=True)
stubs={
'android/content/SharedPreferences.java':'''package android.content; public interface SharedPreferences {int getInt(String k,int d);boolean getBoolean(String k,boolean d);String getString(String k,String d);Editor edit();void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l);interface OnSharedPreferenceChangeListener{void onSharedPreferenceChanged(SharedPreferences p,String k);}interface Editor{Editor putInt(String k,int v);Editor putBoolean(String k,boolean v);Editor putString(String k,String v);void apply();}}''',
'org/telegram/messenger/ApplicationLoader.java':'''package org.telegram.messenger;import java.util.*;import android.content.SharedPreferences;public class ApplicationLoader {public static C applicationContext=new C();public static class C {public P p=new P();public SharedPreferences getSharedPreferences(String n,int m){return p;}} public static class P implements SharedPreferences,SharedPreferences.Editor {Map<String,Object> values=new HashMap<>();java.util.List<OnSharedPreferenceChangeListener> listeners=new ArrayList<>();String key;public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}public String getString(String k,String d){return (String)values.getOrDefault(k,d);}public Editor edit(){return this;}public Editor putInt(String k,int v){values.put(k,v);key=k;return this;}public Editor putBoolean(String k,boolean v){values.put(k,v);key=k;return this;}public Editor putString(String k,String v){values.put(k,v);key=k;return this;}public void apply(){for(var listener:listeners)listener.onSharedPreferenceChanged(this,key);}public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}}}''',
'org/telegram/messenger/UserConfig.java':'''package org.telegram.messenger;public class UserConfig {public static long[] owners={100,200,0};public static UserConfig getInstance(int account){return new UserConfig(account);}int account;UserConfig(int a){account=a;}public long getClientUserId(){return owners[account];}}''',
'org/telegram/messenger/AndroidUtilities.java':'''package org.telegram.messenger;public class AndroidUtilities {public static int dp(float x){return (int)x;}}''',
'org/telegram/messenger/R.java':'''package org.telegram.messenger;public class R {public static class drawable {public static final int msg_copy=1,msg_translate=2,nebula_ai_spark=3,menu_reply=4;}}''',
'app/nebulagram/ui/NebulaText.java':'''package app.nebulagram.ui;public class NebulaText {public static String text(String r,String e){return e;}}''',
'Check.java':'''import app.nebulagram.ui.*;import org.telegram.messenger.*;public class Check {
static void check(boolean value,String message){if(!value)throw new AssertionError(message);}static void math(String s,String result){check(NebulaMath.result(s).equals(result),s+" => "+NebulaMath.result(s));}
public static void main(String[] args){
math("2+2","4");math("2 + 3 * 4","14");math("(2+3)*4","20");math("1 / 4","0.25");math("200 * 15%","30");math("1,5 × 2","3");math("-2 + 3","1");math("1-2=","-1");
for(String invalid:new String[]{"12345","+79999999999","2026-10-05","hello 2+3","1 / 0","2 +","(2+3","(()","eval(1)","http://example/2","2 ** 3","(".repeat(20)+"2+3"+")".repeat(20),"1".repeat(130)+"*2"})math(invalid,"");
check(NebulaChatPreferences.stickerTime()==0&&!NebulaChatPreferences.math()&&!NebulaChatPreferences.forwardCount()&&!NebulaChatPreferences.unmuted(),"opt-in defaults");
NebulaChatPreferences.set("inline_math",true);check(NebulaChatPreferences.math(),"snapshot refresh");NebulaChatPreferences.stickerTime(99);check(NebulaChatPreferences.stickerTime()==2,"enum bound");
check(NebulaChatPreferences.notifications(0)&&NebulaChatPreferences.notifications(1),"notification defaults");NebulaChatPreferences.notifications(0,false);check(!NebulaChatPreferences.notifications(0)&&NebulaChatPreferences.notifications(1),"account isolation");UserConfig.owners[0]=300;check(NebulaChatPreferences.notifications(0),"account slot reuse");UserConfig.owners[2]=100;check(!NebulaChatPreferences.notifications(2),"user identity retained across slot move");
check(java.util.Arrays.equals(NebulaSwipeActions.parse("3,1,0,1,-1,99"),new int[]{3,1,0}),"order dedup bounds");check(NebulaSwipeActions.parse("")[0]==0,"reply fallback");NebulaSwipeActions.set(new int[]{3,1,0});check(NebulaSwipeActions.action(0)==3&&NebulaSwipeActions.action(56)==1&&NebulaSwipeActions.action(112)==0&&NebulaSwipeActions.action(-900)==3&&NebulaSwipeActions.action(900)==0,"gesture vertical selection");int[] order=NebulaSwipeActions.order();order[0]=99;check(NebulaSwipeActions.action(0)==3,"immutable preference snapshot");
System.out.println("Chat preferences: arithmetic bounds, defaults, account identity and ordered vertical swipe passed");}}
'''}
for name,source in stubs.items():
 p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*map(str,work.rglob('*.java')),*[str(ui/(name+'.java')) for name in ['NebulaMath','NebulaChatPreferences','NebulaSwipeActions']]],check=True)
subprocess.run(['java','-cp',str(work),'Check'],check=True)
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/android-upstream-12106'
native=tree/'TMessagesProj/src/main/java/org/telegram'
cell=(native/'ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
assert 'NebulaChatPreferences.stickerTime() == 2' in cell and 'desiredX + timeWidth <= getMeasuredWidth() - dp(8)' in cell
assert 'NebulaChatPreferences.forwardCount() && messageObject.messageOwner.forwards > 0' in cell
enter=(native/'ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
assert 'nebulaMathHint.update(charSequence)' in enter and 'nebulaMathHint.draw(this, canvas)' in enter
chat=(native/'ui/ChatActivity.java').read_text(encoding='utf-8');assert 'NebulaSwipeActions.action(e.getY() - startedTrackingY)' in chat and 'tools.translateOnOpen()' in chat and '!message.messageOwner.noforwards' in chat
notifications=(native/'messenger/NotificationsController.java').read_text(encoding='utf-8');assert notifications.count('NebulaChatPreferences.notifications(currentAccount)')==2
storage=(native/'messenger/MessagesStorage.java').read_text(encoding='utf-8');assert storage.count('NebulaChatPreferences.unmuted()) flags |=')==2 and 'filter.pendingUnreadCount = -1;\n                calcUnreadCounters(true)' in storage
layout=(ui/'NebulaSettingsLayout.java').read_text(encoding='utf-8');assert 'initialTopPadding + top' in layout and 'bodyTop = overlayHeader ? 0 : top' in layout
physics=(ui/'NebulaNavigationAnimation.java').read_text(encoding='utf-8');assert 'setStiffness(900f).setDampingRatio(1f)' in physics and 'foreground.setScaleY(scale)' in physics
bar=(native/'ui/ActionBar/ActionBarLayout.java').read_text(encoding='utf-8');assert 'nebulaNavigation = null; animation.cancel()' in bar and '!preview && app.nebulagram.ui.NebulaTransitions.style()' in bar
privacy=(ui/'NebulaPrivacyFragment.java').read_text(encoding='utf-8');assert 'NebulaDeletedArchive.setEnabled(owner, true)' in privacy
print('Native gesture, timer/display, notification, counter, backdrop and cancellation wiring verified; on-device motion is not tested')
