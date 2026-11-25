"""Progressive header blur: run the style numbers of NebulaProgressiveBlur, then check the drawing path and the 0381 hook."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaProgressiveBlur.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    depth, i = 0, source.index('{', start)
    while True:
        if source[i] == '{': depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0: return source[start:i + 1]
        i += 1

pure = '\n'.join(['public static final int OFF = 0, SOFT = 1, STRONG = 2;', method('static float radiusDp('), method('static float bandDp('), method('static float strength(')])
check = r'''public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  check(Rules.radiusDp(Rules.OFF)==0&&Rules.bandDp(Rules.OFF)==0,"off draws nothing");
  check(Rules.radiusDp(Rules.SOFT)>0&&Rules.radiusDp(Rules.STRONG)>Rules.radiusDp(Rules.SOFT),"strong blurs more");
  check(Rules.bandDp(Rules.STRONG)>Rules.bandDp(Rules.SOFT),"strong reaches further");
  check(Rules.strength(0,40)==1f&&Rules.strength(40,40)==0f&&Rules.strength(80,40)==0f&&Rules.strength(5,0)==0f,"full at the edge, none past the band");
  float prev=2;for(float y=0;y<=40;y+=1){float v=Rules.strength(y,40);check(v<=prev,"progressive: never stronger further down");prev=v;}
  check(Rules.strength(20,40)<.5f,"ease-out: half way is less than half");
  System.out.println("Header blur: "+cases+" cases passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-header-blur-') as work:
    work = Path(work)
    (work / 'Rules.java').write_bytes(('public class Rules {\n' + pure + '\n}').encode('utf-8'))
    (work / 'Check.java').write_bytes(check.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), str(work / 'Rules.java'), str(work / 'Check.java')], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

for part in ['KEY = "header_blur_style"', 'getInt(KEY, OFF)', 'Build.VERSION.SDK_INT >= 31', '!canvas.isHardwareAccelerated() || style() == OFF',
             'android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP)', 'PorterDuff.Mode.DST_IN', 'canvas.restoreToCount(save);',
             'node.endRecording();\n        canvas.drawRenderNode(node);']:
    assert part in source, part
# The effect is only rebuilt when the radius changes, not every frame.
assert source.index('if (r != radius) {') < source.index('blur.setRenderEffect(')
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'if (NebulaProgressiveBlur.supported()) {' in section and 'NebulaText.text("Размытие под шапкой", "Header blur")' in section
assert '0381-chat-header-progressive-blur.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    chat = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
    hook = chat.index('private final app.nebulagram.ui.NebulaProgressiveBlur nebulaHeaderBlur = new app.nebulagram.ui.NebulaProgressiveBlur();')
    assert chat.index('chatListView = new ChatListRecyclerView(context, themeDelegate) {') < hook
    body = chat[hook:hook + 900]
    for part in ['Canvas recording = isInPreviewMode() ? null : nebulaHeaderBlur.begin(this, canvas);', 'super.dispatchDraw(canvas);\n                    return;',
                 'super.dispatchDraw(recording);', 'nebulaHeaderBlur.end(this, canvas, chatListViewPaddingTop);']:
        assert part in body, part
    assert 'public float chatListViewPaddingTop;' in chat
print('Header blur: drawing path, setting and 0381 hook verified')
