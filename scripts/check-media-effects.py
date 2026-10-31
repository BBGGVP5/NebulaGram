"""Run production draw helpers with recorded sampling, shader and canvas activity."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sources={
'app/nebulagram/ui/NebulaFeatureSettings.java':'package app.nebulagram.ui;public class NebulaFeatureSettings {public static boolean on;public static boolean enabled(String k){return on;}}',
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static int dp(int v){return v;}}',
'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=33;}}',
'android/os/SystemClock.java':'package android.os;public class SystemClock {public static long now;public static long uptimeMillis(){return now;}}',
'android/graphics/Shader.java':'package android.graphics;public class Shader {public enum TileMode {CLAMP}}',
'android/graphics/RadialGradient.java':'package android.graphics;public class RadialGradient extends Shader {public static int created;public RadialGradient(float x,float y,float r,int[] c,float[] p,TileMode mode){created++;if(r<=0)throw new AssertionError();}}',
'android/graphics/Paint.java':'package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1;public Paint(int f){}public void setShader(Shader s){}}',
'android/graphics/Bitmap.java':'''package android.graphics;public class Bitmap {public static int reads,created;public boolean recycled;public int generation=1;public enum Config{ARGB_8888}
 public static Bitmap createBitmap(int w,int h,Config c){if(w*h>256)throw new AssertionError();created++;return new Bitmap();}
 public boolean isRecycled(){return recycled;}public int getGenerationId(){return generation;}public int getWidth(){return 16;}public int getHeight(){return 16;}
 public int getPixel(int x,int y){if(recycled)throw new IllegalStateException();reads++;return 0xff880000|(x<<8)|y;}}''',
'android/graphics/Canvas.java':'''package android.graphics;public class Canvas {public int draws,saves,scales;public int getWidth(){return 360;}public int getHeight(){return 720;}
 public int save(){return ++saves;}public void scale(float x,float y,float px,float py){scales++;if(x!=y||x<.92f||x>1)throw new AssertionError();}public void drawRect(float l,float t,float r,float b,Paint p){draws++;}}''',
'android/view/View.java':'''package android.view;public class View {public int invalidations,width=360,height=60;public float x,y;
 public void postInvalidateDelayed(long delay){if(delay<500)throw new AssertionError();invalidations++;}public int getWidth(){return width;}public int getHeight(){return height;}public float getX(){return x;}public float getY(){return y;}}''',
'android/view/ViewGroup.java':'package android.view;public class ViewGroup extends View {public int getPaddingTop(){return 10;}public int getPaddingBottom(){return 10;}}',
'android/view/TextureView.java':'package android.view;import android.graphics.Bitmap;public class TextureView extends View {public int samples;public boolean isAvailable(){return true;}public Bitmap getBitmap(Bitmap b){samples++;return b;}}',
'androidx/recyclerview/widget/RecyclerView.java':'package androidx.recyclerview.widget;public class RecyclerView extends android.view.ViewGroup {public ItemAnimator animator=new ItemAnimator();public ItemAnimator getItemAnimator(){return animator;}public static class ItemAnimator {public boolean running;public boolean isRunning(){return running;}}}',
'MediaEffectsCheck.java':r'''
import app.nebulagram.ui.*;import android.graphics.*;import android.view.*;import android.os.*;
class MediaEffectsCheck{
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  var effect=new NebulaMediaAmbient();var canvas=new Canvas();var host=new View();var image=new Bitmap();var video=new TextureView();
  effect.draw(canvas,host,image,video);ok(Bitmap.reads==0&&Bitmap.created==0&&host.invalidations==0);
  NebulaFeatureSettings.on=true;
  for(int i=0;i<100;i++){SystemClock.now=i*16;effect.draw(canvas,host,image,null);}
  ok(Bitmap.reads==4&&RadialGradient.created==4&&host.invalidations==0);
  SystemClock.now=2000;
  for(int i=0;i<100;i++){SystemClock.now=2000+i*16;effect.draw(canvas,host,image,video);}
  ok(video.samples==4&&Bitmap.created==1&&host.invalidations==4);
  int reads=Bitmap.reads;for(int i=0;i<100;i++){SystemClock.now+=16;effect.draw(canvas,host,image,null);}ok(Bitmap.reads==reads);
  image.generation++;effect.draw(canvas,host,image,null);ok(Bitmap.reads==reads+4);
  NebulaFeatureSettings.on=false;reads=Bitmap.reads;effect.draw(canvas,host,image,video);ok(Bitmap.reads==reads&&host.invalidations==4);
  var list=new androidx.recyclerview.widget.RecyclerView();list.height=600;var child=new View();child.y=-20;
  ok(NebulaMessageEdge.begin(canvas,child,list)==-1);NebulaFeatureSettings.on=true;
  Build.VERSION.SDK_INT=32;ok(NebulaMessageEdge.begin(canvas,child,list)==-1);Build.VERSION.SDK_INT=33;
  list.animator.running=true;ok(NebulaMessageEdge.begin(canvas,child,list)==-1);list.animator.running=false;
  ok(NebulaMessageEdge.begin(canvas,child,list)>=0&&canvas.scales==1);
  child.y=100;ok(NebulaMessageEdge.begin(canvas,child,list)==-1);child.y=580;ok(NebulaMessageEdge.begin(canvas,child,list)>=0);
  for(int y=-1000;y<1000;y++){float scale=NebulaMessageEdge.scale(y,y+60,10,590,56);ok(scale>=.92f&&scale<=1);}
  System.out.println("Media effects: opt-out, bounded frame sampling/invalidation, static reuse, SDK/animator guards and 2000 edge poses passed");
 }
}
'''}
with tempfile.TemporaryDirectory(prefix='nebula-media-effects-') as directory:
    work=Path(directory)
    for name,source in sources.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaMediaAmbient.java'),str(ui/'NebulaMessageEdge.java'),*[str(p) for p in work.rglob('*.java')]],check=True)
    subprocess.run(['java','-cp',str(work),'MediaEffectsCheck'],check=True)
