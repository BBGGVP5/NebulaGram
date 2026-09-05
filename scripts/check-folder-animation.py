"""Run the actual folder-title view against a controllable animation clock."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parent.parent;work=r/'build/folder-animation';work.mkdir(parents=True,exist_ok=True)
stubs={
'android/content/Context.java':'package android.content; public class Context {}',
'android/view/Gravity.java':'package android.view; public class Gravity {public static int LEFT=1,CENTER_VERTICAL=2;}',
'android/view/View.java':'''package android.view; public class View {
private float alpha=1,y; public boolean attached=true; protected android.content.Context context;
public View(android.content.Context c){context=c;}public android.content.Context getContext(){return context;}
public float getAlpha(){return alpha;}public void setAlpha(float a){alpha=a;}public float getTranslationY(){return y;}public void setTranslationY(float v){y=v;}
public int getWidth(){return 320;}public boolean isAttachedToWindow(){return attached;}public void invalidate(){}protected void onDetachedFromWindow(){attached=false;}
protected void onMeasure(int w,int h){}public static class MeasureSpec{public static int AT_MOST=1;public static int getSize(int v){return v;}public static int makeMeasureSpec(int w,int m){return w;}}
}''',
'android/widget/FrameLayout.java':'''package android.widget; public class FrameLayout extends android.view.View {
public java.util.List<android.view.View> children=new java.util.ArrayList<>();public FrameLayout(android.content.Context c){super(c);}public void setClipChildren(boolean b){}
public void addView(android.view.View v,Object p){children.add(v);}public void removeView(android.view.View v){children.remove(v);}public int getChildCount(){return children.size();}public android.view.View getChildAt(int i){return children.get(i);}
public static class LayoutParams {public static int WRAP_CONTENT=-2,MATCH_PARENT=-1;public LayoutParams(int w,int h){}}
}''',
'android/text/TextUtils.java':'package android.text; public class TextUtils {public static boolean equals(CharSequence a,CharSequence b){return a==b || a!=null&&b!=null&&a.toString().equals(b.toString());}}',
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger; public class AndroidUtilities {public static int dp(float x){return (int)x;} public static Object bold(){return null;} public static Size displaySize=new Size();public static class Size {public int x=640;}}',
'org/telegram/ui/Components/CubicBezierInterpolator.java':'package org.telegram.ui.Components; public class CubicBezierInterpolator {public static Object EASE_OUT_QUINT=new Object();}',
'org/telegram/ui/ActionBar/SimpleTextView.java':'''package org.telegram.ui.ActionBar; public class SimpleTextView extends android.view.View {
CharSequence text; public int cache;public SimpleTextView(android.content.Context c){super(c);}public void setText(CharSequence t){text=t;}public CharSequence getText(){return text;}
public void setEmojiCacheType(int c){cache=c;}public void setGravity(int g){}public void setTypeface(Object f){}public void setTextSize(int s){}public void setWidthWrapContent(boolean b){}public void setEllipsizeByGradient(boolean b){}public void setTextColor(int c){}
}''',
'android/animation/ValueAnimator.java':'''package android.animation; public class ValueAnimator {
public static ValueAnimator last;public static int started;float fraction;boolean cancelled;Listener listener;
public interface Listener{void update(ValueAnimator a);}public static ValueAnimator ofFloat(float a,float b){return last=new ValueAnimator();}
public void setDuration(int d){}public void setInterpolator(Object i){}public void addUpdateListener(Listener l){listener=l;}public void start(){started++;pulse(0);}public void cancel(){cancelled=true;}
public Object getAnimatedValue(){return fraction;}public void pulse(float p){if(!cancelled){fraction=p;listener.update(this);}}
}''',
'CheckFolderAnimation.java':'''import app.nebulagram.ui.NebulaFolderTitleView;import org.telegram.ui.ActionBar.SimpleTextView;import android.animation.ValueAnimator;
public class CheckFolderAnimation {
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
public static void main(String[] args)throws Exception{
NebulaFolderTitleView v=new NebulaFolderTitleView(new android.content.Context());v.setText("NebulaGram");
v.setTitle("Folder 🇫🇮",1,false,true);check(v.getChildCount()==2,"old title vanished immediately");
check(v.getChildAt(1).getTranslationY()<0,"new title must enter from above");
ValueAnimator first=ValueAnimator.last;first.pulse(.5f);check(v.getChildAt(0).getTranslationY()>0,"old title must leave downward");check(v.getStatusAlpha()>0&&v.getStatusAlpha()<1,"premium status did not fade");
int starts=ValueAnimator.started;v.setTitle("Folder 🇫🇮",1,false,true);check(ValueAnimator.started==starts,"same title restarted animation");
v.setTitle("Next 😀",2,false,true);first.pulse(1);check(v.getText().toString().equals("Next 😀"),"obsolete animation overwrote current folder");check(v.getChildCount()==2,"rapid swipe leaked old views");ValueAnimator.last.pulse(1);
check(v.getChildCount()==1&&v.getStatusAlpha()==0,"old title or premium emoji remained");check(((SimpleTextView)v.getChildAt(0)).cache==2,"emoji cache type lost");
v.setTitle("NebulaGram",2,true,true);ValueAnimator.last.pulse(1);check(v.getStatusAlpha()==1,"premium status failed to return");
v.setTitle("Away",1,false,true);ValueAnimator.last.pulse(.25f);java.lang.reflect.Method detach=NebulaFolderTitleView.class.getDeclaredMethod("onDetachedFromWindow");detach.setAccessible(true);detach.invoke(v);
check(v.getChildCount()==1&&v.getChildAt(0).getAlpha()==1&&v.getChildAt(0).getTranslationY()==0,"detach left half-animated title");check(v.getStatusAlpha()==0,"detach retained old status");
System.out.println("Folder animation passed: incoming/outgoing direction, status fade, rapid swipes, repeated binds, emoji cache, detach cleanup");}}
'''
}
sources=[]
for n,text in stubs.items():
 p=work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf-8');sources.append(str(p))
sources.append(str(r/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFolderTitleView.java'))
subprocess.run(['javac','-encoding','UTF-8','-d',str(work/'classes'),*sources],check=True)
subprocess.run(['java','-cp',str(work/'classes'),'CheckFolderAnimation'],check=True)
