"""Render the real formatting widgets on an isolated emulator with offline editor/theme adapters.

The parent models the accessory's native measure/layout contract. This is not a
Telegram account, entity serialization, network delivery or full-app acceptance test.
"""
from pathlib import Path
import json, os, re, shutil, subprocess, sys, time, urllib.request, zipfile

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/formatting-widget-qa'
SDK=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home()/'AppData/Local/Android/Sdk'))
OVERLAY=ROOT/'platform/android/overlay'
UI=OVERLAY/'TMessagesProj/src/main/java/app/nebulagram/ui'
PACKAGE='app.nebulagram.formattingqa'

def run(*args,**kwargs):return subprocess.run(list(map(str,args)),check=True,**kwargs)
def write(name,source):
    p=WORK/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');return p

def build():
    WORK.mkdir(parents=True,exist_ok=True)
    platform=max((p for p in (SDK/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)),key=lambda p:tuple(map(int,p.parent.name[8:].split('.'))))
    tools=max((p for p in (SDK/'build-tools').iterdir() if re.fullmatch(r'\d+(?:\.\d+)*',p.name)),key=lambda p:tuple(map(int,p.name.split('.'))))
    tool=lambda name:tools/(name+('.exe' if os.name=='nt' and name=='aapt2' else '.bat' if os.name=='nt' else ''))
    resources=WORK/'res/drawable';resources.mkdir(parents=True,exist_ok=True)
    for p in (OVERLAY/'TMessagesProj/src/main/res/drawable').glob('nebula_format_*.xml'):shutil.copyfile(p,resources/p.name)
    pin=subprocess.check_output(['git','ls-files','--stage','--','vendor/telegram-android'],cwd=ROOT,text=True).split()[1]
    paths=['drawable/formatting_underline.xml','drawable/formatting_strikethrough.xml','drawable/formatting_code.xml','drawable-xhdpi/msg_link.webp']
    for relative in paths:
        local=ROOT/'build/android-1302-integrated-20261015/TMessagesProj/src/main/res'/relative
        data=local.read_bytes() if local.exists() else urllib.request.urlopen(f'https://raw.githubusercontent.com/DrKLO/Telegram/{pin}/TMessagesProj/src/main/res/{relative}',timeout=30).read()
        target=WORK/'res'/relative;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
    write('org/telegram/messenger/ApplicationLoader.java','package org.telegram.messenger; public class ApplicationLoader {public static android.content.Context applicationContext;}')
    write('org/telegram/messenger/AndroidUtilities.java','package org.telegram.messenger; public class AndroidUtilities {public static int dp(float n){return (int)Math.ceil(n*ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density);}}')
    write('app/nebulagram/ui/NebulaText.java','package app.nebulagram.ui; public class NebulaText {public static String text(String ru,String en){return ru;}}')
    write('app/nebulagram/ui/NebulaTheme.java','''package app.nebulagram.ui; public class NebulaTheme {
 public static boolean dark;public static NebulaTheme of(android.content.Context c){return new NebulaTheme();}
 public int surfaceContainer(){return dark?0xff242424:0xffeee8dc;}public int onSurface(){return dark?0xffeeeeee:0xff202020;}
 public static int stateLayer(int c,float f){return (c&0xffffff)|Math.round(f*255)<<24;}}
''')
    write('org/telegram/ui/Components/EditTextCaption.java','''package org.telegram.ui.Components;
public class EditTextCaption extends android.widget.EditText {
 public int calls;public EditTextCaption(android.content.Context c){super(c);}public void setSelectionOverride(int a,int b){}
 public void makeSelectedBold(){calls++;getText().setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),getSelectionStart(),getSelectionEnd(),33);}
 public void makeSelectedItalic(){calls++;}public void makeSelectedMono(){calls++;}public void makeSelectedQuote(){calls++;}
 public void makeSelectedSpoiler(){calls++;}public void makeSelectedUrl(){calls++;}public void makeSelectedRegular(){calls++;}
 public void makeSelectedUnderline(){calls++;}public void makeSelectedStrike(){calls++;}public void makeSelectedCode(){calls++;}
}
''')
    write('app/nebulagram/formattingqa/MainActivity.java',r'''package app.nebulagram.formattingqa;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import android.graphics.*;
import org.telegram.messenger.*;import org.telegram.ui.Components.*;import app.nebulagram.ui.*;
public class MainActivity extends Activity {
 FrameLayout root;Composer composer;EditTextCaption editor;NebulaFormattingToolbar toolbar;boolean keyboard;int sends;
 int dp(float n){return AndroidUtilities.dp(n);}
 public void onCreate(Bundle state){super.onCreate(state);ApplicationLoader.applicationContext=getApplicationContext();
  NebulaTheme.dark=getIntent().getBooleanExtra("dark",true);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  root=new FrameLayout(this);root.setBackgroundColor(NebulaTheme.dark?0xff101418:0xfff6f4f0);setContentView(root);
  TextView heading=new TextView(this);heading.setText("Панель форматирования · QA\nРеальные виджеты, адаптер редактора\nБез аккаунта и сетевых запросов");heading.setTextSize(17);heading.setTextColor(new NebulaTheme().onSurface());heading.setPadding(dp(16),dp(16),dp(16),0);root.addView(heading,new FrameLayout.LayoutParams(-1,-2));
  composer=new Composer();editor=new EditTextCaption(this);editor.setTextColor(new NebulaTheme().onSurface());editor.setHintTextColor(0xff888888);editor.setTextSize(18);editor.setHint("Сообщение");editor.setPadding(dp(16),dp(10),dp(16),dp(10));editor.setMinHeight(dp(44));editor.setMaxLines(5);editor.setInputType(0x20001);editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
  editor.setOnEditorActionListener((v,a,e)->{sends++;return true;});
  android.graphics.drawable.GradientDrawable field=new android.graphics.drawable.GradientDrawable();field.setColor(NebulaTheme.dark?0xff242424:0xffffffff);field.setCornerRadius(dp(22));editor.setBackground(field);
  FrameLayout.LayoutParams input=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);input.leftMargin=input.rightMargin=dp(8);composer.addView(editor,input);
  toolbar=new NebulaFormattingToolbar(this,editor,composer::requestLayout);FrameLayout.LayoutParams accessory=new FrameLayout.LayoutParams(-1,0,Gravity.TOP);accessory.leftMargin=accessory.rightMargin=dp(8);composer.addView(toolbar,accessory);
  FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);panel.bottomMargin=dp(8);root.addView(composer,panel);
  NebulaFormattingToolbar.setPreferenceEnabled(!getIntent().getBooleanExtra("off",false));
  root.setOnApplyWindowInsetsListener((v,insets)->{boolean next=insets.isVisible(WindowInsets.Type.ime());int bottom=Math.max(insets.getInsets(WindowInsets.Type.ime()).bottom,insets.getInsets(WindowInsets.Type.systemBars()).bottom);root.setPadding(0,insets.getInsets(WindowInsets.Type.systemBars()).top,0,bottom);if(keyboard!=next){keyboard=next;composer.requestLayout();}return insets;});
  root.post(root::requestApplyInsets);
  if(getIntent().getBooleanExtra("keyboard",true))root.postDelayed(()->{editor.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(editor,0);},600);
  root.postDelayed(new Runnable(){public void run(){report();java.io.File trigger=new java.io.File(getFilesDir(),"verify.trigger");if(trigger.exists()){trigger.delete();verify();}root.postDelayed(this,300);}},900);
 }
 class Composer extends FrameLayout {
  int height;Composer(){super(MainActivity.this);}
  protected void onMeasure(int w,int h){int next=toolbar==null?0:toolbar.prepare(keyboard);if(next!=height){setPadding(0,next,0,0);setClipToPadding(next==0);height=next;}
   super.onMeasure(w,h);if(height>0)toolbar.measure(MeasureSpec.makeMeasureSpec(Math.max(0,getMeasuredWidth()-dp(16)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}
  protected void onLayout(boolean c,int l,int t,int r,int b){super.onLayout(c,l,t,r,b);if(height>0)toolbar.layout(dp(8),0,getMeasuredWidth()-dp(8),height);}
 }
 void report(){try{org.json.JSONObject j=new org.json.JSONObject();j.put("case",getIntent().getStringExtra("case"));j.put("keyboard",keyboard);j.put("accessory",composer.height);j.put("editorHeight",editor.getHeight());j.put("scale",getResources().getConfiguration().fontScale);j.put("rectEditor",rect(editor));
  View button=find(toolbar,"Новая строка");j.put("returnVisible",button!=null&&button.isShown());j.put("rectReturn",button==null?new org.json.JSONArray():rect(button));save("layout.json",j);}catch(Exception e){throw new RuntimeException(e);}}
 org.json.JSONArray rect(View v)throws org.json.JSONException{Rect r=new Rect();v.getGlobalVisibleRect(r);return new org.json.JSONArray(new int[]{r.left,r.top,r.right,r.bottom});}
 void verify(){try{
  if(!keyboard||toolbar.getVisibility()!=View.VISIBLE)throw new AssertionError("No real keyboard/accessory");
  String text="Hello world";editor.setText(text);editor.setSelection(text.length());find(toolbar,"Жирный").performClick();
  if(editor.calls!=1||editor.getSelectionStart()!=6||editor.getSelectionEnd()!=11||!text.contentEquals(editor.getText()))throw new AssertionError("Word callback lost selection/text");
  editor.setSelection(editor.length());find(toolbar,"Новая строка").performClick();if(!((text+"\n").contentEquals(editor.getText()))||sends!=0)throw new AssertionError("Newline dispatched Send");
  editor.setText("");toolbar.scrollTo(((ViewGroup)toolbar.getChildAt(0)).getWidth(),0);
  root.postDelayed(()->{try{View code=find(toolbar,"Блок кода");Rect visible=new Rect();if(!code.getGlobalVisibleRect(visible)||visible.width()==0)throw new AssertionError("Overflow actions inaccessible");
   org.json.JSONObject j=new org.json.JSONObject();j.put("case",getIntent().getStringExtra("case"));j.put("wordCallback",true);j.put("returnDidNotSend",sends==0);j.put("overflowVisible",true);j.put("emptyDraftAccessory",composer.height>0);save("callbacks.json",j);
  }catch(Exception e){throw new RuntimeException(e);}},500);
 }catch(Exception e){throw new RuntimeException(e);}}
 void save(String name,org.json.JSONObject j)throws Exception{try(java.io.FileOutputStream out=openFileOutput(name,0)){out.write(j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
 View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View match=find(g.getChildAt(i),label);if(match!=null)return match;}}return null;}
}
''')
    manifest=WORK/'AndroidManifest.xml';manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{PACKAGE}"><uses-sdk android:minSdkVersion="30" android:targetSdkVersion="35"/><application android:debuggable="true" android:label="Formatting widget QA" android:theme="@android:style/Theme.Material.Light.NoActionBar"><activity android:name=".MainActivity" android:exported="true"/></application></manifest>',encoding='utf-8')
    run(tool('aapt2'),'compile','--dir',WORK/'res','-o',WORK/'resources.zip')
    apk=WORK/'widgets-unsigned.apk';run(tool('aapt2'),'link','-I',platform,'--manifest',manifest,'--java',WORK/'src','--custom-package','org.telegram.messenger','-o',apk,WORK/'resources.zip')
    classes=WORK/'classes';classes.mkdir(exist_ok=True)
    run('javac','-encoding','UTF-8','-source','17','-target','17','-cp',platform,'-d',classes,*list((WORK/'src').rglob('*.java')),UI/'NebulaFormattingToolbar.java',UI/'NebulaFormattingSelection.java')
    jar=WORK/'widgets.jar';run('jar','cf',jar,'-C',classes,'.');run(tool('d8'),'--lib',platform,'--min-api','30','--output',WORK,jar)
    with zipfile.ZipFile(apk,'a') as archive:archive.write(WORK/'classes.dex','classes.dex')
    key=WORK/'qa.p12'
    if not key.exists():run('keytool','-genkeypair','-keystore',key,'-alias','qa','-keyalg','RSA','-keysize','2048','-validity','7','-storepass','widgetqa','-keypass','widgetqa','-dname','CN=Temporary Formatting Widget QA')
    run(tool('apksigner'),'sign','--ks',key,'--ks-key-alias','qa','--ks-pass','pass:widgetqa','--out',WORK/'widgets.apk',apk)
    print('Compiled actual formatting widgets; editor/theme adapters and parent contract are explicit, no network permission.')

