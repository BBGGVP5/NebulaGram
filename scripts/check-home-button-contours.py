"""Execute native material providers/builders across live theme and blur changes."""
from pathlib import Path
import subprocess
import sys
import tempfile

tree=Path(sys.argv[1]);support=Path(sys.argv[2]) if len(sys.argv)>2 else tree
base='TMessagesProj/src/main/java/'
package='org/telegram/ui/Components/blur3/drawable/color/'
provider=(tree/base/package/'impl/BlurredBackgroundProviderImpl.java').read_text(encoding='utf-8')
dialogs=(tree/base/'org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')

def method(source,signature):
    start=source.index(signature);end=source.index('{',start)+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]

methods='\n'.join(method(provider,s) for s in [
    'public static BlurredBackgroundProvider topPanel(Theme.ResourcesProvider resourcesProvider)',
    'public static BlurredBackgroundProvider topPanelButtons(',
    'private static BlurredBackgroundProvider topPanel(',
    'public static int solveSrcColor('
])
files={
 'androidx/annotation/ColorInt.java':'package androidx.annotation; public @interface ColorInt {}',
 'androidx/annotation/Px.java':'package androidx.annotation; public @interface Px {}',
 'androidx/core/math/MathUtils.java':'''package androidx.core.math; public class MathUtils {
 public static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
 public static int clamp(int x,int lo,int hi){return Math.max(lo,Math.min(hi,x));}}''',
 'android/graphics/Color.java':'''package android.graphics; public class Color {
 public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}
 public static int red(int c){return (c>>>16)&255;}public static int green(int c){return (c>>>8)&255;}
 public static int blue(int c){return c&255;}}''',
 'org/telegram/messenger/AndroidUtilities.java':'''package org.telegram.messenger; public class AndroidUtilities {
 public static float density=1;public static float dpf2(float n){return n*density;}}''',
 'org/telegram/messenger/LiteMode.java':'''package org.telegram.messenger; public class LiteMode {
 public static final int FLAG_LIQUID_GLASS=1;public static boolean enabled;public static boolean isEnabled(int flag){return enabled;}}''',
 'org/telegram/ui/ActionBar/Theme.java':'''package org.telegram.ui.ActionBar;public class Theme {
 public static boolean dark;public interface ResourcesProvider {boolean isDark();}
 public static final int key_windowBackgroundWhite=1,key_glass_targetMainTopPanel=2;
 public static boolean isCurrentThemeDark(){return dark;}
 public static int getColor(int key,ResourcesProvider r){boolean d=r==null?dark:r.isDark();return key==1?(d?0xff10131a:0xfff6f6f9):(d?0xff262a35:0xfffafafa);}}''',
 'org/telegram/ui/Stories/DarkThemeResourceProvider.java':'''package org.telegram.ui.Stories;
 public class DarkThemeResourceProvider implements org.telegram.ui.ActionBar.Theme.ResourcesProvider {public boolean isDark(){return true;}}''',
 package+'impl/BlurredBackgroundProviderImpl.java':'''package org.telegram.ui.Components.blur3.drawable.color.impl;
 import static org.telegram.messenger.AndroidUtilities.dpf2;
 import android.graphics.Color;import androidx.core.math.MathUtils;
 import org.telegram.messenger.LiteMode;import org.telegram.ui.ActionBar.Theme;
 import org.telegram.ui.Components.blur3.drawable.color.*;
 public class BlurredBackgroundProviderImpl {'''+methods+'}',
 'ContoursCheck.java':'''import org.telegram.ui.Components.blur3.drawable.color.*;
 import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
 import org.telegram.ui.ActionBar.Theme;import org.telegram.messenger.LiteMode;
 class ContoursCheck {
 static class LiveTheme implements Theme.ResourcesProvider {boolean dark;public boolean isDark(){return dark;}}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
 for(float density:new float[]{1,1.5f,2,2.625f,3.5f})for(boolean global:new boolean[]{false,true}) {
  org.telegram.messenger.AndroidUtilities.density=density;LiveTheme theme=new LiveTheme();
  Theme.ResourcesProvider r=global?null:theme;
  BlurredBackgroundProvider buttons=BlurredBackgroundProviderImpl.topPanelButtons(r),ordinary=BlurredBackgroundProviderImpl.topPanel(r);
  for(int frame=0;frame<100;frame++){
   theme.dark=Theme.dark=(frame%2==0);LiteMode.enabled=(frame%3==0);
   check(buttons.getStrokeColorTop()==0 && buttons.getStrokeColorBottom()==0 && buttons.getShadowColor()==0,"Theme/blur restores a home contour");
   check(buttons.getStrokeWidthTop()==0 && buttons.getStrokeWidthBottom()==0,"Hardware hairline width restored");
   check(buttons.getBackgroundColor()==ordinary.getBackgroundColor(),"Blur/tint fill changed");
   check(ordinary.getStrokeColorTop()==(theme.dark?0x06FFFFFF:0x11000000),"Other header outline changed");
   check(ordinary.getShadowColor()==(theme.dark?0x04FFFFFF:0x20000000),"Other header shadow changed");
   check(ordinary.getStrokeWidthTop()>0,"Other header width changed");cases++;
  }
 }
 System.out.println(cases+" native provider/builder states: zero home strokes and shadows, unchanged fill and other headers");
 }}''',
}
for name in ['BlurredBackgroundProviderBuilder.java','BlurredBackgroundProvider.java','BlurredBackgroundColorProvider.java']:
    files[package+name]=(support/base/package/name).read_text(encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='nebula-home-contours-') as directory:
    work=Path(directory)
    for name,source in files.items():
        file=work/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work/name) for name in files]],check=True)
    subprocess.run(['java','-cp',str(work),'ContoursCheck'],check=True)

assert 'actionBar.setupGlass(app.nebulagram.ui.NebulaFeatureSettings.materialHeader() ? iBlur3FactoryBlur : iBlur3FactoryLiquidGlass, BlurredBackgroundProviderImpl.topPanelButtons(resourceProvider))' in dialogs
print('Home setup passes the contour-free provider to all header materials')
