#!/usr/bin/env python3
"""Execute the actual settings root against a minimal Android layout contract."""
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
routes = ['Settings', 'SettingsHub', 'Section', 'Menu', 'Servers', 'Subscriptions', 'Privacy', 'Ai', 'Design', 'Updates']
for route in routes:
    source = (UI / ('Nebula' + route + 'Fragment.java')).read_text(encoding='utf-8')
    context = re.search(r'View createView\(Context (\w+)\)', source).group(1)
    assert f'NebulaSettingsLayout.wrap({context}, actionBar,' in source, route
assert 'new NebulaSettingsHero' not in (UI / 'NebulaSettingsFragment.java').read_text(encoding='utf-8')
assert 'setAllCaps(false)' in (UI / 'NebulaCard.java').read_text(encoding='utf-8')
assert 'COMPLEX_UNIT_SP, 16' in (UI / 'NebulaRow.java').read_text(encoding='utf-8')

sources = {
    'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {public boolean isHardwareAccelerated(){return false;}public void drawRect(int l,int t,int r,int b,Paint p){}}',
    'android/graphics/Color.java': 'package android.graphics; public class Color {public static final int TRANSPARENT=0;}',
    'android/graphics/Paint.java': 'package android.graphics; public class Paint {public void setColor(int c){}}',
     'android/widget/ScrollView.java': 'package android.widget; public class ScrollView extends android.view.ViewGroup {public int sy;public boolean clipped=true;public void setClipToPadding(boolean b){clipped=b;}public int getScrollY(){return sy;}}',
    'android/widget/ImageView.java':'package android.widget;public class ImageView extends android.view.View {public void setBackground(Object b){}}',
    'android/widget/TextView.java':'package android.widget;public class TextView extends android.view.View {}',
    'android/content/res/ColorStateList.java':'package android.content.res;public class ColorStateList {public static ColorStateList valueOf(int c){return new ColorStateList();}}',
    'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable;public class GradientDrawable {public static final int OVAL=1;public void setShape(int s){}public void setColor(int c){}}',
    'android/graphics/drawable/RippleDrawable.java':'package android.graphics.drawable;public class RippleDrawable {public RippleDrawable(Object c,Object d,Object m){}}',
    'android/graphics/drawable/InsetDrawable.java':'package android.graphics.drawable;public class InsetDrawable {public InsetDrawable(Object d,int n){}}',
    'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static int dp(int n){return n;}}',
    'app/nebulagram/ui/NebulaSettingsHero.java':'package app.nebulagram.ui;public class NebulaSettingsHero extends android.view.View {public int titleScrollAnchor(){return 140;}}',
    'android/view/ViewTreeObserver.java':'package android.view;import java.util.*;public class ViewTreeObserver {public interface OnScrollChangedListener {void onScrollChanged();}public ArrayList<OnScrollChangedListener> list=new ArrayList<>();public void addOnScrollChangedListener(OnScrollChangedListener l){list.add(l);}public void removeOnScrollChangedListener(OnScrollChangedListener l){list.remove(l);}public boolean isAlive(){return true;}}',

    'app/nebulagram/ui/NebulaProfileGlass.java': 'package app.nebulagram.ui; public class NebulaProfileGlass {public static boolean glass; public static boolean supported(){return glass;}}',
    'app/nebulagram/ui/NebulaSettingsBackdrop.java': 'package app.nebulagram.ui; public class NebulaSettingsBackdrop {public NebulaSettingsBackdrop(int c){}public void draw(android.graphics.Canvas c,android.view.View v,int w,int h){}}',

    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/view/View.java': '''package android.view;
public class View {
 public static final int GONE=8; public int visibility,w,h,left,top,right,bottom; public Object parent;
 public float alpha=1;public int getTop(){return top;}public void setAlpha(float v){alpha=v;}public final ViewTreeObserver observer=new ViewTreeObserver();public ViewTreeObserver getViewTreeObserver(){return observer;}protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}
 public int pl,pt,pr,pb;public android.content.Context getContext(){return new android.content.Context();}public int getWidth(){return w;}public int getPaddingTop(){return pt;}public int getPaddingBottom(){return pb;}public int getPaddingLeft(){return pl;}public int getPaddingRight(){return pr;}public void setPadding(int l,int t,int r,int b){pl=l;pt=t;pr=r;pb=b;}public void setBackgroundColor(int c){}public long getDrawingTime(){return 0;}public int getVisibility(){return visibility;} public Object getParent(){return parent;}
 public final void measure(int w,int h){onMeasure(w,h);} protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}
 protected void setMeasuredDimension(int w,int h){this.w=w;this.h=h;}
 public int getMeasuredWidth(){return w;} public int getMeasuredHeight(){return h;}
 public void layout(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;onLayout(true,l,t,r,b);}
 protected void onLayout(boolean c,int l,int t,int r,int b){}
 public static class MeasureSpec { public static final int EXACTLY=0x40000000, AT_MOST=0x80000000;
  public static int getSize(int x){return x&0x3fffffff;} public static int makeMeasureSpec(int s,int m){return s|m;}}
}''',
    'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View {
 java.util.ArrayList<View> children=new java.util.ArrayList<>();public int getChildCount(){return children.size();}public View getChildAt(int n){return children.get(n);}
 protected void dispatchDraw(android.graphics.Canvas c){}protected boolean drawChild(android.graphics.Canvas c,View v,long time){return true;}public void removeView(View v){v.parent=null;children.remove(v);} public void addView(View v){if(v.parent!=null)throw new AssertionError();v.parent=this;children.add(v);}
}''',
    'android/widget/FrameLayout.java': '''package android.widget; public class FrameLayout extends android.view.ViewGroup {
 public FrameLayout(android.content.Context c){} public void setBackgroundColor(int c){} public void setClipChildren(boolean c){}
}''',
    'org/telegram/ui/ActionBar/ActionBar.java': '''package org.telegram.ui.ActionBar; public class ActionBar extends android.view.View {
 public final android.widget.TextView title=new android.widget.TextView();public android.widget.TextView getTitleTextView(){return title;}public android.widget.TextView getTitleTextView2(){return null;}public android.widget.ImageView getBackButton(){return null;}
 public int wantedHeight=80;public boolean external=true;public void setAddToContainer(boolean x){external=x;}
 protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),wantedHeight);}
}''',
    'app/nebulagram/ui/NebulaTheme.java': '''package app.nebulagram.ui; public class NebulaTheme {
 public static NebulaTheme of(android.content.Context c){return new NebulaTheme();} public int surface(){return 0;}public int opaqueSurface(){return 0;}public int surfaceContainer(){return 0;}public int onSurface(){return 0;}public static int stateLayer(int c,float a){return 0;}
}''',
    'app/nebulagram/ui/NebulaSettingsLinks.java': '''package app.nebulagram.ui; public class NebulaSettingsLinks {
 public static int calls,section;public static android.view.View body;
 public static void bind(android.view.View view,int destination){calls++;body=view;section=destination;}
}''',
    'RootTest.java': '''import android.view.View; import org.telegram.ui.ActionBar.ActionBar; import app.nebulagram.ui.NebulaSettingsLayout;
import app.nebulagram.ui.NebulaSettingsLinks;
public class RootTest {
 public static void main(String[] a){
  ActionBar bar=new ActionBar(); View body=new View(); View root=NebulaSettingsLayout.wrap(new android.content.Context(),bar,body);
  if(bar.external)throw new AssertionError("duplicate native bar");
  for(int height:new int[]{0,40,320,800})for(int width:new int[]{240,390,840})for(int barHeight:new int[]{56,80,100}){
   bar.wantedHeight=barHeight;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);
   if(body.top!=barHeight || body.h!=Math.max(0,height-barHeight) || body.w!=width)throw new AssertionError("overlap");
  }
  bar.visibility=View.GONE;root.measure(390,800);root.layout(0,0,390,800);
  if(body.top!=0 || body.h!=800)throw new AssertionError("hidden bar gap");
  if(NebulaSettingsLinks.calls!=0)throw new AssertionError("unconfigured screen acquired setting links");
  for(int section:new int[]{-1,0,9,-12,-15,-100}){
   int before=NebulaSettingsLinks.calls;
   root=NebulaSettingsLayout.wrap(new android.content.Context(),bar,body,section);
   root.measure(390,800);root.layout(0,0,390,800);
   if(section==-100){if(NebulaSettingsLinks.calls!=before)throw new AssertionError("disabled setting links bound");}
   else if(NebulaSettingsLinks.calls!=before+1 || NebulaSettingsLinks.section!=section || NebulaSettingsLinks.body!=body)
    throw new AssertionError("wrong settings link destination or body");
  }
  app.nebulagram.ui.NebulaProfileGlass.glass=true;
  android.widget.ScrollView scroll=new android.widget.ScrollView();scroll.setPadding(16,12,16,24);bar=new ActionBar();root=NebulaSettingsLayout.wrap(new android.content.Context(),bar,scroll);
  for(int repeat=0;repeat<30;repeat++)for(int barHeight:new int[]{56,80,100}){
   bar.wantedHeight=barHeight;root.measure(390,800);root.layout(0,0,390,800);
   if(scroll.top!=0||scroll.h!=800||scroll.pt!=12+barHeight||scroll.pl!=16||scroll.pb!=24)throw new AssertionError("glass overlay inset accumulated or clipped content");
  }
  bar.visibility=View.GONE;root.measure(390,800);root.layout(0,0,390,800);if(scroll.pt!=12)throw new AssertionError("hidden glass bar retains inset");
  bar.visibility=0;bar.wantedHeight=64;scroll=new android.widget.ScrollView();android.view.ViewGroup column=new android.view.ViewGroup();column.addView(new app.nebulagram.ui.NebulaSettingsHero());scroll.addView(column);
  android.widget.FrameLayout frame=new android.widget.FrameLayout(new android.content.Context());frame.addView(scroll);root=NebulaSettingsLayout.wrap(new android.content.Context(),bar,frame);
  root.measure(390,800);root.layout(0,0,390,800);
  if(frame.top!=0||scroll.pt!=64||scroll.clipped||bar.title.alpha!=0)throw new AssertionError("nested scrolling introduction/header geometry");
  scroll.sy=128;root.layout(0,0,390,800);if(bar.title.alpha!=.5f)throw new AssertionError("title must fade as the introduction collapses");
  scroll.sy=170;root.layout(0,0,390,800);if(bar.title.alpha!=1)throw new AssertionError("collapsed title missing");
  System.out.println("OK: nested glass scroll and large-to-small title collapse");
  System.out.println("OK: actual settings-root geometry, 36 size/bar combinations, hidden bar and opt-in setting links");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-settings-root-') as folder:
    tree = Path(folder)
    for name, content in sources.items():
        path = tree / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding='utf-8')
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    subprocess.run([str(java / 'javac'), '-encoding', 'UTF-8', '-d', str(tree), str(UI / 'NebulaSettingsLayout.java'), *map(str, tree.rglob('*.java'))], check=True)
    subprocess.run([str(java / 'java'), '-cp', str(tree), 'RootTest'], check=True)
print('OK: all ten settings routes own their native bar; shared readable row/header styling')
