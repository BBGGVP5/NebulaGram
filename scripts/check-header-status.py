"""Execute native subtitle configuration/setter and single-line layout policy.

Uses deterministic text metrics, not Android font rendering or raster QA.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile


def block(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--tree', required=True, type=Path)
args = parser.parse_args()
native = args.tree / 'TMessagesProj/src/main/java/org/telegram/ui'
simple = (native / 'ActionBar/SimpleTextView.java').read_text(encoding='utf-8')
header = (native / 'Components/ChatAvatarContainer.java').read_text(encoding='utf-8')
setter = block(simple, 'public void setEllipsizeByGradient(boolean value, Boolean forceLeft)')
configure = block(header[header.index('public void setNebulaCenteredTitle('):],
                  'if (subtitleTextView != null)')
configure = configure.replace('app.nebulagram.ui.NebulaAppearance.chatHeader()', 'floating')
start = simple.index('CharSequence string;', simple.index('} else if (maxLines > 1)'))
end = simple.index('/*if (layout != null', start)
single_line = simple[start:end]
icon_budget = block(simple[simple.index('protected boolean createLayout('):],
                    'if (leftDrawable != null && !leftDrawableOutside)')
program = r'''
public class CheckHeaderStatus {
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Gravity {static final int LEFT=3,CENTER_HORIZONTAL=1;}
 static class Drawable {int getIntrinsicWidth(){return 18;}}
 static class Paint {}
 static class TextUtils {
  enum TruncateAt {END}
  static CharSequence ellipsize(CharSequence text,Paint paint,int width,TruncateAt where){
   int count=Math.max(0,width/7);
   if(text.length()<=count)return text;
   return count==0?"":text.subSequence(0,count-1)+"…";
  }
 }
 static class Subtitle {
  boolean scrollNonFitText,ellipsizeByGradient,leftDrawableOutside;
  Boolean forceEllipsizeByGradientLeft;
  int layouts,gravity,paddingRight,drawablePadding=4;
  Drawable leftDrawable;
  Paint textPaint=new Paint();
  void requestLayout(){layouts++;}
  void updateFadePaints(){}
  void checkUi_layerType(){}
  void setGravity(int value){gravity=value;}
  void setPadding(int l,int t,int r,int b){paddingRight=r;}
  void setEllipsizeByGradient(boolean value){setEllipsizeByGradient(value,null);}
  SETTER
  String render(CharSequence text,int width){
   width-=paddingRight;
   int leftDrawableWidth=0;
   ICON_BUDGET
   SINGLE_LINE
   return string.toString();
  }
 }
 Subtitle subtitleTextView=new Subtitle();boolean floating;
 int dp(int value){return value;}
 void configure(boolean enabled){CONFIGURE}
 static void checkSetter(){
  Subtitle s=new Subtitle();s.setEllipsizeByGradient(true);
  s.layouts=0;s.setEllipsizeByGradient(false);
  require(!s.ellipsizeByGradient,"Disabling gradient was ignored: long status still clips both ends");
  require(s.layouts>0,"Changing truncation must invalidate measured text layout");
  int count=s.layouts;s.setEllipsizeByGradient(false);
  require(s.layouts==count,"Unchanged truncation causes redundant layout");
  s.scrollNonFitText=true;s.setEllipsizeByGradient(true);
  require(s.ellipsizeByGradient,"Gradient toggle incorrectly depends on marquee");
  s.setEllipsizeByGradient(true,Boolean.TRUE);
  require(Boolean.TRUE.equals(s.forceEllipsizeByGradientLeft),"Fade direction update ignored");
  s.setEllipsizeByGradient(true,Boolean.FALSE);
  require(Boolean.FALSE.equals(s.forceEllipsizeByGradientLeft),"Fade direction restoration ignored");
  s.setEllipsizeByGradient(true,null);
  require(s.forceEllipsizeByGradientLeft==null,"Automatic fade direction not restored");
 }
 public static void main(String[] args){
  checkSetter();int cases=0;
  for(boolean floating:new boolean[]{false,true})
  for(int width:new int[]{80,140,240})
  for(boolean icon:new boolean[]{false,true}){
   CheckHeaderStatus h=new CheckHeaderStatus();h.floating=floating;
   Subtitle s=h.subtitleTextView;s.setEllipsizeByGradient(true);
   s.leftDrawable=icon?new Drawable():null;
   for(int round=0;round<2;round++){
    h.configure(true);
    require(!s.ellipsizeByGradient&&!s.scrollNonFitText,"Nebula subtitle must use native END ellipsis");
    require(s.gravity==(floating?Gravity.CENTER_HORIZONTAL:Gravity.LEFT),"Short status alignment changed");
    require(s.paddingRight==0,"Unexpected capsule padding");
    for(String text:new String[]{"в сети","Просто записывает голосовое сообщение"}){
     String visible=s.render(text,width);
     int budget=width-(icon?22:0);
     require(visible.length()*7<=budget,"Text and recording icon exceed viewport");
     if(text.length()*7>budget){
      require(visible.startsWith("Просто"),"Beginning of recording status lost");
      require(visible.endsWith("…"),"Long recording status lacks END ellipsis");
     }else require(visible.equals(text),"Short status was truncated");
     cases++;
    }
    h.configure(false);
    require(s.ellipsizeByGradient&&s.paddingRight==10&&s.gravity==Gravity.LEFT,
            "Disabling Nebula header must restore Telegram subtitle behavior");
   }
  }
  System.out.println(cases+" subtitle cases passed; gradient setter, icon budget and header toggles passed (deterministic metrics)");
 }
}
'''
for key, value in [('SETTER', setter), ('CONFIGURE', configure),
                   ('ICON_BUDGET', icon_budget), ('SINGLE_LINE', single_line)]:
    program = program.replace(key, value)
javac = shutil.which('javac')
if not javac:
    raise SystemExit('JDK required')
java = str(Path(javac).with_name('java.exe' if javac.endswith('.exe') else 'java'))
with tempfile.TemporaryDirectory(prefix='nebula-header-status-') as work:
    source = Path(work) / 'CheckHeaderStatus.java'
    source.write_text(program, encoding='utf-8')
    subprocess.run([javac, '-encoding', 'UTF-8', str(source)], check=True)
    subprocess.run([java, '-cp', work, 'CheckHeaderStatus'], check=True)
