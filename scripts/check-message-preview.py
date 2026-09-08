"""Exercise real preview drawing scope and edit-row measurement, not only placement math."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent.parent
tree=Path(sys.argv[1]);native=tree/'TMessagesProj/src/main/java/org/telegram/ui'
def method(s,signature):
 start=s.index(signature);end=s.index('{',start)+1;depth=1
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
cell=(native/'Cells/ChatMessageCell.java').read_text(encoding='utf-8')
edit=(native/'Components/ChatActivityEnterTopView.java').read_text(encoding='utf-8')
chat=(native/'ChatActivity.java').read_text(encoding='utf-8')
scope=method(cell,'public void drawMenuPreview(Canvas canvas)')
clip=method(chat,'private float nebulaPreviewClipY(float edge, boolean bottom)')
measure=method(edit,'protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)')
measure=measure.replace('app.nebulagram.ui.NebulaAppearance.iosComposer()','glassEnabled').replace('org.telegram.messenger.AndroidUtilities.dp','dp')
src='''public class PreviewScopeCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 static class Canvas {}
 static class Cell {
  static class Image {boolean skip;void setSkipUpdateFrame(boolean x){skip=x;}} Image photoImage=new Image();
  boolean drawingMenuPreview,skipFrameUpdate;float alphaInternal=1f;
  boolean fullyDraw,needNewVisiblePart=true,fail; int firstVisibleBlockNum=8,lastVisibleBlockNum=9,painted;
  void draw(Canvas c){
   check(fullyDraw,"stale list culling used in popup");
   check(!needNewVisiblePart,"popup must not query the list visible rectangle");
   firstVisibleBlockNum=0;lastVisibleBlockNum=12;painted=13;
   if(fail)throw new IllegalStateException("draw failure");
  }
  SCOPE
 }
 static final int GONE=8;
 static boolean glassEnabled;
 static float density=1;
 static int dp(float x){return (int)Math.ceil(x*density);}
 static class MeasureSpec {static final int UNSPECIFIED=0;static int getMode(int x){return 1;}static int getSize(int x){return x;}}
 static class LayoutParams {static final int WRAP_CONTENT=-2;int width=-2;}
 static class Text {int max=Integer.MAX_VALUE,visibility;int getVisibility(){return visibility;}void setMaxWidth(int x){max=x;}}
 static class EditViewButton {
  LayoutParams params=new LayoutParams();Text text=new Text();int visibility;boolean edit;
  int getVisibility(){return visibility;} Text getTextView(){return text;}Object getLayoutParams(){return params;}
  int getPaddingLeft(){return dp(12);}int getPaddingRight(){return dp(12);}boolean isEditButton(){return edit;}
 }
 static class Base {void onMeasure(int w,int h){}}
 static class Row extends Base {
  EditViewButton[] buttons={new EditViewButton(),new EditViewButton()};
  int getPaddingLeft(){return 0;}int getPaddingRight(){return 0;}
  MEASURE
 }
 static class ChatMessageCell {}
 static class Utilities {static float clamp(float p,float max,float min){return Math.max(min,Math.min(max,p));}}
 static class Preview {
  Object scrimView=new ChatMessageCell();
  float nebulaScrimSourceTop,nebulaScrimTop=32,nebulaScrimScale=.65f,scrimViewProgress;
  int getHeight(){return 900;}
  CLIP
 }
 public static void main(String[] args){
  for(float top:new float[]{-1800,-300,200,750})for(float progress:new float[]{0,.5f,1}){
   Preview p=new Preview();p.nebulaScrimSourceTop=top;p.scrimViewProgress=progress;
   for(boolean bottom:new boolean[]{false,true}){
    float edge=bottom?700:200;
    float source=p.nebulaPreviewClipY(edge,bottom);
    float drawn=top+(source-top)*(1+(.65f-1)*progress)+(32-top)*progress;
    float expected=edge+((bottom?900:0)-edge)*progress;
    check(Math.abs(drawn-expected)<.001,"preview inherits scrolled header/input clipping");
   }
  }
  for(boolean previous:new boolean[]{false,true})for(boolean needs:new boolean[]{false,true})for(boolean fail:new boolean[]{false,true}) {
   Cell c=new Cell();c.fullyDraw=previous;c.needNewVisiblePart=needs;c.fail=fail;
   try{c.drawMenuPreview(new Canvas());}catch(IllegalStateException expected){check(fail,"unexpected failure");}
   check(c.painted==13,"early text blocks missing");
   check(c.fullyDraw==previous&&c.needNewVisiblePart==needs&&c.firstVisibleBlockNum==8&&c.lastVisibleBlockNum==9,"list state leaked after preview");
  }
  int count=0;
  for(float d:new float[]{1,2.25f,3})for(int width:new int[]{220,260,320,500})for(int visible:new int[]{1,2}){
   density=d;glassEnabled=true;Row r=new Row();r.buttons[0].edit=true;r.buttons[1].visibility=visible==1?GONE:0;
   r.onMeasure(dp(width),dp(46));int total=0;
   for(EditViewButton b:r.buttons)if(b.visibility!=GONE){total+=b.params.width;check(b.text.max+dp(58)<=b.params.width+1,"text/icon overflow");}
   check(total<=dp(width),"edit actions exceed composer");
   glassEnabled=false;r.onMeasure(dp(width),dp(46));
   check(r.buttons[0].params.width==-2&&r.buttons[0].text.max==dp(116),"native editor not restored");count++;
  }
  System.out.println("8 preview draw/exception scopes and "+count+" media edit widths passed");
 }
}'''.replace('SCOPE',scope).replace('MEASURE',measure).replace('CLIP',clip)
# Match Android's protected method override.
src=src.replace('class Base {void onMeasure','class Base {protected void onMeasure')
work=root/'build/message-preview-check';work.mkdir(exist_ok=True)
p=work/'PreviewScopeCheck.java';p.write_text(src,encoding='utf-8')
layout=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaMessageMenuLayout.java'
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(p),str(layout)],check=True)
subprocess.run(['java','-cp',str(work),'PreviewScopeCheck'],check=True)
assert 'cell.drawMenuPreview(canvas)' in chat
assert 'nebulaPreviewClipY(viewClipTop, false)' in chat and 'nebulaPreviewClipY(viewClipBottom, true)' in chat
assert 'nebulaPreviewClipY(viewClipBottom2, true)' in chat
assert 'nebulaPreviewClipY(listTop, false)' in chat
assert 'setEditPreview(editView)' in chat
composer=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaComposerStyle.java').read_text(encoding='utf-8')
assert 'active && edit.width == -1' in composer and 'editPreviewInset = inset' in composer
print('Text scope, all preview clipping passes and edit-row inset wiring passed')
