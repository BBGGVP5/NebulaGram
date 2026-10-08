"""Run capture fallback and transition policies without Android hardware."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
fixture='''package app.nebulagram.ui;
public class CameraPreferencesCheck {
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);}
 public static void main(String[] args){
  check(NebulaCameraPolicy.audioDurationUs(96000,48000,1)==1000000,"mono PCM timing");
  check(NebulaCameraPolicy.audioDurationUs(192000,48000,2)==1000000,"stereo PCM timing stays synchronized");
  check(NebulaCameraPolicy.audioDurationUs(192000L*600,48000,2)==600000000L,"long recordings do not overflow int arithmetic");
  check(NebulaCameraPolicy.audioDurationUs(96000,0,0)==0,"invalid audio format");
  check(NebulaCameraPolicy.quality(2160,new int[]{720,1080,480})==1080,"unsupported 4K falls back to supported 1080");
  check(NebulaCameraPolicy.quality(360,new int[]{1080,720})==720,"below available sizes uses smallest capture");
  check(NebulaCameraPolicy.quality(0,new int[]{720,1080})==0,"default keeps native selection");
  check(NebulaCameraPolicy.quality(2160,new int[]{})==0,"empty capabilities keeps native selection");
  check(NebulaCameraPolicy.stabilization(true,true,true,true)==1,"prefer OIS instead of combining incompatible modes");
  check(NebulaCameraPolicy.stabilization(true,true,false,true)==2,"fall back to supported EIS");
  check(NebulaCameraPolicy.stabilization(true,false,false,true)==0,"do not silently enable an unrequested effect");
  check(NebulaCameraPolicy.startZoom(true,false,0.6f,8f)==0.6f,"rear ultrawide start");
  check(NebulaCameraPolicy.startZoom(true,true,0.5f,2f)==1f,"front starts at normal zoom");
  check(NebulaCameraPolicy.startZoom(true,false,Float.NaN,Float.POSITIVE_INFINITY)==1f,"invalid range is safe");
  check(NebulaCameraPolicy.switchBlur(false,0f)==0f,"idle never blurs");
  check(NebulaCameraPolicy.switchBlur(true,0f)==1f,"outgoing frame starts blurred");
  float previous=1f;
  for(int i=0;i<=100;i++){float v=NebulaCameraPolicy.switchBlur(true,i/100f);check(v>=0&&v<=previous,"blur settles monotonically");previous=v;}
  check(previous==0f&&NebulaCameraPolicy.switchBlur(true,Float.NaN)==0f,"settled/invalid transition is sharp");
  System.out.println("Camera capability fallbacks, stabilization conflicts, ultrawide and switch envelope passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-camera-policy-') as folder:
 p=Path(folder);(p/'CameraPreferencesCheck.java').write_text(fixture,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(ui/'NebulaCameraPolicy.java'),str(p/'CameraPreferencesCheck.java')],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.CameraPreferencesCheck'],check=True)
