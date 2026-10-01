"""Execute patched recorder routing with deferred camera-open and failure callbacks."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/android-validation'
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java').read_text(encoding='utf-8')
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

def method(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

bridge = '\n'.join(method(signature) for signature in [
    'private app.nebulagram.ui.NebulaZoomCapabilities nebulaAvailableCatalog()',
    'private float nebulaCurrentZoom()', 'private void updateNebulaZoomSlider()',
    'private void applyNebulaZoom(float factor)',
    'private void openNebulaModule(String id, String previousId, boolean restoring)',
    'private void completeNebulaModuleSwitch(String requestedId, String previousId, boolean restoring, boolean front)'])
java = r'''
import app.nebulagram.ui.NebulaZoomCapabilities;
import app.nebulagram.ui.NebulaZoomCapabilities.Module;
import java.util.*;
class RecorderZoomCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void close(float a,float b){check(Math.abs(a-b)<.002f,a+" != "+b);}
 static class View {static final int GONE=8,VISIBLE=0;}
 static class Size {Size(int w,int h){}}
 static class MessagesController {int roundVideoSize=384;static MessagesController getInstance(int a){return new MessagesController();}}
 static class AndroidUtilities {
  static List<Runnable> delayed=new ArrayList<>();
  static void runOnUIThread(Runnable r,int ms){delayed.add(r);}
  static void timeOut(){List<Runnable> tasks=new ArrayList<>(delayed);delayed.clear();for(Runnable r:tasks)r.run();}
 }
 static class Camera2Session {
  static int nullCreates;
  static List<Runnable> closes=new ArrayList<>();
  static void finishClose(){List<Runnable> tasks=new ArrayList<>(closes);closes.clear();for(Runnable r:tasks)r.run();}
  final String cameraId;float zoom=1;boolean ready=true,closed;Runnable done;
  Camera2Session(String id){cameraId=id;}
  static Camera2Session create(boolean front,int w,int h,String id){
   if(nullCreates>0){nullCreates--;return null;}Camera2Session c=new Camera2Session(id);c.ready=false;return c;
  }
  boolean isInitiated(){return ready&&!closed;}
  void complete(){ready=true;if(done!=null)done.run();}
  void whenDone(Runnable r){done=r;if(isInitiated())r.run();}
  void destroy(boolean async, Runnable after){closed=true;closes.add(after);}
  void setRecordingVideo(boolean recording){}
  void setZoom(float v){check(isInitiated(),"zoom only after session configuration");zoom=v;}
  float getZoom(){return zoom;}float getMinZoom(){return 1;}float getMaxZoom(){return 10;}
  int getPreviewWidth(){return 384;}int getPreviewHeight(){return 384;}
 }
 static class CameraSession {float factor=1;float getZoomFactor(){return factor;}float getMaxZoomFactor(){return 10;}void setZoomFactor(float f){factor=f;}}
 static class ThreadBridge {
  int previewTransitions;Camera2Session current;
  void setCurrentSession(Camera2Session c){current=c;}
  void reinitForNewCamera(){previewTransitions++;}
 }
 static class Image {void setImageBitmap(Object bitmap){}void setAlpha(float a){}}
 static class Slider {
  float min,max,current=1;float[] stops;int visibility;
  void setRange(float a,float b){min=a;max=b;}
  void setCameraStops(float[] values){stops=values;}
  void setCameraFacing(boolean front){}
  void setCurrent(float f){current=f;}void setVisibility(int v){visibility=v;}
 }
 static class Recorder {
  boolean recording=true,useCamera2=true,bothCameras,isFrontface,cameraReady=true,nebulaSwitchingModule;
  int currentAccount;float nebulaRequestedZoom=1;
  HashSet<String> nebulaUnavailableModules=new HashSet<>();
  NebulaZoomCapabilities catalog;
  Camera2Session camera2SessionCurrent=new Camera2Session("wide");Camera2Session[] camera2Sessions=new Camera2Session[2];
  CameraSession cameraSession=new CameraSession();ThreadBridge cameraThread=new ThreadBridge();
  Image textureOverlayView=new Image();Object lastBitmap=new Object();Size[] previewSize=new Size[2];Slider nebulaZoomSlider=new Slider();
  Recorder(){
   Module u=new Module("ultra",.5f,1,4,false),w=new Module("wide",1,1,10,false),t=new Module("tele",3,1,20,false);
   catalog=new NebulaZoomCapabilities(Arrays.asList(u,w,t),w,Collections.emptyList());camera2Sessions[1]=camera2SessionCurrent;
  }
  void saveLastCameraBitmap(){}
  NebulaZoomCapabilities nebulaCameraCatalog(boolean front){return catalog;}
  boolean isCameraSessionInitiated(){return useCamera2?camera2SessionCurrent!=null&&camera2SessionCurrent.isInitiated():true;}
  BRIDGE
 }
 public static void main(String[] args){
  Recorder r=new Recorder();Camera2Session original=r.camera2SessionCurrent;
  r.applyNebulaZoom(5);check(r.camera2SessionCurrent==original,"camera close does not block UI");Camera2Session.finishClose();Camera2Session tele=r.camera2SessionCurrent;
  check(tele.cameraId.equals("tele")&&original.closed&&r.nebulaSwitchingModule,"open tele on same-facing transition");
  r.applyNebulaZoom(12);check(r.camera2SessionCurrent==tele,"drag waits for pending session rather than reopening it");
  tele.complete();close(tele.zoom,4);close(r.nebulaZoomSlider.current,12);
  check(r.cameraThread.previewTransitions==1&&!r.nebulaSwitchingModule,"encoder thread retained, one preview transition");
  r.applyNebulaZoom(.5f);Camera2Session.finishClose();Camera2Session ultra=r.camera2SessionCurrent;
  r.applyNebulaZoom(2);ultra.complete();Camera2Session.finishClose();Camera2Session wide=r.camera2SessionCurrent;
  check(wide.cameraId.equals("wide"),"latest queued value routes to its final module");wide.complete();close(r.nebulaZoomSlider.current,2);
  r.applyNebulaZoom(5);Camera2Session.finishClose();r.recording=false;r.camera2SessionCurrent.complete();AndroidUtilities.timeOut();
  check(r.nebulaZoomSlider.current==2,"late completion cannot change a stopped recorder");
  r=new Recorder();r.applyNebulaZoom(5);Camera2Session.finishClose();Camera2Session failed=r.camera2SessionCurrent;AndroidUtilities.timeOut();Camera2Session.finishClose();
  check(failed.closed&&r.camera2SessionCurrent.cameraId.equals("wide"),"failed module restores prior camera");
  r.camera2SessionCurrent.complete();check(!r.nebulaSwitchingModule,"restoration completes");
  check(r.nebulaUnavailableModules.contains("tele"),"failed camera isn't repeatedly reopened");
  close(r.nebulaZoomSlider.max,10);check(r.nebulaZoomSlider.stops.length==2,"failed module removed from UI bounds and stops");
  r.applyNebulaZoom(5);check(r.camera2SessionCurrent.cameraId.equals("wide"),"safe digital zoom after failed tele");
  close(r.nebulaZoomSlider.current,5);
  AndroidUtilities.timeOut();r=new Recorder();Camera2Session.nullCreates=1;r.applyNebulaZoom(5);Camera2Session.finishClose();
  check(r.camera2SessionCurrent!=null&&r.camera2SessionCurrent.cameraId.equals("wide"),"synchronous factory failure restores camera");
  r.camera2SessionCurrent.complete();close(r.nebulaZoomSlider.max,10);
  r=new Recorder();r.applyNebulaZoom(5);r.applyNebulaZoom(.5f);Camera2Session.finishClose();
  check(r.camera2SessionCurrent.cameraId.equals("ultra"),"latest tap opens final lens directly after close");
  r.camera2SessionCurrent.complete();close(r.nebulaZoomSlider.current,.5f);
  r=new Recorder();Camera2Session beforeStop=r.camera2SessionCurrent;r.applyNebulaZoom(5);r.recording=false;Camera2Session.finishClose();
  check(r.camera2SessionCurrent==beforeStop,"stopped recorder never opens a queued camera");
  r=new Recorder();r.useCamera2=false;r.applyNebulaZoom(7);close(r.cameraSession.factor,7);close(r.nebulaZoomSlider.current,7);
  System.out.println("Recorder zoom: deferred lens open, queued drag, local factors, same encoder, failure recovery and stop guards passed");
 }
}
'''.replace('BRIDGE', bridge)
with tempfile.TemporaryDirectory(prefix='nebula-recorder-zoom-') as temp:
    java_file = Path(temp) / 'RecorderZoomCheck.java'
    java_file.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp,
                    str(overlay / 'NebulaZoomCapabilities.java'), str(java_file)], check=True)
    subprocess.run(['java', '-cp', temp, 'RecorderZoomCheck'], check=True)
