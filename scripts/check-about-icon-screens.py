"""Guard the "About" and "App icon" screens and execute their palette math.

The screens are plain overlay views, so most of the contract is wiring: where
they open from, that the icon switch stays Telegram's LauncherIconController,
that every link points at something that exists, and that every label has a
Russian and an English text. The palette code is compiled and run against the
real launcher backgrounds: each icon must produce a readable Apply button and
a backdrop that is darker at the top than its glow.
"""
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
RES = ROOT / 'platform/android/overlay/TMessagesProj/src/main/res'
TREE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'vendor/telegram-android'


def read(name):
    return (UI / name).read_text(encoding='utf-8')


def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


about = read('NebulaAboutFragment.java')
picker = read('NebulaIconPickerFragment.java')
palette = read('NebulaBrandPalette.java')
backdrop = read('NebulaBrandBackdrop.java')
art = read('NebulaIconArt.java')
ui = read('NebulaBrandUi.java')
home = read('NebulaSettingsFragment.java')
search = read('NebulaSettingsSearch.java')

# --- where the screens open from ----------------------------------------------
sections = method(home, 'private void buildSections(')
assert 'presentFragment(new NebulaAboutFragment())' in sections, 'Settings "About" row must open the new screen'
assert 'new NebulaSectionFragment(NebulaSectionFragment.SECTION_ABOUT)' not in sections
assert 'else if (section == -63) f.presentFragment(new NebulaAboutFragment());' in search
assert 'new Entry(-63, NebulaText.text("О программе", "About")' in search
for opener in ['NebulaExtras.java', 'NebulaSettingsHubFragment.java']:
    assert 'new NebulaIconPickerFragment()' in read(opener), opener
# The detailed build card is still reachable, one row away.
assert 'new NebulaSectionFragment(NebulaSectionFragment.SECTION_ABOUT)' in about
assert 'NebulaUpdateSettingsSheet.show(this)' in about
assert 'new NebulaSupportFragment()' in about and 'new NebulaIconPickerFragment()' in about

# --- layout pieces from the reference -----------------------------------------
create = method(about, 'public View createView(')
order = ['new NebulaIconArt(', '"NebulaGram"', 'chip(context, versionName(context))',
         '"Telegram " + org.telegram.messenger.BuildVars.BUILD_VERSION_STRING', 'chip(context, channel(context))',
         'new NebulaUpdatesButton(', 'text("Сообщество", "Community")', 'text("Проект", "Project")',
         'text("Исходный код", "Source code")', 'footer.setText(', 'pill(context,', 'NebulaBrandUi.topBar(']
positions = [create.index(piece) for piece in order]
assert positions == sorted(positions), 'About screen pieces are out of order'
assert create.count('pill(context,') == 2
assert 'actionBar.setAddToContainer(false)' in create and 'actionBar.setVisibility(View.GONE)' in create
channel = method(about, 'static String channel(')
assert '"Dev"' in channel and '"Beta"' in channel and '"Release"' in channel and 'BuildConfig.DEBUG' in channel
resume = method(about, 'public void onResume(')
assert 'NebulaBrandPalette.current()' in resume and 'applyPalette(' in resume and 'art.setIcon(current)' in resume
assert 'backdrop.animateColors(palette.top, palette.glow)' in about
assert 'setElevation(' in create and 'setClipToOutline(true)' in art
assert 'shape.setShape(GradientDrawable.OVAL)' in ui and 'R.drawable.ic_ab_back' in ui

# --- icon picker ------------------------------------------------------------------
assert 'new LinearSnapHelper().attachToRecyclerView(this)' in picker
assert 'LinearLayoutManager.HORIZONTAL' in picker and picker.count('extends RecyclerView.Adapter') == 2
assert 'SIDE_SCALE' in method(picker, 'void transformChildren(') and 'SIDE_ALPHA' in method(picker, 'void transformChildren(')
assert 'NebulaLauncherIconLabels.title(name.getContext(), icons[selected])' in picker
transform = method(picker, 'void transformChildren(')
assert 'badge.setAlpha(index == applied ? 1f - distance : 0f)' in transform, 'The icon in use carries a check'
assert 'carousel.transformChildren()' in method(picker, 'private void applySelected(')
assert picker.count('LauncherIconController.setIcon(') == 1
assert 'LauncherIconController.setIcon(icons[selected])' in method(picker, 'private void applySelected(')
follow = method(picker, 'private void follow(')
assert 'NebulaBrandPalette.blend(a.top, b.top, t)' in follow and 'Math.round(position)' in follow
assert 'centerStrip(selected)' in follow and 'refreshApplyColor()' in follow
update = method(picker, 'private void updateSelection(')
assert 'apply.setEnabled(!inUse)' in update and 'уже используется' in update and 'Применить' in update
swipe = method(picker, 'public boolean isSwipeBackEnabled(')
assert 'inside(carousel, event)' in swipe and 'inside(strip, event)' in swipe
size = method(picker, 'protected void onSizeChanged(')
assert 'post(() ->' in size and size.index('post(() ->') < size.index('notifyDataSetChanged')
for source in [about, picker]:
    assert 'public boolean isLightStatusBar() { return false; }' in source
    assert 'public int getNavigationBarColor() { return NebulaBrandPalette.FLOOR; }' in source
