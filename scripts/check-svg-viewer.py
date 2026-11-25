"""SVG preview: run the detection and page builder of NebulaSvgViewer, then check routing, sandboxing and the setting."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

stubs = {
'android/app/Activity.java': 'package android.app; public class Activity {}',
'org/telegram/ui/ActionBar/BaseFragment.java': 'package org.telegram.ui.ActionBar; public class BaseFragment {public boolean presentFragment(BaseFragment f){return true;}}',
'org/telegram/ui/LaunchActivity.java': 'package org.telegram.ui; public class LaunchActivity {public static org.telegram.ui.ActionBar.BaseFragment getSafeLastFragment(){return null;}}',
'app/nebulagram/ui/NebulaMediaPreferences.java': 'package app.nebulagram.ui; public final class NebulaMediaPreferences {public static boolean on;public static boolean svgViewer(){return on;}}',
'app/nebulagram/ui/NebulaSvgViewerFragment.java': 'package app.nebulagram.ui; public final class NebulaSvgViewerFragment extends org.telegram.ui.ActionBar.BaseFragment {public NebulaSvgViewerFragment(java.io.File f,String t,boolean r){}}',
'Check.java': r'''import app.nebulagram.ui.*; import java.lang.reflect.*;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 static Object call(String name,Class<?>[] types,Object... args)throws Exception{Method m=NebulaSvgViewer.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);}
 static boolean looks(String s)throws Exception{return (Boolean)call("looksLikeSvg",new Class<?>[]{String.class},s);}
 static String page(String s,boolean board)throws Exception{return (String)call("page",new Class<?>[]{String.class,int.class,boolean.class},s,0xFF112233,board);}
 public static void main(String[] a)throws Exception{
  check(NebulaSvgViewer.isSvg("logo.SVG",null)&&NebulaSvgViewer.isSvg(null,"image/svg+xml")&&!NebulaSvgViewer.isSvg("a.png","image/png")&&!NebulaSvgViewer.isSvg("a.svgz",null),"name and mime");
  for(String ok:new String[]{"<svg xmlns=\"http://www.w3.org/2000/svg\"/>","\uFEFF<?xml version=\"1.0\"?>\n<svg>","  <!-- by me -->\n<svg viewBox=\"0 0 1 1\">","<?xml version=\"1.0\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"x\"><svg width=\"1\">","<SVG>"})
   check(looks(ok),"svg: "+ok);
  for(String bad:new String[]{"","%PDF-1.7","<html><svg></svg></html>","<svgfoo>","hello <svg>","\u001f\u008b\u0008","<?xml version=\"1.0\"?><note/>",null})
   check(!looks(bad),"not svg: "+bad);
  // Off by default: the file goes to Telegram's usual path.
  java.io.File f=java.io.File.createTempFile("nebula",".svg");java.nio.file.Files.write(f.toPath(),"<svg/>".getBytes());f.deleteOnExit();
  NebulaMediaPreferences.on=false;check(!NebulaSvgViewer.open(f,"a.svg",null,new android.app.Activity(),false),"setting off");
  NebulaMediaPreferences.on=true;check(!NebulaSvgViewer.open(f,"a.svg",null,new android.app.Activity(),false),"no host fragment");
  check(!NebulaSvgViewer.open(f,"a.png","image/png",new android.app.Activity(),false),"png name stays with Telegram");
  // The page: prolog removed, theme background, strict CSP, scaling only with a viewBox.
  String p=page("<?xml version=\"1.0\"?><svg viewBox=\"0 0 10 10\"><circle r=\"5\"/></svg>",false);
  check(!p.contains("<?xml")&&p.contains("<svg viewBox")&&p.contains("background:#112233")&&p.contains(".f>svg{width:100vw;height:100vh;}"),"page with viewBox");
  check(p.contains("default-src 'none'")&&!p.contains("script-src"),"no script source allowed");
  String fixed=page("<svg width=\"40\" height=\"20\"/>",true);
  check(fixed.contains("max-width:100vw")&&!fixed.contains("width:100vw;height:100vh")&&fixed.contains("linear-gradient(45deg"),"fixed-size drawing and checkerboard");
  System.out.println("SVG viewer: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-svg-') as work:
    work = Path(work)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), str(ui / 'NebulaSvgViewer.java')], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

fragment = (ui / 'NebulaSvgViewerFragment.java').read_text(encoding='utf-8')
for sandbox in ['setJavaScriptEnabled(false)', 'setAllowFileAccess(false)', 'setAllowContentAccess(false)', 'setBlockNetworkLoads(true)',
                'shouldInterceptRequest', 'new ByteArrayInputStream(new byte[0])', 'shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }',
                'loadDataWithBaseURL("about:blank"', 'FLAG_SECURE', 'web.destroy()']:
    assert sandbox in fragment, sandbox
assert fragment.index('if (!restricted) {') < fragment.index('menu.addItem(SHARE')
pdf = (ui / 'NebulaPdfViewer.java').read_text(encoding='utf-8')
assert pdf.index('NebulaSvgViewer.open(file, fileName, mimeType, activity, restricted)') < pdf.index('NebulaMediaPreferences.pdfViewer()')
assert 'getBoolean("svg_viewer", false)' in (ui / 'NebulaMediaPreferences.java').read_text(encoding='utf-8')
screen = (ui / 'NebulaMediaFeaturesFragment.java').read_text(encoding='utf-8')
assert 't("Просмотр SVG", "SVG preview")' in screen and '"svg_viewer", NebulaMediaPreferences.svgViewer()' in screen
if len(sys.argv) > 1:
    utils = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/messenger/AndroidUtilities.java').read_text(encoding='utf-8')
    assert utils.count('app.nebulagram.ui.NebulaPdfViewer.open(') == 3, 'the three document entry points route through the viewer'
print('SVG viewer: sandbox, routing and setting verified')
