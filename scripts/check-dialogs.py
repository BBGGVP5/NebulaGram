"""Run the real dialog builder/camera callbacks against a minimal UI host.

The host records presentation and palette configuration; the real Telegram
window/scroll/keyboard implementation is compiled by the Android APK job.
"""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

sources = {
    'android/content/DialogInterface.java': '''package android.content;
public interface DialogInterface {
 int BUTTON_POSITIVE=-1,BUTTON_NEGATIVE=-2,BUTTON_NEUTRAL=-3;
 interface OnClickListener {void onClick(DialogInterface dialog,int which);}
 interface OnDismissListener {void onDismiss(DialogInterface dialog);}
}''',
    'android/content/SharedPreferences.java': '''package android.content;
public class SharedPreferences {
 java.util.Map<String,Object> data=new java.util.HashMap<>();
 public int getInt(String key,int fallback){return (int)data.getOrDefault(key,fallback);}
 public boolean getBoolean(String key,boolean fallback){return (boolean)data.getOrDefault(key,fallback);}
 public Editor edit(){return new Editor();}
 public class Editor {
  public Editor putInt(String key,int value){data.put(key,value);return this;}
  public Editor putBoolean(String key,boolean value){data.put(key,value);return this;}
  public void apply(){}
 }
}''',
    'android/content/Context.java': '''package android.content;
public class Context {SharedPreferences preferences=new SharedPreferences();
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}}
''',
    'android/content/res/ColorStateList.java': '''package android.content.res;
public class ColorStateList {public int color;public static ColorStateList valueOf(int c){ColorStateList v=new ColorStateList();v.color=c;return v;}}''',
    'android/graphics/Color.java': '''package android.graphics;public class Color {public static int alpha(int c){return c>>>24;}}''',
    'android/graphics/Paint.java': '''package android.graphics;public class Paint {
 public static final int ANTI_ALIAS_FLAG=1;public enum Style{STROKE,FILL}
 public Paint(int flags){}public void setColor(int c){}public void setStyle(Style s){}public void setStrokeWidth(float s){}
}''',
    'android/graphics/Canvas.java': '''package android.graphics;public class Canvas {public void drawCircle(float x,float y,float r,Paint p){}}''',
    'android/graphics/drawable/GradientDrawable.java': '''package android.graphics.drawable;
public class GradientDrawable {public int color,stroke;public float radius;
 public void setColor(int c){color=c;}public void setCornerRadius(float r){radius=r;}public void setStroke(int w,int c){stroke=c;}}
''',
    'android/graphics/drawable/RippleDrawable.java': '''package android.graphics.drawable;
import android.content.res.ColorStateList;public class RippleDrawable {public ColorStateList ripple;public GradientDrawable content;
 public RippleDrawable(ColorStateList r,GradientDrawable c,GradientDrawable m){ripple=r;content=c;}}
''',
    'android/view/Gravity.java': '''package android.view;public class Gravity {public static final int CENTER=17,CENTER_VERTICAL=16,START=8388611;}''',
    'android/view/Window.java': '''package android.view;public class Window {public int gravity;public void setGravity(int v){gravity=v;}}''',
    'android/view/accessibility/AccessibilityNodeInfo.java': '''package android.view.accessibility;
public class AccessibilityNodeInfo {public boolean checked,checkable;public String className;
 public void setClassName(String s){className=s;}public void setCheckable(boolean b){checkable=b;}public void setChecked(boolean b){checked=b;}}
''',
    'android/view/View.java': '''package android.view;
import android.content.Context;import android.graphics.Canvas;import android.view.accessibility.AccessibilityNodeInfo;
public class View {
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4,IMPORTANT_FOR_ACCESSIBILITY_NO=2,TEXT_ALIGNMENT_VIEW_START=5;
 public Object background;public int minimumHeight;public int[] padding;public CharSequence description;public boolean selected;
 OnClickListener click;public interface OnClickListener {void onClick(View v);}
 public View(Context c){}public void setBackground(Object d){background=d;}public void setClipToOutline(boolean b){}
 public void setImportantForAccessibility(int i){}public void setMinimumHeight(int h){minimumHeight=h;}
 public void setSelected(boolean b){selected=b;}public void setFocusable(boolean b){}
 public void setPadding(int a,int b,int c,int d){padding=new int[]{a,b,c,d};}
 public void setPaddingRelative(int a,int b,int c,int d){setPadding(a,b,c,d);}
 public void setContentDescription(CharSequence s){description=s;}
 public void setOnClickListener(OnClickListener listener){click=listener;}public void performClick(){click.onClick(this);}
 public int getWidth(){return 24;}public int getHeight(){return 24;}
 protected void onDraw(Canvas c){}public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){}
}''',
    'android/widget/LinearLayout.java': '''package android.widget;
import android.content.Context;import android.view.View;
public class LinearLayout extends View {
 public static final int VERTICAL=1;public java.util.List<View> children=new java.util.ArrayList<>();
 public LinearLayout(Context c){super(c);}public void setOrientation(int v){}public void setGravity(int v){}
 public void addView(View v){children.add(v);}public void addView(View v,LayoutParams p){children.add(v);}
 public static class LayoutParams {public LayoutParams(int w,int h){}public LayoutParams(int w,int h,float weight){}public void setMarginStart(int v){}}
}''',
    'android/widget/TextView.java': '''package android.widget;import android.content.Context;
public class TextView extends android.view.View {
 public CharSequence text;public int color;public TextView(Context c){super(c);}
 public void setText(CharSequence t){text=t;}public void setTextSize(int s){}public void setTextColor(int c){color=c;}
 public void setGravity(int g){}public void setTextAlignment(int a){}public void setLineSpacing(float a,float b){}
}''',
    'android/widget/EditText.java': '''package android.widget;import android.content.Context;
public class EditText extends TextView {public int hintColor;public EditText(Context c){super(c);}
 public void setMinHeight(int h){minimumHeight=h;}public void setHintTextColor(int c){hintColor=c;}}
''',
    'androidx/core/graphics/ColorUtils.java': '''package androidx.core.graphics;
public class ColorUtils {
 public static int setAlphaComponent(int c,int a){return (c&0xffffff)|(a<<24);}
 public static int compositeColors(int f,int b){int a=f>>>24;int out=0xff000000;
  for(int shift:new int[]{0,8,16})out|=((((f>>>shift)&255)*a+((b>>>shift)&255)*(255-a))/255)<<shift;
  return out;
 }
}''',
    'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;
public class AndroidUtilities {
 public static java.util.List<Runnable> queued=new java.util.ArrayList<>();
 public static int dp(float v){return (int)Math.ceil(v);}public static void runOnUIThread(Runnable r){queued.add(r);}
 public static void drain(){java.util.List<Runnable> work=new java.util.ArrayList<>(queued);queued.clear();work.forEach(Runnable::run);}
}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
    'org/telegram/ui/ActionBar/Theme.java': '''package org.telegram.ui.ActionBar;
public class Theme {
 public static final int key_dialogBackgroundGray=0,key_dialogTextBlack=1,key_dialogTextGray=2,key_dialogTextBlue=3;
 public interface ResourcesProvider {int color(int key);}
 public static int[] colors={0xffededed,0xff121212,0xff696969,0xff269a94};
 public static int getColor(int key,ResourcesProvider provider){return provider==null?colors[key]:provider.color(key);}
}''',
    'org/telegram/ui/ActionBar/AlertDialog.java': '''package org.telegram.ui.ActionBar;
import android.content.*;import android.view.*;
public class AlertDialog implements DialogInterface {
 public static AlertDialog shown;public boolean dim,outside,twoRows,visible;public float alpha;
 public Theme.ResourcesProvider provider;public CharSequence title,message;public View content;Window window=new Window();
 public java.util.Map<Integer,OnButtonClickListener> buttons=new java.util.HashMap<>();
 public OnDismissListener onDismiss;public interface OnButtonClickListener {void onClick(AlertDialog dialog,int which);}
 public void show(){shown=this;visible=true;}public void dismiss(){if(!visible)return;visible=false;if(onDismiss!=null)onDismiss.onDismiss(this);}
 public Window getWindow(){return window;}public void setCanceledOnTouchOutside(boolean b){outside=b;}
 public void setOnDismissListener(OnDismissListener l){onDismiss=l;}
 public void press(int which){buttons.get(which).onClick(this,which);dismiss();}
 public static class Builder {
  AlertDialog dialog=new AlertDialog();public Builder(Context c,Theme.ResourcesProvider p){dialog.provider=p;}
  public Builder setDimEnabled(boolean b){dialog.dim=b;return this;}public Builder setDimAlpha(float a){dialog.alpha=a;return this;}
  public Builder twoRowsButtonsWhenNeeded(){dialog.twoRows=true;return this;}
  public Builder setTitle(CharSequence t){dialog.title=t;return this;}public Builder setMessage(CharSequence m){dialog.message=m;return this;}
  public Builder setPositiveButton(CharSequence t,OnButtonClickListener l){dialog.buttons.put(-1,l);return this;}
  public Builder setNegativeButton(CharSequence t,OnButtonClickListener l){dialog.buttons.put(-2,l);return this;}
  public Builder setNeutralButton(CharSequence t,OnButtonClickListener l){dialog.buttons.put(-3,l);return this;}
  public Builder setView(View v){dialog.content=v;return this;}public Builder setCustomViewOffset(int v){return this;}
  public AlertDialog create(){return dialog;}
 }
}''',
    'app/nebulagram/ui/NebulaText.java': '''package app.nebulagram.ui;
public class NebulaText {public static String text(String ru,String en){return en;}}
''',
    'DialogCheck.java': '''import app.nebulagram.ui.*;import android.content.*;import android.widget.*;import android.view.*;
import android.graphics.drawable.*;import android.view.accessibility.*;import org.telegram.ui.ActionBar.*;
import org.telegram.messenger.*;
public class DialogCheck {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static LinearLayout group(AlertDialog d){return (LinearLayout)((LinearLayout)d.content).children.get(0);}
 static void popup(AlertDialog d,Theme.ResourcesProvider p){
  check(d.dim&&d.alpha>=.5f&&d.alpha<=.7f,"dim surrounding screen");
  check(d.getWindow().gravity==Gravity.CENTER,"centered popup");
  check(d.outside&&d.twoRows,"outside dismissal and long action labels");check(d.provider==p,"native title/footer use supplied Telegram palette");
 }
 public static void main(String[] args){
  Context context=ApplicationLoader.applicationContext;int[] selected={-1};int[] clicks={0,0,0};
  for(Theme.ResourcesProvider palette:new Theme.ResourcesProvider[]{null,key->new int[]{0xff222229,0xfff5f1ee,0xffbab4be,0xffbbaaff}[key]}){
   AlertDialog d=new NebulaDialog.Builder(context,palette).setTitle("Camera").setSelectedIndex(1)
    .setItems(new CharSequence[]{"Front",null,"Rear"},(dialog,index)->selected[0]=index)
    .setDescriptions(new CharSequence[]{"Front lens",null,"Rear lens"}).setNegativeButton("Cancel",null).show();popup(d,palette);
   check(group(d).children.size()==2,"null choices skipped without changing indices");
   LinearLayout row=(LinearLayout)group(d).children.get(0);TextView label=(TextView)((LinearLayout)row.children.get(0)).children.get(0);
   check(label.color==Theme.getColor(Theme.key_dialogTextBlack,palette),"choice inherits Telegram text color");
   check(((GradientDrawable)((RippleDrawable)row.background).content).color==Theme.getColor(Theme.key_dialogBackgroundGray,palette),"choice inherits Telegram surface");
   row=(LinearLayout)group(d).children.get(1);row.performClick();check(selected[0]==2&&!d.visible,"selection returns original index and dismisses");
   d=new NebulaDialog.Builder(context,palette).setSelectedIndex(0).setItems(new String[]{"Selected"},null).create();
   row=(LinearLayout)group(d).children.get(0);AccessibilityNodeInfo info=new AccessibilityNodeInfo();row.onInitializeAccessibilityNodeInfo(info);
   check(info.checked&&info.checkable&&row.minimumHeight>=48,"accessible selected option");
   label=(TextView)((LinearLayout)row.children.get(0)).children.get(0);check(label.color==Theme.getColor(Theme.key_dialogTextBlue,palette),"selected accent from Telegram");
   EditText editor=new EditText(context);d=new NebulaDialog.Builder(context,palette).setView(editor).create();
   check(editor.color==Theme.getColor(Theme.key_dialogTextBlack,palette)&&editor.hintColor==Theme.getColor(Theme.key_dialogTextGray,palette),"editor inherits Telegram palette");
  }
  for(int which:new int[]{-1,-2,-3}){
   AlertDialog d=new NebulaDialog.Builder(context).setPositiveButton("Clear",(dialog,w)->{check(w==-1,"positive ID");clicks[0]++;})
    .setNegativeButton("Cancel",(dialog,w)->{check(w==-2,"negative ID");clicks[1]++;})
    .setNeutralButton("Disable",(dialog,w)->{check(w==-3,"neutral ID");clicks[2]++;}).show();
   d.press(which);check(!d.visible,"native action dismisses");
  }
  check(java.util.Arrays.equals(clicks,new int[]{1,1,1}),"each action invokes only its configured callback");
  AlertDialog confirmation=new NebulaDialog.Builder(context).setTitle("Clear retained messages?").setMessage("Only local copies")
   .setPositiveButton("Clear",null).setNegativeButton("Cancel",null).show();
  popup(confirmation,null);check(confirmation.content==null,"confirmation uses native content without duplicate scrolling or padding");confirmation.press(-2);
  for(int action:new int[]{0,1,2,3,4}){
   int[] started={0},cancelled={0};NebulaRoundCamera.askForRecording(context,null,()->started[0]++,()->cancelled[0]++);
   AlertDialog d=AlertDialog.shown;popup(d,null);
   if(action<2)group(d).children.get(action).performClick();else if(action==2)d.press(-2);else d.dismiss();
   AndroidUtilities.drain();check(started[0]==(action<2?1:0)&&cancelled[0]==(action<2?0:1),"camera starts only on selection; negative/back/outside cancel once");
   if(action<2)check(NebulaRoundCamera.frontface(false)==(action==0),"selected camera remembered");
  }
  int[] cancelled={0};NebulaRoundCamera.askForRecording(null,null,()->{throw new AssertionError("missing context starts recording");},()->cancelled[0]++);
  check(cancelled[0]==1,"missing context cancels once");
  System.out.println("Actual dialog callbacks, dim/center configuration, Telegram palettes and camera cancellation passed");
 }
}''',
}

with tempfile.TemporaryDirectory(prefix='nebula-dialog-check-') as temp:
    work = Path(temp)
    for name, source in sources.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    files = [str(path) for path in work.rglob('*.java')]
    files += [str(ui / name) for name in ('NebulaDialog.java', 'NebulaRoundCamera.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, *files], check=True)
    subprocess.run(['java', '-cp', temp, 'DialogCheck'], check=True)

dialog = (ui / 'NebulaDialog.java').read_text(encoding='utf-8')
assert 'BottomSheet' not in dialog and 'ScrollView' not in dialog and 'NebulaTheme' not in dialog
for name, provider in [('NebulaAutoTranslate.java', 'host'), ('NebulaMessageFilter.java', 'host'),
                       ('NebulaPrivacyFragment.java', 'fragment')]:
    assert f'{provider}.getResourceProvider()' in (ui / name).read_text(encoding='utf-8'), name
print('Native scrolling/footer ownership and chat palette forwarding retained')
