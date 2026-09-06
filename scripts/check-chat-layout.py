"""Run actual overlay geometry with deterministic view/recorded-drawable models.

Draw calls retain mutable drawable references until the frame is checked,
matching the lifetime hazard of Android RenderNode commands. This is not a
device rasterization, input-method, or gesture test.
"""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
work = root / 'build/chat-fixes/composer-check'
stubs = {
'app/nebulagram/ui/NebulaTheme.java': 'package app.nebulagram.ui; public class NebulaTheme {public static boolean enabled=true;public static boolean materialYouEnabled(){return enabled;}}',

'android/content/Context.java': 'package android.content; public class Context {}',
'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {}',
'android/graphics/ColorFilter.java': 'package android.graphics; public class ColorFilter {}',
'android/graphics/PixelFormat.java': 'package android.graphics; public class PixelFormat {public static int TRANSLUCENT=0;}',
'android/graphics/Rect.java': '''package android.graphics; public class Rect {
public int left,top,right,bottom; public Rect(){} public Rect(Rect r){set(r);} public Rect(int l,int t,int r,int b){set(l,t,r,b);}
public void set(Rect r){set(r.left,r.top,r.right,r.bottom);} public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
public int width(){return right-left;} public int height(){return bottom-top;} public boolean isEmpty(){return width()<=0||height()<=0;}
}''',
'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable; import android.graphics.*; public abstract class Drawable {
Rect bounds=new Rect(); public Rect getBounds(){return bounds;} public void setBounds(Rect r){bounds.set(r);}
public abstract void draw(Canvas c); public void setAlpha(int a){} public void setColorFilter(ColorFilter c){} public int getOpacity(){return 0;}
public int getIntrinsicWidth(){return 0;} public int getIntrinsicHeight(){return 0;}
}''',
'android/view/MotionEvent.java': 'package android.view; public class MotionEvent {}',
'android/view/Gravity.java': 'package android.view; public class Gravity {public static int RIGHT=5,LEFT=3,HORIZONTAL_GRAVITY_MASK=7; public static int getAbsoluteGravity(int g,int d){return g;}}',
'android/view/View.java': '''package android.view; public class View {
public static final int VISIBLE=0,INVISIBLE=4,GONE=8; int visibility=0; public ViewGroup parent; int l,t,r,b;
float alpha=1,sx=1,sy=1,tx,ty; Object params=new android.widget.FrameLayout.LayoutParams();
public View(){} public View(android.content.Context c){} public Object getParent(){return parent;}
public Object getLayoutParams(){return params;} public void setLayoutParams(Object p){params=p;}
public void forceLayout(){} public void requestLayout(){} public void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}
public int getWidth(){return r-l;} public int getHeight(){return b-t;} public int getMeasuredWidth(){return getWidth();} public int getMeasuredHeight(){return getHeight();}
public int getLeft(){return l;} public int getRight(){return r;} public int getTop(){return t;} public int getBottom(){return b;}
public float getX(){return l+tx;} public float getY(){return t+ty;} public int getScrollX(){return 0;} public int getScrollY(){return 0;}
public int getPaddingTop(){return 0;} public int getPaddingLeft(){return 0;} public int getPaddingRight(){return 0;} public int getLayoutDirection(){return 0;}
public void setAlpha(float a){alpha=a;} public float getAlpha(){return alpha;} public void setScaleX(float s){sx=s;} public void setScaleY(float s){sy=s;}
public float getScaleX(){return sx;} public float getScaleY(){return sy;} public void setTranslationX(float x){tx=x;} public float getTranslationX(){return tx;} public void setTranslationY(float y){ty=y;}
public int getVisibility(){return visibility;} public void setVisibility(int v){visibility=v;} public boolean dispatchTouchEvent(MotionEvent e){return true;}
}''',
'android/view/ViewGroup.java': '''package android.view; public class ViewGroup extends View {
public static class MarginLayoutParams {public int width,height,leftMargin,rightMargin,topMargin;} public void addView(View v){v.parent=this;}
}''',
'android/widget/FrameLayout.java': 'package android.widget; public class FrameLayout extends android.view.ViewGroup {public static class LayoutParams extends MarginLayoutParams {public int gravity=3;}}',
'android/widget/EditText.java': 'package android.widget; public class EditText extends android.view.View {}',
'android/widget/ImageView.java': 'package android.widget; public class ImageView extends android.view.View {public ImageView(android.content.Context c){super(c);}}',
'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static float density=1; public static int dp(float v){return (int)Math.ceil(v*density);}}',
'app/nebulagram/ui/NebulaHeaderCounter.java': 'package app.nebulagram.ui; public class NebulaHeaderCounter {public static int backWidth(){return org.telegram.messenger.AndroidUtilities.dp(58);}}',
'app/nebulagram/ui/NebulaAppearance.java': 'package app.nebulagram.ui; public class NebulaAppearance {public static boolean enabled=true; public static boolean iosComposer(){return enabled;} public static boolean chatHeader(){return enabled;} public static boolean adaptiveHeader(){return true;} public static boolean centeredHeader(){return true;} public static boolean folderTitle(){return enabled;}}',
'org/telegram/ui/ActionBar/ActionBar.java': 'package org.telegram.ui.ActionBar; public class ActionBar {public SimpleTextView title=new SimpleTextView();public SimpleTextView getTitleTextView(){return title;} public SimpleTextView getTitleTextView2(){return null;} public void setTitleAnimated(CharSequence text,boolean bottom,long duration,Object interpolator){title.setText(text);} public void setTitle(CharSequence text,android.graphics.drawable.Drawable icon){title.setText(text);}public boolean floating,hidden,savedClassic;public void setNebulaClassicSavedHeader(boolean v){savedClassic=v;}public void setNebulaFloatingChatHeader(boolean a,boolean b,boolean c){floating=a;hidden=b;}}',
'org/telegram/ui/ActionBar/ActionBarMenuItem.java': 'package org.telegram.ui.ActionBar; public class ActionBarMenuItem {public void setIcon(android.graphics.drawable.Drawable d){}}',
'org/telegram/ui/Components/AvatarDrawable.java': 'package org.telegram.ui.Components; public class AvatarDrawable extends android.graphics.drawable.Drawable {public static int AVATAR_TYPE_SAVED=1;public void setAvatarType(int i){} public void draw(android.graphics.Canvas c){}}',
'org/telegram/ui/Components/ChatActivityEnterView.java': 'package org.telegram.ui.Components; public class ChatActivityEnterView extends android.widget.FrameLayout {}',
'org/telegram/ui/Components/blur3/BlurredBackgroundDrawableViewFactory.java': 'package org.telegram.ui.Components.blur3; public class BlurredBackgroundDrawableViewFactory {public org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable create(android.view.View view, org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider provider) {return new org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable();}}',
'org/telegram/ui/Components/blur3/drawable/color/BlurredBackgroundColorProvider.java': 'package org.telegram.ui.Components.blur3.drawable.color; public class BlurredBackgroundColorProvider {}',
'org/telegram/ui/Components/blur3/drawable/BlurredBackgroundDrawable.java': '''package org.telegram.ui.Components.blur3.drawable; import android.graphics.*; public class BlurredBackgroundDrawable {
public Rect bounds=new Rect(); public int padding,alpha=255; public static java.util.List<BlurredBackgroundDrawable> nodes=new java.util.ArrayList<>(); public static java.util.List<Rect> surfaces=new java.util.ArrayList<>(); public static java.util.List<Integer> alphas=new java.util.ArrayList<>();
public int getAlpha(){return alpha;} public void setAlpha(int a){alpha=a;} public void setPadding(int p){padding=p;} public void setRadius(int r){}
public Rect getBounds(){return bounds;} public Rect getPaddedBounds(){return new Rect(bounds.left+padding,bounds.top+padding,bounds.right-padding,bounds.bottom-padding);}
public void setBounds(Rect r){bounds.set(r);} public void setBounds(int l,int t,int r,int b){bounds.set(l,t,r,b);} public void draw(Canvas c){nodes.add(this);surfaces.add(getPaddedBounds());alphas.add(alpha);}
}''',
'android/text/TextUtils.java': 'package android.text; public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;} public static boolean equals(CharSequence a,CharSequence b){return a==b || a!=null&&b!=null&&a.toString().equals(b.toString());}}',
 'android/view/ViewPropertyAnimator.java': 'package android.view; public class ViewPropertyAnimator {public ViewPropertyAnimator setListener(Object o){return this;} public void cancel(){}}',
'org/telegram/ui/Components/CubicBezierInterpolator.java': 'package org.telegram.ui.Components; public class CubicBezierInterpolator {public static Object EASE_OUT_QUINT=new Object();}',
'app/nebulagram/ui/NebulaFolderTitleView.java': 'package app.nebulagram.ui; public class NebulaFolderTitleView extends org.telegram.ui.ActionBar.SimpleTextView {public boolean showStatus;public void setTitle(CharSequence text,int cache,boolean status,boolean animated){setText(text);setEmojiCacheType(cache);showStatus=status;}}',
'android/graphics/Paint.java': 'package android.graphics; public class Paint {public Object getFontMetricsInt(){return null;}}',
'org/telegram/ui/ActionBar/SimpleTextView.java': 'package org.telegram.ui.ActionBar; public class SimpleTextView extends android.view.View {public CharSequence text;public int cache;public boolean isAttachedToWindow(){return false;} public android.view.ViewPropertyAnimator animate(){return new android.view.ViewPropertyAnimator();}public void setText(CharSequence t){text=t;}public CharSequence getText(){return text;}public android.graphics.Paint getPaint(){return new android.graphics.Paint();}public void setEmojiCacheType(int type){cache=type;}}',
'org/telegram/messenger/MessagesController.java': 'package org.telegram.messenger; public class MessagesController {public java.util.ArrayList<DialogFilter> filters=new java.util.ArrayList<>();public java.util.ArrayList<DialogFilter> getDialogFilters(){return filters;}public static class DialogFilter {public CharSequence name;public Object entities;public boolean title_noanimate,standard;public boolean isDefault(){return standard;}}}',
'org/telegram/messenger/MessageObject.java': 'package org.telegram.messenger;public class MessageObject {public static CharSequence replaceAnimatedEmoji(CharSequence text,Object entities,Object metrics){return text;}}',
'org/telegram/messenger/Emoji.java': 'package org.telegram.messenger;public class Emoji {public static CharSequence replaceEmoji(CharSequence text,Object metrics,boolean reuse){return text;}}',
'org/telegram/ui/Components/AnimatedEmojiDrawable.java': 'package org.telegram.ui.Components;public class AnimatedEmojiDrawable {public static int CACHE_TYPE_NOANIMATE_FOLDER=1,CACHE_TYPE_MESSAGES=2;}',
'CheckChatLayout.java': '''import android.view.*; import android.widget.*; import android.graphics.*;
import app.nebulagram.ui.*; import org.telegram.messenger.AndroidUtilities; import org.telegram.ui.Components.*;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
public class CheckChatLayout {
static int dp(float v){return AndroidUtilities.dp(v);} static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
static <T extends View> T add(ViewGroup parent,T view,int l,int t,int r,int b){parent.addView(view);view.layout(l,t,r,b);return view;}
public static void main(String[] args){
for(boolean material:new boolean[]{false,true})for(boolean normal:new boolean[]{false,true})for(boolean saved:new boolean[]{false,true}) {
 app.nebulagram.ui.NebulaTheme.enabled=material;
 org.telegram.ui.ActionBar.ActionBar bar=new org.telegram.ui.ActionBar.ActionBar();
 NebulaChatStyle.header(bar,normal,saved);
 check(bar.savedClassic==(material&&normal&&saved),"Saved Messages Material header gate");
 check(bar.floating==(material&&normal&&!saved),"Saved Messages never uses floating header");
}
app.nebulagram.ui.NebulaTheme.enabled=true;
int cases=0;for(boolean bot:new boolean[]{false,true})for(float density:new float[]{1,2.25f,3})for(int width:new int[]{320,360,448,800})for(int height:new int[]{44,92,140}){
AndroidUtilities.density=density; NebulaAppearance.enabled=true; int w=dp(width),d=dp(44),margin=dp(7),h=dp(height);
FrameLayout root=new FrameLayout();root.layout(0,0,w,dp(900));
FrameLayout island=add(root,new FrameLayout(),0,dp(600),w,dp(800));island.setTranslationY(-dp(100));
ChatActivityEnterView host=add(island,new ChatActivityEnterView(),margin,0,w-margin,h);
FrameLayout field=add(host,new FrameLayout(),0,height==92?h-d:0,host.getWidth(),h);
FrameLayout parent=add(field,new FrameLayout(),0,0,host.getWidth()-d,field.getHeight());
EditText editor=add(parent,new EditText(),dp(50),0,parent.getWidth()-dp(50),d);
FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)editor.getLayoutParams();params.leftMargin=dp(50);params.rightMargin=dp(50);
NebulaComposerStyle style=new NebulaComposerStyle();style.createSurfaces(new org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory(),root,null);
NebulaAttachmentButton attach=add(parent,new NebulaAttachmentButton(new android.content.Context(),style),parent.getWidth()-d,parent.getHeight()-d,parent.getWidth(),parent.getHeight());
((FrameLayout.LayoutParams)attach.getLayoutParams()).gravity=Gravity.RIGHT;
attach.setAlpha(0);attach.setScaleX(.5f);attach.setTranslationX(dp(20));
View emoji=add(parent,new View(),0,parent.getHeight()-d,d,parent.getHeight());
View ai=add(field,new View(),0,dp(1),d,dp(1)+d);View expand=add(field,new View(),field.getWidth()-d,dp(1),field.getWidth(),dp(1)+d);
for(View button:new View[]{ai,expand}){((FrameLayout.LayoutParams)button.getLayoutParams()).topMargin=dp(1);button.setVisibility(height==140?View.VISIBLE:View.GONE);button.setAlpha(.5f);button.setScaleX(.8f);button.setScaleY(.8f);button.setTranslationY(dp(12));}
FrameLayout top=add(host,new FrameLayout(),0,0,host.getWidth(),dp(48));
View preview=add(top,new View(),0,0,host.getWidth()-dp(52),dp(48)); View close=add(top,new View(),host.getWidth()-d,0,host.getWidth(),dp(46));
top.setVisibility(height==92?View.VISIBLE:View.GONE);style.setPreview(preview,close);
View botMenu=null;
if(bot){botMenu=add(parent,new View(),dp(8),parent.getHeight()-dp(38),dp(48),parent.getHeight()-dp(6));params.leftMargin=dp(97);style.setBotMenu(botMenu);}
style.prepare(host,editor,host.getWidth(),true);style.layout(emoji,attach,null,ai,expand);
if(bot)check(botMenu.getLeft()>=dp(50)&&botMenu.getRight()<params.leftMargin,"bot menu covers attachment or text");
attach.setAlpha(0);attach.setScaleX(.5f);attach.setTranslationX(dp(20));
check(attach.getLeft()==0&&attach.getAlpha()==1&&attach.getScaleX()==1&&attach.getTranslationX()==0,"attachment lost during typing");
check(params.leftMargin==dp(bot?97:50)+dp(bot?20:14),"input left padding missing");
check(params.rightMargin>=dp(56),"text overlaps emoji after typing");
BlurredBackgroundDrawable bg=new BlurredBackgroundDrawable();bg.padding=margin;bg.setBounds(0,dp(500)-margin,w,dp(500)+h+margin);Rect original=new Rect(bg.getBounds());
bg.nodes.clear();bg.surfaces.clear();bg.alphas.clear();
check(style.draw(new Canvas(),bg,root),"fell back to joined background");
check(new java.util.HashSet<>(bg.nodes).size()==bg.nodes.size(),"multiple surfaces share a mutable RenderNode");
for(int i=0;i<bg.nodes.size();i++){
Rect recorded=bg.surfaces.get(i),current=bg.nodes.get(i).getPaddedBounds();
check(recorded.left==current.left&&recorded.right==current.right&&recorded.top==current.top&&recorded.bottom==current.bottom,"recorded node bounds changed before frame playback");
check(bg.alphas.get(i)==bg.nodes.get(i).getAlpha(),"recorded node alpha changed before frame playback");
}
check(bg.surfaces.size()==(height==140?5:height==92?4:3),"wrong surface count");
Rect left=bg.surfaces.get(0),pill=bg.surfaces.get(1),right=bg.surfaces.get(2);
check(left.width()==d&&left.height()==d&&right.width()==d&&right.height()==d,"record/attachment not circles");
check(left.left==margin&&right.right==w-margin,"parent/host offset mismatch");
check(pill.left-left.right==dp(6)&&right.left-pill.right==dp(6),"island gap mismatch");
check(pill.top==dp(500)&&pill.bottom==dp(500)+h,"IME or multiline/forward height lost");
check(bg.getBounds().left==original.left&&bg.getBounds().bottom==original.bottom,"blur bounds not restored");
if(height==92)check(bg.surfaces.get(3).width()==dp(32),"reply cancel not separate");
if(height==140){
check(ai.getVisibility()==View.VISIBLE&&expand.getVisibility()==View.VISIBLE,"AI/expand hidden");
for(int i=3;i<=4;i++){
Rect circle=bg.surfaces.get(i);check(circle.width()==Math.round(dp(32)*.8f)&&circle.height()==circle.width(),"utility circle lost native scale");
check(bg.alphas.get(i)==128,"utility circle lost native fade");
float centerX=i==3?margin+d/2f:w-margin-d/2f;
check(Math.abs((circle.left+circle.right)/2f-centerX)<=1,"AI/expand not above side buttons");
check(Math.abs((circle.top+circle.bottom)/2f-(dp(500)+dp(1)-dp(8)+dp(12)+d/2f))<=1,"AI/expand translation lost");
}
check(bg.getAlpha()==255,"utility fade leaked into input");
int stableTop=ai.getTop();for(int repeat=0;repeat<20;repeat++)style.layout(emoji,attach,null,ai,expand);
check(ai.getTop()==stableTop&&expand.getTop()==stableTop,"accessory gap accumulates across layouts");
ai.setVisibility(View.GONE);expand.setAlpha(0);bg.surfaces.clear();bg.alphas.clear();
style.draw(new Canvas(),bg,root);check(bg.surfaces.size()==3,"hidden AI/expand left background");
}
int shortHeader=NebulaChatStyle.headerWidth(w,dp(120));int longHeader=NebulaChatStyle.headerWidth(w,dp(1000));
check(shortHeader<=longHeader&&longHeader==w-dp(58)-dp(70),"dynamic header width");
check((w-shortHeader)/2>=dp(64)-1,"header collides with back/avatar");
style.restoreInsets();NebulaAppearance.enabled=false;style.prepare(host,editor,host.getWidth(),true);style.layout(emoji,attach,null,ai,expand);
check(ai.getTop()==dp(1)&&expand.getTop()==dp(1),"accessory position not restored");
check(attach.getAlpha()==0&&attach.getScaleX()==.5f&&attach.getTranslationX()==dp(20),"native attachment style not restored");
check(params.leftMargin==dp(bot?97:50)&&params.rightMargin==dp(50),"input insets not restored");cases++;}
org.telegram.ui.ActionBar.ActionBar bar1=new org.telegram.ui.ActionBar.ActionBar(),bar2=new org.telegram.ui.ActionBar.ActionBar();
NebulaFolderTitleView collapsed1=new NebulaFolderTitleView(),collapsed2=new NebulaFolderTitleView();
org.telegram.messenger.MessagesController controller=new org.telegram.messenger.MessagesController();
for(int i=0;i<8;i++){org.telegram.messenger.MessagesController.DialogFilter filter=new org.telegram.messenger.MessagesController.DialogFilter();filter.standard=i==0;filter.name="Folder "+i+" 🇫🇮 😀";filter.title_noanimate=i%2==0;controller.filters.add(filter);}
NebulaAppearance.enabled=true;NebulaDialogsTitle.bind(bar1,collapsed1);NebulaDialogsTitle.bind(bar2,collapsed2);NebulaDialogsTitle.apply(bar2,controller,6,null);
for(int i:new int[]{0,1,2,3,4,5,6,7,6,4,2,0,-1,15}){NebulaDialogsTitle.apply(bar1,controller,i,null);
CharSequence expected=i>0&&i<8?controller.filters.get(i).name:"NebulaGram";
check(collapsed1.getText()==expected&&bar1.title.getText()==expected,"folder text/spans lost between collapsed and expanded header");
check(collapsed1.showStatus==(i<=0||i>=8),"premium status leaked into folder title");
check(collapsed2.getText()==controller.filters.get(6).name,"folder leaked into another ActionBar");
if(i>0&&i<8)check(collapsed1.cache==(controller.filters.get(i).title_noanimate?1:2),"folder animation preference lost");}
System.out.println("14 folder bindings passed across two headers");
System.out.println(cases+" layout cases passed: 3 densities, 4 widths, empty/forward/multiline, bots; independent recorded drawables; attachment retained; AI/expand fade/scale/translation; native state restored");}}
'''
}
for name, body in stubs.items():
    target = work / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(body, encoding='utf-8')
sources = [str(work / name) for name in stubs]
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sources += [str(overlay / name) for name in ['NebulaComposerStyle.java','NebulaAttachmentButton.java','NebulaChatStyle.java','NebulaDialogsTitle.java']]
out = work / 'classes'
out.mkdir(exist_ok=True)
subprocess.run(['javac','-encoding','UTF-8','-d',str(out),*sources],check=True)
subprocess.run(['java','-cp',str(out),'CheckChatLayout'],check=True)
