"""Exercise the actual ActionBar draw branch across avatar/selection transitions."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
source = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
start = source.index('        final boolean separateAvatar =')
block = source[start:source.index('        if (blurredBackground', start)]
block = block.replace('app.nebulagram.ui.NebulaChatStyle.', '')
style = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaChatStyle.java').read_text(encoding='utf-8')
start = style.index('    public static int avatarBackdropAlpha(')
alpha = style[start:style.index('\n    }', start)+6]
java = r"""
import java.util.*;
class CapsuleCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Draw {int alpha,left,right;List<int[]> calls=new ArrayList<>();
  void setBounds(int l,int t,int r,int b){left=l;right=r;}void setAlpha(int a){alpha=a;}
  void draw(Object c){calls.add(new int[]{alpha,left,right});}}
 static class Avatar {boolean visible;boolean hasVisibleAvatar(){return visible;}float getAlpha(){return 1f;}}
 static class Animated {float getFloatValue(){return .6f;}}
 boolean nebulaFloatingChatHeader=true,nebulaChatMenuHidden=true,glassOnlyBack,doNotDrawGlassMenu,hasForcedMenuWidth;
 Avatar nebulaChatAvatarContainer=new Avatar();Draw glassDrawableMenu=new Draw();Animated animatorHasMenuItems=new Animated();
 float actionModeFactor,searchFactor;int menuWidth=96,s=48,p=6,t=0,b=60;Object canvas;
 int getWidth(){return 400;}
 ALPHA
 void draw(){ BLOCK }
 public static void main(String[] args){int cases=0;
  for(boolean separate:new boolean[]{false,true})for(boolean visible:new boolean[]{false,true})
  for(boolean forced:new boolean[]{false,true})for(int width:new int[]{0,48,96,144})
  for(int flags=0;flags<4;flags++)for(int frame=0;frame<=100;frame++){
   CapsuleCheck c=new CapsuleCheck();c.nebulaFloatingChatHeader=separate;c.nebulaChatAvatarContainer.visible=visible;
   c.hasForcedMenuWidth=forced;c.menuWidth=width;c.glassOnlyBack=(flags&1)!=0;c.doNotDrawGlassMenu=(flags&2)!=0;
   c.actionModeFactor=frame/100f;c.draw();
   boolean avatar=separate&&visible&&frame<100;
   boolean menu=width>0&&flags==0&&(!separate||frame>0);
   check(c.glassDrawableMenu.calls.size()==(avatar?1:0)+(menu?1:0));
   if(menu){int[] d=c.glassDrawableMenu.calls.get(c.glassDrawableMenu.calls.size()-1);
    check(d[0]==Math.round(255*(forced?1f:.6f)*(separate?frame/100f:1f)));
    check(d[1]==400-Math.max(48,width)-12&&d[2]==400);
   }
   cases++;
  }
  CapsuleCheck c=new CapsuleCheck();c.glassDrawableMenu=null;c.draw();
  System.out.println(cases+" selection capsule drawing cases passed");
 }
}
""".replace('ALPHA', alpha).replace('BLOCK', block)
with tempfile.TemporaryDirectory(prefix='nebula-selection-') as folder:
    p = Path(folder) / 'CapsuleCheck.java'
    p.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(p)], check=True)
    subprocess.run(['java', '-cp', folder, 'CapsuleCheck'], check=True)
