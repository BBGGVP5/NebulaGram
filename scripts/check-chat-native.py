"""Exercise extracted native layout/adapter methods with minimal JVM view models.

This checks measurement contracts and row mapping, not Android rasterization.
"""
from pathlib import Path
import subprocess, sys
root = Path(__file__).resolve().parent.parent
if len(sys.argv) != 2:
    raise SystemExit('Usage: python scripts/check-chat-native.py <Telegram tree after patches>')
tree = Path(sys.argv[1])
java = tree / 'TMessagesProj/src/main/java/org/telegram/ui'
work = root / 'build/chat-fixes/native-check'
work.mkdir(parents=True, exist_ok=True)

def method(s, signature):
    start = s.index(signature)
    brace = s.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        if s[end] == '{': depth += 1
        elif s[end] == '}': depth -= 1
        end += 1
    return s[start:end]

header = (java / 'Components/ChatAvatarContainer.java').read_text(encoding='utf-8')
camera = (java / 'Components/ChatAttachAlertPhotoLayout.java').read_text(encoding='utf-8')
adapter = camera[camera.index('private class PhotoAttachAdapter'):]
layout = method(header, 'protected void onLayout(')
center = layout.index('        if (nebulaCenteredTitle)')
layout = layout[:center] + method(layout[center:], 'if (nebulaCenteredTitle)') + '\n}'
header_methods = '\n'.join(method(header, signature) for signature in [
    'protected void onMeasure(', 'public int getNebulaCenteredHeaderWidth()'])
if 'private int measureNebulaHeaderWidth(' in header:
    header_methods += '\n' + method(header, 'private int measureNebulaHeaderWidth(')
header_methods += '\n' + layout
simple = (java / 'ActionBar/SimpleTextView.java').read_text(encoding='utf-8')
draw = method(simple, 'protected void onDraw(')
start = draw.index('        if (rightDrawable != null && rightDrawableOutside)')
status_draw = method(draw[start:], 'if (rightDrawable != null && rightDrawableOutside)')
start = draw.index('        if (rightDrawable2 != null && rightDrawableOutside)', start)
status_draw += '\n' + method(draw[start:], 'if (rightDrawable2 != null && rightDrawableOutside)')
header_methods = header_methods.replace('app.nebulagram.ui.NebulaAppearance.chatHeader()', 'floating').replace('app.nebulagram.ui.NebulaHeaderCounter.backWidth()', 'dp(58)')
header_methods = header_methods.replace('app.nebulagram.ui.NebulaChatStyle.headerWidth', 'headerWidth').replace('app.nebulagram.ui.NebulaChatStyle.headerLeft', 'headerLeft').replace('app.nebulagram.ui.NebulaChatStyle.headerTextLeft', 'headerTextLeft')
camera_methods = '\n'.join(method(adapter, signature) for signature in [
    'public int getItemCount()', 'public int getItemViewType(', 'private MediaController.PhotoEntry getPhoto('])
camera_methods = camera_methods.replace('MediaController.PhotoEntry', 'Integer')
if 'private boolean isCameraTileEnabled()' in adapter:
    camera_methods += '\n' + method(adapter, 'private boolean isCameraTileEnabled()').replace('app.nebulagram.ui.NebulaAppearance.hideAttachCamera()', 'hidden')

