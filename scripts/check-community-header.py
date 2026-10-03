"""Run the production community strip capture with API/position/motion fallbacks."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1])
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs = {
 'android/annotation/TargetApi.java': 'package android.annotation; public @interface TargetApi {int value();}',
 'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=31;}}',
 'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {
 public boolean hardware=true; public float x,y; public int saves; public boolean isHardwareAccelerated(){return hardware;}
 public void drawColor(int c){} public void translate(float dx,float dy){x+=dx;y+=dy;} public void save(){saves++;} public void restore(){saves--;x=y=0;}}
 ''',
 'android/view/View.java': '''package android.view; public class View {public static final int VISIBLE=0,GONE=8;
 public int width=393,height=56,visibility=0,draws; public float x,y;
 public int getWidth(){return width;}public int getHeight(){return height;}public float getX(){return x;}public float getY(){return y;}
 public int getVisibility(){return visibility;}public void draw(android.graphics.Canvas c){draws++;sampleX=c.x;sampleY=c.y;}public float sampleX,sampleY;public void setTranslationX(float x){this.x=x;}}
 ''',
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static int dp(float n){return Math.round(n);} public static float dpf2(float n){return n;}}',
 'org/telegram/messenger/LiteMode.java': 'package org.telegram.messenger; public class LiteMode {public static int FLAG_CHAT_BLUR=1;public static boolean enabled=true;public static boolean isEnabled(int n){return enabled;}}',
 'org/telegram/ui/ActionBar/Theme.java': 'package org.telegram.ui.ActionBar; public class Theme {public interface ResourcesProvider{}public static int key_windowBackgroundGray=1;public static int color=0xff171717;public static int getColor(int k,ResourcesProvider p){return color;}}',
 'org/telegram/ui/ActionBar/ActionBar.java': '''package org.telegram.ui.ActionBar; public class ActionBar extends android.view.View {
 public boolean shared,avatar;public android.view.View title=new android.view.View();
 public void setupGlass(org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory f,Object p){}
 public void setNebulaCommunityGlass(boolean enabled,boolean hasAvatar){shared=enabled;avatar=hasAvatar;}
 public android.view.View getTitleTextView(){return title;}}
 ''',
 'org/telegram/ui/Components/blur3/source/BlurredBackgroundSourceColor.java': 'package org.telegram.ui.Components.blur3.source; public class BlurredBackgroundSourceColor {public int color;public void setColor(int c){color=c;}}',
 'org/telegram/ui/Components/blur3/source/BlurredBackgroundSourceRenderNode.java': '''package org.telegram.ui.Components.blur3.source;
 public class BlurredBackgroundSourceRenderNode {public static BlurredBackgroundSourceRenderNode last;public int width,height,begins,ends,invalidations;public float blur;public boolean recording;
 public BlurredBackgroundSourceRenderNode(Object fallback){last=this;}public void setBlur(float n){blur=n;}
 public android.graphics.Canvas beginRecording(int w,int h){if(recording)throw new AssertionError("recursive capture");recording=true;width=w;height=h;begins++;return new android.graphics.Canvas();}
 public void endRecording(){recording=false;ends++;}public void invalidateDisplayListForDrawables(){invalidations++;}}
 ''',
 'org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java': '''package org.telegram.ui.Components.blur3.drawable; public class BlurredBackgroundDrawable {
 public int left,top,right,bottom,ox,oy,draws;public BlurredBackgroundDrawable setColorProvider(Object p){return this;}public void setPadding(int p){}public void setRadius(float a,float b,float c,float d){}
 public void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public void setSourceOffset(int x,int y){ox=x;oy=y;}public void draw(android.graphics.Canvas c){draws++;}}
 ''',
 'org/telegram/ui/Components/blur3/BlurredBackgroundDrawableViewFactory.java': '''package org.telegram.ui.Components.blur3;
 public class BlurredBackgroundDrawableViewFactory {public static org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable last;
 public BlurredBackgroundDrawableViewFactory(Object source){}public void setLiquidGlassEffectAllowed(boolean b){}
 public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable create(){return last=new org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable();}}
 ''',
 'org/telegram/ui/Components/blur3/drawable/color/impl/BlurredBackgroundProviderImpl.java': 'package org.telegram.ui.Components.blur3.drawable.color.impl; public class BlurredBackgroundProviderImpl {public static Object topPanel(org.telegram.ui.ActionBar.Theme.ResourcesProvider p){return p;}}',
 'app/nebulagram/ui/NebulaAppearance.java': 'package app.nebulagram.ui; public class NebulaAppearance {public static boolean enabled=true;public static boolean homeGlassHeader(){return enabled;}}',
 'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui; public class NebulaMenuStyle {public static boolean animated(){return true;}}',
 'app/nebulagram/ui/NebulaGlass.java': 'package app.nebulagram.ui; public class NebulaGlass {public static boolean reduced;public static boolean reduced(){return reduced;}public static float blur(){return 12;}}',
 'Check.java': '''import android.graphics.Canvas;import android.view.View;import org.telegram.ui.ActionBar.ActionBar;
 import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;import app.nebulagram.ui.*;
 class Check {static void check(boolean b,String why){if(!b)throw new AssertionError(why);}public static void main(String[] args){int cases=0;
 for(int api:new int[]{23,29,30,31,33,37})for(int w:new int[]{320,393,768})for(int h:new int[]{48,56,64})for(int y:new int[]{24,176,500})for(int mode=0;mode<4;mode++){
  android.os.Build.VERSION.SDK_INT=api;NebulaGlass.reduced=mode==1;org.telegram.messenger.LiteMode.enabled=mode!=2;
  ActionBar bar=new ActionBar();bar.width=w;bar.height=h;bar.x=12;bar.y=y;bar.title.x=-18;View list=new View();list.height=2400;list.x=0;list.y=8;
  Canvas canvas=new Canvas();canvas.hardware=mode!=3;BlurredBackgroundSourceRenderNode.last=null;
  NebulaCommunityHeader header=NebulaCommunityHeader.create(bar,true,null);
  check(bar.shared&&bar.avatar&&bar.title.x==0,"native community/title binding");header.draw(canvas,bar,list);
  var material=BlurredBackgroundDrawableViewFactory.last;check(material.left==0&&material.top==0&&material.right==w&&material.bottom==h,"shared bounds cover all header controls");
  check(canvas.saves==0&&canvas.x==0&&canvas.y==0,"drawing restores caller canvas");
  boolean blur=api>=31&&mode==0;check(list.draws==(blur?1:0),"no list capture on old API, low power, disabled blur or software canvas");
  if(api>=31){var node=BlurredBackgroundSourceRenderNode.last;int padding=blur?32:0;
   check(node.width==w+padding*2&&node.height==h+padding*2,"capture is bounded to header, not screen");
   check(node.begins==1&&node.ends==1&&!node.recording,"capture closes every frame");
   check(material.ox==padding&&material.oy==padding,"material samples padded source correctly");
   if(blur)check(list.sampleX==padding-12&&list.sampleY==padding+8-y,"source tracks expanded/collapsed sheet origin");
   list.visibility=View.GONE;header.draw(canvas,bar,list);check(list.draws==(blur?1:0),"hidden list cannot leak into backdrop");
   check(node.begins==2&&node.ends==2,"next frame reuses source safely");
  }else check(BlurredBackgroundSourceRenderNode.last==null,"old API does not construct RenderNode");cases++;
 }
 NebulaAppearance.enabled=false;check(NebulaCommunityHeader.create(new ActionBar(),false,null)==null,"native style fallback is preserved");
 System.out.println(cases+" community capture API/position/power/fallback cases passed");}}
 ''',
}
with tempfile.TemporaryDirectory(prefix='nebula-community-header-') as temp:
    work = Path(temp)
    for name, content in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*[str(work / name) for name in stubs],str(ui / 'NebulaCommunityHeader.java')],check=True)
    subprocess.run(['java','-cp',str(work),'Check'],check=True)

bar = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
dialogs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
sheet = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/community/CommunitySheet.java').read_text(encoding='utf-8')
assert 'setNebulaCommunityGlass(true, true)' in dialogs
assert dialogs.count('isNebulaSharedHeaderGlass()') >= 3
assert sheet.count('afterInit();') == 3 and 'NebulaCommunityHeader.create(actionBar, hasAvatar, resourcesProvider)' in sheet
assert 'if (child == actionBar && nebulaHeader != null)' in sheet
assert 'isNebulaSharedHeaderGlass()' in (ui / 'NebulaLinkShortcut.java').read_text(encoding='utf-8')
assert bar.count('if (nebulaCommunityGlass) textLeft = Math.max(textLeft, nebulaCommunityTitleInset);') == 2
print('Both community entry points bind shared material and matching title measure/layout insets')
