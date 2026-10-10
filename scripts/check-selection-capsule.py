"""Exercise the actual ActionBar draw branch across avatar/selection transitions."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
source = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
start = source.index('        final boolean separateAvatar =')
block = source[start:source.index('        if (blurredBackground', start)]
plain_start = source.index('        final boolean nebulaPlainHeaderButtons =')
block = source[plain_start:source.index('        if (glassDrawableBack != null', plain_start)] + block
block = block.replace('app.nebulagram.ui.NebulaChatStyle.', '')
selection_start = source.index('            if (actionModeFactor > 0f && actionMode != null')
selection = source[selection_start:source.index('            glassDrawable.setBounds(left, t, right, b);', selection_start)]
style = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaChatStyle.java').read_text(encoding='utf-8')
start = style.index('    public static int avatarBackdropAlpha(')
alpha = style[start:style.index('\n    }', start)+6]
java = r"""
import java.util.*;
class CapsuleCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Draw {int alpha,left,right;List<int[]> calls=new ArrayList<>();
  void setBounds(int l,int t,int r,int b){left=l;right=r;}void setAlpha(int a){alpha=a;}int getAlpha(){return alpha;}
  void draw(Object c){calls.add(new int[]{alpha,left,right});}}
 static class Avatar {boolean visible;boolean hasVisibleAvatar(){return visible;}float getAlpha(){return 1f;}}
 static class Animated {float getFloatValue(){return .6f;}}
 static class View {static final int VISIBLE=0;int x,width=46,visibility=VISIBLE;float alpha=1f;
  View(int x){this.x=x;}int getVisibility(){return visibility;}int getWidth(){return width;}
  float getAlpha(){return alpha;}float getX(){return x;}}
 static class ActionBarMenuItem extends View {ActionBarMenuItem(int x){super(x);}}
 static class Menu {int x=240;View[] children={new ActionBarMenuItem(0),new ActionBarMenuItem(46),new ActionBarMenuItem(92),new ActionBarMenuItem(138)};
  int getChildCount(){return children.length;}View getChildAt(int i){return children[i];}float getX(){return x;}}
 boolean nebulaFloatingChatHeader=true,nebulaChatMenuHidden=true,glassOnlyBack,doNotDrawGlassMenu,hasForcedMenuWidth;
 boolean nebulaHomeGlass,nebulaHomeTabsGlass,nebulaCommunityGlass,isSearchFieldVisible,nebulaClassicSavedHeader,nebulaSavedMessagesHeader;Menu menu=new Menu(),actionMode=new Menu();
 Avatar nebulaChatAvatarContainer=new Avatar();Draw glassDrawableMenu=new Draw();Animated animatorHasMenuItems=new Animated();
 float actionModeFactor,searchFactor;int menuWidth=96,s=48,p=6,t=0,b=60;Object canvas;
 boolean isNebulaSharedHeaderGlass(){return nebulaHomeTabsGlass||nebulaCommunityGlass;}
 static class NebulaSelectionGlass {
  static int counterRight(Menu menu,int fallback){return Math.min(fallback,menu.x-4);}
  static void drawActions(Object c,Menu menu,Draw draw,int w,int t,int b,float f){}
 }
 NebulaSelectionGlass nebulaSelectionGlass=new NebulaSelectionGlass();
 int getWidth(){return 400;}
 int dp(int value){return value;}
 ALPHA
 void draw(){ BLOCK }
 static int lerp(int a,int b,float p){return Math.round(a+(b-a)*p);}
 int[] selectionBounds(int left,int right){int rightDefault=right; SELECTION return new int[]{left,right}; }
 public static void main(String[] args){int cases=0;
  for(boolean separate:new boolean[]{false,true})for(boolean visible:new boolean[]{false,true})
  for(boolean forced:new boolean[]{false,true})for(int width:new int[]{0,48,96,144})
  for(int flags=0;flags<4;flags++)for(int frame=0;frame<=100;frame++){
   CapsuleCheck c=new CapsuleCheck();c.nebulaFloatingChatHeader=separate;c.nebulaChatAvatarContainer.visible=visible;
   c.hasForcedMenuWidth=forced;c.menuWidth=width;c.glassOnlyBack=(flags&1)!=0;c.doNotDrawGlassMenu=(flags&2)!=0;
   c.actionModeFactor=frame/100f;c.draw();
   boolean avatar=separate&&visible&&frame<100; // The restored chat avatar surface fades out for selection.
   if(avatar){int[] d=c.glassDrawableMenu.calls.get(0);check(d[0]==Math.round(255*(1f-frame/100f)));check(d[1]==340&&d[2]==400);}
   boolean menu=width>0&&flags==0&&!separate;
   check(c.glassDrawableMenu.calls.size()==(avatar?1:0)+(menu?1:0));
   if(menu){int[] d=c.glassDrawableMenu.calls.get(c.glassDrawableMenu.calls.size()-1);
    check(d[0]==Math.round(255*(forced?1f:.6f)*(separate?frame/100f:1f)));
    check(d[1]==400-Math.max(48,width)-12&&d[2]==400);
   }
   cases++;
  }
  for(int frame=0;frame<=100;frame++){
   CapsuleCheck search=new CapsuleCheck();search.nebulaChatAvatarContainer.visible=true;search.searchFactor=frame/100f;search.draw();
   check(search.glassDrawableMenu.calls.size()==1);check(search.glassDrawableMenu.calls.get(0)[0]==Math.round(255*(1f-frame/100f)));
   search.glassDrawableMenu.calls.clear();search.searchFactor=0;search.draw();check(search.glassDrawableMenu.calls.size()==1&&search.glassDrawableMenu.calls.get(0)[0]==255);
  }
  CapsuleCheck home=new CapsuleCheck();home.nebulaFloatingChatHeader=false;home.nebulaHomeGlass=true;
  home.menuWidth=138;home.menu.children[3].visibility=8;home.draw();
  check(home.glassDrawableMenu.calls.isEmpty());
  check(home.glassDrawableMenu.left==332&&home.glassDrawableMenu.right==378); // bounds retained for hit testing
  CapsuleCheck tabs=new CapsuleCheck();tabs.nebulaFloatingChatHeader=false;tabs.nebulaHomeTabsGlass=true;
  tabs.menuWidth=138;tabs.menu.children[3].visibility=8;tabs.draw();
  check(tabs.glassDrawableMenu.calls.isEmpty());
  check(tabs.glassDrawableMenu.left==236&&tabs.glassDrawableMenu.right==382);
  tabs.glassDrawableMenu.calls.clear();tabs.searchFactor=1f;tabs.draw();
  check(tabs.glassDrawableMenu.calls.isEmpty());
  for(int frame=1;frame<=100;frame++){
   CapsuleCheck selection=new CapsuleCheck();selection.nebulaFloatingChatHeader=false;
   selection.nebulaHomeTabsGlass=true;selection.actionModeFactor=frame/100f;
   selection.actionMode.x=0;selection.actionMode.children=new View[]{new View(0),new View(54),new ActionBarMenuItem(240),new ActionBarMenuItem(286),new ActionBarMenuItem(332)};
   selection.draw();check(selection.glassDrawableMenu.calls.isEmpty());
   check(selection.glassDrawableMenu.left==236&&selection.glassDrawableMenu.right==382);
  }
  for(int mode=0;mode<4;mode++)for(int frame=0;frame<=100;frame++) {
   CapsuleCheck selected=new CapsuleCheck();selected.nebulaFloatingChatHeader=mode==0;
   selected.nebulaClassicSavedHeader=mode==1;selected.nebulaSavedMessagesHeader=mode==2;
   selected.actionModeFactor=frame/100f;int[] bounds=selected.selectionBounds(52,288);
   check(bounds[0]==52 && bounds[1]>=236 && bounds[1]<=288);
   if(frame==100 && mode<3)check(bounds[0]==52 && bounds[1]==236);
   if(frame==0 || mode==3)check(bounds[0]==52 && bounds[1]==288);
  }
  CapsuleCheck c=new CapsuleCheck();c.glassDrawableMenu=null;c.draw();
  System.out.println(cases+" header button material and touch-bound cases passed");
 }
}
""".replace('ALPHA', alpha).replace('BLOCK', block).replace('SELECTION', selection).replace('app.nebulagram.ui.NebulaSelectionGlass.', 'NebulaSelectionGlass.')
with tempfile.TemporaryDirectory(prefix='nebula-selection-') as folder:
    p = Path(folder) / 'CapsuleCheck.java'
    p.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(p)], check=True)
    subprocess.run(['java', '-cp', folder, 'CapsuleCheck'], check=True)