def render():
    if os.environ.get('CI')!='true':raise SystemExit('Render only on the isolated CI emulator, never change a user device display.')
    adb=SDK/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
    devices=subprocess.check_output([str(adb),'devices'],text=True)
    serials=[x.split()[0] for x in devices.splitlines()[1:] if x.strip().endswith('\tdevice')]
    assert len(serials)==1 and serials[0].startswith('emulator-'),devices
    command=lambda *args,**kw:run(adb,'-s',serials[0],*args,**kw)
    read=lambda name:json.loads(subprocess.check_output([str(adb),'-s',serials[0],'shell','run-as',PACKAGE,'cat','files/'+name],stderr=subprocess.DEVNULL))
    command('install','-r',WORK/'widgets.apk');output=WORK/'screenshots';output.mkdir(exist_ok=True)
    try:
        for name,width,dark,scale,keyboard,off in [('dark',1080,True,1,True,False),('light',1080,False,1,True,False),('narrow-large',840,True,1.3,True,False),('closed',1080,True,1,False,False),('disabled',1080,True,1,True,True)]:
            command('shell','am','force-stop',PACKAGE);command('shell','wm','size',str(width)+'x1920');command('shell','wm','density','420');command('shell','settings','put','system','font_scale',str(scale));time.sleep(2)
            command('shell','input','keyevent','KEYCODE_WAKEUP');command('shell','wm','dismiss-keyguard')
            command('shell','am','start','-W','-n',PACKAGE+'/.MainActivity','--es','case',name,'--ez','dark',str(dark).lower(),'--ez','keyboard',str(keyboard).lower(),'--ez','off',str(off).lower())
            report=None;tapped=False
            for attempt in range(20):
                time.sleep(1)
                try:report=read('layout.json')
                except (ValueError,subprocess.CalledProcessError):continue
                if report.get('case')==name and keyboard and not report['keyboard'] and not tapped:
                    # Some fresh emulator IMEs need a tap after the window gains focus.
                    a=report['rectEditor'];command('shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2));tapped=True
                if report.get('case')==name and report['keyboard']==keyboard and abs(report['scale']-scale)<.01:break
            with (output/(name+'.png')).open('wb') as target:command('exec-out','screencap','-p',stdout=target)
            (output/(name+'.json')).write_text(json.dumps(report,indent=2)+'\n')
            assert report and report['case']==name and report['keyboard']==keyboard and abs(report['scale']-scale)<.01,report
            visible=keyboard and not off;assert report['returnVisible']==visible,report
            assert report['editorHeight']>0 and (report['accessory']>0)==visible,report
            if visible:
                a,b=report['rectReturn'],report['rectEditor'];assert a[2]>a[0] and a[3]>a[1] and a[3]<=b[1],report
            if visible:
                command('shell','run-as',PACKAGE,'touch','files/verify.trigger');callbacks=None
                for attempt in range(15):
                    time.sleep(1)
                    try:callbacks=read('callbacks.json')
                    except (ValueError,subprocess.CalledProcessError):continue
                    if callbacks.get('case')==name:break
                assert callbacks and callbacks['case']==name and all(callbacks[k] for k in ['wordCallback','returnDidNotSend','overflowVisible','emptyDraftAccessory']),callbacks
                (output/(name+'-callbacks.json')).write_text(json.dumps(callbacks,indent=2)+'\n')
        crash=subprocess.check_output([str(adb),'-s',serials[0],'logcat','-d','-b','crash'],text=True)
        assert PACKAGE not in crash,crash[-2000:]
    finally:
        crash=subprocess.check_output([str(adb),'-s',serials[0],'logcat','-d','-b','crash'],text=True)
        (output/'crash.log').write_text(crash)
        command('shell','wm','size','reset');command('shell','wm','density','reset');command('shell','settings','put','system','font_scale','1')
    print('Real widget rendering/IME, word callback, direct newline and horizontal overflow verified. Native Telegram parent/serialization/network acceptance remains separate.')

if __name__=='__main__':build() if sys.argv[1]=='build' else render()
