"""Exercise production drawing outsets/source lifecycle without changing native popup hit geometry."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs = {
    'android/graphics/Color.java': 'package android.graphics;public class Color {public static int alpha(int c){return c>>>24;}}',
    'android/graphics/Rect.java': 'package android.graphics;public class Rect {public int left,top,right,bottom;}',
    'android/graphics/Paint.java': 'package android.graphics;public class Paint {public static int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public int alpha;public Paint(int flags){}public void setAlpha(int a){alpha=a;}}',
    'android/graphics/Bitmap.java': '''package android.graphics;public class Bitmap {public enum Config {ARGB_8888}public int w,h;public boolean recycled;
     public static Bitmap createBitmap(int w,int h,Config c){Bitmap b=new Bitmap();b.w=w;b.h=h;return b;}public int getWidth(){return w;}public int getHeight(){return h;}public void recycle(){recycled=true;}}''',
    'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {public int draws,alpha;public float x,y;public Canvas(Bitmap b){}
     public void drawBitmap(Bitmap b,float x,float y,Paint p){draws++;alpha=p.alpha;this.x=x;this.y=y;}}''',
    'android/graphics/drawable/Drawable.java': 'package android.graphics.drawable;public class Drawable {}',
    'android/graphics/drawable/ColorDrawable.java': 'package android.graphics.drawable;public class ColorDrawable extends Drawable {public int c;public ColorDrawable(int c){this.c=c;}public int getColor(){return c;}}',
    'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}',
    'android/view/ViewOutlineProvider.java': 'package android.view;public class ViewOutlineProvider {}',
    'android/view/Gravity.java': '''package android.view;public class Gravity {public static int LEFT=3,RIGHT=5,TOP=48,BOTTOM=80,CENTER_HORIZONTAL=1,CENTER_VERTICAL=16,
     HORIZONTAL_GRAVITY_MASK=7,VERTICAL_GRAVITY_MASK=112;public static int getAbsoluteGravity(int g,int dir){return g;}}''',
    'android/view/ViewParent.java': 'package android.view;public interface ViewParent {ViewParent getParent();}',
    'android/view/View.java': '''package android.view;public class View implements ViewParent {
     public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
     public java.util.ArrayList<OnAttachStateChangeListener> listeners=new java.util.ArrayList<>();
     public int x,y,w=240,h=400,draws;public float elevation,alpha=1;public boolean attached=true,failDraw;
     public View parent;public ViewOutlineProvider outline=new ViewOutlineProvider();
     public int getMeasuredWidth(){return w;}public int getMeasuredHeight(){return h;}public int getWidth(){return w;}public int getHeight(){return h;}
     public boolean isAttachedToWindow(){return attached;}public void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}
     public void getLocationInWindow(int[] p){p[0]=x;p[1]=y;}public void getWindowVisibleDisplayFrame(android.graphics.Rect r){r.left=0;r.top=24;r.right=1080;r.bottom=1920;}
     public View getRootView(){return parent==null?this:parent.getRootView();}public View getParent(){return parent;}public int getLayoutDirection(){return 0;}
     public float getElevation(){return elevation;}public void setElevation(float e){elevation=e;}
     public ViewOutlineProvider getOutlineProvider(){return outline;}public void setOutlineProvider(ViewOutlineProvider p){outline=p;}
     public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
     public void detach(){attached=false;for(OnAttachStateChangeListener l:new java.util.ArrayList<>(listeners))l.onViewDetachedFromWindow(this);}
     public float getAlpha(){return alpha;}public void setAlpha(float a){alpha=a;}public void draw(android.graphics.Canvas c){if(failDraw)throw new RuntimeException();draws++;}}
     ''',
    'android/view/ViewGroup.java': '''package android.view;public class ViewGroup extends View {public boolean children=true,padding=true;
     public java.util.ArrayList<View> views=new java.util.ArrayList<>();public void add(View v){views.add(v);v.parent=this;}
     public int getChildCount(){return views.size();}public View getChildAt(int i){return views.get(i);}
     public boolean getClipChildren(){return children;}public boolean getClipToPadding(){return padding;}
     public void setClipChildren(boolean b){children=b;}public void setClipToPadding(boolean b){padding=b;}}''',
    'android/widget/PopupWindow.java': '''package android.widget;public class PopupWindow {public boolean showing,clipping=true;public float elevation=4;
     public android.view.View content;public int x=42,y=78,w=240,h=400;public android.graphics.drawable.Drawable background;
     public boolean isShowing(){return showing;}public boolean isClippingEnabled(){return clipping;}public float getElevation(){return elevation;}public void setElevation(float v){elevation=v;}
     public android.graphics.drawable.Drawable getBackground(){return background;}}''',
    'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger;public class AndroidUtilities {public static int dp(float x){return (int)Math.ceil(x);}}',
    'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui;public class NebulaMenuStyle {public static boolean enabled=true;public static boolean animated(){return enabled;}}',
    'app/nebulagram/ui/NebulaMenuReveal.java': 'package app.nebulagram.ui;public class NebulaMenuReveal {public android.view.View anchor;public boolean ready;public android.view.View getAnchor(){return anchor;}public void setViewportReady(boolean r){ready=r;}}',
    'org/telegram/ui/ActionBar/ActionBarPopupWindow.java': '''package org.telegram.ui.ActionBar;public class ActionBarPopupWindow {
     public static class ActionBarPopupWindowLayout extends android.view.ViewGroup {public boolean shownFromBottom;public app.nebulagram.ui.NebulaMenuReveal nebulaReveal=new app.nebulagram.ui.NebulaMenuReveal();}}''',
    'Check.java': '''import android.view.*;import android.widget.*;import android.graphics.*;import android.graphics.drawable.*;
     import app.nebulagram.ui.*;import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
     public class Check {static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}public static void main(String[] args) {
      NebulaMenuViewport viewport=new NebulaMenuViewport();View app=new View();app.w=1080;app.h=1920;app.x=0;app.y=24;
      View anchor=new View();anchor.w=anchor.h=48;anchor.x=990;anchor.y=70;
      ViewGroup root=new ViewGroup(),nested=new ViewGroup();ActionBarPopupWindowLayout menu=new ActionBarPopupWindowLayout();root.add(nested);nested.add(menu);
      menu.nebulaReveal.anchor=anchor;root.padding=false;nested.children=false;root.elevation=3;ViewOutlineProvider outline=root.outline;
      PopupWindow window=new PopupWindow();window.content=root;window.background=new ColorDrawable(0);
      for(int cycle=0;cycle<20;cycle++) {
       root.attached=true;
       viewport.prepare(window,root,app,Gravity.LEFT|Gravity.TOP,790,110,false);
       ViewGroup framework=new ViewGroup();framework.add(root);viewport.onViewAttachedToWindow(root);
       check(!framework.children&&!framework.padding,"framework background wrapper created by show also allows extended surface");
       check(menu.nebulaReveal.ready&&root.outline==null,"reserve drawing surface before native show");
       check(window.content==root&&window.x==42&&window.y==78&&window.w==240&&window.h==400&&window.clipping,"native content, placement, dimensions, clipping and hit rectangle remain unchanged");
       check(!root.children&&!root.padding&&!nested.children&&!nested.padding&&!menu.children&&!menu.padding,"the complete ancestor path allows the bubble outside its logical rect");
       check(window.elevation*2>=NebulaMenuBubble.outset(240,400,224,-16,48,16),"SDK elevation reserves the full drawing envelope");
       root.detach();
       check(framework.children&&framework.padding,"framework wrapper clipping restored on detach");root.parent=null;
       check(!menu.nebulaReveal.ready&&root.listeners.isEmpty()&&root.outline==outline&&root.elevation==3&&window.elevation==4,"detach restores original outline, elevation and listener");
       check(root.children&&!root.padding&&!nested.children&&nested.padding&&menu.children&&menu.padding,"restore every original clipping flag on reuse");
      }
      for(int guard=0;guard<5;guard++) {
       root.attached=true;NebulaMenuStyle.enabled=guard!=0;android.os.Build.VERSION.SDK_INT=guard==1?19:36;
       window.background=guard==2?new ColorDrawable(0xff000000):new ColorDrawable(0);window.showing=guard==3;root.w=guard==4?0:240;
       viewport.prepare(window,root,app,Gravity.TOP,790,110,false);
       check(!menu.nebulaReveal.ready&&window.elevation==4&&root.outline==outline&&root.listeners.isEmpty(),"reduced mode, old API, nontransparent background, already showing and unmeasured roots keep native behavior");
      }
      NebulaMenuStyle.enabled=true;android.os.Build.VERSION.SDK_INT=36;window.background=null;window.showing=false;root.w=240;
      viewport.prepare(window,root,anchor,Gravity.TOP,-200,10,true);check(menu.nebulaReveal.ready,"drop-down path");viewport.restore();
      viewport.prepare(window,root,app,Gravity.BOTTOM|Gravity.RIGHT,10,20,false);check(menu.nebulaReveal.ready,"bottom/right path");viewport.restore();
      NebulaMenuSource source=new NebulaMenuSource();anchor.alpha=.7f;source.capture(anchor);check(anchor.alpha==0,"small source hidden only after successful snapshot");
      NebulaMenuBubble.Frame frame=new NebulaMenuBubble.Frame();frame.x=100;frame.y=200;frame.content=.25f;Canvas canvas=new Canvas(null);source.draw(canvas,frame);
      check(canvas.draws==1&&canvas.x==76&&canvas.y==176&&canvas.alpha==Math.round(.7f*.75f*255),"source follows bubble center at native size with content crossfade");
      frame.content=1;source.draw(canvas,frame);check(canvas.draws==1,"source is absent once content is fully revealed");source.clear();source.clear();check(anchor.alpha==.7f,"exact original source alpha restored idempotently");
      anchor.failDraw=true;source.capture(anchor);check(anchor.alpha==.7f,"snapshot failure never hides the source");anchor.failDraw=false;
      anchor.w=400;source.capture(anchor);check(anchor.alpha==.7f,"message-sized anchors are never captured or hidden");anchor.w=48;
      source.capture(anchor);View next=new View();next.w=next.h=40;source.capture(next);check(anchor.alpha==.7f&&next.alpha==0,"replacing source releases the previous control");source.clear();check(next.alpha==1,"replacement releases too");
      System.out.println("Drawing outsets preserve native window/hit geometry; clipping, reduced modes, source handoff and 20 reuses passed");
     }}''',
}
with tempfile.TemporaryDirectory(prefix='nebula-menu-viewport-') as folder:
    work = Path(folder)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, *map(str, work.rglob('*.java')),
                    *[str(ui / name) for name in ['NebulaMenuBubble.java', 'NebulaMenuSource.java', 'NebulaMenuViewport.java']]], check=True)
    subprocess.run(['java', '-cp', folder, 'Check'], check=True)
if len(sys.argv) > 1:
    popup = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java').read_text(encoding='utf-8')
    for signature, show in [('public void showAtLocation(', 'super.showAtLocation('),
                            ('public void showAsDropDown(View anchor, int xoff, int yoff, int gravity)', 'super.showAsDropDown(')]:
        start = popup.index(signature)
        assert popup.index('nebulaViewport.prepare(', start) < popup.index(show, start)
        assert 'if (!isShowing()) nebulaViewport.restore();' in popup[start:popup.index(show, start) + 250]
    assert 'nebulaReveal.drawSource(canvas);' in popup
    assert 'nebulaDrawGapStart = nebulaReveal.isMorphing() ? -1000000 : gapStartY;' in popup
    assert 'backgroundDrawable != null && !nebulaReveal.isMorphing()' in popup, 'gaps must travel as faded content instead of clipping the moving platter'
    assert 'public View getContentView(' not in popup, 'native callers must keep the original content identity'
    assert 'surfaceInsets' not in (ui / 'NebulaMenuViewport.java').read_text(), 'no hidden framework field access'
    print('Native show paths prepare outsets before attachment and restore on failure; native content getter unchanged')
