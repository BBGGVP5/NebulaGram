"""Execute production glass preferences and native blur setters, counting hot-path work."""
from pathlib import Path
import subprocess
import sys
import tempfile
import re

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])

def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

with tempfile.TemporaryDirectory(prefix='nebula-render-hot-path-') as folder:
    work = Path(folder)
    files = {
        'app/nebulagram/ui/NebulaGlassRuntime.java': 'package app.nebulagram.ui; public class NebulaGlassRuntime {public static void invalidateWindows() {}}',
        'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
 public int reads; public Map<String,Object> values=new HashMap<>();
 public interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String k);}
 private OnSharedPreferenceChangeListener listener;
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listener=l;}
 public boolean getBoolean(String k,boolean d){reads++;return (Boolean)values.getOrDefault(k,d);}
 public int getInt(String k,int d){reads++;return (Integer)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor();}
 public class Editor {
  List<String> changed=new ArrayList<>(); boolean clear;
  public Editor putBoolean(String k,boolean v){values.put(k,v);changed.add(k);return this;}
  public Editor putInt(String k,int v){values.put(k,v);changed.add(k);return this;}
  public Editor clear(){values.clear();clear=true;return this;}
  public void apply(){if(listener!=null){if(clear)listener.onSharedPreferenceChanged(SharedPreferences.this,null);else for(String k:changed)listener.onSharedPreferenceChanged(SharedPreferences.this,k);}}
 }
}''',
        'android/content/Context.java': '''package android.content; public class Context {
 public int lookups; public final SharedPreferences p=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String n,int m){lookups++;return p;}
}''',
        'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
        'GlassPrefsCheck.java': '''import app.nebulagram.ui.NebulaGlass;
