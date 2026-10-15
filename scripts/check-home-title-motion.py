"""Execute native folder-title width and slot reuse during fade and interruption."""
from pathlib import Path
import re,subprocess,sys,tempfile

root=Path(__file__).resolve().parents[1]
source=(Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
def method(signature):
    start=source.index(signature);end=source.index('{',start)+1;depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start:end]
signature=re.search(r'private (?:int|float) nebulaHomeTitleWidth\(',source).group()
methods='\n'.join(method(x) for x in [signature,'private int nebulaHomeStoryPrefix(',
    'public float getNebulaHomeStoriesLeft(', 'public float getNebulaHomeTitleLeft(',
    'private void applyNebulaHomeStoryLayout(',
    'private void updateNebulaHomeTitleLayout(',
    'public void setNebulaHomeTitleLayoutChangedListener(',
    'public void setTitleAnimated(CharSequence title, boolean fromBottom, long duration, Interpolator interpolator)'])
clip_start=source.index('        boolean clip = shouldClipChild(child);')
clip_prelude=source[clip_start:source.index('        boolean result = super.drawChild',clip_start)]
stories=(Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java').read_text(encoding='utf-8')
begin=stories.index('    public void setActionBar(ActionBar actionBar)');end=stories.index('{',begin)+1;depth=1
while depth:
    depth+=(stories[end]=='{')-(stories[end]=='}');end+=1
binding=stories[begin:end]
harness='''
class HeaderTitleMotionCheck {
 static final int VISIBLE=0,GONE=8;
 static class View {static final int VISIBLE=0,GONE=8;}
 static class Animator {}
 static class AnimatorListenerAdapter {public void onAnimationEnd(Animator a){}}
 interface Interpolator {}
 static class TextUtils {static boolean isEmpty(CharSequence x){return x==null||x.length()==0;}}
 static class ViewGroup {void removeView(SimpleTextView v){v.parent=null;}}
 static class AndroidUtilities {static int statusBarHeight=24;}
 static class Canvas {int depth,clips;float left,right;
  void save(){depth++;}void restore(){depth--;}
  void clipRect(float l,float t,float r,float b){clips++;left=l;right=r;}}
 static class ViewPropertyAnimator {
  AnimatorListenerAdapter listener;float target;
  java.util.function.Consumer<Object> update;
  ViewPropertyAnimator alpha(float x){target=x;return this;}
  ViewPropertyAnimator translationY(float x){return this;}
  ViewPropertyAnimator setDuration(long x){return this;}
  ViewPropertyAnimator setInterpolator(Interpolator x){return this;}
  ViewPropertyAnimator setListener(AnimatorListenerAdapter x){listener=x;return this;}
  ViewPropertyAnimator setUpdateListener(java.util.function.Consumer<Object> x){update=x;return this;}
  void start(){if(update!=null)update.accept(null);}void cancel(){if(listener!=null)listener.onAnimationEnd(new Animator());}
 }
 static class SimpleTextView {
  int width,left=110,visibility=VISIBLE;float alpha=1,translation;CharSequence text;
  ViewGroup parent=new ViewGroup();ViewPropertyAnimator animator=new ViewPropertyAnimator();
  SimpleTextView(CharSequence value,int w){text=value;width=w;}
  int getMeasuredWidth(){return width;}int getLeft(){return left;}
  int getVisibility(){return visibility;}float getAlpha(){return alpha;}
  void setAlpha(float x){alpha=x;}void setTranslationY(float x){}
  void setTranslationX(float x){translation=x;}void setVisibility(int x){visibility=x;}
  ViewGroup getParent(){return parent;}ViewPropertyAnimator animate(){return animator;}
 }
 boolean nebulaHomeLayout=true,titleAnimationRunning,overlayTitleAnimation,fromBottom;
 float nebulaHomeTitleStartWidth=-1,nebulaHomeStoryProgress=1;
 Runnable nebulaHomeTitleLayoutChanged;int storyInvalidations;float paintedStoryLeft;
 int nebulaHomeVisibleStories=3,layouts;
 CharSequence subtitle;SimpleTextView subtitleTextView=new SimpleTextView("",0);
 SimpleTextView[] titleTextView={new SimpleTextView("Long",160),null};
 SimpleTextView additionalSubTitleOverlayContainer=new SimpleTextView("",0);
 Object titlesContainer=new Object();boolean useContainerForTitles=true,occupyStatusBar=true,verticalClip;
 int getMeasuredWidth(){return 448;}int nebulaTitleEndInset(){return 97;}int dp(int v){return v;}
 int getMeasuredHeight(){return 80;}float getTranslationY(){return 0;}
 boolean shouldClipChild(Object child){return verticalClip;}
 void requestLayout(){layouts++;}
 void setTitle(CharSequence value){titleTextView[0]=new SimpleTextView(value,value.toString().equals("Long")?160:24);}
''' + methods + '''
 static class ActionBar extends HeaderTitleMotionCheck {}
 static class StoryBinding {
  ActionBar actionBar;Object telegramLogoView;int invalidations;
  void invalidate(){invalidations++;}void publishNebulaHomeHeader(){}
''' + binding + '''
 }
 void clipTitle(Canvas canvas,Object child){''' + clip_prelude + '''
 if(clip || clipHomeTitle)canvas.restore();
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void close(float a,float b,String why){check(Math.abs(a-b)<.01f,why+": "+a+" != "+b);}
 void pose(float p){titleTextView[0].alpha=p;if(titleTextView[1]!=null)titleTextView[1].alpha=1-p;
  if(titleTextView[0].animator.update!=null)titleTextView[0].animator.update.accept(null);}
 void finish(){pose(1);titleTextView[1].animator.listener.onAnimationEnd(new Animator());applyNebulaHomeStoryLayout();}
 public static void main(String[] args) {
  var b=new HeaderTitleMotionCheck();b.setTitleAnimated("Emoji",false,220,null);
  b.pose(.5f);close(b.nebulaHomeTitleWidth(),92,"Width must move during fade, not after old text removal");
  b.pose(.999f);float before=b.getNebulaHomeStoriesLeft();b.finish();
  check(Math.abs(before-b.getNebulaHomeStoriesLeft())<1,"Old-slot removal jumps the story stack");
  close(b.nebulaHomeTitleWidth(),24,"Emoji-only folder keeps measured width");
  b.setTitleAnimated("Long",false,220,null);b.pose(0);close(b.nebulaHomeTitleWidth(),24,"Reverse starts from shown width");
  b.pose(.5f);close(b.nebulaHomeTitleWidth(),92,"Reverse interpolates");b.finish();
  close(b.nebulaHomeTitleWidth(),160,"Long endpoint");
  int frames=0;
  for(int pass=0;pass<80;pass++) {
   b=new HeaderTitleMotionCheck();final var owner=b;
   b.nebulaHomeTitleLayoutChanged=()->{owner.storyInvalidations++;owner.paintedStoryLeft=owner.getNebulaHomeStoriesLeft();};
   b.setTitleAnimated("Emoji",false,220,null);
   float interruption=(pass%9+1)/10f;b.pose(interruption);
   float width=b.nebulaHomeTitleWidth(),left=b.getNebulaHomeStoriesLeft();
   var stale=b.titleTextView[1].animator.listener;
   b.setTitleAnimated("Long",false,220,null);
   close(b.paintedStoryLeft,b.getNebulaHomeStoriesLeft(),"First synchronous fade tick publishes an obsolete width");
   b.pose(0);
   close(b.nebulaHomeTitleWidth(),width,"Interrupted fade must snapshot visible width before slot reuse");
   close(b.getNebulaHomeStoriesLeft(),left,"Interrupted fade jumps the stack");
   stale.onAnimationEnd(new Animator());check(b.titleAnimationRunning,"Stale callback ends newer motion");
   int layoutsBefore=b.layouts;
   for(int f=0;f<=120;f++) {
    int paints=b.storyInvalidations;
    b.pose(f/120f);float x=b.getNebulaHomeStoriesLeft(),t=b.getNebulaHomeTitleLeft();
    check(b.storyInvalidations==paints+1,"Cached story canvas is not invalidated on the native fade tick");
    close(b.paintedStoryLeft,x,"Separate story display list receives a stale title pose");
    close(t-x,74,"Title and stories lose shared prefix");
    close(b.titleTextView[0].left+b.titleTextView[0].translation,t,"Incoming native title leaves shared slot");
    close(b.titleTextView[1].left+b.titleTextView[1].translation,t,"Outgoing native title leaves shared slot");
    close(b.additionalSubTitleOverlayContainer.left+b.additionalSubTitleOverlayContainer.translation,t,"Status overlay leaves shared slot");
    check(x>=84 && t+b.nebulaHomeTitleWidth()<=351,"Interpolated group crosses toolbar controls");frames++;
   }
   check(b.layouts==layoutsBefore,"Native title motion requests layout every frame");
   b.finish();
  }
  b=new HeaderTitleMotionCheck();b.nebulaHomeLayout=false;b.setTitleAnimated("Emoji",false,220,null);b.pose(.5f);
  close(b.nebulaHomeTitleWidth(),160,"Classic title width changed");b.finish();
  for(boolean home:new boolean[]{false,true})for(boolean animated:new boolean[]{false,true})
  for(boolean vertical:new boolean[]{false,true})for(int child=0;child<5;child++) {
   b=new HeaderTitleMotionCheck();b.nebulaHomeLayout=home;b.titleAnimationRunning=animated;b.verticalClip=vertical;
   Object target=child==0?b.titleTextView[0]:child==1?b.titlesContainer:
    child==2?b.additionalSubTitleOverlayContainer:child==3?(b.titleTextView[1]=new SimpleTextView("Old",160)):new Object();
   var canvas=new Canvas();b.clipTitle(canvas,target);check(canvas.depth==0,"Unbalanced native title clip");
   boolean mask=home&&animated&&child<4;
   check(canvas.clips==(vertical?1:0)+(mask?1:0),"Home animation clip changes unrelated content");
   if(mask){close(canvas.left,84,"Wrong leading clip");close(canvas.right,351,"Fading text crosses plain actions");}
  }
  System.out.println(frames+" native folder fade/interruption frames and exact removal endpoints passed");
  var row=new StoryBinding();var first=new ActionBar();var second=new ActionBar();
  row.setActionBar(first);first.nebulaHomeTitleLayoutChanged.run();check(row.invalidations==1,"Native story binding does not invalidate");
  row.setActionBar(second);check(first.nebulaHomeTitleLayoutChanged==null,"Replaced toolbar retains an obsolete story callback");
  second.nebulaHomeTitleLayoutChanged.run();check(row.invalidations==2,"New toolbar does not own story callback");
  row.setActionBar(null);check(second.nebulaHomeTitleLayoutChanged==null,"Unbound toolbar retains story callback");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-home-title-motion-') as folder:
    work=Path(folder);test=work/'HeaderTitleMotionCheck.java';test.write_text(harness,encoding='utf-8')
    geometry=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaHomeTitleGeometry.java'
    stub=work/'app/nebulagram/ui/NebulaDialogsTitle.java';stub.parent.mkdir(parents=True,exist_ok=True)
    stub.write_text('package app.nebulagram.ui;public class NebulaDialogsTitle {public static void bind(Object bar,Object title){}}',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(test),str(geometry),str(stub)],check=True)
    subprocess.run(['java','-cp',str(work),'HeaderTitleMotionCheck'],check=True)
