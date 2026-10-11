"""Execute native phone-modal positioning; never submit an authentication request."""
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1])
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java').read_text(encoding='utf-8')
start = source.index('private final static class PhoneNumberConfirmView')
scope = source[start:source.index('private final static class PhoneInputData', start)]


def method(signature, optional=False):
    if optional and signature not in scope:
        return ''
    start = scope.index(signature)
    opening = scope.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (scope[end] == '{') - (scope[end] == '}')
        end += 1
    return scope[start:end]


update = method('private void updateFabPosition()')
layout = method('protected void onLayout(')
resize = method('protected void onSizeChanged(', True)
show = scope[scope.index('private void show()'):]
show_prefix = show[show.index('{') + 1:show.index('ValueAnimator anim')]
for signature in ['onFabPressed(this, fabTransform)', 'onEditPressed(this, editTextView)', 'onConfirmPressed(this, confirmTextView)',
                  'callback.onDismiss(this)', 'fabContainer.setVisibility(VISIBLE)', 'fabButton.requestFocus()', 'fabButton.setProgressVisible(true, true)']:
    assert signature in scope, 'native confirmation/edit/dismiss/progress/accessibility callback changed: ' + signature
assert 'Utilities.stackBlurBitmap(bitmap, Math.max(8, Math.max(w, h) / 150))' in scope, 'blur was not part of this requested fix'
methods = '\n'.join([update, layout, resize, 'void open(){' + show_prefix + '}'])
program = '''class PointF {float x,y;}
class View {
 float x,y,tx,ty;int width=56,height=56,left,right,top,bottom;
 int getWidth(){return width;}int getHeight(){return height;}int getMeasuredWidth(){return width;}int getMeasuredHeight(){return height;}
 void setTranslationX(float x){tx=x;}void setTranslationY(float y){ty=y;}float getTranslationY(){return ty;}
 int getLeft(){return left;}int getRight(){return right;}
 void layout(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
}
class ViewGroup extends View {}
class Parent extends ViewGroup {
 protected void onLayout(boolean c,int l,int t,int r,int b){}
 protected void onSizeChanged(int w,int h,int oldw,int oldh){width=w;height=h;}
}
class ViewPositionWatcher {static void computeCoordinatesInParent(View source,ViewGroup parent,PointF point){point.x=source.x;point.y=source.y;}}
class LocaleController {static boolean isRTL;}
class AndroidUtilities {static int dp(float n){return Math.round(n);}}
public class PhoneOverlayCheck extends Parent {
 boolean positionLocked;final PointF pointF=new PointF();
 final ViewGroup fragmentView=new ViewGroup();final View fabContainer=new View(),fabButton=new View(),popupLayout=new View();
 int dp(float n){return AndroidUtilities.dp(n);}void requestLayout(){}
 METHODS
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
  for(boolean rtl:new boolean[]{false,true})for(int buttonWidth:new int[]{56,320})for(int y:new int[]{430,630}){
   PhoneOverlayCheck modal=new PhoneOverlayCheck();modal.width=393;modal.height=1000;LocaleController.isRTL=rtl;
   modal.popupLayout.height=140;modal.popupLayout.left=24;modal.popupLayout.right=369;
   modal.fabContainer.x=rtl?24:337-buttonWidth;modal.fabContainer.y=y;modal.fabContainer.width=buttonWidth;
   modal.updateFabPosition();modal.open();modal.onLayout(true,0,0,393,1000);
   float captured=modal.fabButton.ty;int bottom=modal.popupLayout.bottom;
   for(int tick=1;tick<=60;tick++){
    modal.fabContainer.y=y+280*tick/60f;modal.updateFabPosition();modal.onLayout(false,0,0,393,1000);
    check(modal.fabButton.ty==captured&&modal.popupLayout.bottom==bottom,"shown confirmation follows the hidden button after keyboard hide");cases++;
   }
   check(modal.popupLayout.top>=0&&modal.popupLayout.bottom<modal.fabButton.ty,"popup/button overlap or negative top");
   modal.height=360;modal.onLayout(true,0,0,393,360);
   check(modal.popupLayout.top>=0&&modal.fabButton.ty+56<=360,"modal off screen after viewport shrink");
   modal.fabContainer.y=270;modal.fabContainer.x=24;modal.onSizeChanged(600,360,393,360);modal.onLayout(true,0,0,600,360);
   check(modal.fabButton.ty==270&&modal.positionLocked,"width change loses a valid anchor or unlocks later keyboard ticks");cases++;
  }
  System.out.println(cases+" native phone-confirmation states: frozen keyboard ticks, RTL/wide button, viewport clamp and width refresh passed");
 }
}
'''.replace('METHODS', methods)
with tempfile.TemporaryDirectory(prefix='nebula-phone-confirmation-') as folder:
    work = Path(folder)
    java = work / 'PhoneOverlayCheck.java'
    java.write_text(program, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(java)], check=True)
    subprocess.run(['java', '-cp', str(work), 'PhoneOverlayCheck'], check=True)
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'AppData/Local/Android/Sdk'))
    platforms = sorted((p for p in (sdk / 'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*', p.parent.name)),
                       key=lambda p: tuple(map(int, p.parent.name.removeprefix('android-').split('.'))))
    if not platforms:
        raise SystemExit('Android SDK platform required for native modal API check')
    api = work / 'PhoneOverlayApi.java'
    api.write_text('''import android.content.Context;import android.graphics.PointF;import android.view.View;import android.view.ViewGroup;import android.widget.FrameLayout;
class AndroidUtilities {static int dp(float n){return Math.round(n);}}
class LocaleController {static boolean isRTL;}
class ViewPositionWatcher {static void computeCoordinatesInParent(View source,ViewGroup parent,PointF point){}}
class PhoneOverlayApi extends FrameLayout {
 PhoneOverlayApi(Context c){super(c);}boolean positionLocked;final PointF pointF=new PointF();
 ViewGroup fragmentView;View fabContainer,fabButton;FrameLayout popupLayout;
 int dp(float n){return AndroidUtilities.dp(n);}
 METHODS
}
'''.replace('METHODS', methods), encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(platforms[-1]), '-d', str(work / 'sdk'), str(api)], check=True)
    print('Actual phone-overlay hooks compile against ' + platforms[-1].parent.name)
