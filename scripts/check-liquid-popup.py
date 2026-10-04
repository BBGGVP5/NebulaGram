"""Execute popup effect lifecycle and selection geometry using production classes."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs = {
    'android/annotation/TargetApi.java': 'package android.annotation;public @interface TargetApi {int value();}',
    'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=31;}}',
    'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {
     public java.util.ArrayList<org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable> nodes=new java.util.ArrayList<>();}''',
    'android/graphics/Shader.java': 'package android.graphics;public class Shader {public enum TileMode {CLAMP}}',
    'android/graphics/RenderEffect.java': '''package android.graphics;public class RenderEffect {public static int created;public float radius;
     public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode mode){created++;RenderEffect e=new RenderEffect();e.radius=x;return e;}}''',
    'android/view/View.java': '''package android.view;public class View {public static final int VISIBLE=0;public int visibility,width=48;public float x,alpha=1;public boolean hardware=true;
     public android.graphics.RenderEffect effect;public boolean isHardwareAccelerated(){return hardware;}public void setRenderEffect(android.graphics.RenderEffect e){effect=e;}
     public int getVisibility(){return visibility;}public int getWidth(){return width;}public float getX(){return x;}public float getAlpha(){return alpha;}}''',
    'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger;public class AndroidUtilities {public static float density=1;public static int dp(float v){return Math.round(v*density);}public static float dpf2(float v){return v*density;}}',
    'org/telegram/messenger/LiteMode.java': 'package org.telegram.messenger;public class LiteMode {public static int FLAG_CHAT_BLUR=1;public static boolean enabled=true;public static boolean isEnabled(int n){return enabled;}}',
    'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui;public class NebulaMenuStyle {public static boolean enabled=true;public static boolean animated(){return enabled;}}',
    'app/nebulagram/ui/NebulaGlass.java': 'package app.nebulagram.ui;public class NebulaGlass {public static boolean reduced;public static boolean reduced(){return reduced;}}',
    'org/telegram/ui/ActionBar/ActionBarMenuItem.java': 'package org.telegram.ui.ActionBar;public class ActionBarMenuItem extends android.view.View {}',
    'org/telegram/ui/ActionBar/ActionBarMenu.java': '''package org.telegram.ui.ActionBar;public class ActionBarMenu extends android.view.View {
     public java.util.ArrayList<android.view.View> children=new java.util.ArrayList<>();public int getChildCount(){return children.size();}public android.view.View getChildAt(int i){return children.get(i);}}''',
    'org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java': '''package org.telegram.ui.Components.blur3.drawable;public class BlurredBackgroundDrawable {
     public int left,top,right,bottom,alpha;public java.util.ArrayList<int[]> draws=new java.util.ArrayList<>();
     public void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}public void setAlpha(int a){alpha=a;}
     public float ox,oy; public void setSourceOffset(float x,float y){ox=x;oy=y;}public float getSourceOffsetX(){return ox;}public float getSourceOffsetY(){return oy;}
     public BlurredBackgroundDrawable setColorProvider(Object p){return this;}public BlurredBackgroundDrawable setRadius(float r){return this;}
     public BlurredBackgroundDrawable setPadding(int p){return this;}
     public void updateColors(){}
     public void draw(android.graphics.Canvas c){c.nodes.add(this);}}''',
    'org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundColorProvider.java': 'package org.telegram.ui.Components.blur3.drawable.color;public class BlurredBackgroundColorProvider {}',
    'org/telegram/ui/Components/blur3/BlurredBackgroundDrawableViewFactory.java': '''package org.telegram.ui.Components.blur3;public class BlurredBackgroundDrawableViewFactory {
     public int created;public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable create(){created++;return new org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable();}}''',
    'Check.java': '''import app.nebulagram.ui.*;import android.view.View;import android.graphics.*;import org.telegram.ui.ActionBar.*;
     import org.telegram.messenger.*;import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
     class Check {static void check(boolean b,String why){if(!b)throw new AssertionError(why);}public static void main(String[] args){
      View view=new View();NebulaMenuFocus.apply(view,6);check(view.effect!=null&&view.effect.radius==4,"popup-only focus radius");
      int creations=RenderEffect.created;for(int i=0;i<100;i++)NebulaMenuFocus.apply(view,6);check(RenderEffect.created==creations,"reuse effect, no per-frame allocation");
      NebulaMenuFocus.clear(view);check(view.effect==null,"detach/finish clears effect");
      AndroidUtilities.density=2;NebulaMenuFocus.apply(view,6);check(RenderEffect.created==creations+1&&view.effect.radius==8,"density change rebuilds cached effect");
      for(int guard=0;guard<4;guard++){
       NebulaMenuStyle.enabled=guard!=0;NebulaGlass.reduced=guard==1;LiteMode.enabled=guard!=2;view.hardware=guard!=3;
       NebulaMenuFocus.apply(view,12);check(view.effect==null,"animation, power, blur and software gates clear focus");
      }
      NebulaMenuStyle.enabled=true;NebulaGlass.reduced=false;LiteMode.enabled=true;view.hardware=true;
      android.os.Build.VERSION.SDK_INT=23;creations=RenderEffect.created;NebulaMenuFocus.apply(view,12);NebulaMenuFocus.clear(view);
      check(RenderEffect.created==creations,"old API never instantiates GPU effects");android.os.Build.VERSION.SDK_INT=31;
      int cases=0;
      for(float density:new float[]{1,1.5f,2.75f})for(int count=0;count<=6;count++)for(int flags=0;flags<8;flags++)for(int frame=0;frame<=100;frame++){
       AndroidUtilities.density=density;ActionBarMenu menu=new ActionBarMenu();menu.x=AndroidUtilities.dp(-10);
       View title=new View();title.x=AndroidUtilities.dp(85);menu.children.add(title);
       int width=AndroidUtilities.dp(393),first=width,last=0,visible=0;
       for(int i=0;i<count;i++){ActionBarMenuItem item=new ActionBarMenuItem();item.width=AndroidUtilities.dp(48);item.x=AndroidUtilities.dp(403-count*48+i*48);
        if(i==0){item.visibility=(flags&1)!=0?8:0;item.alpha=(flags&2)!=0?0:1;if((flags&4)!=0)item.width=0;}
        menu.children.add(item);if(item.visibility==0&&item.alpha>0&&item.width>0){visible++;int l=Math.round(menu.x+item.x);first=Math.min(first,l);last=Math.max(last,l+item.width);}
       }
       int counterRight=NebulaSelectionGlass.counterRight(menu,width);
       check(counterRight==(visible>0?first-AndroidUtilities.dp(4):width),"title surface stops before first visible action");
       BlurredBackgroundDrawable material=new BlurredBackgroundDrawable();material.setSourceOffset(31,47);float factor=frame/100f;
       NebulaSelectionGlass selection=new NebulaSelectionGlass();
       org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory factory=new org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory();
       selection.setup(factory,null);Canvas canvas=new Canvas();
       selection.drawActions(canvas,menu,material,width,0,AndroidUtilities.dp(60),factor);
       check(canvas.nodes.size()==(visible>0?1:0),"one shared capsule for visible actions");
       for(BlurredBackgroundDrawable draw:canvas.nodes){
        check(draw!=material,"group GPU node must not alias the native touch union");
        check(draw.left==first&&draw.right==last,"group spans exactly the visible actions");
        check(draw.top==0&&draw.bottom==AndroidUtilities.dp(60),"group aligns with the selection header");
        check(draw.alpha==Math.round(255*factor),"material follows selection transition");
        check(draw.ox==31&&draw.oy==47,"same backdrop alignment as the header");}
       int allocated=factory.created;canvas.nodes.clear();
       selection.drawActions(canvas,menu,material,width,0,AndroidUtilities.dp(60),factor);
       check(factory.created==allocated,"no material allocation on subsequent frames");
       check(material.left==(visible>0?first:0)&&material.right==(visible>0?last:0),"native touch union retained after grouped draw");cases++;
      }
      NebulaMenuBubble.Frame bubble=new NebulaMenuBubble.Frame(),captured=new NebulaMenuBubble.Frame();
      for(float w:new float[]{64,240,600})for(float h:new float[]{64,400,900})for(int corner=0;corner<4;corner++){
       float x=(corner&1)==0?0:w,y=(corner&2)==0?0:h;
       NebulaMenuBubble.opening(bubble,0,w,h,x,y,48,24);
       check(bubble.width==48&&bubble.height==48&&bubble.radius==24&&bubble.content==0,"starts as a bubble, labels hidden");
       check(bubble.x==x&&bubble.y==y,"first bubble stays at the true initiating control");
       for(int i=0;i<=1000;i++){
        NebulaMenuBubble.opening(bubble,i/1000f,w,h,x,y,48,24);
        check(bubble.width>=48&&bubble.width<=w*1.05f&&bubble.height>=48&&bubble.height<=h*1.05f,"bounded independent growth");
        check(bubble.content>=0&&bubble.content<=1&&bubble.alpha>=0&&bubble.alpha<=1,"bounded focus/opacity");
        float outset=NebulaMenuBubble.outset(w,h,x,y,48,16);
        check(bubble.x-bubble.width/2>=-outset&&bubble.x+bubble.width/2<=w+outset
          &&bubble.y-bubble.height/2>=-outset&&bubble.y+bubble.height/2<=h+outset,"drawing envelope retains the arc without moving or resizing logical menu bounds");
       }
       check(bubble.x==w/2&&bubble.y==h/2&&bubble.width==w&&bubble.height==h&&bubble.radius==24&&bubble.content==1,"exact native endpoint");
       for(float interrupt:new float[]{0,.05f,.2f,.45f,1}){
        NebulaMenuBubble.opening(bubble,interrupt,w,h,x,y,48,24);captured.copy(bubble);
        NebulaMenuBubble.closing(bubble,captured,0,w,h,x,y,48);
        check(bubble.x==captured.x&&bubble.y==captured.y&&bubble.width==captured.width&&bubble.content==captured.content,"interrupted close starts at displayed frame");
        float previous=bubble.width;
        for(int i=1;i<=100;i++){
         NebulaMenuBubble.closing(bubble,captured,i/100f,w,h,x,y,48);
         check(bubble.width<=previous+.001f,"closing shrinks before returning");previous=bubble.width;
        }
        check(bubble.alpha==0&&bubble.content==0&&bubble.width==48,"returns to source and disappears");
       }
      }
      int flat=0,longestFlat=0;float priorWidth=48;
      for(int ms=1;ms<=500;ms++) {
       NebulaMenuBubble.opening(bubble,ms/750f,240,400,216,-12,48,24);
       flat=bubble.width>242&&Math.abs(bubble.width-priorWidth)<.0001f?flat+1:0;
       longestFlat=Math.max(longestFlat,flat);priorWidth=bubble.width;
      }
      check(longestFlat<5,"spring must never hit a flat overshoot cap");
      NebulaMenuBubble.opening(bubble,.3f,240,400,216,-12,48,24);captured.copy(bubble);
      check(NebulaMenuBubble.closeDuration(captured,240,400,48)>=380,"early dismiss of a grown panel must not snap shut");
      NebulaMenuBubble.closing(bubble,captured,.001f,240,400,216,-12,48);
      check(captured.width-bubble.width<(captured.width-48)*.0001f,"close starts with a soft size response");
      for(float x:new float[]{-500,-24,120,264,800})for(float y:new float[]{-600,-24,200,424,1200}) {
       float envelope=NebulaMenuBubble.outset(240,400,x,y,48,16);
       NebulaMenuBubble.opening(bubble,0,240,400,x,y,48,24);
       check(bubble.x==x&&bubble.y==y,"out-of-menu anchor must not be clamped to an edge");
       for(int i=0;i<=1000;i++) {
        NebulaMenuBubble.opening(bubble,i/1000f,240,400,x,y,48,24);
        check(bubble.x-bubble.width/2>=-envelope&&bubble.x+bubble.width/2<=240+envelope
         &&bubble.y-bubble.height/2>=-envelope&&bubble.y+bubble.height/2<=400+envelope,"outsets must cover all intermediate frames, including source outside menu");
       }
      }
      NebulaMenuBubble.opening(bubble,.03f,240,400,216,-12,48,24);
      check(bubble.x<216&&bubble.y> -12,"bubble moves in both axes instead of staying pinned to upper right");
      check(bubble.y-bubble.height/2< -8 || bubble.x+bubble.width/2>248,"trajectory actually leaves the old viewport");
      for(int edge=0;edge<4;edge++) {
       float x=(edge&1)==0?30:210,y=(edge&2)==0?30:370;
       float lastX=x,lastY=y;
       for(int i=0;i<=1000;i++) {
        NebulaMenuBubble.openingWithin(bubble,i/1000f,240,400,x,y,48,24,-10,-10,250,410);
        check(bubble.x-bubble.width/2>=-10.01f&&bubble.x+bubble.width/2<=250.01f
         &&bubble.y-bubble.height/2>=-10.01f&&bubble.y+bubble.height/2<=410.01f,"edge-aware center timing retains the growing bubble on the actual screen");
        check(Math.abs(bubble.x-lastX)<4&&Math.abs(bubble.y-lastY)<4,"screen-edge lead must be continuous");lastX=bubble.x;lastY=bubble.y;
       }
      }
      NebulaMenuBubble.openingWithin(bubble,.12f,240,400,210,30,48,24,-10,-10,250,410);
      check(bubble.x+bubble.width/2<248,"inward bow must leave breathing room instead of locking the edge to the screen");
      for(int i=0;i<=1000;i++){float p=i/1000f;
       check(NebulaMenuMotion.response(p)>=0&&NebulaMenuMotion.response(p)<=1.025,"bounded response");
       check(NebulaMenuMotion.radius(24,200,p)>=24&&NebulaMenuMotion.radius(24,200,p)<=100,"rounded source morph");
       check(NebulaMenuMotion.focus(p)>=0&&NebulaMenuMotion.focus(p)<=12,"bounded focus");
      }
      System.out.println(cases+" grouped selection action cases; popup effect cache, API/power cleanup and spring bounds passed");
     }}''',
}
with tempfile.TemporaryDirectory(prefix='nebula-liquid-popup-') as folder:
    work = Path(folder)
    for name, content in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder,
                    *[str(work / name) for name in stubs],
                    *[str(ui / name) for name in ['NebulaMenuFocus.java', 'NebulaMenuMotion.java', 'NebulaMenuBubble.java', 'NebulaSelectionGlass.java']]], check=True)
    subprocess.run(['java', '-cp', folder, 'Check'], check=True)
