"""Execute the actual Saved Messages material draw branch through search/selection cancellation."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/android-validation'
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
start = source.index('        if (glassDrawable != null && !glassOnlyBack) {', source.index('protected void dispatchDraw(Canvas canvas)'))
block = source[start:source.index('        if (glassDrawableBack != null', start)]
block = block.replace('app.nebulagram.ui.NebulaAppearance.', 'Appearance.').replace('app.nebulagram.ui.NebulaChatStyle.', 'Style.')
block = block.replace('app.nebulagram.ui.NebulaSelectionGlass.', 'Selection.')
java = r'''
import java.util.*;
class SavedMaterialCheck {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 static int dp(float value){return Math.round(value);}
 static int lerp(int a,int b,float p){return Math.round(a+(b-a)*p);}
 static class View {static final int VISIBLE=0;int getVisibility(){return VISIBLE;}int getLeft(){return 120;}
  int getMeasuredWidth(){return 160;}float getX(){return 290;}}
 static class MarginLayoutParams {int leftMargin=58;}
 static class ChatAvatarContainer extends View {
  int getNebulaCenteredHeaderWidth(){return 140;}void setTranslationX(float x){}void setPivotX(float x){}
  int getLeftPadding(){return 0;}Object getLayoutParams(){return new MarginLayoutParams();}
 }
 static class Animated {float getFloatValue(){return 1;}float getFactor(){return 140;}float set(int v,boolean instant){return v;}}
 static class Appearance {static boolean liquidAnimations(){return true;}static boolean centeredHeader(){return true;}static boolean chatHeader(){return true;}}
 static class Style {static int headerLeft(int width,int capsule){return (width-capsule)/2;}}
 static class Selection {static int counterRight(View v,int fallback){return Math.min(fallback,236);}}
 static class Bounds {int height(){return 60;}int width(){return 400;}}
 static class Material {
  int alpha,left,right;List<int[]> calls=new ArrayList<>();
  void setBounds(int l,int t,int r,int b){left=l;right=r;}
  void setRadius(float radius){}Bounds getPaddedBounds(){return new Bounds();}
  void setAlpha(int a){alpha=a;}int getAlpha(){return alpha;}
  void draw(Object canvas){calls.add(new int[]{alpha,left,right});}
 }
 boolean nebulaClassicSavedHeader,nebulaSavedMessagesHeader,nebulaFloatingChatHeader=true,glassOnlyBack;
 boolean hasForcedMenuWidth,hasForcedMenuMinWidth,nebulaHomeGlass,nebulaHomeTabsGlass,nebulaCommunityGlass,nebulaProfileGlass,isSearchFieldVisible;
 int menuWidth=92,p=6,s=46,nebulaBackWidth=58,t=0,b=60;boolean hasBackButton=true;
 float actionModeFactor,searchFactor;Object canvas=new Object();
 Material glassDrawable=new Material();View menu=new View(),actionMode=new View();View[] titleTextView={new View(),null};
 ChatAvatarContainer nebulaChatAvatarContainer=new ChatAvatarContainer(),chatAvatarContainer;
 Animated animatorHasMenuItems=new Animated(),animatorAvatarContainerHasAvatar=new Animated(),animatorAvatarContainerWidth=new Animated(),nebulaCapsuleWidth=new Animated();
 boolean isNebulaSharedHeaderGlass(){return nebulaHomeTabsGlass||nebulaCommunityGlass;}
 int getWidth(){return 400;}
 void draw(){BLOCK}
 public static void main(String[] args){int cases=0;
  for(int mode=0;mode<2;mode++)for(int phase=0;phase<2;phase++)for(int frame=0;frame<=100;frame++){
   SavedMaterialCheck c=new SavedMaterialCheck();c.nebulaClassicSavedHeader=mode==0;c.nebulaSavedMessagesHeader=mode==1;
   float factor=frame/100f;if(phase==0)c.searchFactor=factor;else c.actionModeFactor=factor;c.draw();
   check(c.glassDrawable.calls.size()==(Math.round(255*factor)>0?1:0),"resting title material is absent");
   if(!c.glassDrawable.calls.isEmpty()){
    int[] draw=c.glassDrawable.calls.get(0);check(draw[0]==Math.round(255*factor),"search/selection fades through last frame");
    int left=52,right=phase==0?302:lerp(302,236,factor);
    check(draw[1]==left&&draw[2]==right,"search keeps its field; selection counter stays separate");
    if(phase==1&&frame==100)check(draw[1]==52&&draw[2]==236,"counter material ends before action controls");
   }cases++;
  }
  SavedMaterialCheck regular=new SavedMaterialCheck();regular.draw();
  int[] draw=regular.glassDrawable.calls.get(0);check(draw[0]==255&&draw[2]-draw[1]==140,"regular chat capsule retained");
  System.out.println(cases+" Saved Messages search/selection fade cases passed; regular title capsule retained");
 }
}
'''.replace('BLOCK', block)
with tempfile.TemporaryDirectory(prefix='nebula-saved-material-') as temp:
    path = Path(temp) / 'SavedMaterialCheck.java'
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'SavedMaterialCheck'], check=True)
