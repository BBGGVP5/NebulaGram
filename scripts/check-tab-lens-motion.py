"""Exercise production lens retargeting, feedback and view lifecycle deterministically."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
sources = {
'android/graphics/RectF.java': '''package android.graphics;public class RectF {public float left,top,right,bottom;public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public void set(RectF r){set(r.left,r.top,r.right,r.bottom);}public void inset(float x,float y){left+=x;right-=x;top+=y;bottom-=y;}public float width(){return right-left;}public float height(){return bottom-top;}public float centerX(){return (left+right)/2;}public boolean isEmpty(){return width()<=0||height()<=0;}public void setEmpty(){set(0,0,0,0);}}''',
'android/graphics/Shader.java': '''package android.graphics;public class Shader {public enum TileMode {CLAMP}public void setLocalMatrix(Matrix m){}}''',
'android/graphics/LinearGradient.java': '''package android.graphics;public class LinearGradient extends Shader {public LinearGradient(float a,float b,float c,float d,int[] colors,float[] positions,TileMode mode){}}''',
'android/graphics/Matrix.java': '''package android.graphics;public class Matrix {public void setScale(float x,float y){}public void postTranslate(float x,float y){}}''',
'android/graphics/Color.java': '''package android.graphics;public class Color {}''',
'android/graphics/Paint.java': '''package android.graphics;public class Paint {public static final int ANTI_ALIAS_FLAG=1;public enum Style {STROKE}float stroke;public Paint(int f){}public void setStyle(Style s){}public void setStrokeWidth(float f){stroke=f;}public float getStrokeWidth(){return stroke;}public void setShader(Shader s){}public void setColor(int c){}}''',
'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {public int draws;public void drawRoundRect(RectF r,float x,float y,Paint p){draws++;}}''',
'android/view/View.java': '''package android.view;public class View {public int invalidations;public void invalidate(){invalidations++;}public int getWidth(){return 600;}public int getHeight(){return 60;}public int getPaddingLeft(){return 0;}public int getPaddingRight(){return 0;}public interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}OnAttachStateChangeListener listener;public void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listener=l;}public void detach(){listener.onViewDetachedFromWindow(this);}}''',
'android/view/animation/LinearInterpolator.java': '''package android.view.animation;public class LinearInterpolator {}''',
'android/animation/ValueAnimator.java': '''package android.animation;import java.util.*;public class ValueAnimator {public static boolean enabled=true;public static boolean areAnimatorsEnabled(){return enabled;}public static final List<ValueAnimator> all=new ArrayList<>();public interface Listener{void update(ValueAnimator a);}Listener listener;float start,end,value;public boolean running;public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.start=a;v.end=b;all.add(v);return v;}public void setDuration(int d){}public void setInterpolator(Object i){}public void addUpdateListener(Listener l){listener=l;}public Object getAnimatedValue(){return value;}public void start(){running=true;advance(0);}public void advance(float t){if(!running)return;value=start+(end-start)*t;listener.update(this);if(t==1)running=false;}public void cancel(){running=false;}}''',
'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;public class AndroidUtilities {public static float dpf2(float f){return f;}}''',
'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar;public class Theme {public static int multAlpha(int c,float a){return c;}public static boolean isCurrentThemeDark(){return true;}}''',
'org/telegram/ui/Components/CubicBezierInterpolator.java': '''package org.telegram.ui.Components;public class CubicBezierInterpolator {public static final CubicBezierInterpolator EASE_OUT_QUINT=new CubicBezierInterpolator();public float getInterpolation(float t){return (float)(1-Math.pow(1-t,5));}}''',
'app/nebulagram/ui/NebulaGlass.java': '''package app.nebulagram.ui;public class NebulaGlass {public static boolean reduced,highlights=true,animations=true;public static boolean animations(){return animations;}public static boolean reduced(){return reduced;}public static boolean highlights(){return highlights;}}''',
'android/os/Build.java': '''package android.os;public class Build {public static class VERSION {public static int SDK_INT=37;}}''',
'LensCheck.java': r'''
import app.nebulagram.ui.*;import android.graphics.*;import android.view.*;import android.animation.*;import java.lang.reflect.*;
public class LensCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static RectF bounds(NebulaTabLens lens)throws Exception{Field f=NebulaTabLens.class.getDeclaredField("paintedBounds");f.setAccessible(true);RectF copy=new RectF();copy.set((RectF)f.get(lens));return copy;}
 static void same(RectF a,RectF b){check(Math.abs(a.left-b.left)<.01&&Math.abs(a.right-b.right)<.01&&Math.abs(a.top-b.top)<.01&&Math.abs(a.bottom-b.bottom)<.01,"interrupted animation jumped");}
 public static void main(String[] args)throws Exception {
  View view=new View();Canvas c=new Canvas();NebulaTabLens lens=new NebulaTabLens(view);
  lens.draw(c,10,5,110,55,0);lens.draw(c,210,5,310,55,0);
  for(int i=0;i<200;i++){
   ValueAnimator.all.get(ValueAnimator.all.size()-1).advance(.13f+(i%6)*.1f);
   int x=i%2==0?210:410;lens.draw(c,x,5,x+100,55,0);RectF before=bounds(lens);
   int next=i%2==0?410:210;lens.draw(c,next,5,next+100,55,0);same(before,bounds(lens));
  }
  lens.drawDrag(c,100,5,200,55,0);check(Math.abs(bounds(lens).left-100)<.01,"drag takeover changed position");
  lens.setPressed(true);int count=ValueAnimator.all.size();for(int i=0;i<1000;i++)lens.setPressed(true);check(ValueAnimator.all.size()==count,"repeated press restarts animation");
  lens.pulse();view.detach();check(ValueAnimator.all.stream().noneMatch(a->a.running),"detached view still animates");
  check(bounds(lens).isEmpty(),"detached lens retains stale outline");
  NebulaGlass.reduced=true;count=ValueAnimator.all.size();lens.draw(c,20,5,120,55,0);lens.draw(c,220,5,320,55,0);lens.pulse();lens.setPressed(true);lens.setPressed(false);
  check(ValueAnimator.all.size()==count&&Math.abs(bounds(lens).left-220)<.01,"reduced mode still creates animations");
  NebulaGlass.reduced=false;NebulaGlass.animations=false;lens.draw(c,420,5,520,55,0);check(ValueAnimator.all.size()==count,"disabled motion ignored");
  NebulaGlass.animations=true;ValueAnimator.enabled=false;lens.draw(c,420,5,520,55,0);check(ValueAnimator.all.size()==count,"system animation setting ignored");
  NebulaGlass.highlights=false;c.draws=0;lens.draw(c,420,5,520,55,0);check(c.draws==2,"disabled highlights still paint edges");
  int redraws=view.invalidations;for(int i=0;i<10000;i++)lens.draw(c,420,5,520,55,0);check(view.invalidations==redraws,"idle lens invalidates itself");
  System.out.println("Lens: 200 interrupted transitions, drag takeover, duplicate press, detach, reduced motion/highlights and 10,000 idle frames passed");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-lens-') as directory:
    path = Path(directory)
    for name, content in sources.items():
        file = path / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_text(content, encoding='utf-8')
    production = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaTabLens.java'
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, *map(str, path.rglob('*.java')), str(production)], check=True)
    subprocess.run(['java', '-cp', directory, 'LensCheck'], check=True)