assert 'private static final float BLEED = 0.25f;' in art, 'Adaptive icons show the middle 72 of 108dp'

# --- texts and links ---------------------------------------------------------------
cyrillic = re.compile('[а-яё]', re.I)
pairs = 0
for name, source in [('about', about), ('picker', picker), ('ui', ui)]:
    for ru, en in re.findall(r'text\("((?:[^"\\]|\\.)*)",\s*"((?:[^"\\]|\\.)*)"\)', source):
        pairs += 1
        assert ru and en and cyrillic.search(ru) and not cyrillic.search(en), (name, ru, en)
    assert 'cherry' not in source.lower(), name + ' must not carry another client\'s brand'
assert pairs >= 18, pairs
urls = set(re.findall(r'"(https://[^"]*)"', about))
assert urls == {'https://github.com/BBGGVP5/NebulaGram', 'https://t.me/', 'https://github.com/DrKLO/Telegram'}, urls
assert 'REPOSITORY + "/issues"' in about and 'REPOSITORY + "/blob/main/LICENSE"' in about
assert (ROOT / 'LICENSE').exists()
for handle in ['NEWS', 'NebulaRelease.CHANNEL', 'NebulaCommunityCard.SOURCE_USERNAME']:
    assert f'"https://t.me/" + {handle}' in about, handle
assert 'static final String NEWS = "ngram_official";' in about and '"https://t.me/ngram_official"' in home

# Every drawable the new screens use must exist in Telegram or the overlay.
drawables = set()
for source in [about, picker, ui]:
    drawables.update(re.findall(r'R\.drawable\.(\w+)', source))
res_roots = [RES] + ([TREE / 'TMessagesProj/src/main/res'] if TREE.exists() else [])
for name in drawables:
    found = any(any(root.glob(f'drawable*/{name}.*')) for root in res_roots)
    assert found or not TREE.exists(), 'Missing drawable ' + name

# --- palette math, compiled and executed ----------------------------------------
backgrounds = {}
for xml in sorted(RES.glob('drawable/nebula_launcher_*_background.xml')):
    colors = [c for c in re.findall(r'Color="#([0-9A-Fa-f]{6,8})"', xml.read_text(encoding='utf-8')) if len(c) == 6][:2]
    backgrounds[xml.name[len('nebula_launcher_'):-len('_background.xml')]] = colors
assert len(backgrounds) == 16, sorted(backgrounds)

color_stub = '''package android.graphics;
public class Color {
 public static int alpha(int c){return c>>>24;} public static int red(int c){return (c>>16)&255;}
 public static int green(int c){return (c>>8)&255;} public static int blue(int c){return c&255;}
 public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}
 public static void RGBToHSV(int r,int g,int b,float[] hsv){float rf=r/255f,gf=g/255f,bf=b/255f;float max=Math.max(rf,Math.max(gf,bf)),min=Math.min(rf,Math.min(gf,bf)),d=max-min;float h=0;
  if(d!=0){if(max==rf)h=((gf-bf)/d)%6;else if(max==gf)h=(bf-rf)/d+2;else h=(rf-gf)/d+4;h*=60;if(h<0)h+=360;}
  hsv[0]=h;hsv[1]=max==0?0:d/max;hsv[2]=max;}
 public static void colorToHSV(int c,float[] hsv){RGBToHSV(red(c),green(c),blue(c),hsv);}
 public static int HSVToColor(float[] hsv){float h=hsv[0],s=hsv[1],v=hsv[2];float c=v*s,x=c*(1-Math.abs((h/60f)%2-1)),m=v-c;float r,g,b;
  if(h<60){r=c;g=x;b=0;}else if(h<120){r=x;g=c;b=0;}else if(h<180){r=0;g=c;b=x;}else if(h<240){r=0;g=x;b=c;}else if(h<300){r=x;g=0;b=c;}else{r=c;g=0;b=x;}
  return argb(255,Math.round((r+m)*255),Math.round((g+m)*255),Math.round((b+m)*255));}
}'''
body = ''.join(method(palette, signature) for signature in [
    'static NebulaBrandPalette fromColor(', 'private static float clamp(', 'public static int blend(', 'public static int withAlpha('])
