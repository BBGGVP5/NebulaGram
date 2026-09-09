"""Execute the real header text draw branch against a recording Canvas (not raster QA)."""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--tree', type=Path, default=ROOT / 'build/final-verify-0904/tree')
args = parser.parse_args()
source = (args.tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatAvatarContainer.java').read_text(encoding='utf-8')
start = source.index('        if (nebulaCenteredTitle', source.index('protected boolean drawChild('))
end = source.index('        if (child == avatarImageView)', start)
branch = source[start:end].replace('app.nebulagram.ui.NebulaAppearance.chatHeader()', 'floating')
program = '''
import java.util.concurrent.atomic.AtomicReference;
class View { float x; int width; View(float x,int w){this.x=x;width=w;} float getX(){return x;} int getWidth(){return width;} }
class Canvas { int saves,clips,scales; float left,right; boolean restored;
 int save(){saves++;return saves;} void clipRect(float l,float t,float r,float b){clips++;left=l;right=r;}
 void scale(float x,float y,float px,float py){scales++;} void restoreToCount(int n){restored=true;} }
class ActionBar {static int getCurrentActionBarHeight(){return 56;}}
class Bounce {float getScale(float a){return 0.98f;}}
class Base {boolean drawChild(Canvas c,View v,long time){return true;}}
public class CheckHeaderClip extends Base {
 boolean nebulaCenteredTitle=true,floating;
 View titleTextView=new View(86,210),subtitleTextView=new View(86,200),animatedSubtitleTextView=new View(86,190);
 AtomicReference<View> titleTextLargerCopyView=new AtomicReference<>(new View(86,500));
 AtomicReference<View> subtitleTextLargerCopyView=new AtomicReference<>(new View(86,450));
 Bounce bounce=new Bounce(); int getWidth(){return 380;} int getHeight(){return 80;}
 View getSubtitleTextView(){return subtitleTextView;}
 public boolean drawChild(Canvas canvas,View child,long drawingTime){
''' + branch + '''return super.drawChild(canvas,child,drawingTime);}
 void check(View child,View bounds,boolean enabled) {
  Canvas c=new Canvas(); if(!drawChild(c,child,0))throw new AssertionError("draw result lost");
  if(enabled && (c.clips!=1 || c.left!=bounds.getX() || c.right!=bounds.getX()+bounds.getWidth() || !c.restored))
   throw new AssertionError("header text/copy escaped its measured viewport; floating="+floating);
  if(!enabled && c.clips!=0)throw new AssertionError("clipped unrelated content");
  if(c.scales!=(enabled&&floating?1:0))throw new AssertionError("bounce changed");
 }
 public static void main(String[] args){CheckHeaderClip h=new CheckHeaderClip();
  for(boolean f:new boolean[]{false,true}){h.floating=f;
   h.titleTextView.x=86;h.titleTextView.width=210;
   h.check(h.titleTextView,h.titleTextView,true);h.check(h.subtitleTextView,h.subtitleTextView,true);
   h.check(h.animatedSubtitleTextView,h.subtitleTextView,true);
   h.check(h.titleTextLargerCopyView.get(),h.titleTextView,true);
   h.check(h.subtitleTextLargerCopyView.get(),h.subtitleTextView,true);
   h.check(new View(2,40),null,false);
   h.titleTextView.x=93.5f;h.titleTextView.width=0;h.check(h.titleTextView,h.titleTextView,true);
  }
  h.nebulaCenteredTitle=false;h.check(h.titleTextView,null,false);
  System.out.println("15 header clipping cases passed (classic/floating, copies, translation, zero width, opt-out)");
 }
}
'''
javac = shutil.which('javac')
if not javac:
    candidates = list((Path.home() / '.gradle/jdks').glob('*/bin/javac.exe'))
    if not candidates: raise SystemExit('JDK required')
    javac = str(candidates[0])
java = str(Path(javac).with_name('java.exe' if javac.endswith('.exe') else 'java'))
with tempfile.TemporaryDirectory(prefix='nebula-header-clip-') as work:
    file = Path(work) / 'CheckHeaderClip.java'
    file.write_text(program, encoding='utf-8')
    subprocess.run([javac, '-encoding', 'UTF-8', str(file)], check=True)
    subprocess.run([java, '-cp', work, 'CheckHeaderClip'], check=True)
