"""Run the actual rounded emoji-panel draw path and tab hit-cell method."""
from pathlib import Path
import subprocess
import sys
import tempfile

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
panel_draw = method(emoji, '@Override public void draw(Canvas canvas)')
factory = method(chat, 'public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable createNebulaEmojiBackground(')
factory = factory.replace('org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable', 'Drawable').replace(
    'app.nebulagram.ui.NebulaMenuStyle.provider(themeDelegate)', 'null')
hit = method(tabs, 'public boolean hitBounds(').replace('android.graphics.RectF', 'RectF')

with tempfile.TemporaryDirectory(prefix='nebula-emoji-panel-') as temp:
    source = Path(temp) / 'EmojiPanelCheck.java'
    source.write_text('''class Canvas {int clips;int save(){return 1;}void clipPath(Path p){clips++;}void restoreToCount(int n){}}
class Path {float rx,ry;void rewind(){}void addRoundRect(float l,float t,float r,float b,float x,float y,Direction d){rx=x;ry=y;}
 static class Direction {static final Direction CW=new Direction();}}
class Drawable {String source;Drawable(String s){source=s;}Drawable setColorProvider(Object p){return this;}Drawable setRadius(int r){return this;}}
class Factory {String source;Factory(String s){source=s;}Drawable create(View v){return new Drawable(source);}}
class View {int left,right,width=400,height=320,draws;float x;int getLeft(){return left;}int getRight(){return right;}float getX(){return x;}
 int getWidth(){return width;}int getHeight(){return height;}void draw(Canvas c){draws++;}}
class RectF {float left,top,right,bottom;void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
class EmojiPanelCheck extends View {
 static void check(boolean value){if(!value)throw new AssertionError();}
 Drawable nebulaPanelBackground;Path nebulaPanelClip=new Path();int dp(int n){return n;}
 Factory glassBackgroundDrawableFactory=new Factory("chat-glass");Object themeDelegate;
 View tabsContainer=new View(),tab=new View();boolean allowed=true;
 int getScrollX(){return 5;}View getTab(int i){return tab;}boolean bounds(int i,RectF out){return allowed;}
''' + panel_draw + '\n' + factory + '\n' + hit + '''
 public static void main(String[] args){EmojiPanelCheck x=new EmojiPanelCheck();
  x.nebulaPanelBackground=x.createNebulaEmojiBackground(new View());check(x.nebulaPanelBackground.source.equals("chat-glass"));
  Canvas c=new Canvas();x.draw(c);check(c.clips==1&&x.draws==1&&x.nebulaPanelClip.rx==22&&x.nebulaPanelClip.ry==22);
  x.nebulaPanelBackground=null;x.draw(c);check(c.clips==1&&x.draws==2);
  x.height=48;
  x.tab.left=100;x.tab.right=180;x.tabsContainer.x=4;RectF r=new RectF();
  check(x.hitBounds(1,r));check(r.left==99&&r.right==179&&r.top==0&&r.bottom==48);
  x.allowed=false;check(!x.hitBounds(1,r));
  System.out.println("Rounded emoji-panel clipping, chat glass routing and full tab touch cells passed");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', str(source)], check=True)
    subprocess.run(['java', '-cp', temp, 'EmojiPanelCheck'], check=True)

constructor = emoji.index('final boolean nebulaGlassPanel')
assert constructor < emoji.index('this.shouldDrawBackground = shouldDrawBackground;', constructor)
assert 'final boolean shouldDrawBackground = drawBackground && !nebulaGlassPanel;' in emoji
assert 'glassDesign = glassDesign || nebulaGlassPanel;' in emoji
assert 'nebulaPanelClip.addRoundRect(0, 0, getWidth(), getHeight(), dp(22), dp(22), Path.Direction.CW);' in panel_draw
assert 'contentView::drawList' in chat, 'Chat glass must not capture the emoji panel recursively'
assert 'updateColors();' in method(emoji[emoji.index('setBlurredBackgroundDrawableFactory(blurredBackgroundDrawableFactory);'):], 'if (nebulaGlassPanel)')
print('Early transparent child construction and separate chat capture are wired')