harness_palette = ('package app.nebulagram.ui; import android.graphics.Color; public final class NebulaBrandPalette {'
                   'public static final int FLOOR = 0xFF060608; public final int top, glow, accent;'
                   'private NebulaBrandPalette(int top, int glow, int accent){this.top=top;this.glow=glow;this.accent=accent;}'
                   + body + '}')
cases = ';'.join(f'check("{key}",0x{a},0x{b})' for key, (a, b) in backgrounds.items())
harness = '''package app.nebulagram.ui; import android.graphics.Color;
public class PaletteCheck {
 static double lin(int c){double s=c/255.0;return s<=0.03928?s/12.92:Math.pow((s+0.055)/1.055,2.4);}
 static double lum(int c){return 0.2126*lin(Color.red(c))+0.7152*lin(Color.green(c))+0.0722*lin(Color.blue(c));}
 static double contrast(int a,int b){double x=lum(a),y=lum(b);return (Math.max(x,y)+0.05)/(Math.min(x,y)+0.05);}
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static java.util.Set<Integer> accents=new java.util.HashSet<>();
 static void check(String key,int a,int b){
  int avg=NebulaBrandPalette.blend(0xFF000000|a,0xFF000000|b,0.5f);float[] hsv=new float[3];Color.colorToHSV(avg,hsv);
  NebulaBrandPalette p=NebulaBrandPalette.fromColor(hsv);
  ok(Color.alpha(p.top)==255&&Color.alpha(p.glow)==255&&Color.alpha(p.accent)==255,key+" opaque");
  ok(contrast(0xFFFFFFFF,p.accent)>=3.0,key+" Apply text contrast "+contrast(0xFFFFFFFF,p.accent));
  ok(contrast(0xFFFFFFFF,p.top)>=7.0,key+" title contrast on the backdrop "+contrast(0xFFFFFFFF,p.top));
  ok(lum(p.top)<lum(p.glow),key+" glow must be brighter than the top");
  ok(lum(p.top)>lum(NebulaBrandPalette.FLOOR),key+" top must stand out from the floor");
  float[] t=new float[3];Color.colorToHSV(p.accent,t);
  if(hsv[1]<0.14f)ok(t[1]<=0.12f,key+" neutral icon gained a hue");
  else ok(Math.abs(t[0]-hsv[0])<1.5f||Math.abs(t[0]-hsv[0])>358.5f,key+" accent hue drifted");
  accents.add(p.accent);
 }
 public static void main(String[] a){
  ok(NebulaBrandPalette.blend(0xFF000000,0xFFFFFFFF,0f)==0xFF000000&&NebulaBrandPalette.blend(0xFF000000,0xFFFFFFFF,1f)==0xFFFFFFFF,"blend ends");
  ok(NebulaBrandPalette.blend(0xFF000000,0xFFFFFFFF,2f)==0xFFFFFFFF&&NebulaBrandPalette.blend(0xFF102030,0xFF000000,-1f)==0xFF102030,"blend clamps");
  ok(NebulaBrandPalette.withAlpha(0xFF123456,0f)==0x00123456&&NebulaBrandPalette.withAlpha(0x00123456,1f)==0xFF123456,"alpha");
  CASES;
  ok(accents.size()>=12,"icons must look different: "+accents.size());
  System.out.println("PASS: "+accents.size()+" distinct accents, readable Apply text and backdrops for 16 icons");
 }
}'''.replace('CASES', cases)

with tempfile.TemporaryDirectory(prefix='nebula-about-') as tmp:
    work = Path(tmp)
    files = {'android/graphics/Color.java': color_stub,
             'app/nebulagram/ui/NebulaBrandPalette.java': harness_palette,
             'app/nebulagram/ui/PaletteCheck.java': harness}
    for name, content in files.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    javac = shutil.which('javac')
    if not javac:
        candidates = list((Path.home() / '.gradle/jdks').glob('*/bin/javac.exe')) + list(Path('C:/Program Files/Java').glob('*/bin/javac.exe'))
        assert candidates, 'JDK required for the palette regression'
        javac = str(candidates[0])
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work)] + [str(work / f) for f in files], check=True)
    java = str(Path(javac).with_name('java.exe' if javac.endswith('.exe') else 'java'))
    subprocess.run([java, '-cp', str(work), 'app.nebulagram.ui.PaletteCheck'], check=True)

print(f'PASS: About and App icon screens: settings and search routes, reference layout order, '
      f'{pairs} RU/EN texts, real links only, carousel/strip/Apply wiring through LauncherIconController')
