"""Execute the real Camera2 discovery code against representative OEM metadata."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source = (overlay / 'NebulaCameraZoom.java').read_text(encoding='utf-8')

def block(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

discovery = '\n'.join(block(signature) for signature in [
    'private static final class Camera', 'public static NebulaZoomCapabilities discover',
    'private static float focal']).replace('android.graphics.ImageFormat.JPEG', 'ImageFormat.JPEG')
java = r'''
import app.nebulagram.ui.NebulaZoomCapabilities;
import java.util.*;
class CameraDiscoveryCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void close(float a,float b){check(Math.abs(a-b)<.002f,a+" != "+b);}
 static class Context {static final String CAMERA_SERVICE="camera";Object getSystemService(String key){return manager;}}
 static class ApplicationLoader {static Context applicationContext=new Context();}
 static class Build {static class VERSION {static int SDK_INT=30;}}
 static class SurfaceTexture {}
 static class ImageFormat {static int JPEG=256;}
 static class FileLog {static void e(Exception e){}}
 static class SizeF {float width;SizeF(float w){width=w;}float getWidth(){return width;}}
 static class Range<T> {T low,high;Range(T l,T h){low=l;high=h;}T getLower(){return low;}T getUpper(){return high;}}
 static class StreamConfigurationMap {
  boolean jpeg=true,preview=true;
  int[] getOutputSizes(Class<?> type){return preview?new int[]{1}:null;}
  boolean isOutputSupportedFor(int format){return jpeg;}
 }
 static class CameraCharacteristics {
  static class Key<T>{}
  static final Key<Integer> LENS_FACING=new Key<>();
  static final int LENS_FACING_FRONT=0,LENS_FACING_BACK=1,REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA=11;
  static final Key<StreamConfigurationMap> SCALER_STREAM_CONFIGURATION_MAP=new Key<>();
  static final Key<Float> SCALER_AVAILABLE_MAX_DIGITAL_ZOOM=new Key<>();
  static final Key<Range<Float>> CONTROL_ZOOM_RATIO_RANGE=new Key<>();
  static final Key<int[]> REQUEST_AVAILABLE_CAPABILITIES=new Key<>();
  static final Key<float[]> LENS_INFO_AVAILABLE_FOCAL_LENGTHS=new Key<>();
  static final Key<SizeF> SENSOR_INFO_PHYSICAL_SIZE=new Key<>();
  Map<Key<?>,Object> values=new HashMap<>();Set<String> physical=new LinkedHashSet<>();
  <T> void put(Key<T> k,T v){values.put(k,v);}
  @SuppressWarnings("unchecked") <T> T get(Key<T> k){return (T)values.get(k);}
  Set<String> getPhysicalCameraIds(){return physical;}
 }
 static class CameraManager {
  Map<String,CameraCharacteristics> values=new LinkedHashMap<>();List<String> exposed=new ArrayList<>();
  String[] getCameraIdList(){return exposed.toArray(new String[0]);}
  CameraCharacteristics getCameraCharacteristics(String id){return values.get(id);}
  void add(String id,CameraCharacteristics c,boolean publicId){values.put(id,c);if(publicId)exposed.add(id);}
 }
 static CameraManager manager;
 static CameraCharacteristics camera(boolean front,float focal,float sensor,float maximum) {
  CameraCharacteristics c=new CameraCharacteristics();
  c.put(CameraCharacteristics.LENS_FACING,front?0:1);
  c.put(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,new float[]{focal});
  c.put(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,new SizeF(sensor));
  c.put(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM,maximum);
  c.put(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP,new StreamConfigurationMap());
  return c;
 }
 static class Discovery { DISCOVERY }
 public static void main(String[] args) {
  manager=new CameraManager();
  // Enumerate ultra-wide first to ensure selection isn't based on ID order or sensor shape.
  manager.add("ultra",camera(false,2,6,4),true);
  manager.add("wide",camera(false,4,6,8),true);
  manager.add("tele",camera(false,12,6,20),true);
  manager.add("front",camera(true,3,6,4),true);
  CameraCharacteristics depth=camera(false,1,6,200);
  depth.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).jpeg=false;
  manager.add("depth",depth,true);
  NebulaZoomCapabilities c=Discovery.discover(false);
  check(c.primary.id.equals("wide"),"main lens selected by field of view");
  close(c.minimum,.5f);close(c.maximum,60);check(c.stops.length==3,"depth-only camera isn't a lens button");
  c=Discovery.discover(true);check(c.modules.size()==1,"front and rear catalogs isolated");close(c.maximum,4);
  CameraCharacteristics logical=camera(false,4,6,10);
  logical.put(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,new int[]{11});
  logical.put(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE,new Range<Float>(.6f,100f));
  logical.physical.addAll(Arrays.asList("ultra","wide","tele"));
  manager.add("logical",logical,true);
  c=Discovery.discover(false);
  check(c.primary.id.equals("logical")&&c.modules.size()==1,"virtual camera preferred over standalone reopen");
  close(c.minimum,.6f);close(c.maximum,100);close(c.stops[0],.6f);close(c.stops[2],3);
  manager.exposed.removeAll(Arrays.asList("ultra","wide","tele"));
  c=Discovery.discover(false);check(c.stops.length==3,"non-public physical lens metadata works through logical zoom");
  Build.VERSION.SDK_INT=28;c=Discovery.discover(false);
  check(!c.primary.logical&&c.stops.length==1,"old API doesn't advertise inaccessible physical lens switches");
  manager=new CameraManager();check(Discovery.discover(false)==null,"no camera has a safe fallback");
  System.out.println("Camera discovery: focal normalization, logical priority, hidden sensors, JPEG filtering and front isolation passed");
 }
}
'''.replace('DISCOVERY', discovery)
with tempfile.TemporaryDirectory(prefix='nebula-camera-discovery-') as temp:
    source = Path(temp) / 'CameraDiscoveryCheck.java'
    source.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp,
                    str(overlay / 'NebulaZoomCapabilities.java'), str(source)], check=True)
    subprocess.run(['java', '-cp', temp, 'CameraDiscoveryCheck'], check=True)
