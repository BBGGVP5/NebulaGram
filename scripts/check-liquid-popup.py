"""Execute popup effect lifecycle and selection geometry using production classes."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs = {
    'android/annotation/TargetApi.java': 'package android.annotation;public @interface TargetApi {int value();}',
    'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=31;}}',
    'android/graphics/Canvas.java': 'package android.graphics;public class Canvas {}',
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
     public void draw(android.graphics.Canvas c){int p=org.telegram.messenger.AndroidUtilities.dp(6);draws.add(new int[]{left+p,top+p,right-p,bottom-p,alpha});}}''',
    'Check.java': '''import app.nebulagram.ui.*;import android.view.View;import android.graphics.*;import org.telegram.ui.ActionBar.*;
     import org.telegram.messenger.*;import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
     class Check {static void check(boolean b,String why){if(!b)throw new AssertionError(why);}public static void main(String[] args){
      View view=new View();NebulaMenuFocus.apply(view,8);check(view.effect!=null&&view.effect.radius==2,"popup-only focus radius");
      int creations=RenderEffect.created;for(int i=0;i<100;i++)NebulaMenuFocus.apply(view,8);check(RenderEffect.created==creations,"reuse effect, no per-frame allocation");
      NebulaMenuFocus.clear(view);check(view.effect==null,"detach/finish clears effect");
      AndroidUtilities.density=2;NebulaMenuFocus.apply(view,8);check(RenderEffect.created==creations+1&&view.effect.radius==4,"density change rebuilds cached effect");
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
       BlurredBackgroundDrawable material=new BlurredBackgroundDrawable();float factor=frame/100f;
       NebulaSelectionGlass.drawActions(new Canvas(),menu,material,width,0,AndroidUtilities.dp(60),factor);
       check(material.draws.size()==visible,"one surface per visible native action; no title background reuse");
       int previousRight=-1;
       for(int[] draw:material.draws){check(draw[0]>previousRight,"visible action surfaces remain separate");previousRight=draw[2];
        check(draw[4]==Math.round(255*factor),"material follows selection transition");}
       check(material.left==(visible>0?first:0)&&material.right==(visible>0?last:0),"native touch union retained after separate draws");cases++;
      }
      for(int i=0;i<=1000;i++){float p=i/1000f;
       check(NebulaMenuMotion.response(p)>=0&&NebulaMenuMotion.response(p)<=1.025,"bounded response");
       check(NebulaMenuMotion.radius(24,200,p)>=24&&NebulaMenuMotion.radius(24,200,p)<=100,"rounded source morph");
       check(NebulaMenuMotion.focus(p)>=0&&NebulaMenuMotion.focus(p)<=12,"bounded focus");
      }
      System.out.println(cases+" separate selection action cases; popup effect cache, API/power cleanup and spring bounds passed");
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
                    *[str(ui / name) for name in ['NebulaMenuFocus.java', 'NebulaMenuMotion.java', 'NebulaSelectionGlass.java']]], check=True)
    subprocess.run(['java', '-cp', folder, 'Check'], check=True)
