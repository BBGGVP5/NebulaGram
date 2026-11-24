"""Execute the chat header visible-centre and the rich-message selection shift.

1. The centred glass header title is centred by what is drawn (left icon,
   text, premium/emoji status, mute/verified icon), from the drawables present
   at draw time, even when the offset cached at measure time is stale.
2. A wide rich message (TYPE_ARTICLE) in selection mode moves as far as the
   screen allows, and its avatar follows that shift instead of borrowing the
   full 35dp of the sender's previous message.

Extracts the patched methods from a prepared Telegram tree and runs them in a
small JVM model. Not Android rendering.
"""
from pathlib import Path
import subprocess, sys, tempfile

if len(sys.argv) != 2:
    raise SystemExit('Usage: python scripts/check-header-center-selection.py <Telegram tree after patches>')
tree = Path(sys.argv[1])
ui = tree / 'TMessagesProj/src/main/java/org/telegram/ui'


def method(s, signature):
    start = s.index(signature)
    brace = s.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (s[end] == '{') - (s[end] == '}')
        end += 1
    return s[start:end]


simple = (ui / 'ActionBar/SimpleTextView.java').read_text(encoding='utf-8')
header = (ui / 'Components/ChatAvatarContainer.java').read_text(encoding='utf-8')
cell = (ui / 'Cells/ChatMessageCell.java').read_text(encoding='utf-8')
chat = (ui / 'ChatActivity.java').read_text(encoding='utf-8')

draw = method(simple, 'protected void onDraw(')
assert 'super.onDraw(canvas);\n        nebulaApplyVisibleCenter();' in draw, 'visible centre is not applied before drawing'
center = method(simple, 'private void nebulaApplyVisibleCenter()')
start = draw.index('        if (rightDrawable != null && rightDrawableOutside)')
icons = method(draw[start:], 'if (rightDrawable != null && rightDrawableOutside)')
start = draw.index('        if (rightDrawable2 != null && rightDrawableOutside)', start)
icons += '\n' + method(draw[start:], 'if (rightDrawable2 != null && rightDrawableOutside)')

configure = method(header, 'public void setNebulaCenteredTitle(boolean enabled, boolean hideAvatarForSavedMessages)')
assert 'titleTextView.setNebulaCenterVisibleWidth(enabled && app.nebulagram.ui.NebulaAppearance.chatHeader());' in configure
for signature in ['public void setTitle(CharSequence value, boolean scam', 'public void setTitleIcons(']:
    assert 'nebulaRemeasureCenteredTitle();' in method(header, signature), signature + ' does not re-measure the capsule'
assert 'requestLayout();' in method(header, 'private void nebulaRemeasureCenteredTitle()')

limit = method(cell, 'private int nebulaLimitCheckBoxTranslation(')
limited = method(cell, 'public boolean isNebulaCheckBoxTranslationLimited()')
assert 'getCurrentBackgroundRight() + dp(35) > getWidth())' not in cell, 'all-or-nothing article clamp is back'
assert cell.count('checkBoxTranslation = nebulaLimitCheckBoxTranslation(') == 2, 'both selection shifts go through the limit'
guard = 'canUpdateTx = cmcell.isCheckBoxVisible() && tx == 0 && !cmcell.isNebulaCheckBoxTranslationLimited();'
assert guard in chat, 'avatar still borrows the previous message shift'

