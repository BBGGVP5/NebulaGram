"""Exercise the production scale-compensated popup blur cache and cleanup policy."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs = {
    'android/annotation/TargetApi.java': 'package android.annotation;public @interface TargetApi {int value();}',
    'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}',
    'android/graphics/Shader.java': 'package android.graphics;public class Shader {public enum TileMode {CLAMP}}',
    'android/graphics/RenderEffect.java': '''package android.graphics;public class RenderEffect {
        public static int created;public float radius;public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode mode){
            if(x!=y||x<=0)throw new AssertionError("invalid blur");created++;RenderEffect e=new RenderEffect();e.radius=x;return e;}}''',
    'android/view/View.java': '''package android.view;public class View {public boolean hardware=true;public android.graphics.RenderEffect effect;
        public boolean isHardwareAccelerated(){return hardware;}public void setRenderEffect(android.graphics.RenderEffect e){effect=e;}}''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;public class AndroidUtilities {
        public static float density=1;public static float dpf2(float x){return density*x;}}''',
    'org/telegram/messenger/LiteMode.java': '''package org.telegram.messenger;public class LiteMode {
        public static int FLAG_CHAT_BLUR=1;public static boolean enabled=true;public static boolean isEnabled(int flag){return enabled;}}''',
    'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui;public class NebulaMenuStyle {public static boolean enabled=true;public static boolean animated(){return enabled;}}',
    'app/nebulagram/ui/NebulaGlass.java': 'package app.nebulagram.ui;public class NebulaGlass {public static boolean reduced;public static boolean reduced(){return reduced;}}',
    'Check.java': '''import android.view.View;import android.graphics.RenderEffect;import android.os.Build;
        import app.nebulagram.ui.*;import org.telegram.messenger.*;
        public class Check {static void check(boolean b,String why){if(!b)throw new AssertionError(why);}public static void main(String[] args){
            View view=new View();int samples=0;
            for(float density:new float[]{1,1.5f,2.75f}){
                AndroidUtilities.density=density;
                for(int step=1;step<=48;step++)NebulaMenuFocus.apply(view,step);
                int allocated=RenderEffect.created;
                for(int cycle=0;cycle<100;cycle++)for(int step=1;step<=48;step++){
                    NebulaMenuFocus.apply(view,step);check(Math.abs(view.effect.radius-density*step*2/3)<.001f,"cached world-space blur bucket");samples++;
                }
                check(RenderEffect.created==allocated,"no warmed frame allocations");
                NebulaMenuFocus.apply(view,100);check(view.effect.radius==32*density,"bounded blur when content is tiny");
                NebulaMenuFocus.apply(view,-1);check(view.effect==null,"negative step clears blur");
                NebulaMenuFocus.apply(view,12);NebulaMenuFocus.clear(view);check(view.effect==null,"detach/cancel clears blur");
                NebulaMenuBubble.Frame frame=new NebulaMenuBubble.Frame();
                for(int ms=0;ms<=750;ms++){
                    NebulaMenuBubble.opening(frame,ms/750f,240,400,216,-12,48,24);
                    float scale=NebulaMenuBubble.contentScale(frame,240,400,8,false);
                    int step=Math.min(48,Math.round(12*(1-frame.content)/scale));NebulaMenuFocus.apply(view,step);
                    if(step>0&&step<48)check(Math.abs(view.effect.radius*scale-density*8*(1-frame.content))<=density*scale/3+.001f,"focus radius stays outside the growing canvas scale");
                }
            }
            for(int mode=0;mode<4;mode++){
                NebulaMenuStyle.enabled=mode!=0;NebulaGlass.reduced=mode==1;view.hardware=mode!=2;LiteMode.enabled=mode!=3;
                NebulaMenuFocus.apply(view,48);check(view.effect==null,"fallback/reduced/software/disabled blur clears filter");
            }
            Build.VERSION.SDK_INT=30;int allocated=RenderEffect.created;NebulaMenuFocus.apply(view,48);NebulaMenuFocus.clear(view);
            check(RenderEffect.created==allocated,"legacy API does not create effects");
            System.out.println(samples+" warmed focus frames; scale compensation, blur bounds and reduced/software/API cleanup passed");
        }}''',
}
with tempfile.TemporaryDirectory(prefix='nebula-menu-focus-') as folder:
    work = Path(folder)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, *map(str, work.rglob('*.java')),
                    str(ui / 'NebulaMenuFocus.java'), str(ui / 'NebulaMenuBubble.java')], check=True)
    subprocess.run(['java', '-cp', folder, 'Check'], check=True)