import org.telegram.messenger.ApplicationLoader;
class GlassPrefsCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void near(float actual,float expected){check(Math.abs(actual-expected)<.0001,"wrong effective setting: "+actual+" vs "+expected);}
 public static void main(String[] args){
  var c=ApplicationLoader.applicationContext;var p=c.p;
  near(NebulaGlass.opacity(),.72f);near(NebulaGlass.blur(),12f);near(NebulaGlass.refraction(),0f);check(!NebulaGlass.custom(),"default override");
  int reads=p.reads,lookups=c.lookups;
  for(int i=0;i<10000;i++){NebulaGlass.custom();NebulaGlass.opacity();NebulaGlass.blur();NebulaGlass.refraction();}
  check(p.reads==reads&&c.lookups==lookups,"render hot path repeatedly reads preferences");
  long revision=NebulaGlass.revision();p.edit().putBoolean("glass_highlights",false).apply();check(NebulaGlass.revision()>revision&&!NebulaGlass.highlights(),"highlight setting not live");
  NebulaGlass.custom(true);NebulaGlass.setValue("blur",25);near(NebulaGlass.blur(),7.5f);
  NebulaGlass.setValue("opacity",-20);near(NebulaGlass.opacity(),.25f);
  NebulaGlass.setValue("refraction",200);near(NebulaGlass.refraction(),.5f);
  // Imported/external preference edits must not leave cached values stale.
  p.edit().putInt("glass_blur",100).putInt("glass_opacity",100).apply();near(NebulaGlass.blur(),30);near(NebulaGlass.opacity(),1);
  p.edit().putBoolean("glass_custom",false).apply();near(NebulaGlass.opacity(),.72f);near(NebulaGlass.blur(),12);near(NebulaGlass.refraction(),0f);
  reads=p.reads;p.edit().putInt("unrelated",1).apply();check(p.reads==reads,"unrelated setting rebuilds glass cache");
  p.edit().clear().apply();check(!NebulaGlass.custom(),"clear ignored");near(NebulaGlass.opacity(),.72f);
  for(int mode=0;mode<3;mode++)for(int mask=0;mask<8;mask++) {
   NebulaGlass.quality(mode);NebulaGlass.environment((mask&1)!=0,(mask&2)!=0,(mask&4)!=0);
   check(NebulaGlass.reduced()==(mode==2||mode==0&&mask!=0),"adaptive policy mismatch");
  }
  NebulaGlass.quality(0);NebulaGlass.environment(true,false,false);near(NebulaGlass.blur(),6);near(NebulaGlass.refraction(),0);near(NebulaGlass.opacity(),.85f);
  NebulaGlass.environment(false,false,false);near(NebulaGlass.blur(),12);near(NebulaGlass.refraction(),0f);
  NebulaGlass.custom(true);NebulaGlass.setValue("blur",100);NebulaGlass.quality(2);near(NebulaGlass.blur(),6);NebulaGlass.quality(1);near(NebulaGlass.blur(),30);
  reads=p.reads;lookups=c.lookups;for(int i=0;i<10000;i++){NebulaGlass.reduced();NebulaGlass.blur();}check(p.reads==reads&&c.lookups==lookups,"adaptive draw polls settings");
  System.out.println("Glass preferences: 40,000 warmed getter calls without preference access; edits, bounds and reset passed");
 }
}'''
    }
    sources=[]
    for path, text in files.items():
        p=work/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(str(p))
    sources.append(str(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaGlassPolicy.java'))
    sources.append(str(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaGlass.java'))
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*sources],check=True)
    subprocess.run(['java','-cp',str(work),'GlassPrefsCheck'],check=True)

    source=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/source/BlurredBackgroundSourceRenderNode.java').read_text(encoding='utf-8')
    methods='\n'.join(method(source,s) for s in ['public void setBlur(float radius)', 'public void setBlur(float radius, RenderEffect effect)', 'private void nebulaSetBlur(float radius, RenderEffect input)'])
    methods=methods.replace('app.nebulagram.ui.NebulaGlass.','Settings.').replace('org.telegram.messenger.AndroidUtilities.dpf2','dp')
    fields=source[source.index('    private float nebulaBlurRadius'):source.index('    @RequiresApi',source.index('    private float nebulaBlurRadius'))]
    java='''class BlurReuseCheck {
 static class RenderEffect {
  static int creates; RenderEffect inner,outer;float radius;
  static RenderEffect createBlurEffect(float x,float y,Object mode){if(x<=0||y<=0)throw new AssertionError("invalid radius");creates++;RenderEffect r=new RenderEffect();r.radius=x;return r;}
  static RenderEffect createChainEffect(RenderEffect outer,RenderEffect inner){creates++;RenderEffect r=new RenderEffect();r.inner=inner;r.outer=outer;return r;}
 }
 static class RenderNode {int installs;RenderEffect value;void setRenderEffect(RenderEffect e){installs++;value=e;}}
 static class Shader {static class TileMode {static Object CLAMP;}}
 static class Settings {static boolean custom,reduced;static boolean reduced(){return reduced;}static float radius;static boolean custom(){return custom;}static float blur(){return radius;}}
 static float dp(float n){return n*2;}
 final RenderNode renderNode=new RenderNode();
 FIELDS
 METHODS
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  BlurReuseCheck source=new BlurReuseCheck();source.setBlur(12);
  for(int i=0;i<10000;i++)source.setBlur(12);
  check(RenderEffect.creates==1&&source.renderNode.installs==1,"identical effects recreated");
  source.setBlur(10);check(source.renderNode.installs==2,"new radius ignored");
  RenderEffect input=new RenderEffect();source.setBlur(10,input);int creates=RenderEffect.creates,installs=source.renderNode.installs;
  for(int i=0;i<10000;i++)source.setBlur(10,input);
  check(RenderEffect.creates==creates&&source.renderNode.installs==installs,"same chain recreated");
  source.setBlur(10,new RenderEffect());check(source.renderNode.installs==++installs,"new input ignored");
  source.setBlur(10);check(source.renderNode.installs==++installs&&source.renderNode.value.inner==null,"chain stuck on plain blur");
  source.setBlur(0,input);check(source.renderNode.value==input,"zero blur loses input");source.setBlur(0);check(source.renderNode.value==null,"zero blur not removed");
  Settings.custom=true;Settings.radius=7;source.setBlur(12);check(source.renderNode.value.radius==14,"custom effective radius ignored");
  creates=RenderEffect.creates;source.setBlur(30);check(RenderEffect.creates==creates,"same effective custom radius recreated");
  Settings.radius=8;source.setBlur(30);check(source.renderNode.value.radius==16,"changed preference ignored");
  Settings.custom=false;source=new BlurReuseCheck();source.setBlur(0);source.setBlur(0);check(source.renderNode.installs==1,"initial zero not cached");
  Settings.reduced=true;source.setBlur(30);check(source.renderNode.value.radius==12,"light cap ignored");Settings.reduced=false;source.setBlur(30);check(source.renderNode.value.radius==30,"full radius not restored");
  System.out.println("Blur effects: 20,000 unchanged setters cached; radius, chain, custom and zero transitions passed");
 }
}'''.replace('FIELDS',fields).replace('METHODS',methods)
    p=work/'BlurReuseCheck.java';p.write_text(java,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True)
    subprocess.run(['java','-cp',str(work),'BlurReuseCheck'],check=True)

    # Exercise the real shader controller: a slider tick is only .005, well
    # below the old .1 cutoff. Unchanged frames must still reuse the effect.
    liquid = (tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/LiquidGlassEffect.java').read_text(encoding='utf-8')
    liquid = re.sub(r'^(package |import |@RequiresApi).*\n', '', liquid, flags=re.M)
    (work/'LiquidGlassEffect.java').write_text(liquid, encoding='utf-8')
    (work/'LiquidSliderCheck.java').write_text('''
