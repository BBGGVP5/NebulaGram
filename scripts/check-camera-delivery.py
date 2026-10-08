"""Exercise the native recorder's delivery guard across queued cancellation races."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/android-upstream-12106'
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java').read_text(encoding='utf8')
start = source.index('        private boolean canDeliverRecording()')
end = source.index('\n        }', start) + len('\n        }')
method = source[start:end]
fixture = '''import java.io.File;
import java.util.ArrayList;
public class DeliveryCheck {
 boolean cancelled, discarded; File videoFile, cameraFile; int delivered;
 METHOD
 void queue(ArrayList<Runnable> queue) { queue.add(() -> {if(canDeliverRecording())delivered++;}); }
 public static void main(String[] args) {
  DeliveryCheck d=new DeliveryCheck();ArrayList<Runnable> queue=new ArrayList<>();
  d.videoFile=new File("first.mp4");d.cameraFile=d.videoFile;
  d.queue(queue);queue.remove(0).run();
  if(d.delivered!=1)throw new AssertionError("active recording was discarded");
  d.queue(queue);d.cancelled=true;d.discarded=true;d.cameraFile=null;queue.remove(0).run();
  if(d.delivered!=1)throw new AssertionError("cancelled completion was delivered");
  d.queue(queue);d.cancelled=false;d.cameraFile=new File("second.mp4");queue.remove(0).run();
  if(d.delivered!=1)throw new AssertionError("old completion replaced new recording");
  d.discarded=false;d.queue(queue);queue.remove(0).run();
  if(d.delivered!=1)throw new AssertionError("file ownership ignored");
  d.videoFile=d.cameraFile;d.queue(queue);queue.remove(0).run();
  if(d.delivered!=2)throw new AssertionError("new recording cannot deliver");
  d.videoFile=null;if(d.canDeliverRecording())throw new AssertionError("missing encoder file");
  System.out.println("Queued recorder delivery: active, cancelled, replaced and missing files passed");
 }
}'''.replace('METHOD', method)
with tempfile.TemporaryDirectory(prefix='nebula-camera-delivery-') as folder:
    java = Path(folder) / 'DeliveryCheck.java'
    java.write_text(fixture, encoding='utf8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(java)], check=True)
    subprocess.run(['java', '-cp', folder, 'DeliveryCheck'], check=True)