source = r'''
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
public class CheckChatNative {
static int VISIBLE=0, INVISIBLE=4, GONE=8;
static float density=1; static boolean floating=true;
static int dp(float x){return (int)Math.ceil(x*density);}
static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
static class MeasureSpec {static int EXACTLY=1<<30,AT_MOST=1<<31;static int getSize(int s){return s&0x3fffffff;}static int makeMeasureSpec(int s,int m){return s|m;}}
static class View {
 int mw,mh,l,t,r,b,visibility=VISIBLE;
 void measure(int w,int h){onMeasure(w,h);}
 protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}
 void setMeasuredDimension(int w,int h){mw=w;mh=h;}
 void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;onLayout(true,l,t,r,b);}
 protected void onLayout(boolean c,int l,int t,int r,int b){}
 int getMeasuredWidth(){return mw;}int getMeasuredHeight(){return mh;}int getWidth(){return r-l;}
 int getTop(){return t;}int getPaddingTop(){return 0;}int getPaddingBottom(){return 0;}
 int getVisibility(){return visibility;}Object getParent(){return null;}
}
// SimpleTextView caches center offset in onMeasure. Its onLayout does not recalculate it.
static class SimpleTextView extends View {
 int naturalWidth,offset,textWidth,gravity=1;
 SimpleTextView(int w){naturalWidth=w;} void setGravity(int g){gravity=g;} SimpleTextView getDrawable(){return this;} float getAnimateToWidth(){return naturalWidth;}
 protected void onMeasure(int w,int h){super.onMeasure(w,h);textWidth=Math.min(mw,naturalWidth);offset=gravity==3?0:(mw-textWidth)/2;}
 float getExactWidthIncludeDrawables(){return naturalWidth;}int getTextHeight(){return mh;}
}
static class Gravity {static int LEFT=3,CENTER=17,CENTER_HORIZONTAL=1,HORIZONTAL_GRAVITY_MASK=7,VERTICAL_GRAVITY_MASK=112,CENTER_VERTICAL=16;}
static class ActionBar {static int getCurrentActionBarHeight(){return dp(56);}int getNebulaChatAvatarTrailingInset(){return 0;}}
static class AndroidUtilities {static int statusBarHeight=0;}
static int headerLeft(int width,int capsule){return floating?(width-capsule)/2:dp(58);}
static int headerTextLeft(int width,int capsule,int text){return floating?(width-text)/2:dp(58)+dp(54);}
static int headerWidth(int barWidth,int content){if(!floating)return barWidth-dp(58)-dp(58);return Math.max(0,Math.min(Math.max(dp(112),content+dp(40)),barWidth-dp(128)));}
static class Header extends View {
 boolean nebulaCenteredTitle=true,glassMode=true,occupyStatusBar=false;
 int avatarSizeInDp=44,lastWidth=-1,largerWidth,nebulaHeaderWidth;
 SimpleTextView titleTextView=new SimpleTextView(0),subtitleTextView=new SimpleTextView(0),animatedSubtitleTextView;
 View avatarImageView=new View(),communityItem,timeItem,starBgItem,starFgItem;
 AtomicReference<SimpleTextView> titleTextLargerCopyView=new AtomicReference<>(),subtitleTextLargerCopyView=new AtomicReference<>();
 View getSubtitleTextView(){return subtitleTextView;}
 void fadeOutToLessWidth(int w){throw new AssertionError("centered header creates a left-aligned copy");}
HEADER_METHODS
}
static class Album {ArrayList<Integer> photos=new ArrayList<>();}
static class StatusTitle {
 int gravity=1,offsetX,textWidth,drawablePadding=4,textOffsetX,scrollingOffset=0,nextScrollX=900,paddingRight=0;
 int rightDrawableX,rightDrawableY,rightDrawableTopPadding=0,textHeight=20;
 float rightDrawableScale=1;boolean rightDrawableOutside=true;
 Icon rightDrawable=new Icon(),rightDrawable2;
 Object canvas=null;
 int getMaxTextWidth(){return 300;}int getMeasuredHeight(){return 30;}int getPaddingTop(){return 6;}
 void draw(){STATUS_DRAW}
}
static class Icon {int left,right; int getIntrinsicWidth(){return 24;}int getIntrinsicHeight(){return 24;}
 void setBounds(int l,int t,int r,int b){left=l;right=r;}void draw(Object canvas){}
}
static class Camera {
 boolean hidden,mediaEnabled=true,noGalleryPermissions,noCameraPermissions,showAvatarConstructor;
 int itemsPerRow=3;
 Album galleryAlbumEntry=new Album(),selectedAlbumEntry=galleryAlbumEntry;
 ArrayList<Integer> cameraPhotos=new ArrayList<>();
 Adapter adapter=new Adapter(true);
 Integer getPhotoEntryAtPosition(int p){return p;}
 class Adapter {
  final boolean needCamera; boolean hasCamera,hasCameraSpaceRow;
  int itemsCount,photosStartRow,photosEndRow;
  static final int VIEW_TYPE_EMPTY=7,VIEW_TYPE_CAMERA_PERMISSION_BUTTON=8,VIEW_TYPE_AVATAR_CONSTRUCTOR=6,VIEW_TYPE_CELL_PERMISSION=3;
  Adapter(boolean camera){needCamera=camera;}
CAMERA_METHODS
 }
}
public static void main(String[] args){
 int headers=0,cameras=0;
 for(int offset:new int[]{0,15,40,80})for(int textWidth:new int[]{10,60,120})for(boolean second:new boolean[]{false,true}){
  StatusTitle t=new StatusTitle();t.offsetX=offset;t.textWidth=textWidth;if(second)t.rightDrawable2=new Icon();t.draw();
  check(t.rightDrawable.left==offset+textWidth+4,"premium emoji fails to follow centered title offset");
  if(second)check(t.rightDrawable2.left==t.rightDrawable.right+4,"mute/verified icon overlaps premium status");
 }
 for(boolean layoutFloating:new boolean[]{false,true})for(float d:new float[]{1,2.25f,3})for(int width:new int[]{320,360,448,800})for(int title:new int[]{12,100,600})for(int status:new int[]{0,80,650}){
  density=d;floating=layoutFloating;Header h=new Header();h.subtitleTextView.setGravity(floating?1:3);h.titleTextView.naturalWidth=dp(title);h.subtitleTextView.naturalWidth=dp(status);h.subtitleTextView.visibility=status==0?GONE:VISIBLE;
  for(int w:new int[]{width,width-30,width}){
   int content=dp(w)-dp(12);h.measure(MeasureSpec.makeMeasureSpec(content,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(56),MeasureSpec.EXACTLY));h.layout(0,0,content,dp(56));
   for(SimpleTextView text:new SimpleTextView[]{h.titleTextView,h.subtitleTextView})if(text.getVisibility()!=GONE){
    check(text.getWidth()==text.getMeasuredWidth(),"header lays out text narrower than its cached measure width");
    if(floating)check(Math.abs(text.l+text.offset+text.textWidth/2f-content/2f)<=1,"title/status center differs from capsule center");
    else {check(text.l>=h.avatarImageView.r+dp(6),"avatar collides with title");check(text.l+text.getMeasuredWidth()<=content-dp(58),"title overlaps menu");check(text.offset==0,"nonfloating title/subtitle not left aligned");}
    check(text.getWidth()<=h.getNebulaCenteredHeaderWidth()-dp(40),"header text exceeds capsule");
   }
   headers++;
  }
 }
 for(boolean hidden:new boolean[]{false,true})for(boolean gallery:new boolean[]{false,true})for(boolean permission:new boolean[]{false,true})for(int photos=0;photos<15;photos++)for(int columns:new int[]{3,4}){
  Camera c=new Camera();c.hidden=hidden;c.noCameraPermissions=!permission;c.itemsPerRow=columns;
  if(!gallery)c.selectedAlbumEntry=new Album();
  for(int i=0;i<photos;i++)c.selectedAlbumEntry.photos.add(i);
  int count=c.adapter.getItemCount(),photo=0;
  for(int i=0;i<count;i++){
   int type=c.adapter.getItemViewType(i);
   if(hidden)check(type!=1&&type!=5&&type!=8,"hidden camera leaves a tile, spacer or permission row");
   if(type==0)check(c.adapter.getPhoto(i)==photo++,"camera visibility shifted a photo index");
  }
  check(photo==photos,"camera row mapping lost a photo");
  if(hidden)check(count==photos+1,"hidden camera still reserves grid space");
  c.hidden=true;c.adapter.getItemCount();check(!c.adapter.hasCamera&&!c.adapter.hasCameraSpaceRow,"toggle leaves camera state cached");
  c.hidden=false;c.adapter.getItemCount();check(c.adapter.hasCamera==gallery,"toggle fails to restore camera in all-media album");
  cameras++;
 }
 System.out.println(headers+" header measurements and "+cameras+" camera-grid cases plus 24 status-icon cases passed");
}}
'''.replace('HEADER_METHODS', header_methods).replace('CAMERA_METHODS', camera_methods).replace('STATUS_DRAW', status_draw)
target = work / 'CheckChatNative.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target)], check=True)
subprocess.run(['java','-cp',str(work),'CheckChatNative'], check=True)
