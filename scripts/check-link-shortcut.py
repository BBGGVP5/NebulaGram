"""Exercise the home shortcut state policy and real asynchronous toggle methods."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
work = root / 'build/link-shortcut-check'
work.mkdir(parents=True, exist_ok=True)
code = (ui / 'NebulaLinkShortcut.java').read_text(encoding='utf-8')


def method(signature):
    start = code.index(signature)
    end = code.index('{', start) + 1
    depth = 1
    while depth:
        depth += (code[end] == '{') - (code[end] == '}')
        end += 1
    return code[start:end]


source = r'''
import app.nebulagram.ui.NebulaLinkShortcutState;
public class CheckLinkShortcut {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Data {String id; Data(String id){this.id=id;} String optString(String k){return id;}}
 static class Result {boolean ok;String error;Data data;Result(boolean ok,String id){this.ok=ok;data=new Data(id);error="error";}}
 interface Callback {void done(Result r);}
 static class NebulaLink {
  static String method;static int calls;static Callback callback;
  static void call(String m,Object payload,Callback cb){method=m;callback=cb;calls++;}
  static void reply(boolean ok,String id){callback.done(new Result(ok,id));}
 }
 static class NebulaHaptics {static void tick(Object v){}}
 static class NebulaServersFragment {}
 static class Owner {int selections;void presentFragment(NebulaServersFragment f){selections++;}}
 static class Control {
  boolean pending,failed,attached=true;Owner owner=new Owner();Object item=new Object();String phase="disconnected";int reports;
  boolean isAttachedToWindow(){return attached;}String phase(){return phase;}void refresh(){}void report(String s){reports++;}
  METHODS
 }
 public static void main(String[] args){
  check(NebulaLinkShortcutState.resolve("disconnected",false,false,false)==0,"off");
  check(NebulaLinkShortcutState.resolve("connecting",false,false,false)==1,"connecting");
  check(NebulaLinkShortcutState.resolve("connected",true,false,false)==2,"connected and routed");
  check(NebulaLinkShortcutState.resolve("connected",false,false,false)==0,"no routing, no false connected");
  check(NebulaLinkShortcutState.resolve("failed",false,false,false)==3,"core error");
  check(NebulaLinkShortcutState.resolve("disconnected",false,false,true)==3,"request error");
  check(NebulaLinkShortcutState.resolve("connected",true,true,false)==1,"pending stop");
  Control c=new Control();c.toggle();check(NebulaLink.method.equals("settings.get"),"load selected server");
  int count=NebulaLink.calls;c.toggle();check(NebulaLink.calls==count,"serialize repeated tap");
  NebulaLink.reply(true,"");check(c.owner.selections==1 && !c.pending,"missing server opens selection");
  c.toggle();NebulaLink.reply(true,"server-a");check(NebulaLink.method.equals("tunnel.start"),"start selected tunnel");
  NebulaLink.reply(false,"");check(c.failed && !c.pending && c.reports==1,"show start error and unlock");
  c.phase="connected";c.toggle();check(NebulaLink.method.equals("tunnel.stop"),"stop connected tunnel");
  NebulaLink.reply(true,"");check(!c.failed && !c.pending,"successful stop");
  c.phase="connecting";c.toggle();check(NebulaLink.method.equals("tunnel.stop"),"cancel connecting tunnel");
  NebulaLink.reply(true,"");c.phase="disconnected";c.toggle();c.attached=false;
  NebulaLink.reply(true,"server-a");check(!c.pending && NebulaLink.method.equals("settings.get"),"do not start after detach");
  System.out.println("Shortcut states, selection, serialized start/stop, failure and detach passed");
 }
}
'''.replace('METHODS', '\n'.join(method(sig) for sig in ['private void toggle(', 'private void runTunnel(', 'private void complete(']))
target = work / 'CheckLinkShortcut.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(target), str(ui / 'NebulaLinkShortcutState.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'CheckLinkShortcut'], check=True)
assert 'preview >= 0 ? preview :' in method('private int state(')
assert 'NebulaLink.call' not in method('@Override protected void onDraw(')
assert 'if (preview >= 0) return;' in method('@Override protected void onAttachedToWindow(')
assert 'removeStatusListener' in method('@Override protected void onDetachedFromWindow(')
assert 'unregisterOnSharedPreferenceChangeListener' in method('@Override protected void onDetachedFromWindow(')
assert 'removeObserver' in method('@Override protected void onDetachedFromWindow(')
assert 'payload.put("ids", new JSONArray().put(id))' in method('private void probe(')
assert 'result.data.optInt(id, -1)' in method('private void probe(')
assert 'getBoolean(KEY, true)' in code and 'state < 4' in code
assert 'if (!onlySelect && folderId == 0) app.nebulagram.ui.NebulaLinkShortcut.install(this, menu);' in (native / 'DialogsActivity.java').read_text()
assert 'NebulaLinkShortcut.addSettings(content)' not in (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'if (SCREEN_ADVANCED.equals(screenId)) NebulaLinkShortcut.addSettings(content);' in (ui / 'NebulaMenuFragment.java').read_text(encoding='utf-8')
assert 'Кнопка NebulaLink' in code and 'NebulaLink на главной' not in code
assert 'new NebulaMenuFragment(NebulaMenuFragment.SCREEN_ADVANCED)' in (ui / 'NebulaSettingsSearch.java').read_text(encoding='utf-8')
assert 'R.drawable.nebula_link_shield' in code and 'R.drawable.msg_link' not in code
assert 'state % 2 == 0' in code and 'text.setTextSize(13)' in code
print('Shortcut home guard, four inert previews, listeners, visibility switch and selected-only ping wiring passed')
