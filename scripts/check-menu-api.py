"""Typecheck production popup helpers against the installed Android SDK."""
from pathlib import Path
import os
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or
           str(Path.home() / 'AppData/Local/Android/Sdk'))
def version(path):
    return tuple(map(int, path.parent.name.removeprefix('android-').split('.')))

platforms = sorted((path for path in (sdk / 'platforms').glob('android-*/android.jar')
                    if re.fullmatch(r'android-\d+(?:\.\d+)*', path.parent.name)), key=version)
if not platforms or version(platforms[-1])[0] < 31:
    raise SystemExit('An Android SDK platform >= 31 is required for the popup API check')
stubs = {
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
public class AndroidUtilities { public static int dp(float x){return (int)x;}public static float dpf2(float x){return x;} }''',
    'org/telegram/messenger/LiteMode.java': '''package org.telegram.messenger;
public class LiteMode { public static int FLAG_CHAT_BLUR=1;public static boolean isEnabled(int x){return true;} }''',
    'org/telegram/ui/ActionBar/ActionBarPopupWindow.java': '''package org.telegram.ui.ActionBar;
public class ActionBarPopupWindow {public static class ActionBarPopupWindowLayout extends android.widget.FrameLayout {
 public boolean shownFromBottom;public ActionBarPopupWindowLayout(android.content.Context c){super(c);}
 public int getItemsCount(){return 0;}public android.view.View getItemAt(int i){return getChildAt(i);}
 public android.graphics.drawable.Drawable getBackgroundDrawable(){return getBackground();}}}''',
    'org/telegram/ui/ActionBar/ActionBarMenu.java': '''package org.telegram.ui.ActionBar;
public class ActionBarMenu extends android.widget.LinearLayout {public ActionBarMenu(android.content.Context c){super(c);}}''',
    'org/telegram/ui/ActionBar/ActionBarMenuItem.java': '''package org.telegram.ui.ActionBar;
public class ActionBarMenuItem extends android.widget.FrameLayout {public ActionBarMenuItem(android.content.Context c){super(c);}}''',
    'org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundColorProvider.java': '''package org.telegram.ui.Components.blur3.drawable.color;
public interface BlurredBackgroundColorProvider {}''',
    'org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java': '''package org.telegram.ui.Components.blur3.drawable;
public abstract class BlurredBackgroundDrawable extends android.graphics.drawable.Drawable {
 public BlurredBackgroundDrawable setRadius(float x){return this;}public BlurredBackgroundDrawable setPadding(int x){return this;}
 public BlurredBackgroundDrawable setColorProvider(org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider p){return this;}
 public void setSourceOffset(float x,float y){}public float getSourceOffsetX(){return 0;}public float getSourceOffsetY(){return 0;}
 public void setThickness(float x){}public void setIntensity(float x){}public void updateColors(){} }''',
    'org/telegram/ui/Components/blur3/BlurredBackgroundDrawableViewFactory.java': '''package org.telegram.ui.Components.blur3;
public class BlurredBackgroundDrawableViewFactory {public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable create(){return null;}}''',
    'androidx/dynamicanimation/animation/FloatPropertyCompat.java': '''package androidx.dynamicanimation.animation;
public abstract class FloatPropertyCompat<T> {public FloatPropertyCompat(String name){}public abstract float getValue(T x);public abstract void setValue(T x,float v);}''',
    'androidx/dynamicanimation/animation/SpringAnimation.java': '''package androidx.dynamicanimation.animation;
public class SpringAnimation {public <T> SpringAnimation(T x,FloatPropertyCompat<T> p){}
 public SpringAnimation setSpring(SpringForce force){return this;}public void animateToFinalPosition(float x){}public void cancel(){} }''',
    'androidx/dynamicanimation/animation/SpringForce.java': '''package androidx.dynamicanimation.animation;
public class SpringForce {public SpringForce(float v){}public SpringForce setStiffness(float x){return this;}public SpringForce setDampingRatio(float x){return this;} }''',
    'app/nebulagram/ui/NebulaMenuStyle.java': '''package app.nebulagram.ui;
public class NebulaMenuStyle {public static boolean animated(){return true;}public static float radius(){return 24;} }''',
    'app/nebulagram/ui/NebulaGlass.java': '''package app.nebulagram.ui;
public class NebulaGlass {public static float refraction(){return 1;}public static boolean reduced(){return false;} }''',
    'app/nebulagram/ui/NebulaHaptics.java': '''package app.nebulagram.ui;
public class NebulaHaptics {public static void tick(android.view.View v){} }''',
}
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
with tempfile.TemporaryDirectory(prefix='nebula-menu-api-') as folder:
    work = Path(folder)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    production = ['NebulaMenuReveal.java', 'NebulaMenuMotion.java', 'NebulaMenuBubble.java',
                  'NebulaMenuFocus.java', 'NebulaSelectionGlass.java']
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(platforms[-1]), '-d', folder,
                    *map(str, work.rglob('*.java')), *[str(ui / name) for name in production]], check=True)
print('Production popup drawing, focus and touch helpers typechecked against ' + platforms[-1].parent.name)
