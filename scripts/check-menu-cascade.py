"""Cascade in glass menus: run the stagger curve of NebulaMenuCascade, then check how NebulaMenuStyle.opening drives it."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaMenuCascade.java').read_text(encoding='utf-8')
start = source.index('    static float item(')
item = source[start:source.index('\n    }\n', start) + 7]
constants = '\n'.join(l for l in source.splitlines() if l.strip().startswith(('static final float SPAN', 'static final float WAVE')))

stubs = {
'Curve.java': 'public class Curve {\n' + constants + '\n' + item + '\n}',
'Check.java': r'''public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  for(int count:new int[]{1,2,5,12}){
   for(int i=0;i<count;i++){
    check(Curve.item(0f,i,count)==0f,"hidden at start: "+i+"/"+count);
    check(Curve.item(Curve.SPAN,i,count)==1f&&Curve.item(1f,i,count)==1f,"in place once the items' part of the morph is over: "+i+"/"+count);
    float prev=-1;for(float t=0;t<=1.0001f;t+=.02f){float v=Curve.item(t,i,count);check(v>=prev-1e-6&&v>=0&&v<=1,"monotonic and clamped");prev=v;}
   }
   // A wave: an earlier item is never behind a later one.
   for(float t=0;t<=1;t+=.05f)for(int i=1;i<count;i++)check(Curve.item(t,i-1,count)>=Curve.item(t,i,count)-1e-6,"earlier first");
  }
  check(Curve.item(.2f,0,6)>Curve.item(.2f,5,6),"the first item leads mid-way");
  System.out.println("Menu cascade: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-menu-cascade-') as work:
    work = Path(work)
    for name, text in stubs.items():
        (work / name).write_bytes(text.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

assert 'KEY = "menu_cascade"' in source and 'NebulaFeatureSettings.enabled(KEY)' in source
assert 'ScaleStateListAnimator.apply(child, .04f, 1.4f)' in source and 'instanceof ActionBarPopupWindow.GapView' in source
settings = (ui / 'NebulaFeatureSettings.java').read_text(encoding='utf-8')
assert '"menu_cascade"' not in settings  # not in the default-on list: off by default
style = (ui / 'NebulaMenuStyle.java').read_text(encoding='utf-8')
opening = style[style.index('public static AnimatorSet opening('):style.index('public static AnimatorSet closing(')]
for hook in ['final boolean cascade = NebulaMenuCascade.enabled();', 'if (cascade) NebulaMenuCascade.prepare(content);',
             'if (cascade) NebulaMenuCascade.step(content, t);', 'onAnimationCancel(Animator animation) { cancelled = true; if (cascade) NebulaMenuCascade.settle(content); }',
             'if (cascade) NebulaMenuCascade.settle(content);']:
    assert hook in opening, hook
assert opening.index('content.nebulaReveal.begin();') < opening.index('NebulaMenuCascade.prepare(content);')
controls = (ui / 'NebulaFeatureControls.java').read_text(encoding='utf-8')
assert 'toggle(parent, NebulaMenuCascade.KEY, "Каскад в стеклянных меню", "Cascade in glass menus"' in controls
print('Menu cascade: curve, opening hooks and setting verified')
