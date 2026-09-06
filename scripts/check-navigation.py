"""Exercise actual navigation preferences and icon resolution with an in-memory Android model."""
from pathlib import Path
import re, subprocess
r=Path(__file__).resolve().parent.parent
work=r/'build/navigation-settings/check';work.mkdir(parents=True,exist_ok=True)
overlay=r/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
icons=(overlay/'NebulaIcons.java').read_text(encoding='utf-8')
names=sorted(set(re.findall(r'R.drawable.(\w+)',icons)) | {'msg_check_s'})
stubs={
'android/content/SharedPreferences.java': '''package android.content; public class SharedPreferences {
public final java.util.Map<String,Object> values=new java.util.HashMap<>();
public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);} public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);} public String getString(String k,String d){return (String)values.getOrDefault(k,d);} public Editor edit(){return new Editor();}
public class Editor {public Editor putInt(String k,int v){values.put(k,v);return this;} public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public Editor putString(String k,String v){values.put(k,v);return this;}public void apply(){}}}''',
 'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable; public class Drawable {public final int id;public Drawable(int id){this.id=id;}}',
'android/util/TypedValue.java': 'package android.util; public class TypedValue {public int resourceId;}',
'android/content/res/Resources.java': """package android.content.res; public class Resources {
public static class NotFoundException extends RuntimeException{} public static class Theme{}
public Resources(Object a,Object b,Object c){} public Object getAssets(){return this;}public Object getDisplayMetrics(){return this;}public Object getConfiguration(){return this;}
public void updateConfiguration(Object c,Object d){}
public android.graphics.drawable.Drawable getDrawable(int id){return new android.graphics.drawable.Drawable(id);}
public android.graphics.drawable.Drawable getDrawable(int id,Theme t){return getDrawable(id);}
public android.graphics.drawable.Drawable getDrawableForDensity(int id,int d){return getDrawable(id);}
public android.graphics.drawable.Drawable getDrawableForDensity(int id,int d,Theme t){return getDrawable(id);}
public void getValue(int id,android.util.TypedValue out,boolean resolve){out.resourceId=id;}
public void getValueForDensity(int id,int d,android.util.TypedValue out,boolean resolve){out.resourceId=id;}
}""",
'android/content/Context.java': '''package android.content; public class Context {public final SharedPreferences p=new SharedPreferences(); public SharedPreferences getSharedPreferences(String n,int m){return p;}}''',
'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger; public class AndroidUtilities {public static int dp(float v){return (int)Math.ceil(v);}}''',
'android/text/TextUtils.java': '''package android.text; public class TextUtils {public static String join(String separator,Object[] values){return String.join(separator,java.util.Arrays.copyOf(values,values.length,String[].class));}}''',
'android/util/SparseIntArray.java': '''package android.util; public class SparseIntArray {java.util.Map<Integer,Integer> map=new java.util.HashMap<>(); public void put(int k,int v){map.put(k,v);} public int get(int k,int d){return map.getOrDefault(k,d);}}''',
'android/view/View.java': '''package android.view; public class View {public static int VISIBLE=0,GONE=8; public Object getLayoutParams(){return null;}public void setLayoutParams(Object p){}public void setVisibility(int v){}}''',
'android/view/Gravity.java': '''package android.view; public class Gravity {public static int CENTER_HORIZONTAL=1,TOP=2,CENTER=3;}''',
'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout {public static class LayoutParams {public int gravity,topMargin;}}''',
'org/telegram/ui/MainTabsLayout.java': '''package org.telegram.ui; public class MainTabsLayout {public void bringChildToFront(Object v){} public void setViewVisible(Object v,boolean b,boolean a){} public void requestLayout(){}}''',
'org/telegram/ui/Components/glass/GlassTabView.java': '''package org.telegram.ui.Components.glass; public class GlassTabView {public void nebulaApplyLabel(){}}''',
'org/telegram/messenger/R.java': 'package org.telegram.messenger; public class R {public static class drawable {'+''.join(f'public static int {n}={i+1};' for i,n in enumerate(names))+'}}',
'CheckNavigation.java': '''import app.nebulagram.ui.*; import org.telegram.messenger.*;
public class CheckNavigation {
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
public static void main(String[] args){
android.content.SharedPreferences prefs=ApplicationLoader.applicationContext.p;
for(int mask=0;mask<16;mask++){
 prefs.values.clear();prefs.edit().putBoolean("side_panel",(mask&1)!=0).putBoolean("bottom_bar",(mask&2)!=0).putBoolean("bottom_bar_settings",(mask&4)!=0).putBoolean("bottom_bar_profile",(mask&8)!=0).apply();
 check(NebulaBottomBar.settingsInOverflow(false)||NebulaBottomBar.enabled()&&NebulaBottomBar.tabEnabled("settings"),"settings unreachable for legacy state "+mask);
}
prefs.values.clear();NebulaBottomBar.setTabEnabled("settings",false);check(NebulaBottomBar.settingsInOverflow(false),"hidden settings lost overflow entry");
NebulaBottomBar.setEnabled(false);check(NebulaBottomBar.settingsInOverflow(false),"no bar and no settings entry");
for(int mask=0;mask<8;mask++){
 prefs.values.clear();String[] tabs={"contacts","settings","profile"};for(int i=0;i<3;i++)NebulaBottomBar.setTabEnabled(tabs[i],(mask&(1<<i))!=0);
 check(NebulaBottomBar.settingsInOverflow(false)==!NebulaBottomBar.tabEnabled("settings"),"settings fallback mismatch");
 check(NebulaBottomBar.settingsInOverflow(true),"calls displaced settings without fallback");
 NebulaBottomBar.setEnabled(false);check(NebulaBottomBar.settingsInOverflow(false),"hidden bar lost settings");NebulaBottomBar.setEnabled(true);
 int pos=0,steps=0;while((pos=NebulaBottomBar.nextEnabledPosition(pos,true))<4){check(NebulaBottomBar.positionEnabled(pos),"swipe visited hidden page");check(++steps<=3,"swipe loop");}
 pos=4;while((pos=NebulaBottomBar.nextEnabledPosition(pos,false))>=0)check(NebulaBottomBar.positionEnabled(pos),"reverse swipe visited hidden page");
}
NebulaBottomBar.setCompact(true);check(NebulaBottomBar.minimumTabsWidth(2)==144,"two-tab bar did not shrink");
for(int count=1;count<=4;count++)check(NebulaBottomBar.minimumTabsWidth(count)>=72*count,"compact hit target too small");
NebulaBottomBar.setCompact(false);check(NebulaBottomBar.minimumTabsWidth(2)==320,"wide panel not restored");
for(String name:new String[]{NAMES})try{
 int id=R.drawable.class.getField(name).getInt(null);NebulaIcons.setEnabled(true);
 int mapped=NebulaIcons.resource(id);check(mapped!=id,"missing iOS mapping "+name);
 check(NebulaIcons.resource(mapped)==mapped,"recursive icon remapping");
 NebulaIcons.setEnabled(false);check(NebulaIcons.resource(id)==id,"native icon not restored "+name);
}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
NebulaIcons.setEnabled(true);check(NebulaIcons.resource(R.drawable.msg_check_s)==R.drawable.msg_check_s,"delivery checks replaced");
check(NebulaIcons.resource(-123)==-123,"unknown resource changed");
android.content.res.Resources base=new android.content.res.Resources(null,null,null);
android.content.res.Resources wrapped=new NebulaIconResources(new NebulaIconResources(base));
for(int active=0;active<3;active++)for(int preview=0;preview<3;preview++) {
 NebulaIcons.setPack(active);
 for(int id:new int[]{R.drawable.msg_saved,R.drawable.msg_search,R.drawable.msg_calls,R.drawable.msg_sendfile}) {
  int expected=NebulaIcons.previewResource(id,preview);
  check(NebulaIconResources.originalDrawable(wrapped,expected).id==expected,"pack preview contaminated by active pack");
  check(wrapped.getDrawable(id).id==NebulaIcons.resource(id),"normal icon substitution bypassed");
 }
}
System.out.println("Icon previews passed: all 9 active/preview pack combinations through nested resource wrappers");
System.out.println("Navigation passed: 16 legacy states, all hidden-tab combinations, compact width, and every icon mapping restored on disable");}}
'''.replace('NAMES',','.join('"'+n+'"' for n in re.findall(r'ICONS.put\(R.drawable.(\w+)',icons)))
}
sources=[]
for n,text in stubs.items():
 p=work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(str(p))
sources += [str(overlay/n) for n in ['NebulaBottomBar.java','NebulaIcons.java','NebulaIconResources.java']]
subprocess.run(['javac','-encoding','UTF-8','-d',str(work/'classes'),*sources],check=True)
subprocess.run(['java','-cp',str(work/'classes'),'CheckNavigation'],check=True)