source = r'''
public class CheckHeaderCenterSelection {
static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
static int dp(float v){return (int)Math.ceil(v*2.75f);}
static class Gravity {static final int LEFT=3,CENTER_HORIZONTAL=1,HORIZONTAL_GRAVITY_MASK=7,VERTICAL_GRAVITY_MASK=112,CENTER_VERTICAL=16;}
static class Layout {int getLineCount(){return 1;} float getLineLeft(int l){return 0;}}
static class Icon {int w,left,right;Icon(int w){this.w=w;} int getIntrinsicWidth(){return w;} int getIntrinsicHeight(){return w;}
 void setBounds(int l,int t,int r,int b){left=l;right=r;} void draw(Object c){}}
static class Title {
 boolean nebulaCenterVisibleWidth=true,leftDrawableOutside,rightDrawableOutside=true,rightDrawableInside,rightDrawableHidden;
 int gravity=Gravity.CENTER_HORIZONTAL,offsetX,textWidth,drawablePadding=dp(4),measured,paddingRight=0;
 int scrollingOffset=0,nextScrollX=9000,textHeight=40,rightDrawableTopPadding,rightDrawableX,rightDrawableY;
 float rightDrawableScale=1; Layout layout=new Layout(); Object canvas;
 Icon leftDrawable,rightDrawable,rightDrawable2;
 int getMeasuredWidth(){return measured;} int getMeasuredHeight(){return 80;} int getPaddingLeft(){return 0;} int getPaddingRight(){return 0;} int getPaddingTop(){return 0;}
 int getMaxTextWidth(){return measured-(rightDrawableOutside&&rightDrawable!=null?rightDrawable.getIntrinsicWidth()+drawablePadding:0)-(rightDrawableOutside&&rightDrawable2!=null?rightDrawable2.getIntrinsicWidth()+drawablePadding:0);}
 @@CENTER@@
 int[] draw(){
  nebulaApplyVisibleCenter();
  int textOffsetX=leftDrawable!=null&&!leftDrawableOutside?drawablePadding+leftDrawable.getIntrinsicWidth():0;
  @@ICONS@@
  int left=offsetX, right=offsetX+textOffsetX+textWidth;
  if(rightDrawable!=null)right=rightDrawable.right; if(rightDrawable2!=null)right=rightDrawable2.right;
  return new int[]{left,right};
 }
}
static class MessageObject {static final int TYPE_TEXT=0,TYPE_ARTICLE=36; int type;}
static class Cell {
 MessageObject currentMessageObject=new MessageObject(); boolean checkBoxVisible=true,checkBoxAnimationInProgress,nebulaCheckBoxTranslationLimited; int width=dp(411),backgroundRight;
 int getWidth(){return width;} int getCurrentBackgroundRight(){return backgroundRight;}
 @@LIMIT@@
 @@LIMITED@@
}
public static void main(String[] a){
 int titles=0,cells=0;
 for(int measured:new int[]{dp(104),dp(180),dp(260)})for(int text:new int[]{dp(20),dp(60),dp(120)})
 for(int left=0;left<2;left++)for(int icons=0;icons<3;icons++)for(float scale:new float[]{1f,.5f})for(int stale:new int[]{-dp(30),0,dp(17),dp(70)}){
  Title t=new Title();t.measured=measured;t.textWidth=text;t.offsetX=stale;t.rightDrawableScale=scale;
  if(left==1){t.leftDrawable=new Icon(dp(14));}
  if(icons>0)t.rightDrawable=new Icon(dp(24)); if(icons>1)t.rightDrawable2=new Icon(dp(18));
  int visible=text+(left==1?dp(4)+dp(14):0)+(icons>0?dp(4)+(int)(dp(24)*scale):0)+(icons>1?dp(4)+(int)(dp(18)*scale):0);
  int[] g=t.draw();
  if(visible<=measured&&(left==1?dp(4)+dp(14):0)+text<=t.getMaxTextWidth()){
   check(Math.abs((g[0]+g[1])/2f-measured/2f)<=1.5f,"title group off centre: "+measured+"/"+text+"/"+left+"/"+icons+"/"+scale+" -> "+g[0]+".."+g[1]);
  } else {
   check(g[0]==0,"overflowing title is not start-aligned for the marquee");
  }
  titles++;
 }
 for(int type:new int[]{MessageObject.TYPE_TEXT,MessageObject.TYPE_ARTICLE})for(int right:new int[]{dp(200),dp(380),dp(400),dp(411)})for(int p=0;p<=35;p+=5){
  Cell c=new Cell();c.currentMessageObject.type=type;c.backgroundRight=right;
  int want=dp(p), own=c.nebulaLimitCheckBoxTranslation(want);
  if(type==MessageObject.TYPE_ARTICLE)check(right+own<=c.width,"wide rich message pushed past the screen edge");
  else check(own==want,"ordinary message shift changed");
  check(own==Math.min(want,Math.max(0,c.width-right))||type!=MessageObject.TYPE_ARTICLE,"article does not move as far as it can");
  // ChatActivity: avatar tx starts at this cell's own shift; a zero shift may borrow the previous message's 35dp.
  float tx=own; boolean canUpdateTx=c.checkBoxVisible&&tx==0&&!c.isNebulaCheckBoxTranslationLimited();
  float neighbour=dp(35); if(canUpdateTx&&neighbour>0)tx=neighbour;
  if(p>0)check(tx==own,"avatar leaves its bubble: own "+own+" avatar "+tx);
  cells++;
 }
 System.out.println(titles+" title centring cases and "+cells+" selection shift cases passed");
}}
'''.replace('@@CENTER@@', center).replace('@@ICONS@@', icons).replace('@@LIMITED@@', limited).replace('@@LIMIT@@', limit)
with tempfile.TemporaryDirectory() as work:
    target = Path(work) / 'CheckHeaderCenterSelection.java'
    target.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', work, str(target)], check=True)
    subprocess.run(['java', '-cp', work, 'CheckHeaderCenterSelection'], check=True)
