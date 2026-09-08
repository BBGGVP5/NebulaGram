"""Exercise the active wallpaper crop and shared-drawable restoration used by previews."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
wallpaper = (ui/'NebulaWallpaperPreview.java').read_text(encoding='utf-8')
start = wallpaper.index('    public static void drawWallpaper(')
brace = wallpaper.index('{',start)
depth,end = 1,brace+1
while depth:
    depth += (wallpaper[end]=='{')-(wallpaper[end]=='}')
    end += 1
method = wallpaper[start:end]
source = r'''
public class CheckMenuPreview {
 static class Rect {int left,top,right,bottom;Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}}
 static class Canvas {int depth,color;int save(){return depth++;}void clipRect(int a,int b,int c,int d){}void restoreToCount(int n){depth=n;}void drawColor(int c){color=c;}}
 static class Drawable {
  Rect bounds=new Rect(1,2,3,4),drawn;int w,h;boolean fail;
  Drawable(int w,int h){this.w=w;this.h=h;}Rect getBounds(){return bounds;}
  int getIntrinsicWidth(){return w;}int getIntrinsicHeight(){return h;}
  void setBounds(int l,int t,int r,int b){bounds=new Rect(l,t,r,b);}void setBounds(Rect r){bounds=r;}
  void draw(Canvas c){drawn=new Rect(bounds);if(fail)throw new IllegalStateException();}
 }
 static class Theme {static Drawable wallpaper;static int key_chat_wallpaper=1;static Drawable getCachedWallpaperNonBlocking(){return wallpaper;}static int getColor(int k){return 42;}}
 METHOD
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int[] natural:new int[][]{{100,200},{300,100},{-1,-1},{1080,2400}})
  for(int[] size:new int[][]{{400,120},{120,400},{300,300}}){
   Drawable d=Theme.wallpaper=new Drawable(natural[0],natural[1]);Canvas c=new Canvas();
   drawWallpaper(c,size[0],size[1]);Rect r=d.drawn;
   check(r.left<=0&&r.top<=0&&r.right>=size[0]&&r.bottom>=size[1],"crop has gaps");
   check(Math.abs(r.left+r.right-size[0])<=1&&Math.abs(r.top+r.bottom-size[1])<=1,"crop not centered");
   check(d.bounds.left==1&&d.bounds.bottom==4&&c.depth==0,"shared bounds/canvas leaked");
   d.fail=true;try{drawWallpaper(c,size[0],size[1]);throw new AssertionError("expected failure");}catch(IllegalStateException expected){}
   check(d.bounds.left==1&&d.bounds.bottom==4&&c.depth==0,"exception leaked shared state");
  }
  Theme.wallpaper=null;Canvas c=new Canvas();drawWallpaper(c,300,120);check(c.color==42,"theme fallback");
  System.out.println("12 wallpaper crops, shared-state restoration, failure cleanup and theme fallback passed");
 }
}
'''.replace('METHOD',method)
work = root/'build/menu-preview-check'
work.mkdir(parents=True,exist_ok=True)
target = work/'CheckMenuPreview.java'
target.write_text(source,encoding='utf-8')
subprocess.run(['javac','-d',str(work),str(target)],check=True)
subprocess.run(['java','-cp',str(work),'CheckMenuPreview'],check=True)
glass = (ui/'NebulaGlassSettings.java').read_text(encoding='utf-8')
assert glass.count('NebulaWallpaperPreview.drawWallpaper(')==2
assert 'Utilities.stackBlurBitmap(blurredWallpaper,blur)' in glass
assert 'cachedWallpaper == wallpaper' in glass and 'cachedBlur == blur' in glass
assert 'blurredWallpaper.recycle()' in glass and 'onDetachedFromWindow()' in glass
assert 'LinearGradient' not in glass and 'drawCircle' not in glass
menu = (ui/'NebulaMainMenu.java').read_text(encoding='utf-8')
assert 'new NebulaQrIcon(true)' in menu and 'new NebulaQrIcon(false)' in menu
assert 'menu.add(R.drawable.msg_qrcode' not in menu
icon = (ui/'NebulaQrIcon.java').read_text(encoding='utf-8')
assert 'paint.setColorFilter(filter)' in icon and 'paint.setAlpha(alpha)' in icon
print('Wallpaper blur cache/lifecycle and distinct tintable scan/create QR icons are wired')
