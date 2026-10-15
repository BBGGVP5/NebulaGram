from pathlib import Path
import re,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
s=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaConnectFragment.java').read_text(encoding='utf-8')
body=re.search(r'private boolean acceptsRequest\(int request\) \{([^}]+)\}',s).group(1)
source='''class ConnectRequests {
 int requestGeneration;boolean destroyed,loginOpened;
 boolean acceptsRequest(int request){BODY}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){ConnectRequests p=new ConnectRequests();int count=0;
 for(int generation=1;generation<=100;generation++)for(int request=0;request<=101;request++)for(int state=0;state<4;state++){
 p.requestGeneration=generation;p.destroyed=(state&1)!=0;p.loginOpened=(state&2)!=0;
 check(p.acceptsRequest(request)==(request==generation&&state==0));count++;}
 p.requestGeneration=1;p.destroyed=false;p.loginOpened=false;int initial=1;
 int importing=++p.requestGeneration;check(!p.acceptsRequest(initial));check(p.acceptsRequest(importing));
 p.loginOpened=true;++p.requestGeneration;check(!p.acceptsRequest(importing));
 p.loginOpened=false;check(!p.acceptsRequest(importing)); // Returning from login must not revive the request.
 int retry=++p.requestGeneration;check(p.acceptsRequest(retry));p.destroyed=true;++p.requestGeneration;check(!p.acceptsRequest(retry));
 System.out.println(count+" request ownership states; skip, return, retry and destruction passed");
 }
}'''.replace('BODY',body)
with tempfile.TemporaryDirectory(prefix='nebula-connect-') as d:
 p=Path(d)/'ConnectRequests.java';p.write_text(source,encoding='utf-8')
 subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'ConnectRequests'],check=True)
assert s.count('if (!acceptsRequest(request)) return;')==3
assert 'acceptsRequest(initialRequest) && !busy && !input.hasFocus() && input.length() == 0' in s
assert 'if (destroyed || loginOpened) return;' in s
print('Actual production callback guards cover import, server listing and tunnel completion')

# Execute the production artwork lifecycle body with a controllable animator.
art=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAuthArt.java').read_text(encoding='utf-8')
start=art.index('    private void updatePlayback() {')
body=art[start:art.index('    private void start()', start)]
java = """class ArtPlayback {
 boolean visible,callback,empty,motion;Object cycle;int starts,stops,invalidations;float phase;
 boolean isVisible(){return visible;}Object getCallback(){return callback?this:null;}
 ArtPlayback getBounds(){return this;}boolean isEmpty(){return empty;}boolean motionEnabled(){return motion;}
 void detach(){if(cycle!=null){stops++;cycle=null;}}void start(){starts++;cycle=new Object();}void invalidateSelf(){invalidations++;}
 BODY
 public static void main(String[] args){int cases=0;for(int state=0;state<32;state++){
 ArtPlayback p=new ArtPlayback();p.visible=(state&1)!=0;p.callback=(state&2)!=0;p.empty=(state&4)!=0;p.motion=(state&8)!=0;p.cycle=(state&16)!=0?new Object():null;
 boolean run=p.visible&&p.callback&&!p.empty&&p.motion;boolean running=p.cycle!=null;
 p.updatePlayback();if((p.cycle!=null)!=run||p.starts!=(run&&!running?1:0)||p.stops!=(!run&&running?1:0))throw new AssertionError(state);
 for(int i=0;i<1000;i++)p.updatePlayback();if(p.starts>(run?1:0))throw new AssertionError("animator restarted");cases++;
 }System.out.println(cases+" artwork visibility/motion states and stable animator reuse passed");}
}
""".replace('BODY',body)
with tempfile.TemporaryDirectory(prefix='nebula-auth-art-') as d:
 p=Path(d)/'ArtPlayback.java';p.write_text(java,encoding='utf-8');subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'ArtPlayback'],check=True)