class RenderNode {
 int installs; RenderEffect effect;
 int getWidth(){return 300;} int getHeight(){return 64;}
 void setRenderEffect(RenderEffect e){installs++;effect=e;}
}
class RuntimeShader {
 static float intensity; RuntimeShader(String code){}
 void setFloatUniform(String name,float... values){if(name.equals("refract_intensity"))intensity=values[0];}
}
class RenderEffect {
 static RenderEffect createRuntimeShaderEffect(RuntimeShader s,String name){return new RenderEffect();}
}
class Color {
 static int alpha(int c){return c>>>24;} static int red(int c){return (c>>16)&255;}
 static int green(int c){return (c>>8)&255;} static int blue(int c){return c&255;}
}
class AndroidUtilities {static String readRes(int id){return "shader";}}
class R {static class raw {static int liquid_glass_shader;}}
class LiquidSliderCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void update(LiquidGlassEffect effect,float intensity){effect.update(0,0,300,64,32,32,32,32,11,intensity,1.2f,0x99000000);}
 public static void main(String[] args){
  RenderNode node=new RenderNode(); LiquidGlassEffect effect=new LiquidGlassEffect(node);
  update(effect,0);int installs=node.installs;
  for(int i=1;i<=100;i++){
   update(effect,i*.005f);
   check(Math.abs(RuntimeShader.intensity-i*.005f)<.00001f,"slider tick skipped at "+i);
  }
  check(node.installs==installs+100,"slider did not refresh every tick");
  installs=node.installs;for(int i=0;i<10000;i++)update(effect,.5f);
  check(node.installs==installs,"unchanged intensity rebuilds shader");
  effect.setEnabled(false);check(node.effect==null,"zero/reduced bypass retains distortion");
  installs=node.installs;effect.setEnabled(false);check(node.installs==installs,"bypass not cached");
  effect.setEnabled(true);check(node.effect!=null,"effect not restored");
  System.out.println("Liquid glass: all 100 slider ticks applied, 10,000 unchanged frames cached, disable/restore passed");
 }
}''', encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8',str(work/'LiquidGlassEffect.java'),str(work/'LiquidSliderCheck.java')],check=True)
    subprocess.run(['java','-cp',str(work),'LiquidSliderCheck'],check=True)

    # NebulaTheme.of is called from onDraw and onMeasure in eight of our views.
    # Every call used to read a preference, ask for the resource configuration
    # and allocate a palette, which is a preference lock and garbage per frame.
    theme = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaTheme.java').read_text(encoding='utf-8')
    body = method(theme, 'public static NebulaTheme of(Context context)')
    assert 'getSharedPreferences' not in body, 'NebulaTheme.of reads preferences on a draw path'
    assert 'getConfiguration' not in body, 'NebulaTheme.of asks for the configuration on a draw path'
    assert 'cached' in body, 'NebulaTheme.of does not reuse the palette'
    assert 'private static volatile Boolean materialYou;' in theme, 'the Material You flag is not cached'
    assert 'registerOnSharedPreferenceChangeListener(materialYouListener)' in theme, 'the cached flag is never invalidated'
    # A static holding an Activity outlives it; the palette keeps the application.
    assert 'getApplicationContext()' in body, 'the cached palette may hold an Activity'

    # Shaders are native objects, and rebuilding one makes the paint recompile
    # its fill program. The profile header draws on every scroll frame.
    art = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProfileArt.java').read_text(encoding='utf-8')
    for name in ['IdentityBackground']:
        start = art.index('class ' + name)
        section = art[start:art.index(chr(10) + '    }', start)]
        assert 'new LinearGradient' in section and 'gradient == null' in section, name + ' rebuilds its shader every frame'
    print('Palette and shader reuse: theme cached with invalidation, profile gradients rebuilt only on change')

