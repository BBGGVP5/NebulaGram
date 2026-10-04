"""Execute production profile rendering state and typecheck it against the real SDK."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'

native = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java').read_text(encoding='utf-8')
start = native.index('        avatarContainer2 = new FrameLayout(context) {')
end = native.index('        fallbackImage =', start)
header = native[start:end]
assert header.index('super.drawChild(canvas, child, drawingTime)') < header.index('nebulaHero.drawForeground(canvas)')
assert 'child == overlaysView && !isInLandscapeMode' in header
assert 'extraHeight + searchTransitionOffset) * (1f - mediaHeaderAnimationProgress)' in header
assert 'avatarsViewPager.getCurrentItemView()' in header and 'nebulaHero.clear(actionsView)' in header
assert native.index('avatarContainer2.addView(overlaysView)') < native.index('avatarContainer2.addView(actionsView,')
assert native.index('avatarContainer2.addView(overlaysView)') < native.index('avatarContainer2.addView(musicView,')
actions = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ProfileActionsView.java').read_text(encoding='utf-8')
assert 'if (!hasCustomActionSurface(canvas)) drawRenderNode(canvas)' in actions
assert actions.index('if (hasCustomActionSurface(canvas)) {') < actions.index('int newAlpha = (int) (action.getAlpha() * alphaFraction1 * wasAlpha)')

# These Telegram signatures are used both for execution with instrumented Android
# primitives and a second compilation against android.jar (no Android stubs).
contracts = {
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger;public class AndroidUtilities {public static int dp(float x){return Math.round(x);}}',
 'org/telegram/ui/ActionBar/Theme.java': """package org.telegram.ui.ActionBar;public class Theme {
 public interface ResourcesProvider {} public static int page=0xff112233;
 public static int key_windowBackgroundWhiteBlueText=1,key_windowBackgroundWhite=2,key_windowBackgroundWhiteBlackText=3,key_windowBackgroundGray=4;
 public static int getColor(int k){return getColor(k,null);}public static int getColor(int k,ResourcesProvider p){return k==4?page:k==3?0xffffffff:0xff223344;}
 public static int multAlpha(int c,float a){return (c&0xffffff)|(Math.round((c>>>24)*a)<<24);}}""",
 'org/telegram/ui/ActionBar/SimpleTextView.java': """package org.telegram.ui.ActionBar;public class SimpleTextView extends android.view.View {
 private final android.graphics.Paint paint=new android.graphics.Paint();public SimpleTextView(android.content.Context c){super(c);}
 public android.graphics.Paint getTextPaint(){return paint;}}""",
 'org/telegram/ui/Components/BackupImageView.java': """package org.telegram.ui.Components;public class BackupImageView extends android.view.View {
 public final org.telegram.messenger.ImageReceiver receiver=new org.telegram.messenger.ImageReceiver();
 public BackupImageView(android.content.Context c){super(c);}public org.telegram.messenger.ImageReceiver getImageReceiver(){return receiver;}}""",
 'org/telegram/messenger/ImageReceiver.java': """package org.telegram.messenger;public class ImageReceiver {
 public float x=11,y=12,w=90,h=100,alpha=.7f;public final int[] radii={9,8,7,6};public boolean loaded=true,throwDraw;public int draws;
 public float getImageX(){return x;}public float getImageY(){return y;}public float getImageWidth(){return w;}public float getImageHeight(){return h;}
 public float getAlpha(){return alpha;}public int[] getRoundRadius(){return radii;}public boolean hasImageLoaded(){return loaded;}
 public void setAlpha(float a){alpha=a;}public void setRoundRadius(int[] r){System.arraycopy(r,0,radii,0,4);}
 public void setImageCoords(float xx,float yy,float ww,float hh){x=xx;y=yy;w=ww;h=hh;}
 public void setImageCoords(android.graphics.RectF r){setImageCoords(r.left,r.top,r.width(),r.height());}
 public void draw(android.graphics.Canvas c,Object ignored){draws++;if(throwDraw)throw new IllegalStateException("test draw failure");}}""",
 'org/telegram/ui/Components/ProfileActionsView.java': """package org.telegram.ui.Components;public class ProfileActionsView extends android.view.View {
 public int color;public boolean byId;public ProfileActionsView(android.content.Context c,int h){super(c);}
 public void setActionsColor(int c,boolean b){color=c;byId=b;}public float getRoundRadius(){return 16;}
 protected boolean hasCustomActionSurface(android.graphics.Canvas c){return false;}
 protected void drawActionSurface(android.graphics.Canvas c,android.graphics.RectF r,int key,float radius,float alpha){}}""",
 'app/nebulagram/ui/NebulaAppearance.java': """package app.nebulagram.ui;public class NebulaAppearance {
 public static boolean style=true,banner=true;public static boolean profileStyle(){return style;}public static boolean profilePhotoBanner(){return banner;}
 public static boolean glassHighlights(){return true;}}""",
 'app/nebulagram/ui/NebulaTheme.java': """package app.nebulagram.ui;public class NebulaTheme {
 public static NebulaTheme of(android.content.Context c){return new NebulaTheme();}public boolean isDynamic(){return false;}public int surfaceContainer(){return 0xff112233;}}""",
 'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui;public class NebulaMenuStyle {public static boolean enabled(){return true;}}',
 'app/nebulagram/ui/NebulaProfileGlass.java': """package app.nebulagram.ui;class NebulaProfileGlass {
 static boolean enabled=true;static int captures,ends,draws;static CanvasSize last;
 static class CanvasSize {int w,h;CanvasSize(int w,int h){this.w=w;this.h=h;}}
 static boolean supported(){return enabled;}NebulaProfileGlass(org.telegram.ui.ActionBar.Theme.ResourcesProvider p){}
 android.graphics.Canvas begin(int w,int h){captures++;last=new CanvasSize(w,h);return new android.graphics.Canvas();}
 void end(){ends++;}void draw(android.graphics.Canvas c,android.graphics.RectF r,int key,float radius,float alpha,float x,float y){draws++;}}""",
 'androidx/core/graphics/ColorUtils.java': """package androidx.core.graphics;public class ColorUtils {
 public static int blendARGB(int a,int b,float f){return a;}public static double calculateContrast(int a,int b){return 5;}
 public static double calculateLuminance(int a){return .6;}public static int setAlphaComponent(int c,int a){return (c&0xffffff)|(a<<24);}}""",
}
android = {
 'android/content/Context.java':'package android.content;public class Context {}',
 'android/view/View.java':"""package android.view;public class View {
 public static int VISIBLE=0;public float x,y;public int height=30,visibility=VISIBLE;
 public View(android.content.Context c){}public android.content.Context getContext(){return null;}
 public float getX(){return x;}public float getY(){return y;}public int getHeight(){return height;}public int getVisibility(){return visibility;}
 protected void onDetachedFromWindow(){}}""",
 'android/view/ViewGroup.java':"""package android.view;public class ViewGroup extends View {
 public java.util.List<View> children=new java.util.ArrayList<>();public ViewGroup(android.content.Context c){super(c);}
 public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}""",
 'android/graphics/Shader.java':'package android.graphics;public class Shader {public enum TileMode {CLAMP}public void setLocalMatrix(Matrix m){}}',
 'android/graphics/LinearGradient.java':"""package android.graphics;public class LinearGradient extends Shader {
 public static int created;public final int[] colors;public final float y1;
 public LinearGradient(float x0,float y0,float x1,float y1,int start,int end,TileMode mode){this(x0,y0,x1,y1,new int[]{start,end},null,mode);}
 public LinearGradient(float x0,float y0,float x1,float y1,int[] colors,float[] locations,TileMode mode){created++;this.colors=colors;this.y1=y1;}}""",
 'android/graphics/Matrix.java':'package android.graphics;public class Matrix {public void setScale(float x,float y){}public void postTranslate(float x,float y){}}',
 'android/graphics/Color.java':'package android.graphics;public class Color {public static int BLACK=0xff000000,WHITE=0xffffffff;}',
 'android/graphics/RectF.java':"""package android.graphics;public class RectF {
 public float left,top,right,bottom;public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
 public void set(Rect r){set(r.left,r.top,r.right,r.bottom);}public void set(RectF r){set(r.left,r.top,r.right,r.bottom);}
 public float width(){return right-left;}public float height(){return bottom-top;}}""",
 'android/graphics/Rect.java':'package android.graphics;public class Rect {public int left,top,right,bottom;}',
 'android/graphics/Path.java':"""package android.graphics;public class Path {
 public enum Direction {CW}public void rewind(){}public void addRect(RectF r,Direction d){}public void addRoundRect(RectF r,float[] radii,Direction d){}}""",
 'android/graphics/Paint.java':"""package android.graphics;public class Paint {
 public static int ANTI_ALIAS_FLAG=1;public enum Style {FILL,STROKE}public Shader shader;public int alpha=255,color;
 public Paint(){}public Paint(int f){}public void setStyle(Style s){}public void setColor(int c){color=c;}public int getColor(){return color;}
 public void setStrokeWidth(float w){}public void setShader(Shader s){shader=s;}public void setAlpha(int a){alpha=a;}}""",
 'android/graphics/Canvas.java':"""package android.graphics;public class Canvas {
 public boolean hardware=true;public int saves;public java.util.List<Op> ops=new java.util.ArrayList<>();
 public static class Op {public float bottom;public int alpha;public Shader shader;Op(float b,Paint p){bottom=b;alpha=p.alpha;shader=p.shader;}}
 public boolean isHardwareAccelerated(){return hardware;}public int save(){return ++saves;}public void restoreToCount(int n){saves=n-1;}
 public void clipPath(Path p){}public void drawRect(RectF r,Paint p){ops.add(new Op(r.bottom,p));}
 public void drawRect(float l,float t,float r,float b,Paint p){ops.add(new Op(b,p));}
 public void drawPath(Path x,Paint p){}public void drawRoundRect(RectF r,float x,float y,Paint p){ops.add(new Op(r.bottom,p));}}""",
 'android/graphics/ColorFilter.java':'package android.graphics;public class ColorFilter {}',
 'android/graphics/PixelFormat.java':'package android.graphics;public class PixelFormat {public static int TRANSLUCENT=-3;}',
 'android/graphics/drawable/Drawable.java':"""package android.graphics.drawable;public abstract class Drawable {
 public android.graphics.Rect getBounds(){return new android.graphics.Rect();}public void invalidateSelf(){}
 public abstract void draw(android.graphics.Canvas c);public abstract void setAlpha(int a);public abstract void setColorFilter(android.graphics.ColorFilter f);public abstract int getOpacity();}""",
}
check = r"""package app.nebulagram.ui;
import android.graphics.*;import android.view.*;import org.telegram.ui.Components.*;import org.telegram.ui.ActionBar.*;
import org.telegram.messenger.ImageReceiver;
class ProfileCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void restored(ImageReceiver r){check(r.x==11&&r.y==12&&r.w==90&&r.h==100&&r.alpha==.7f,"receiver state leaked from banner drawing");
  check(java.util.Arrays.equals(r.radii,new int[]{9,8,7,6}),"avatar radius/base shape changed");}
 public static void main(String[] args){
  ViewGroup avatar=new ViewGroup(null);avatar.y=120;BackupImageView small=new BackupImageView(null);avatar.children.add(small);
  BackupImageView gallery=new BackupImageView(null);SimpleTextView title=new SimpleTextView(null);View subtitle=new View(null);
  NebulaProfileArt.Actions buttons=new NebulaProfileArt.Actions(null,74,null);buttons.y=350;buttons.setActionsColor(0xff778899,true);
  NebulaProfileArt.Hero hero=new NebulaProfileArt.Hero();
  int cases=0;
  for(float end:new float[]{449.5f,474.5f,704.25f})for(int phase=0;phase<=100;phase++){
   float expanded=phase/100f;Canvas canvas=new Canvas();int s=small.receiver.draws,g=gallery.receiver.draws;
   hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,end,1,expanded,0,1,null);
   check(buttons.hasCustomActionSurface(canvas),"glass missing during expansion");
   check(NebulaProfileGlass.last.h==(int)Math.ceil(end),"capture does not include complete header/music row");
   hero.drawForeground(canvas);Canvas.Op fade=canvas.ops.get(canvas.ops.size()-1);
   check(fade.alpha==255&&fade.bottom==end+1,"fade disappears on expansion or exposes fractional seam");
   LinearGradient gradient=(LinearGradient)fade.shader;
   check(gradient.colors[gradient.colors.length-1]==Theme.page&&gradient.y1==end,"fade does not end at exact page colour/header bottom");
   if(expanded>.5f)check(gallery.receiver.draws>g&&small.receiver.draws==s,"expanded glass freezes the first avatar");
   else check(small.receiver.draws>s&&gallery.receiver.draws==g,"collapsed glass must use the actual small avatar");
   restored(small.receiver);restored(gallery.receiver);cases++;
  }
  int created=LinearGradient.created;
  for(int i=0;i<100;i++){Canvas canvas=new Canvas();hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,704.25f,1,1,0,1,null);hero.drawForeground(canvas);}
  check(LinearGradient.created==created,"unchanged profile rebuilds shaders every frame");
  Canvas resized=new Canvas();hero.draw(resized,500,avatar,title,subtitle,buttons,gallery,704.25f,1,1,0,1,null);
  check(LinearGradient.created==created+1,"profile width change must refresh diagonal tint");
  Theme.page=0xffeeeeee;resized=new Canvas();hero.draw(resized,500,avatar,title,subtitle,buttons,gallery,704.25f,1,1,0,1,null);hero.drawForeground(resized);
  check(((LinearGradient)resized.ops.get(resized.ops.size()-1).shader).colors[3]==Theme.page,"live light-theme change retains the old lower edge colour");
  for(int mode=0;mode<5;mode++){
   Canvas canvas=new Canvas();NebulaAppearance.banner=mode!=0;NebulaAppearance.style=mode!=1;small.receiver.loaded=mode!=2;gallery.receiver.loaded=false;
   hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,474.5f,mode==3?0:1,1,mode==4?1:0,1,null);
   hero.drawForeground(canvas);check(canvas.ops.isEmpty()&&!buttons.hasCustomActionSurface(canvas),"disabled/missing/collapsed/media state retains stale banner");
   check(buttons.color==0xff778899&&buttons.byId,"native peer colour not restored without banner");cases++;
  }
  NebulaAppearance.banner=true;NebulaAppearance.style=true;small.receiver.loaded=true;gallery.receiver.loaded=true;
  for(int fallback=0;fallback<2;fallback++){
   Canvas canvas=new Canvas();canvas.hardware=fallback!=0;NebulaProfileGlass.enabled=fallback!=1;
   hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,474.5f,1,1,0,1,null);
   check(!buttons.hasCustomActionSurface(canvas),"software/reduced mode replaces native fallback");cases++;
  }
  NebulaProfileGlass.enabled=true;
  Canvas canvas=new Canvas();hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,474.5f,1,1,0,1,null);
  hero.clear(buttons);canvas.ops.clear();hero.drawForeground(canvas);
  check(canvas.ops.isEmpty()&&!buttons.hasCustomActionSurface(canvas)&&buttons.color==0xff778899,"landscape reset retains portrait material");
  canvas=new Canvas();hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,474.5f,1,1,0,1,null);
  buttons.onDetachedFromWindow();check(!buttons.hasCustomActionSurface(canvas)&&buttons.color==0xff778899&&buttons.byId,"detach leaves captured glass or neutral colour behind");
  for(int expanded=0;expanded<2;expanded++){
   ImageReceiver receiver=expanded==0?small.receiver:gallery.receiver;receiver.throwDraw=true;
   canvas=new Canvas();int captures=NebulaProfileGlass.captures,ends=NebulaProfileGlass.ends;
   try{hero.draw(canvas,393,avatar,title,subtitle,buttons,gallery,474.5f,1,expanded,0,1,null);throw new AssertionError("draw did not throw");}
   catch(IllegalStateException expected){}
   receiver.throwDraw=false;restored(receiver);check(canvas.saves==0,"canvas save leaked on failure");
   check(NebulaProfileGlass.captures-captures==NebulaProfileGlass.ends-ends,"glass recording not closed on failure");cases++;
  }
  System.out.println(cases+" production banner state cases: music boundary, expansion, gallery, theme, fallback, shader reuse and exception restoration passed");
 }
}
"""
with tempfile.TemporaryDirectory(prefix='nebula-profile-banner-') as folder:
 work=Path(folder)
 for name,content in {**contracts,**android,'app/nebulagram/ui/ProfileCheck.java':check}.items():
  dest=work/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(content,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',str(work/'classes'),*[str(work/name) for name in {**contracts,**android,'app/nebulagram/ui/ProfileCheck.java':check}],str(ui/'NebulaProfileArt.java')],check=True)
 subprocess.run(['java','-cp',str(work/'classes'),'app.nebulagram.ui.ProfileCheck'],check=True)
 sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home()/'AppData/Local/Android/Sdk'))
 platforms=sorted((p for p in (sdk/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)),key=lambda p:tuple(map(int,p.parent.name.removeprefix('android-').split('.'))))
 if not platforms:raise SystemExit('Android SDK platform required for profile API check')
 subprocess.run(['javac','-encoding','UTF-8','-cp',str(platforms[-1]),'-d',str(work/'sdk-classes'),*[str(work/name) for name in contracts],str(ui/'NebulaProfileArt.java')],check=True)
 print('Production profile hero/action hook typechecked against '+platforms[-1].parent.name)
