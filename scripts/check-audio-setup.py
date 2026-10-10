"""Execute the production audio-choice callbacks and native header drawable branches."""
from pathlib import Path
import argparse, subprocess

parser = argparse.ArgumentParser()
parser.add_argument('tree', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work = root / 'build/audio-setup-check'
work.mkdir(parents=True, exist_ok=True)
audio = (ui / 'NebulaAudioSettingsFragment.java').read_text(encoding='utf-8')
method = audio[audio.index('    private void chooseService(boolean speech, boolean enableAfterSelection)'):audio.rfind('\n}')]
editor = (ui / 'NebulaAiServiceEditorFragment.java').read_text(encoding='utf-8')
factory = editor[editor.index('    public static NebulaAiServiceEditorFragment forAudio('):editor.index('    @Override public View createView(')]
source = r'''
import java.util.*;
import java.util.function.*;
class C {}
class Body {C getContext(){return new C();}}
class NebulaAiClient {static final int OPENAI=0,GEMINI=2,NANO=4;}
class NebulaAiServices {
 static class Service {String id,name,model;int provider;Service(String id,int p,String model){this.id=id;name=id;provider=p;this.model=model;}}
 static ArrayList<Service> values=new ArrayList<>();static ArrayList<Service> list(){return values;}
 static String providerName(int p){return p==0?"OpenAI":p==2?"Gemini":"Nano";}
}
class Prefs {Map<String,String> values=new HashMap<>();Prefs edit(){return this;}Prefs putString(String k,String v){values.put(k,v);return this;}void apply(){}}
class NebulaAudioPreferences {static Prefs p=new Prefs();static Prefs prefs(){return p;}static boolean supported(NebulaAiServices.Service s){return s.provider==0||s.provider==2;}static String serviceId(boolean speech){return p.values.getOrDefault(speech?"speech_service":"transcription_service",speech?"device":"");}}
class NebulaTranscription {static boolean enabled;static void setEnabled(boolean value){enabled=value;}}
class NebulaAiServiceEditorFragment {
 String id;int provider;boolean audioEditor,speechEditor;Consumer<NebulaAiServices.Service> saved;
 NebulaAiServiceEditorFragment(String id){this.id=id;}
__FACTORY__
 String defaultModel(){return initialAudioModel();}
 void finishSave(NebulaAiServices.Service s){saved.accept(s);}
}
class NebulaDialog {
 interface Click {void click(Object d,int index);}
 static class Builder {String[] items;Click click;Builder(C c,Object r){}Builder setTitle(String t){return this;}Builder setSelectedIndex(int i){return this;}Builder setItems(String[] items,Click click){this.items=items;this.click=click;return this;}Builder create(){return this;}}
}
class AudioScreen {
 Body body=new Body();NebulaDialog.Builder dialog;NebulaAiServiceEditorFragment editor;int rebuilds;
 Object getResourceProvider(){return null;}String text(String ru,String en){return en;}void showDialog(NebulaDialog.Builder d){dialog=d;}void presentFragment(NebulaAiServiceEditorFragment value){editor=value;}void rebuild(){rebuilds++;}
__METHOD__
 void choose(boolean speech,boolean enable){chooseService(speech,enable);}
}
public class AudioSetupCheck {
 static void check(boolean yes,String detail){if(!yes)throw new AssertionError(detail);}
 public static void main(String[] args){
  NebulaAiServices.values.add(new NebulaAiServices.Service("nano",4,"nano"));AudioScreen screen=new AudioScreen();
  screen.choose(false,true);check(Arrays.equals(screen.dialog.items,new String[]{"Not selected","Add OpenAI","Add Gemini"}),"Empty audio list offers both supported providers");
  screen.dialog.click.click(null,2);check(screen.editor.audioEditor&&!screen.editor.speechEditor&&screen.editor.provider==2,"Direct Gemini transcription editor");check(screen.editor.defaultModel().equals("gemini-3.8-flash"),"Transcription model seeded");
  check(!NebulaTranscription.enabled,"Opening setup does not enable transcription");screen.editor.finishSave(new NebulaAiServices.Service("audio-gemini",2,"chosen-audio-model"));
  check(NebulaTranscription.enabled&&NebulaAudioPreferences.serviceId(false).equals("audio-gemini"),"Saving completes the explicit enable flow");check(NebulaAudioPreferences.p.values.get("transcription_model_2").equals("chosen-audio-model"),"Saved audio model used");
  screen.choose(false,true);screen.dialog.click.click(null,0);check(!NebulaTranscription.enabled&&NebulaAudioPreferences.serviceId(false).isEmpty(),"Clearing selection disables replacement");
  screen.choose(true,false);check(screen.dialog.items[0].equals("On device"),"Device speech remains available");screen.dialog.click.click(null,1);check(screen.editor.provider==0&&screen.editor.speechEditor&&screen.editor.defaultModel().equals("gpt-4o-mini-tts"),"Direct OpenAI speech editor");
  screen.editor.finishSave(new NebulaAiServices.Service("audio-openai",0,"chosen-tts"));check(NebulaAudioPreferences.serviceId(true).equals("audio-openai")&&!NebulaTranscription.enabled,"Speech setup changes only speech");
  NebulaAiServices.values.add(new NebulaAiServices.Service("saved-openai",0,"text-model"));screen.choose(false,true);screen.dialog.click.click(null,1);check(NebulaAudioPreferences.serviceId(false).equals("saved-openai")&&NebulaTranscription.enabled,"Saved compatible connection selectable");
  try{NebulaAiServiceEditorFragment.forAudio(4,false,s->{});throw new AssertionError("Nano offered as audio");}catch(IllegalArgumentException expected){}
  System.out.println("Audio setup: empty list, provider creation, existing services, explicit enable/cancel and independent speech passed");
 }
}
'''.replace('__METHOD__', method).replace('__FACTORY__', factory)
(work / 'AudioSetupCheck.java').write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(work / 'AudioSetupCheck.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'AudioSetupCheck'], check=True)

header = (args.tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
start = header.index('        if (glassDrawableBack != null && nebulaHomeTabsGlass && actionModeFactor > 0f')
end = header.index('        if (actionModeFactor > 0f && actionMode != null', start)
draw = header[start:end]
source = r'''
class View {float getX(){return 0;}int getWidth(){return 46;}int getChildCount(){return 1;}View getChildAt(int i){return this;}float getAlpha(){return 1;}boolean hasVisibleAvatar(){return true;}}
class D {int visible;int alpha=255;void setAlpha(int value){alpha=value;}void setBounds(int a,int b,int c,int d){}void draw(Object canvas){if(alpha>0)visible++;}}
class Header {D glassDrawableBack=new D(),glassDrawableMenu=new D();View actionMode=new View(),backButtonImageView=new View(),nebulaChatAvatarContainer=new View();boolean nebulaHomeTabsGlass,nebulaPlainHeaderButtons=true,hasBackButton=true,nebulaProfileGlass,nebulaFloatingChatHeader=true,nebulaClassicSavedHeader,nebulaSavedMessagesHeader,nebulaChatMenuHidden=true;float actionModeFactor,searchFactor;int t=0,b=46,s=48,p=6,nebulaBackWidth=56;Object canvas=new Object();int dp(int n){return n;}int getWidth(){return 400;}void draw(){__DRAW__}}
class app {static class nebulagram {static class ui {static class NebulaChatStyle {static int avatarBackdropAlpha(float search,float action,float alpha){return Math.round(255*(1-Math.max(0,Math.min(1,Math.max(search,action))))*Math.max(0,Math.min(1,alpha)));}}}}}
public class HeaderSurfaceCheck {static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}public static void main(String[] args){Header normal=new Header();normal.draw();check(normal.glassDrawableBack.visible==1&&normal.glassDrawableMenu.visible==1,"Normal chat surfaces missing");Header selection=new Header();selection.actionModeFactor=1;selection.draw();check(selection.glassDrawableBack.visible==1&&selection.glassDrawableMenu.visible==0,"Selection overlap");Header search=new Header();search.searchFactor=1;search.draw();check(search.glassDrawableMenu.visible==0,"Avatar does not fade for search");Header home=new Header();home.nebulaFloatingChatHeader=false;home.draw();check(home.glassDrawableBack.visible==0&&home.glassDrawableMenu.visible==0,"Home/shared header behavior changed");Header returning=new Header();returning.actionModeFactor=1;returning.draw();returning.actionModeFactor=0;returning.draw();check(returning.glassDrawableMenu.visible==1,"Avatar surface not restored after selection");System.out.println("Native header: normal chat, selection, search, return and shared-header surfaces passed");}}
'''.replace('__DRAW__', draw)
(work / 'HeaderSurfaceCheck.java').write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(work / 'HeaderSurfaceCheck.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'HeaderSurfaceCheck'], check=True)
chat = (args.tree / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
assert 'NebulaTranscription.openTools(this, selectedObject)' in chat and 'object.forceUpdate = true' in chat
assert 'NebulaTranscription.eligible(message)' in chat and 'options.add(7919)' in chat
print('Native transcription menu and resume rebind hooks present')
