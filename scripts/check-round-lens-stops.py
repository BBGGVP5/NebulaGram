"""Lens zoom steps of the Camera2 round-video engine: run NebulaRoundZoom, then check the 0373 hooks."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

stubs = {
'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static int posted;public static void runOnUIThread(Runnable r){posted++;r.run();}}',
'app/nebulagram/ui/NebulaCameraSettings.java': 'package app.nebulagram.ui; public final class NebulaCameraSettings {public static boolean stops;public static boolean enabled(String k){if(!"lens_stops".equals(k))throw new AssertionError(k);return stops;}}',
'app/nebulagram/ui/NebulaZoomCapabilities.java': 'package app.nebulagram.ui; public final class NebulaZoomCapabilities {public final float[] stops;public NebulaZoomCapabilities(float[] s){stops=s;}}',
'app/nebulagram/ui/NebulaCameraZoom.java': 'package app.nebulagram.ui; public final class NebulaCameraZoom {public static int calls;public static NebulaZoomCapabilities discover(boolean front){calls++;return new NebulaZoomCapabilities(front?new float[]{1f}:new float[]{0.6f,1f,3f,10f});}}',
'Check.java': r'''import app.nebulagram.ui.*; import java.util.Arrays;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 static void same(float[] got,float[] want,String m){check(Arrays.equals(got,want),m+": "+Arrays.toString(got));}
 public static void main(String[] a){
  // Off: the old single 1× mark, no camera discovery.
  NebulaCameraSettings.stops=false;
  same(NebulaRoundZoom.stops(false,1f,4f),new float[]{1f},"off keeps 1x");check(NebulaCameraZoom.calls==0,"off does not query cameras");
  same(NebulaRoundZoom.stops(false,0.6f,4f),new float[]{1f},"off with ultrawide start");
  // On: optical lenses inside the range; 10x beyond the 4x UI cap is dropped; discovery is cached.
  NebulaCameraSettings.stops=true;
  same(NebulaRoundZoom.stops(false,0.6f,4f),new float[]{0.6f,1f,3f},"rear lenses");
  same(NebulaRoundZoom.stops(false,1f,4f),new float[]{1f,3f},"ultrawide below the start is hidden");
  same(NebulaRoundZoom.stops(true,1f,2f),new float[]{1f},"front");
  NebulaRoundZoom.stops(false,1f,4f);check(NebulaCameraZoom.calls==2,"one discovery per facing: "+NebulaCameraZoom.calls);
  // Slider value and controller progress agree at both ends and in between.
  check(NebulaRoundZoom.factor(0f,0.6f,4f)==0.6f&&NebulaRoundZoom.factor(1f,0.6f,4f)==4f,"factor ends");
  check(Math.abs(NebulaRoundZoom.progress(NebulaRoundZoom.factor(0.3f,0.6f,4f),0.6f,4f)-0.3f)<1e-5,"round trip");
  check(NebulaRoundZoom.progress(0.2f,0.6f,4f)==0f&&NebulaRoundZoom.progress(9f,0.6f,4f)==1f&&NebulaRoundZoom.progress(2f,1f,1f)==0f,"progress clamps");
  check(NebulaRoundZoom.factor(0.5f,1f,4f)==2.5f,"old 1x..max mapping is unchanged without ultrawide");
  // The controller's real start reaches the UI once per change; invalid values fall back to 1x.
  check(NebulaRoundZoom.minimum(false)==1f,"default start");
  int[] calls={0};NebulaRoundZoom.listen(()->calls[0]++);
  NebulaRoundZoom.publish(false,0.6f);NebulaRoundZoom.publish(false,0.6f);
  check(calls[0]==1&&NebulaRoundZoom.minimum(false)==0.6f&&NebulaRoundZoom.minimum(true)==1f,"published once: "+calls[0]);
  NebulaRoundZoom.publish(false,Float.NaN);check(NebulaRoundZoom.minimum(false)==1f&&calls[0]==2,"NaN falls back");
  NebulaRoundZoom.publish(true,2f);check(NebulaRoundZoom.minimum(true)==1f&&calls[0]==2,"a start above 1x is not a start");
  NebulaRoundZoom.listen(null);NebulaRoundZoom.publish(false,0.5f);check(calls[0]==2,"detached listener");
  System.out.println("Round lens stops: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-round-lens-') as work:
    work = Path(work)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), str(ui / 'NebulaRoundZoom.java')], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

camera = (ui / 'NebulaCameraFragment.java').read_text(encoding='utf-8')
assert 'if(modern) round.add(toggle("lens_stops","Шаги по объективам","Lens zoom steps"' in camera
assert '0373-round-camera2-lens-stops.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram'
    view = (tree / 'ui/Components/InstantCameraView2.java').read_text(encoding='utf-8')
    for hook in ['nebulaZoomSlider.setRange(minimum, maximum);', 'app.nebulagram.ui.NebulaRoundZoom.stops(front, minimum, maximum)',
                 'app.nebulagram.ui.NebulaRoundZoom.factor(zoomProgress, nebulaMinimumZoom(), maximum)',
                 'zoomProgress = app.nebulagram.ui.NebulaRoundZoom.progress(factor, minimum, maximum);', 'app.nebulagram.ui.NebulaRoundZoom.listen(']:
        assert hook in view, hook
    assert 'nebulaZoomSlider.setRange(1f, maximum);' not in view
    controller = (tree / 'utils/recorder/round/RoundVideoCameraController.java').read_text(encoding='utf-8')
    publish = controller.index('app.nebulagram.ui.NebulaRoundZoom.publish(')
    assert controller.index('NebulaCameraPolicy.startZoom(true, false') < publish < controller.index('float ratio = minimum + zoomProgress')
print('Round lens stops: setting, HOOKS entry and 0373 hooks verified')
