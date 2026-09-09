"""Run actual panel backdrop and tab hit-cell methods; no Android raster claims."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

emoji = (native / 'Components/EmojiView.java').read_text(encoding='utf-8')
chat = (native / 'ChatActivity.java').read_text(encoding='utf-8')
tabs = (native / 'Components/PagerSlidingTabStrip.java').read_text(encoding='utf-8')
backdrop = method(emoji, 'private void drawNebulaPanelBackdrop(').replace('app.nebulagram.ui.NebulaMenuStyle.enabled()', 'enabled')
factory = method(chat, 'public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable createNebulaEmojiBackground(')
factory = factory.replace('org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable', 'Drawable').replace('app.nebulagram.ui.NebulaMenuStyle.provider(themeDelegate)', 'null')
hit = method(tabs, 'public boolean hitBounds(').replace('android.graphics.RectF', 'RectF')
with tempfile.TemporaryDirectory(prefix='nebula-emoji-panel-') as temp:
    p = Path(temp) / 'EmojiPanelCheck.java'
    p.write_text('''class EmojiPanelCheck {
 static void check(boolean value){if(!value)throw new AssertionError();}
 static class Canvas {int fills,draws,color;void drawColor(int c){fills++;color=c;}}
 static class Drawable {int width,height;String source;Drawable(String s){source=s;}
  void setBounds(int l,int t,int r,int b){width=r-l;height=b-t;}
  void draw(Canvas c){c.draws++;} Drawable setColorProvider(Object p){return this;} Drawable setRadius(int r){return this;} }
 static class Factory {String source;Factory(String s){source=s;}Drawable create(View v){return new Drawable(source);}}
 static class Theme {static int key_chat_emojiPanelBackground=1,key_windowBackgroundWhite=2;}
 static class View {int left,right;float x;int getLeft(){return left;}int getRight(){return right;}float getX(){return x;}}
 static class RectF {float left,top,right,bottom;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
 boolean enabled=true;Drawable nebulaPanelBackground;int getMeasuredWidth(){return 400;}int getMeasuredHeight(){return 320;}
 int getThemedColor(int key){return key;}int dp(int n){return n;}
 Factory glassBackgroundDrawableFactory=new Factory("chat-glass"),navbarContentDrawableFactory=new Factory("wallpaper-only");
 View tabsContainer=new View(),tab=new View();boolean allowed=true;
 int getScrollX(){return 5;}int getHeight(){return 48;}View getTab(int i){return tab;}
 boolean bounds(int i,RectF out){return allowed;}
''' + backdrop + '\n' + factory + '\n' + hit + '''
 public static void main(String[] args){EmojiPanelCheck x=new EmojiPanelCheck();
 x.nebulaPanelBackground=x.createNebulaEmojiBackground(new View());check(x.nebulaPanelBackground.source.equals("chat-glass"));
 Canvas c=new Canvas();x.drawNebulaPanelBackdrop(c);check(c.draws==1&&c.fills==0);
 check(x.nebulaPanelBackground.width==400&&x.nebulaPanelBackground.height==320);
 x.nebulaPanelBackground=null;x.drawNebulaPanelBackdrop(c);check(c.draws==1&&c.fills==1&&c.color==1);
 x.enabled=false;x.drawNebulaPanelBackdrop(c);check(c.fills==2&&c.color==2);
 x.tab.left=100;x.tab.right=180;x.tabsContainer.x=4;RectF r=new RectF();
 check(x.hitBounds(1,r));check(r.left==99&&r.right==179&&r.top==0&&r.bottom==48);
 x.allowed=false;check(!x.hitBounds(1,r));
 System.out.println("Emoji backdrop routing, non-opaque capture, fallback and full tab touch cells passed");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(p)], check=True)
    subprocess.run(['java', '-cp', temp, 'EmojiPanelCheck'], check=True)

constructor = emoji.index('final boolean nebulaGlassPanel')
assert constructor < emoji.index('this.shouldDrawBackground = shouldDrawBackground;', constructor)
assert 'final boolean shouldDrawBackground = drawBackground && !nebulaGlassPanel;' in emoji
assert 'glassDesign = glassDesign || nebulaGlassPanel;' in emoji
assert 'drawNebulaPanelBackdrop(c);' in method(emoji[emoji.index('private Drawable nebulaPanelBackground;'):], 'protected void dispatchDraw(')
assert 'contentView::drawList' in chat, 'Chat glass must not capture the emoji panel recursively'
assert 'updateColors();' in method(emoji[emoji.index('setBlurredBackgroundDrawableFactory(blurredBackgroundDrawableFactory);'):], 'if (nebulaGlassPanel)')
print('Early transparent child construction and separate chat capture are wired')
