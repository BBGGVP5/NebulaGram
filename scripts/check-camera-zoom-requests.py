"""Execute the patched Camera2 request queue with a deterministic camera handler."""
from pathlib import Path
import subprocess
import tempfile
import sys

tree = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('build/android-validation')
source = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/camera/Camera2Session.java').read_text(encoding='utf-8')

def block(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(block(s) for s in ['public void setZoom(float value)', 'private void applyZoomToRequest()', 'private void updateCaptureRequest()'])
queued = block('private final Runnable applyPendingZoom =') + ';'
java = r'''
import java.util.*;
class CameraZoomRequests {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Looper {static Object current;static Object myLooper(){return current;}}
 static class Handler {
  Object looper=new Object();List<Runnable> work=new ArrayList<>();
  Object getLooper(){return looper;}boolean post(Runnable r){work.add(r);return true;}
  boolean postDelayed(Runnable r,int ms){check(ms==16,"one frame coalescing");return post(r);}
  void drain(){Looper.current=looper;while(!work.isEmpty())work.remove(0).run();Looper.current=null;}
 }
 static class Build {static class VERSION {static int SDK_INT=36;}}
 static class Utilities {static float clamp(float v,float max,float min){return Math.max(min,Math.min(v,max));}}
 static class Rect {
  int left,top,right,bottom;Rect(){}Rect(int l,int t,int r,int b){set(l,t,r,b);}
  void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
  int width(){return right-left;}int height(){return bottom-top;}
  int centerX(){return (left+right)/2;}int centerY(){return (top+bottom)/2;}
 }
 static class Range<T> {T lower,upper;Range(T a,T b){lower=a;upper=b;}T getLower(){return lower;}T getUpper(){return upper;}}
 static class CameraCharacteristics {static String CONTROL_AE_COMPENSATION_RANGE="exposureRange";Range<Integer> get(String key){return new Range<>(-4,6);}}
 static class Options {void apply(CaptureRequest.Builder builder){builder.set("ois",1);}}
 static class CameraMetadata {static int CONTROL_SCENE_MODE_BARCODE=1,CONTROL_SCENE_MODE_NIGHT_PORTRAIT=2,CONTROL_SCENE_MODE_NIGHT=3;}
 static class CaptureRequest {
  static String CONTROL_AE_EXPOSURE_COMPENSATION="exposure",CONTROL_ZOOM_RATIO="ratio",SCALER_CROP_REGION="crop",CONTROL_SCENE_MODE="scene",FLASH_MODE="flash",CONTROL_AE_TARGET_FPS_RANGE="fps",CONTROL_CAPTURE_INTENT="intent";
  static int FLASH_MODE_TORCH=2,FLASH_MODE_SINGLE=1,FLASH_MODE_OFF=0,CONTROL_CAPTURE_INTENT_VIDEO_RECORD=3;
  static class Builder {
   Map<String,Object> values=new HashMap<>();void set(String k,Object v){values.put(k,v);}
   void addTarget(Object s){}Map<String,Object> build(){return new HashMap<>(values);}
  }
 }
 static class CameraDevice {
  static int TEMPLATE_RECORD=1,TEMPLATE_STILL_CAPTURE=2,TEMPLATE_PREVIEW=3;int creates;
  CaptureRequest.Builder createCaptureRequest(int template){creates++;return new CaptureRequest.Builder();}
 }
 static class Session {
  int submits;Map<String,Object> latest;
  void setRepeatingRequest(Map<String,Object> request,Object cb,Handler h){
   check(Looper.myLooper()==h.getLooper(),"camera IPC ran on UI thread");submits++;latest=request;
  }
 }
 static class FileLog {
  static void e(Exception e){throw new AssertionError(e);}
  static void e(String s,Exception e){throw new AssertionError(s,e);}
 }
 static class Camera {
  Options nebulaOptions=new Options();CameraCharacteristics cameraCharacteristics=new CameraCharacteristics();float nebulaExposure=.5f;
  volatile boolean isClosed;boolean initiated=true,zoomRatioSupported=true,recordingVideo=true,scanningBarcode,nightMode,isFront,flashing;
  volatile float currentZoom=1;float maxZoom=100,minZoom=.5f;
  Handler handler=new Handler();CameraDevice cameraDevice=new CameraDevice();Object surface=new Object();
  Session captureSession=new Session();CaptureRequest.Builder captureRequestBuilder;
  Rect sensorSize=new Rect(20,40,4020,3040),cropRegion=new Rect();Object zoomLock=new Object();boolean zoomQueued;
  boolean isInitiated(){return initiated;}
  QUEUED
  METHODS
 }
 public static void main(String[] args){
  Camera c=new Camera();c.updateCaptureRequest();check(c.cameraDevice.creates==0,"builder on caller");c.handler.drain();
  check(c.cameraDevice.creates==1&&c.captureSession.submits==1,"initial request");
  for(int i=1;i<=1000;i++)c.setZoom(.5f+i*.05f);
  check(c.handler.work.size()==1&&c.captureSession.submits==1,"rapid input must coalesce");
  c.handler.drain();check(c.captureSession.submits==2&&c.cameraDevice.creates==1,"one submit and builder reuse");
  check(c.captureSession.latest.get("exposure").equals(3)&&c.captureSession.latest.get("ois").equals(1),"zoom retains exposure and selected stabilization");
  check(Math.abs((Float)c.captureSession.latest.get("ratio")-50.5f)<.001,"last input wins");
  c.setZoom(50.5f);c.setZoom(Float.NaN);c.setZoom(Float.POSITIVE_INFINITY);
  check(c.handler.work.isEmpty(),"invalid and unchanged input ignored");
  c.setZoom(1000);c.handler.drain();check((Float)c.captureSession.latest.get("ratio")==100,"true maximum");
  c.setZoom(.1f);c.handler.drain();check((Float)c.captureSession.latest.get("ratio")==.5f,"true minimum");
  c.setZoom(1);c.handler.drain();check((Float)c.captureSession.latest.get("ratio")==1,"normal speed zoom reset");
  c=new Camera();c.zoomRatioSupported=false;c.minZoom=1;c.updateCaptureRequest();c.handler.drain();
  c.setZoom(2);c.handler.drain();Rect crop=(Rect)c.captureSession.latest.get("crop");
  check(crop.left==1020&&crop.top==790&&crop.width()==2000&&crop.height()==1500,"active-array origin");
  c.setZoom(1);c.handler.drain();crop=(Rect)c.captureSession.latest.get("crop");
  check(crop.left==20&&crop.top==40&&crop.width()==4000&&crop.height()==3000,"reused crop must restore 1x");
  int submits=c.captureSession.submits;c.setZoom(4);c.isClosed=true;c.handler.drain();
  check(c.captureSession.submits==submits,"closed camera ignores pending work");
  c.setZoom(8);c.updateCaptureRequest();check(c.handler.work.isEmpty(),"closed camera rejects new work");
  System.out.println("Camera2: 1000 events coalesced; one camera-thread request; builder reuse; ratio/crop reset; closed-session guard passed");
 }
}
'''.replace('QUEUED', queued).replace('METHODS', methods)
with tempfile.TemporaryDirectory(prefix='nebula-zoom-requests-') as temp:
    p = Path(temp) / 'CameraZoomRequests.java'
    p.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(p)], check=True)
    subprocess.run(['java', '-cp', temp, 'CameraZoomRequests'], check=True)
