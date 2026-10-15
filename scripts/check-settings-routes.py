"""Compile and execute the shared search/deep-link destination dispatcher."""
from pathlib import Path
import os,re,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source=(ui/'NebulaSettingsSearch.java').read_text(encoding='utf-8');start=source.index('    public static boolean openSection(');brace=source.index('{',start);depth=1;end=brace+1
while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
method=source[start:end]
names=sorted(set(re.findall(r'new (Nebula\w+)\(',method)))
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-settings-routes-') as work:
 work=Path(work);base=work/'org/telegram/ui/ActionBar/BaseFragment.java';base.parent.mkdir(parents=True);base.write_text('''package org.telegram.ui.ActionBar; public class BaseFragment {public Object last;public int getCurrentAccount(){return 127;}public void presentFragment(Object value){last=value;}}''',encoding='utf-8')
 package=work/'app/nebulagram/ui';package.mkdir(parents=True)
 (package/'NebulaSettingsSearch.java').write_text('package app.nebulagram.ui;import org.telegram.ui.ActionBar.BaseFragment;public class NebulaSettingsSearch{'+method+'}',encoding='utf-8')
 definitions='''package app.nebulagram.ui;class Target {Object[]args;String focus;int row=-1;Target(Object...args){this.args=args;}Target focus(String s){focus=s;return this;}Target focusRowIndex(int r){row=r;return this;}}\n'''
 for name in names:definitions+='class '+name+' extends Target {static final int SECTION_GLASS=12,TOOLS=5;static final String SCREEN_ADVANCED="advanced";'+name+'(Object...args){super(args);}}\n'
 definitions+='class NebulaUpdateSettingsSheet {static void show(org.telegram.ui.ActionBar.BaseFragment host){host.last="updates";}}'
 (package/'Targets.java').write_text(definitions,encoding='utf-8')
 harness='''package app.nebulagram.ui;import org.telegram.ui.ActionBar.BaseFragment;public class RouteCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){BaseFragment host=new BaseFragment();int checked=0;
 for(int id=0;id<=12;id++){check(NebulaSettingsSearch.openSection(host,id,"focus",3));Target target=(Target)host.last;check(target instanceof NebulaSectionFragment&&target.row==3&&(int)target.args[0]==id);checked++;}
 int[]ids={-1,-10,-11,-12,-13,-14,-15,-16,-17,-18,-19,-20,-21,-22,-23,-24,-25,-26,-27,-28,-29,-30,-31};
 for(int id:ids){check(NebulaSettingsSearch.openSection(host,id,"focus",-1));check(host.last!=null);checked++;}
 check(NebulaSettingsSearch.openSection(host,-15,"privacy",-1));check(((Target)host.last).focus.equals("privacy"));
 check(NebulaSettingsSearch.openSection(host,-23,null,-1));check(((Target)host.last).args[0].equals(127));
 check(NebulaSettingsSearch.openSection(host,-17,null,-1));check(((Target)host.last).args[0].equals(127));
 check(NebulaSettingsSearch.openSection(host,-29,null,-1)&&host.last instanceof NebulaAccountsFragment);
 check(NebulaSettingsSearch.openSection(host,-30,null,-1)&&host.last instanceof NebulaAudioSettingsFragment);
 for(int id:new int[]{-2,-9,-32,13,999,Integer.MIN_VALUE}){Object last=host.last;check(!NebulaSettingsSearch.openSection(host,id,null,-1)&&host.last==last);}
 System.out.println(checked+" shared settings destinations, account ownership, legacy focus and unknown-route rejection passed");}}
'''
 (package/'RouteCheck.java').write_text(harness,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'--release','8','-encoding','UTF-8','-d',str(work),*map(str,work.rglob('*.java'))],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'app.nebulagram.ui.RouteCheck'],check=True)
