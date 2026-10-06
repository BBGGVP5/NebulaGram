"""Run the production emoji component through loading, focus and reduced-motion transitions."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sources={
'android/content/Context.java': 'package android.content;public class Context {}',
'android/graphics/Rect.java': 'package android.graphics;public class Rect {}',
'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=37;}}',
'android/util/TypedValue.java': 'package android.util;public class TypedValue {public static final int COMPLEX_UNIT_DIP=1;}',
'android/view/Gravity.java': 'package android.view;public class Gravity {public static final int CENTER=17;}',
'android/view/animation/LinearInterpolator.java': 'package android.view.animation;public class LinearInterpolator {}',
'android/view/ViewTreeObserver.java': """package android.view;import java.util.*;public class ViewTreeObserver {
public interface OnScrollChangedListener {void onScrollChanged();}public final ArrayList<OnScrollChangedListener> observers=new ArrayList<>();
public void addOnScrollChangedListener(OnScrollChangedListener x){observers.add(x);}public void removeOnScrollChangedListener(OnScrollChangedListener x){observers.remove(x);}
public boolean isAlive(){return true;}public void scroll(){for(var x:new ArrayList<>(observers))x.onScrollChanged();}}""",
'android/view/View.java': """package android.view;import java.util.*;public class View {
public static final int VISIBLE=0,INVISIBLE=4,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2;
public boolean focus=true,viewport=true;public int visibility=0;public float rotation,translation,scaleX=1,scaleY=1;
public final ViewTreeObserver tree=new ViewTreeObserver();public static final ArrayList<Runnable> posts=new ArrayList<>();
public View(android.content.Context c){}public void setVisibility(int n){visibility=n;onVisibilityChanged(this,n);}public int getVisibility(){return visibility;}
public boolean isShown(){return visibility==0;}public boolean hasWindowFocus(){return focus;}public boolean getGlobalVisibleRect(android.graphics.Rect r){return viewport;}
public void setImportantForAccessibility(int x){}public void setTranslationY(float x){translation=x;}public void setRotation(float x){rotation=x;}
public void setScaleX(float x){scaleX=x;}public void setScaleY(float x){scaleY=x;}public boolean post(Runnable r){posts.add(r);return true;}
public static void flush(){while(!posts.isEmpty())posts.remove(0).run();}public ViewTreeObserver getViewTreeObserver(){return tree;}
public void onWindowFocusChanged(boolean x){focus=x;}protected void onVisibilityChanged(View v,int n){}protected void onSizeChanged(int w,int h,int ow,int oh){}
protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}public void attach(){onAttachedToWindow();}public void detach(){onDetachedFromWindow();}
}""",
'android/widget/FrameLayout.java': """package android.widget;import java.util.*;public class FrameLayout extends android.view.View {
public ArrayList<android.view.View> children=new ArrayList<>();public FrameLayout(android.content.Context c){super(c);}public void addView(android.view.View v,LayoutParams p){children.add(v);}
public static class LayoutParams {public LayoutParams(int w,int h){}}}""",
'android/widget/TextView.java': """package android.widget;public class TextView extends android.view.View {
public TextView(android.content.Context c){super(c);}public void setGravity(int x){}public void setTextSize(int u,float x){}public void setTextColor(int c){}public void setIncludeFontPadding(boolean b){}
public void setText(CharSequence t){}public Paint getPaint(){return new Paint();}public static class Paint {public Object getFontMetricsInt(){return null;}}}""",
'android/animation/ValueAnimator.java': """package android.animation;import java.util.*;public class ValueAnimator {
public static final int INFINITE=-1;public static boolean enabled=true;public static final ArrayList<ValueAnimator> running=new ArrayList<>();
public interface Update {void update(ValueAnimator v);}public Update update;public float value;public static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}
public static boolean areAnimatorsEnabled(){return enabled;}public void setDuration(long d){}public void setRepeatCount(int n){}public void setInterpolator(Object i){}
public void addUpdateListener(Update u){update=u;}public void start(){running.add(this);}public void cancel(){running.remove(this);}public Object getAnimatedValue(){return value;}
public void advance(float v){value=v;update.update(this);}}
""",
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static float dpf2(float v){return v;}}',
'org/telegram/messenger/Emoji.java':'package org.telegram.messenger;public class Emoji {public static CharSequence replaceEmoji(String v,Object f,boolean x){return v;}}',
'org/telegram/tgnet/TLRPC.java':'package org.telegram.tgnet;public class TLRPC {public static class Document {public long id=10;}}',
'org/telegram/messenger/MediaDataController.java':"""package org.telegram.messenger;public class MediaDataController {
public static final int TYPE_EMOJI=4;public static org.telegram.tgnet.TLRPC.Document doc;public static final MediaDataController instance=new MediaDataController();
public static MediaDataController getInstance(int a){return instance;}public org.telegram.tgnet.TLRPC.Document getEmojiAnimatedSticker(String e){return doc;}public void checkStickers(int t){}}
""",
'org/telegram/messenger/NotificationCenter.java':"""package org.telegram.messenger;import java.util.*;public class NotificationCenter {
public static final int stickersDidLoad=1;public static final NotificationCenter instance=new NotificationCenter();public ArrayList<NotificationCenterDelegate> observers=new ArrayList<>();
public interface NotificationCenterDelegate {void didReceivedNotification(int id,int a,Object...args);}public static NotificationCenter getInstance(int a){return instance;}
public void addObserver(NotificationCenterDelegate d,int n){observers.add(d);}public void removeObserver(NotificationCenterDelegate d,int n){observers.remove(d);}
public void fire(){for(var d:new ArrayList<>(observers))d.didReceivedNotification(stickersDidLoad,0);}}
""",
'org/telegram/messenger/ImageReceiver.java':"""package org.telegram.messenger;public class ImageReceiver {
public boolean lottie,video,allow,allowLottie;public int repeat;public Object getLottieAnimation(){return lottie?this:null;}public Object getAnimation(){return video?this:null;}
public void setAutoRepeat(int r){repeat=r;}public void setAllowStartAnimation(boolean x){allow=x;}public void setAllowStartLottieAnimation(boolean x){allowLottie=x;}}
""",
'org/telegram/ui/Components/RLottieImageView.java':"""package org.telegram.ui.Components;public class RLottieImageView extends android.view.View {
public org.telegram.messenger.ImageReceiver receiver;public boolean playing;public RLottieImageView(android.content.Context c){super(c);}protected void onLoaded(){}
public void load(boolean lottie,boolean video){onLoaded();receiver.lottie=lottie;receiver.video=video;}
public void setAutoRepeat(boolean r){}public void setAnimation(org.telegram.tgnet.TLRPC.Document d,int w,int h){receiver=new org.telegram.messenger.ImageReceiver();}
public org.telegram.messenger.ImageReceiver getImageReceiver(){return receiver;}public void clearAnimationDrawable(){receiver=null;playing=false;}
public void playAnimation(){playing=true;}public void stopAnimation(){playing=false;}}
""",
'app/nebulagram/ui/NebulaGlass.java':'package app.nebulagram.ui;public class NebulaGlass {public static boolean reduce;public static boolean reduced(){return reduce;}}',
'CheckEmoji.java': """import app.nebulagram.ui.*;import android.view.View;import android.animation.ValueAnimator;import org.telegram.messenger.*;import org.telegram.ui.Components.RLottieImageView;
public class CheckEmoji {
static void check(boolean x,String m){if(!x)throw new AssertionError(m);}public static void main(String[] args){
var emoji=new NebulaAnimatedEmoji(new android.content.Context(),0,"🛡️",112);emoji.attach();
var text=emoji.children.get(0);var image=(RLottieImageView)emoji.children.get(1);
check(ValueAnimator.running.size()==1,"missing shield document must still animate its native intro glyph");
ValueAnimator.running.get(0).advance(.25f);check(text.rotation!=0&&text.translation!=0,"intro motion reaches the glyph");
emoji.onWindowFocusChanged(false);check(ValueAnimator.running.isEmpty()&&text.rotation==0,"blurred window must stop and reset motion");emoji.onWindowFocusChanged(true);
check(ValueAnimator.running.size()==1,"focus resumes the intro");
MediaDataController.doc=new org.telegram.tgnet.TLRPC.Document();NotificationCenter.instance.fire();
image.load(false,false);View.flush();check(text.getVisibility()==View.VISIBLE&&image.getVisibility()==View.INVISIBLE,"thumbnail cannot replace the native glyph");
image.load(true,false);check(text.getVisibility()==View.VISIBLE,"loaded callback must wait until receiver installs animation");View.flush();
check(text.getVisibility()==View.GONE&&image.playing&&ValueAnimator.running.isEmpty(),"decoded Lottie replaces fallback and starts");
check(image.receiver.repeat==1&&image.receiver.allow&&image.receiver.allowLottie,"repeat and start apply to receiver");
emoji.onWindowFocusChanged(false);check(!image.playing&&!image.receiver.allow&&image.receiver.repeat==0,"lost focus stops decoded animation");emoji.onWindowFocusChanged(true);
image.load(false,true);View.flush();check(image.playing&&text.getVisibility()==View.GONE,"video emoji supported too");
emoji.viewport=false;emoji.tree.scroll();check(!image.playing,"offscreen intro must stop");emoji.viewport=true;emoji.tree.scroll();check(image.playing,"visible intro resumes");
NebulaGlass.reduce=true;emoji.tree.scroll();check(!image.playing&&ValueAnimator.running.isEmpty(),"reduced motion respected");NebulaGlass.reduce=false;
emoji.detach();check(NotificationCenter.instance.observers.isEmpty()&&emoji.tree.observers.isEmpty()&&!image.playing,"detach releases observers and animation");
MediaDataController.doc=null;var small=new NebulaAnimatedEmoji(new android.content.Context(),0,"🤖",32);small.attach();check(ValueAnimator.running.isEmpty(),"small row fallback must stay quiet");small.detach();
var reduced=new NebulaAnimatedEmoji(new android.content.Context(),0,"🛡️",112);ValueAnimator.enabled=false;reduced.attach();check(ValueAnimator.running.isEmpty(),"system animation setting respected");reduced.detach();ValueAnimator.enabled=true;
System.out.println("Settings emoji: thumbnail/decoded loading, shield fallback, video, focus, viewport, reduced motion and detach passed");
}}
"""}
with tempfile.TemporaryDirectory(prefix='nebula-emoji-') as temp:
 tree=Path(temp)
 for name,text in sources.items():
  f=tree/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,encoding='utf-8')
 javac=Path(os.environ['JAVA_HOME'])/'bin/javac';java=Path(os.environ['JAVA_HOME'])/'bin/java'
 subprocess.run([str(javac),'-encoding','UTF-8','-d',str(tree),str(ui/'NebulaAnimatedEmoji.java'),*map(str,tree.rglob('*.java'))],check=True)
 subprocess.run([str(java),'-cp',str(tree),'CheckEmoji'],check=True)
