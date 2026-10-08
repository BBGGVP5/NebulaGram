"""Exercise camera/encoder capability disagreement with the production sizing policy."""
from pathlib import Path
import subprocess, tempfile

root=Path(__file__).resolve().parents[1]
files={
 'android/util/Size.java':'''package android.util;public class Size {final int w,h;public Size(int w,int h){this.w=w;this.h=h;}public int getWidth(){return w;}public int getHeight(){return h;}}''',
 'android/util/Range.java':'''package android.util;public class Range<T> {public int clamp(int n){return Math.max(100000,Math.min(16000000,n));}}''',
 'android/media/MediaCodecInfo.java':'''package android.media;public class MediaCodecInfo {
 public static class VideoCapabilities {
  public boolean unavailable;public int getWidthAlignment(){return 16;}public int getHeightAlignment(){return 8;}
  public boolean areSizeAndRateSupported(int w,int h,double fps){return !unavailable&&w>=160&&h>=120&&w<=1920&&h<=1080&&fps<=30&&w%16==0&&h%8==0;}
  public android.util.Range<Integer> getBitrateRange(){return new android.util.Range<>();}
 }
}''',
 'app/nebulagram/ui/EncodingCheck.java':'''package app.nebulagram.ui;
import android.media.MediaCodecInfo;import android.util.Size;
public class EncodingCheck {
 public static void main(String[] args){
  MediaCodecInfo.VideoCapabilities caps=new MediaCodecInfo.VideoCapabilities();
  Size normal=NebulaCameraEncoding.size(caps,1280,720,30);if(normal.getWidth()!=1280||normal.getHeight()!=720)throw new AssertionError("supported source changed");
  for(int[] source:new int[][]{{3840,2160},{2160,3840},{1081,1921},{4096,4096}}){
   Size result=NebulaCameraEncoding.size(caps,source[0],source[1],30);
   if(!caps.areSizeAndRateSupported(result.getWidth(),result.getHeight(),30))throw new AssertionError("unsupported output");
   if(result.getWidth()>source[0]||result.getHeight()>source[1])throw new AssertionError("upscaled capture");
   if(Math.abs((double)result.getWidth()/result.getHeight()-(double)source[0]/source[1])>.04)throw new AssertionError("framing changed");
  }
  if(NebulaCameraEncoding.bitrate(caps,3840,2160,3500000)!=16000000)throw new AssertionError("encoder bitrate limit ignored");
  caps.unavailable=true;try{NebulaCameraEncoding.size(caps,3840,2160,30);throw new AssertionError("unsupported codec accepted");}catch(IllegalStateException expected){}
  System.out.println("Encoder fallback preserves framing, alignment, bitrate limits and supported dimensions");
 }
}'''}
with tempfile.TemporaryDirectory(prefix='nebula-encoder-') as folder:
 out=Path(folder)
 for name,content in files.items():
  path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaCameraEncoding.java'),*map(str,out.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.EncodingCheck'],check=True)
