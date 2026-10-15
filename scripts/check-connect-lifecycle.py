"""Exercise production onboarding methods with delayed network/UI test doubles."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def run(name, source, extra=None):
    with tempfile.TemporaryDirectory(prefix='nebula-lifecycle-') as folder:
        root = Path(folder)
        files = {name + '.java': source, **(extra or {})}
        paths = []
        for relative, text in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding='utf-8')
            paths.append(str(path))
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(root), *paths], check=True)
        subprocess.run(['java', '-cp', str(root), name], check=True)


production = (UI / 'NebulaConnectFragment.java').read_text(encoding='utf-8')
methods = '\n'.join(method(production, signature) for signature in [
    'private void submit()', 'private void start()', 'private int begin(String message)',
    'private boolean acceptsRequest(int request)', 'private void end()',
    'private void openLogin()', 'public void onResume()', 'public void onPause()',
    'public void onFragmentDestroy()',
])
# Keep the real initial-list callback, too; only view construction is replaced.
initial = production[production.index('NebulaLink.call("servers.list", page(), result -> {'):
                     production.index('        return root;', production.index('NebulaLink.call("servers.list", page(), result -> {'))]
run('ConnectRequests', r'''
import java.util.*;
class Base {
 void onResume() {} void onPause() {} void onFragmentDestroy() {}
}
class ConnectRequests extends Base {
 int requestGeneration=1, presented, choices, failures; boolean busy,destroyed,loginOpened,allowPresentation=true;
 String selected="server-1",status;
 Widget input=new Widget(),connect=new Widget(),servers=new Widget(); Art art=new Art();
 Object page(){return null;}
 boolean presentFragment(Object ignored){presented++;return allowPresentation;}
 void choose(JSONObject data){choices++;servers.visibility=View.VISIBLE;}
 void fail(String text){failures++;status=text;}
 void show(String text){status=text;}
 void initialLoad(){ final int initialRequest=requestGeneration; INITIAL }
 METHODS
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static ConnectRequests fresh(){NebulaLink.queue.clear();return new ConnectRequests();}
 static void reply(boolean ok){NebulaLink.take().accept(new Result(ok));}
 public static void main(String[] args){
  ConnectRequests p=fresh();p.input.text="";p.submit();
  check(NebulaLink.queue.isEmpty()&&p.failures==1&&p.input.focus,"Empty import must stay editable");
  p=fresh();p.submit();p.submit();
  check(NebulaLink.queue.size()==1&&p.busy&&!p.input.enabled,"Double submit must create one request");
  reply(true);check(NebulaLink.queue.size()==1&&p.choices==0,"Import must list servers before showing them");
  reply(true);check(p.choices==1&&!p.busy&&p.input.enabled,"Successful import presents editable server selection");
  p.start();reply(true);check(p.presented==1&&p.loginOpened&&!p.busy,"Successful tunnel opens login once");
  p.openLogin();check(p.presented==1,"Repeated completion cannot open another login");

  p=fresh();p.submit();java.util.function.Consumer<Result> old=NebulaLink.take();p.openLogin();old.accept(new Result(true));
  check(NebulaLink.queue.isEmpty()&&p.presented==1,"Skip must invalidate import callback");
  p.onResume();old.accept(new Result(true));check(NebulaLink.queue.isEmpty(),"Return must not revive old requests");
  p.submit();reply(false);check(p.failures==1&&!p.busy&&p.input.enabled,"Failed retry must restore controls");
  p.submit();old.accept(new Result(false));check(p.busy&&p.failures==1,"Old failure cannot end the new attempt");
  reply(true);reply(true);check(p.choices==1,"Fresh retry still completes");

  p=fresh();p.submit();reply(true);old=NebulaLink.take();p.onFragmentDestroy();old.accept(new Result(true));
  check(p.choices==0&&p.presented==0&&p.art==null,"Destroyed view cannot render delayed server list");
  p=fresh();p.start();old=NebulaLink.take();p.openLogin();p.onResume();old.accept(new Result(true));
  check(p.presented==1,"Late tunnel completion cannot navigate after return");
  p=fresh();p.start();old=NebulaLink.take();p.onFragmentDestroy();old.accept(new Result(false));
  check(p.failures==0,"Destroyed view cannot receive tunnel errors");
  p=fresh();p.start();reply(false);check(p.failures==1&&p.presented==0&&!p.busy,"Connection failure stays on current screen");
  p.allowPresentation=false;p.submit();old=NebulaLink.take();p.openLogin();old.accept(new Result(true));
  check(!p.loginOpened&&NebulaLink.queue.isEmpty(),"Rejected navigation still invalidates its old request");

  p=fresh();p.initialLoad();p.input.focus=true;reply(true);check(p.choices==0,"Initial load cannot interrupt editing");
  p=fresh();p.initialLoad();p.input.text="draft";reply(true);check(p.choices==0,"Initial load cannot replace a draft");
  p=fresh();p.input.text="";p.initialLoad();reply(true);check(p.choices==1,"Initial saved servers remain discoverable");
  p=fresh();p.initialLoad();old=NebulaLink.take();p.submit();old.accept(new Result(true));
  check(p.choices==0&&p.busy,"Initial load cannot interrupt import");
  p.onPause();check(!p.art.visible,"Hidden onboarding stops art");p.onResume();check(p.art.visible,"Returning restores art visibility");
  System.out.println("Production import/list/connect callbacks: skip, return, retry, duplicate submission, destruction and editing passed");
 }
}
class View {static final int VISIBLE=0;}
class Widget {
 String text="vless://test";boolean enabled=true,focus;int visibility=8;
 int getVisibility(){return visibility;}String getText(){return text;}void setText(String t){text=t;}
 void setEnabled(boolean v){enabled=v;}void requestFocus(){focus=true;}boolean hasFocus(){return focus;}int length(){return text.length();}
}
class Art {boolean visible=true;void setVisible(boolean value,boolean restart){visible=value;}void detach(){visible=false;}}
class JSONObject {void put(String k,String v)throws JSONException{}int optInt(String k){return 1;}}
class JSONException extends Exception {}
class Result {boolean ok;String error="offline";JSONObject data=new JSONObject();Result(boolean ok){this.ok=ok;}}
class NebulaLink {
 static ArrayDeque<java.util.function.Consumer<Result>> queue=new ArrayDeque<>();
 static void call(String name,Object payload,java.util.function.Consumer<Result> callback){queue.add(callback);}
 static java.util.function.Consumer<Result> take(){return queue.remove();}
}
class LocaleController {static boolean isRTL;static String getString(int id){return "message-"+id;}}
class R {static class string {static int NebulaAuthLinkEmpty=1,NebulaConnecting=2,NebulaAuthPickEmpty=3,NebulaConnect=4;}}
class AndroidUtilities {static void shakeView(Object v){}static void hideKeyboard(Object v){}}
'''.replace('METHODS', methods).replace('INITIAL', initial), {
    'org/telegram/ui/LoginActivity.java': 'package org.telegram.ui; public class LoginActivity {}'
})

art = (UI / 'NebulaAuthArt.java').read_text(encoding='utf-8')
run('ArtPlayback', r'''
class ArtPlayback {
 boolean visible=true,callback,empty=true,motion=true;Object cycle;int starts,stops;float phase;
 boolean isVisible(){return visible;}Object getCallback(){return callback?this:null;}
 ArtPlayback getBounds(){return this;}boolean isEmpty(){return empty;}boolean motionEnabled(){return motion;}
 void detach(){if(cycle!=null){stops++;cycle=null;}}void start(){starts++;cycle=new Object();}void invalidateSelf(){}
 BODY
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 public static void main(String[] args){
  ArtPlayback p=new ArtPlayback();p.updatePlayback();check(p.starts==0,"Unattached drawable must stay idle");
  p.callback=true;p.updatePlayback();check(p.starts==0,"Empty bounds cannot start animator");
  p.empty=false;p.updatePlayback();Object animator=p.cycle;
  for(int i=0;i<1000;i++)p.updatePlayback();check(p.cycle==animator&&p.starts==1,"Relayout cannot restart animation");
  p.visible=false;p.updatePlayback();check(p.cycle==null&&p.stops==1,"Background drawable must stop");
  p.visible=true;p.updatePlayback();check(p.starts==2,"Returning resumes playback");
  p.motion=false;p.updatePlayback();check(p.cycle==null&&p.phase==.5f,"Reduced motion keeps a static visible pose");
  p.motion=true;p.updatePlayback();check(p.starts==3,"Motion setting restores playback");
  p.callback=false;p.updatePlayback();check(p.cycle==null&&p.stops==3,"Detached drawable cannot keep animator alive");
  System.out.println("Production artwork playback: attach, bounds, repeated layout, hide/resume, reduced motion and detach passed");
 }
}
'''.replace('BODY', method(art, 'private void updatePlayback()')))
