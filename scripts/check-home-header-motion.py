"""Run the native home collapse publisher, reversals and border-free Edit draw branch."""
from pathlib import Path
import subprocess
import sys
import tempfile

root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1])
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stories=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java').read_text(encoding='utf-8')
bar=(tree/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
glass=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java').read_text(encoding='utf-8')

def method(source,signature):
    start=source.index(signature);end=source.index('{',start)+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]

native='\n'.join(method(stories,s) for s in [
    'private boolean updateNebulaHomeCollapse(', 'private void publishNebulaHomeHeader()', 'private void checkCollapsedProgress()'
])
start=bar.index('        if (nebulaHomeTabsGlass && glassDrawableHomeLeading != null')
leading=bar[start:bar.index('        final boolean nebulaPlainHeaderButtons',start)]
stroke='\n'.join(method(glass,s) for s in [
    'public static void drawStroke(\n',
    'public static void drawStroke(Canvas canvas, float left, float top, float right, float bottom,',
    'public void setStrokeWidth(float strokeWidthTop, float strokeWidthBottom)',
    'private void drawStrokeInternalIfNeeded(Canvas canvas)'
])
java='''import java.util.*;import app.nebulagram.ui.NebulaHomeTitleGeometry;
class HomeMotionCheck {
 static final int VISIBLE=0,EXPANDED_STATE=0,TRANSITION_STATE=1,COLLAPSED_STATE=2;
 static class MathUtils {static float clamp(float p,float min,float max){return Math.max(min,Math.min(max,p));}}
 static class AndroidUtilities {static float lerp(float a,float b,float p){return a+(b-a)*p;}}
 static class ActionBar {boolean home=true;int count,updates;float progress;
  boolean isNebulaHomeTabsGlass(){return home;}void setNebulaHomeStories(int c,float p){count=c;progress=p;updates++;}}
 static class AnimatorSet {int cancels,removes;void removeAllListeners(){removes++;}void cancel(){cancels++;}}
 static class Stories {
  ActionBar actionBar=new ActionBar();ArrayList<Integer> miniItems=new ArrayList<>(Arrays.asList(1,2,3));
  AnimatorSet storiesAnimatorSet;float collapsedProgress1=-1,collapsedProgress2,collapsedProgress,K=.3f;
  boolean collapsed;float alpha=1;int visibility=VISIBLE,state,drawInvalidations,titleUpdates;
  float getAlpha(){return alpha;}
  int getVisibility(){return visibility;}void checkUi_titleVisibility(){titleUpdates++;}
  void updateCurrentState(int s){state=s;}void invalidate(){drawInvalidations++;}
''' + native + '''
 }
 static class View {float alpha=1;int visibility=VISIBLE;int getVisibility(){return visibility;}int getLeft(){return 8;}
  int getTop(){return 30;}int getRight(){return 76;}int getBottom(){return 74;}float getAlpha(){return alpha;}}
 static class Material {float top=1,bottom=1;int alpha,draws;void setStrokeWidth(float t,float b){top=t;bottom=b;}
  void setBounds(int l,int t,int r,int b){}void setAlpha(int a){alpha=a;}void draw(Object c){draws++;}}
 static class Bar {boolean nebulaHomeTabsGlass=true;Material glassDrawableHomeLeading=new Material();
  View nebulaHomeLeadingView=new View();Object canvas=new Object();void draw(){
''' + leading + '''
 }}
 static class Canvas {int strokes,operations;void save(){operations++;}void restore(){operations++;}
  boolean clipRect(float l,float t,float r,float b){operations++;return true;}
  void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){strokes++;}
  void drawPath(Object path,Paint paint){strokes++;}}
 static class Paint {void setColor(int c){}}
 static class Color {static int alpha(int c){return c>>>24;}}
 static class Theme {static int multAlpha(int color,float alpha){return color;}}
 static class Props {float strokeWidthTop=1,strokeWidthBottom=1;int builds;
  Object strokePathTop=new Object(),strokePathBottom=new Object();void build(){builds++;}}
 static class Glass {Props boundProps=new Props();int invalidations,alpha=255,strokeColorTop=0xffffffff,strokeColorBottom=0xffffffff;
  Paint paintStrokeFill=new Paint();void onBoundPropsChanged(){invalidations++;}
''' + stroke + '''
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
  for(float density:new float[]{1,1.5f,2.625f,3.5f})for(int count=0;count<=3;count++) {
   Stories s=new Stories();s.miniItems.clear();for(int i=0;i<count;i++)s.miniItems.add(i);
   AnimatorSet old=s.storiesAnimatorSet=new AnimatorSet();
   float[] path={0,.01f,.3f,.31f,.8f,.8f,.4f,.4f,.9f,1,.99f,.5f,0,1,0};
   for(float p:path){int before=s.actionBar.updates;
    check(s.updateNebulaHomeCollapse(p),"Home did not take the synchronous path");
    check(s.collapsedProgress==p && s.actionBar.progress==NebulaHomeTitleGeometry.gather(p) && s.collapsedProgress2==p,"Title and avatar progress differ");
    check(s.actionBar.updates>before,"Title was not published before paint / on paused frames");
    check(s.actionBar.count==count,"Story identity count lost");
    check(s.state==(p==0?EXPANDED_STATE:p==1?COLLAPSED_STATE:TRANSITION_STATE),"Wrong renderer at reversal");
    int w=(int)Math.ceil(448*density),l=(int)Math.ceil(84*density),r=(int)Math.ceil(97*density),title=(int)Math.ceil(96*density);
    int prefix=NebulaHomeTitleGeometry.storyPrefix(count,(int)Math.ceil(34*density),(int)Math.ceil(16*density),(int)Math.ceil(8*density));
    float x=NebulaHomeTitleGeometry.groupLeftFloat(w,l,r,title,prefix,p);
    check(x>=l && x+title+prefix*p<=w-r+.001f,"Fractional shared slot escapes controls");cases++;
   }
   check(old.cancels==1 && old.removes==1 && s.storiesAnimatorSet==null,"Old spring survived reversal");
   s.visibility=8;s.updateNebulaHomeCollapse(.5f);check(s.actionBar.count==0,"Hidden row leaves a phantom stack");
   s.visibility=VISIBLE;s.alpha=0;s.updateNebulaHomeCollapse(.5f);check(s.actionBar.count==0,"Transparent row leaves a phantom stack");
   s.actionBar.home=false;float before=s.collapsedProgress;check(!s.updateNebulaHomeCollapse(.7f) && s.collapsedProgress==before,"Classic renderer changed");
   s.collapsedProgress1=.6f;s.collapsedProgress2=.8f;s.checkCollapsedProgress();
   check(Math.abs(s.collapsedProgress-.48f)<.001f,"Classic native interpolation changed");
  }
  float a=NebulaHomeTitleGeometry.groupLeftFloat(448,84,97,96,74,.5000f);
  float b=NebulaHomeTitleGeometry.groupLeftFloat(448,84,97,96,74,.5001f);
  check(a!=b && Math.abs(a-b)<.01f,"Fractional movement snaps to integer pixels");
  Bar edit=new Bar();for(int frame=0;frame<=100;frame++){
   edit.glassDrawableHomeLeading.top=edit.glassDrawableHomeLeading.bottom=1;edit.nebulaHomeLeadingView.alpha=frame/100f;
   edit.draw();check(edit.glassDrawableHomeLeading.top==0 && edit.glassDrawableHomeLeading.bottom==0,"Theme update restores button border");
   check(edit.glassDrawableHomeLeading.alpha==Math.round(255*frame/100f),"Fill/blur fade lost");
  }
  edit.nebulaHomeLeadingView.visibility=8;int before=edit.glassDrawableHomeLeading.draws;edit.draw();check(before==edit.glassDrawableHomeLeading.draws,"Hidden Edit still draws");
  Glass g=new Glass();g.setStrokeWidth(0,0);check(g.boundProps.builds==1 && g.invalidations==1,"Cached outline geometry survives width change");
  g.setStrokeWidth(0,0);check(g.invalidations==1,"Unchanged width invalidates every frame");
  Canvas c=new Canvas();g.drawStrokeInternalIfNeeded(c);check(c.strokes==0,"Software fallback draws disabled outline");
  float[] radii={12,12,12,12,12,12,12,12};Paint paint=new Paint();
  for(boolean top:new boolean[]{false,true})for(float width:new float[]{0,-1}){
   Glass.drawStroke(c,0,0,60,44,radii,width,top,paint);Glass.drawStroke(c,0,0,60,44,12,width,top,paint);
  }
  check(c.strokes==0 && c.operations==0,"Hardware renderer draws zero-width hairline");
  Glass.drawStroke(c,0,0,60,44,radii,1,true,paint);Glass.drawStroke(c,0,0,60,44,12,1,false,paint);
  check(c.strokes==2,"Ordinary glass borders changed");g.setStrokeWidth(1,1);g.drawStrokeInternalIfNeeded(c);
  check(c.strokes==4 && g.invalidations==2,"Re-enabling ordinary outline fails");
  int poses=0;
  for(float density:new float[]{1,1.5f,2,2.625f,3.5f})for(int screen:new int[]{320,360,393,448,600,900})
  for(int actions=2;actions<=5;actions++)for(int count=0;count<=3;count++)for(int text:new int[]{60,96,160,260}) {
   int w=(int)Math.ceil(screen*density),left=(int)Math.ceil(84*density),right=(int)Math.ceil((actions*44+9)*density);
   int available=Math.max(0,w-left-right),title=Math.min((int)Math.ceil(text*density),available);
   int footprint=(int)Math.ceil(34*density),step=(int)Math.ceil(16*density),gap=(int)Math.ceil(8*density);
   int visible=NebulaHomeTitleGeometry.visibleStories(count,available,title,footprint,step,gap);
   int prefix=NebulaHomeTitleGeometry.storyPrefix(visible,footprint,step,gap);
   for(int frame=0;frame<=120;frame++)for(float extra:new float[]{0,24*density}) {
    float p=frame/120f,q=NebulaHomeTitleGeometry.gather(p),lift=NebulaHomeTitleGeometry.lift(p);
    check(q>=0 && q<=1 && lift>=0 && lift<=1,"Invalid phase");check(lift==0 || q==1,"Rings rise before gathering");
    float cellWidth=(float)Math.ceil(70*density),size=(float)Math.ceil(48*density)+extra;
    float finalSize=size+((float)Math.ceil(26.33f*density)-size)*q;
    float radius=finalSize/2+(float)Math.ceil(4*density),bottom=100*density,barCenter=bottom-28*density;
    float expanded=bottom+25*density+extra/2;
    float y=NebulaHomeTitleGeometry.avatarCenterY(expanded,bottom,barCenter,radius,2*density,p);
    check(Float.isFinite(y),"Invalid vertical pose");
    if(lift==0)check(y-radius>=bottom-4*density-.001f,"Gathering rings collide with header buttons");
    float slot=NebulaHomeTitleGeometry.groupLeftFloat(w,left,right,title,prefix,q),titleLeft=slot+prefix*q;
    for(int i=0;i<visible;i++) {
     float startLeft=(i+1)*cellWidth,target=slot+(float)Math.ceil(4*density)+i*step;
     float avatarCx=(cellWidth-finalSize)/2*(1-q)+finalSize/2;
     float x=startLeft+(target-startLeft)*q+avatarCx;
     if(lift>0){check(x-radius>=left-.001f,"Ring overlaps Edit");
      check(x+radius<=titleLeft-3*density+.001f,"Ring intersects title; would need rectangular clipping");}
     poses++;
    }
    if(frame==120)check(Math.abs(y-barCenter)<.001f,"Mini-list handoff jumps vertically");
   }
  }
  check(NebulaHomeTitleGeometry.gather(.6f)==1 && NebulaHomeTitleGeometry.lift(.6f)==0,"Phase seam jumps");
  System.out.println(cases+" native shared-frame/reversal cases and 101 border-free Edit frames passed");
  System.out.println(poses+" full-ring trajectory poses avoid title/Edit collisions, including overscroll and narrow toolbars");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-home-motion-') as directory:
    work=Path(directory);file=work/'HomeMotionCheck.java';file.write_text(java,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaHomeTitleGeometry.java'),str(file)],check=True)
    subprocess.run(['java','-cp',str(work),'HomeMotionCheck'],check=True)

draw=method(stories,'protected void dispatchDraw(Canvas canvas)')
assert 'setNebulaHomeStories(' not in draw, 'Story paint mutates an already-painted sibling'
assert 'if (updateNebulaHomeCollapse(progress)) return;' in method(stories,'public void setProgressToCollapse(float progress, boolean animated)')
assert 'translationX = (toX - cell.getLeft()) * homeGather;' in draw
assert 'NebulaHomeTitleGeometry.avatarCenterY(' in draw
assert 'cell.getNebulaAvatarSize(homeGather)' in draw
assert 'nebulaHomeStoriesClip' not in stories
assert 'drawInParent &= adapterPosition < animateFromPosition + actionBar.getNebulaHomeVisibleStories()' in draw
assert '!nebulaHome && adapterPosition' in draw
print('Home uses one reversible scroll frame; classic spring and ring safety remain separate')
