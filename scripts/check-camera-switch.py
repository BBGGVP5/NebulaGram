"""Compile the actual legacy encoder shader variants and inspect transition invariants."""
from pathlib import Path
import os,shutil,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/android-upstream-12106'
native=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java').read_text(encoding='utf8')
def method(signature):
 start=native.index(signature);end=native.index('{',start)+1;depth=1
 while depth:
  depth+=(native[end]=='{')-(native[end]=='}');end+=1
 return native[start:end]
fixture='''import java.nio.file.*;
import app.nebulagram.ui.NebulaCameraSwitch;
public class ShaderCheck {
 static class SharedConfig {static boolean low;static boolean deviceIsLow(){return low;}}
 static class Size {int getWidth(){return 800;}int getHeight(){return 800;}}
 static class MessagesController {int roundVideoSize=480;static MessagesController getInstance(int a){return new MessagesController();}}
 int currentAccount;boolean allowBigSizeCamera(){return true;}
 METHODS
 public static void main(String[] args)throws Exception{
  ShaderCheck source=new ShaderCheck();int count=0;
  for(boolean low:new boolean[]{true,false}){SharedConfig.low=low;
   for(String original:new String[]{source.createFragmentShader(new Size()),source.createFragmentShaderV2(new Size())}){
    String shader=NebulaCameraSwitch.shader(original);
    if(shader.equals(original)||!shader.contains("uniform float nebulaBlur"))throw new AssertionError("unpatched encoder shader");
    String main=shader.substring(shader.indexOf("void main()"));
    if(main.contains("texture2D(sTexture"))throw new AssertionError("sampling bypasses blur");
    if(original.contains("float radius")&&!main.contains("float radius"))throw new AssertionError("round mask lost");
    if(!main.contains("alpha"))throw new AssertionError("crossfade lost");
    Files.writeString(Path.of(args[0],"encoder-"+(count++)+".frag"),shader);
   }
  }
  if(!NebulaCameraSwitch.shader("unrelated shader").equals("unrelated shader"))throw new AssertionError("unsupported shader changed");
 }
}'''.replace('METHODS',method('private String createFragmentShader(Size previewSize)')+'\n'+method('private String createFragmentShaderV2(Size previewSize)'))
validator=os.environ.get('GLSLANG_VALIDATOR') or shutil.which('glslangValidator')
with tempfile.TemporaryDirectory(prefix='nebula-camera-glsl-') as folder:
 p=Path(folder);(p/'ShaderCheck.java').write_text(fixture,encoding='utf8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaCameraSwitch.java'),str(p/'ShaderCheck.java')],check=True)
 subprocess.run(['java','-cp',folder,'ShaderCheck',folder],check=True)
 if validator:
  for shader in p.glob('*.frag'):subprocess.run([validator,'-S','frag',str(shader)],check=True)
  print('Four actual encoder GLSL variants compile; circular mask, sampling and alpha preserved')
 else:print('Four encoder variants retain mask/alpha and all sampling hooks; GLSL compiler unavailable')
