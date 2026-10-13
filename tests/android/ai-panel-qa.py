"""Render production Aa widgets without Telegram/network credentials or audio uploads.

This is a UI geometry fixture, not a full Telegram/camera/ASR acceptance test.
"""
from pathlib import Path
import ast, json, os, re, subprocess, sys, time, zipfile

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/ai-panel-widget-qa'
SDK=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home()/'AppData/Local/Android/Sdk'))
UI=ROOT/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
def run(*args,**kw):return subprocess.run(list(map(str,args)),check=True,**kw)
def write(name,source):
 p=WORK/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8');return p
def build():
 WORK.mkdir(parents=True,exist_ok=True)
 stubs=next(ast.literal_eval(n.value) for n in ast.parse((ROOT/'scripts/check-draft-voice-api.py').read_text(encoding='utf-8')).body if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='stubs' for t in n.targets))
 stubs['org/telegram/messenger/AndroidUtilities.java']='''package org.telegram.messenger; public class AndroidUtilities {
  private static final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
  public static void runOnUIThread(Runnable r){handler.post(r);} public static void runOnUIThread(Runnable r,long d){handler.postDelayed(r,d);}
  public static void cancelRunOnUIThread(Runnable r){handler.removeCallbacks(r);}
  public static int dp(float n){return (int)Math.ceil(n*ApplicationLoader.applicationContext.getResources().getDisplayMetrics().density);}
  public static android.graphics.Typeface bold(){return android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD);}
  public static void hideKeyboard(android.view.View v){((android.view.inputmethod.InputMethodManager)v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);}
 }'''
 stubs['app/nebulagram/ui/NebulaText.java']='package app.nebulagram.ui; public class NebulaText {public static String text(String ru,String en){return ru;}}'
 stubs['app/nebulagram/ui/NebulaTheme.java']='''package app.nebulagram.ui; public class NebulaTheme {
  public static boolean dark; public static NebulaTheme of(android.content.Context c){return new NebulaTheme();}
  public boolean isDark(){return dark;}public int modalSurface(){return dark?0xff202426:0xfff2f3f5;}
  public int surfaceContainer(){return dark?0xff15191b:0xffffffff;}public int primary(){return dark?0xff64cbd0:0xff006c72;}
  public int onPrimary(){return dark?0xff00383b:0xffffffff;}public int onSurface(){return dark?0xffeef3f4:0xff161c20;}
  public int onSurfaceVariant(){return dark?0xffaab4b8:0xff485158;}
 }'''
 stubs['app/nebulagram/ui/NebulaFormUi.java']='''package app.nebulagram.ui;public class NebulaFormUi {
  public static android.widget.EditText field(android.content.Context c,String hint,int lines,int maximum){android.widget.EditText v=new android.widget.EditText(c);v.setTextColor(NebulaTheme.of(c).onSurface());v.setHintTextColor(NebulaTheme.of(c).onSurfaceVariant());v.setHint(hint);v.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);v.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(maximum)});return v;}
 }'''
 stubs['app/nebulagram/ui/NebulaAudioPreferences.java']='''package app.nebulagram.ui;public class NebulaAudioPreferences {
  public static android.content.SharedPreferences prefs(){return org.telegram.messenger.ApplicationLoader.applicationContext.getSharedPreferences("qa",0);}
  public static String serviceId(boolean s){return "device-recognition";}public static String localLocale(){return "ru-RU";}
  public static String model(boolean s,NebulaAiServices.Service c){return "";}public static String title(boolean s){return "Android · на устройстве";}
  public static boolean localTranscription(){return true;}public static NebulaAudioClient.Configuration capture(boolean s){throw new IllegalStateException("No network service in widget QA");}
 }'''
 stubs['app/nebulagram/ui/NebulaLocalTranscription.java']='''package app.nebulagram.ui; public class NebulaLocalTranscription {
  public interface Progress {void text(String s);void status(String s);}public NebulaLocalTranscription(boolean a,String l){}public void cancel(){}
  public String transcribe(java.io.File f,Progress p){throw new IllegalStateException("No recognition request in widget QA");}public static String errorText(Exception e){return "QA";}
 }'''
 stubs['org/telegram/ui/ActionBar/Theme.java']='''package org.telegram.ui.ActionBar;public class Theme {
  public interface ResourcesProvider{}public static int key_dialogBackground=0,key_dialogTextBlack=1,key_dialogTextGray=2,key_dialogTextBlue=3;
  public static int getColor(int key,ResourcesProvider r){app.nebulagram.ui.NebulaTheme t=app.nebulagram.ui.NebulaTheme.of(null);return key==0?t.modalSurface():key==1?t.onSurface():key==2?t.onSurfaceVariant():t.primary();}
 }'''
 for name,source in stubs.items():write(name,source)
 write('app/nebulagram/uiqa/MainActivity.java',r'''package app.nebulagram.uiqa;
import android.app.*;import android.os.*;import android.view.*;import android.widget.*;import app.nebulagram.ui.*;import org.telegram.messenger.*;
public class MainActivity extends Activity {
 private NebulaDraftVoiceTranscription preview;private FrameLayout root;
 private int dp(int n){return AndroidUtilities.dp(n);}
 public void onCreate(Bundle state){super.onCreate(state);ApplicationLoader.applicationContext=getApplicationContext();
  NebulaTheme.dark=getIntent().getBooleanExtra("dark",true);getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
  root=new FrameLayout(this);root.setBackgroundColor(NebulaTheme.dark?0xff090d0f:0xffe4eded);
  FrameLayout decor=new FrameLayout(this);decor.setBackgroundColor(NebulaTheme.dark?0xff090d0f:0xffe4eded);decor.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(decor);
  // The real Telegram recorder is moved by its keyboard layout. Model that host contract here.
  if(Build.VERSION.SDK_INT>=30)decor.setOnApplyWindowInsetsListener((v,insets)->{
   int top=insets.getInsets(WindowInsets.Type.systemBars()).top,bottom=Math.max(insets.getInsets(WindowInsets.Type.systemBars()).bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);
   FrameLayout.LayoutParams bounds=(FrameLayout.LayoutParams)root.getLayoutParams();int height=Math.max(0,decor.getHeight()-top-bottom);
   if(bounds.height!=height||bounds.topMargin!=top){bounds.height=height;bounds.topMargin=top;root.setLayoutParams(bounds);}return insets;
  });
  decor.post(decor::requestApplyInsets);
  if(getIntent().getBooleanExtra("camera",false)){showCamera();return;}
  TextView title=new TextView(this);title.setText("Предпросмотр записи · QA\nБез запросов к сервисам");title.setTextColor(NebulaTheme.of(this).onSurface());title.setTextSize(18);title.setPadding(dp(16),dp(18),dp(16),0);root.addView(title,new FrameLayout.LayoutParams(-1,-2));
  LinearLayout recorder=new LinearLayout(this);recorder.setGravity(Gravity.CENTER_VERTICAL);recorder.setPadding(dp(12),0,dp(12),0);
  TextView waveform=NebulaAiPanelUi.button(this,"▶  ▂ ▅ ▃ ▆ ▁ ▄  00:14",false,v->{});recorder.addView(waveform,new LinearLayout.LayoutParams(0,dp(54),1));
  TextView send=NebulaAiPanelUi.button(this,"↑",true,v->{});recorder.addView(send,new LinearLayout.LayoutParams(dp(54),dp(54)));
  FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM);bar.bottomMargin=dp(24);root.addView(recorder,bar);
  TextView aa=NebulaAiPanelUi.button(this,"Aa   ·   ❚❚",false,v->{if(preview!=null)preview.cancel();});
  FrameLayout.LayoutParams control=new FrameLayout.LayoutParams(dp(112),dp(44),Gravity.BOTTOM|Gravity.RIGHT);control.bottomMargin=dp(92);control.rightMargin=dp(12);root.addView(aa,control);
  root.post(()->{
   NebulaDraftVoicePolicy.Snapshot source=new NebulaDraftVoicePolicy.Snapshot(0,1L,1L,1,1L,new java.io.File("/qa/unused.ogg"),0L,14000L,"qa");
   org.telegram.ui.ActionBar.BaseFragment host=new org.telegram.ui.ActionBar.BaseFragment(){public Activity getParentActivity(){return MainActivity.this;}};
   NebulaDraftVoiceInlineHost inline=new NebulaDraftVoiceInlineHost(recorder,root,()->{int[] p=new int[2];aa.getLocationOnScreen(p);return p[1];},()->{});
   preview=new NebulaDraftVoiceTranscription(host,source,()->source,"Всем привет! Встречаемся в четверг в 18:00. Обсудим новый дизайн и запишем основные задачи. Проверьте текст перед отправкой.",false,v->{},(s,t)->{},inline);
   if(getIntent().getBooleanExtra("keyboard",false))root.postDelayed(()->{EditText edit=findEditor(root);if(edit!=null){edit.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(edit,0);}},600);
   root.postDelayed(new Runnable(){public void run(){try{
    org.json.JSONObject report=new org.json.JSONObject();EditText edit=findEditor(root);report.put("editorWidth",edit==null?0:edit.getWidth());report.put("editorHeight",edit==null?0:edit.getHeight());
    report.put("scale",getResources().getConfiguration().fontScale);report.put("dark",NebulaTheme.dark);report.put("keyboardRequested",getIntent().getBooleanExtra("keyboard",false));report.put("case",getIntent().getStringExtra("case"));
    boolean ime=Build.VERSION.SDK_INT>=30&&root.getRootWindowInsets()!=null&&root.getRootWindowInsets().isVisible(WindowInsets.Type.ime());report.put("imeVisible",ime);
    android.graphics.Rect editorRect=new android.graphics.Rect();if(edit!=null)edit.getGlobalVisibleRect(editorRect);
    report.put("editorLeft",editorRect.left);report.put("editorTop",editorRect.top);report.put("editorRight",editorRect.right);report.put("editorBottom",editorRect.bottom);
    report.put("voiceChoiceVisible",fullyVisible(findText(root,"Голосовое")));report.put("textChoiceVisible",fullyVisible(findText(root,"Текст")));report.put("retryVisible",fullyVisible(findText(root,"Повторить")));report.put("editorVisible",fullyVisible(edit));
    java.io.FileOutputStream out=openFileOutput("layout.json",0);out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.close();
   }catch(Exception e){throw new RuntimeException(e);}if(!isFinishing()&&!isDestroyed())root.postDelayed(this,333);}},1400);
  });
 }
 private void showCamera(){
  boolean front=!getIntent().getStringExtra("case").contains("rear");
  ScrollView sheet=NebulaRoundCameraSheet.content(this,null,front,index->{},()->{});
  root.addView(sheet,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  int[] clicks={0},closes={0};
  ScrollView detached=NebulaRoundCameraSheet.content(this,null,front,index->{if(index!=1)throw new AssertionError("Wrong camera index");clicks[0]++;},()->closes[0]++);
  View rear=(View)findText(detached,"Основная").getParent().getParent();rear.performClick();rear.performClick();
  findDescription(detached,"Закрыть").performClick();
  if(clicks[0]!=1||closes[0]!=1)throw new AssertionError("Camera callback duplicated or close inaccessible");
  root.postDelayed(new Runnable(){public void run(){try{
   TextView frontLabel=findText(root,"Фронтальная"),rearLabel=findText(root,"Основная");
   View frontRow=(View)frontLabel.getParent().getParent(),rearRow=(View)rearLabel.getParent().getParent();
   org.json.JSONObject report=new org.json.JSONObject();report.put("case",getIntent().getStringExtra("case"));
   report.put("camera",true);report.put("dark",NebulaTheme.dark);report.put("scale",getResources().getConfiguration().fontScale);report.put("keyboardRequested",false);
   report.put("titleVisible",fullyVisible(findText(root,"Камера кружка")));report.put("frontVisible",fullyVisible(frontRow));report.put("rearVisible",fullyVisible(rearRow));
   report.put("closeVisible",fullyVisible(findDescription(root,"Закрыть")));report.put("frontSelected",frontRow.isSelected());report.put("rearSelected",rearRow.isSelected());
   report.put("callbackCount",clicks[0]);report.put("closeCount",closes[0]);report.put("sheetHeight",sheet.getHeight());
   java.io.FileOutputStream out=openFileOutput("layout.json",0);out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.close();
  }catch(Exception e){throw new RuntimeException(e);}if(!isFinishing()&&!isDestroyed())root.postDelayed(this,333);}},1400);
 }
 private View findDescription(View v,String text){if(text.equals(v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View child=findDescription(g.getChildAt(i),text);if(child!=null)return child;}}return null;}
 private EditText findEditor(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++){EditText e=findEditor(group.getChildAt(i));if(e!=null)return e;}}return null;}
 private TextView findText(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=findText(g.getChildAt(i),text);if(t!=null)return t;}}return null;}
 private boolean fullyVisible(View v){if(v==null||v.getHeight()==0)return false;android.graphics.Rect r=new android.graphics.Rect();return v.getGlobalVisibleRect(r)&&r.width()>=v.getWidth()-1&&r.height()>=v.getHeight()-1;}
 protected void onDestroy(){if(preview!=null)preview.cancel();super.onDestroy();}
}''')
 platform=sorted((p for p in (SDK/'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*',p.parent.name)),key=lambda p:tuple(map(int,p.parent.name[8:].split('.'))))[-1]
 tools=sorted((p for p in (SDK/'build-tools').iterdir() if re.fullmatch(r'\d+(?:\.\d+)*',p.name) and p.is_dir() and ((p/'aapt2').exists() or (p/'aapt2.exe').exists())),key=lambda p:tuple(map(int,p.name.split('.'))))[-1]
 def tool(name):return tools/(name+('.exe' if os.name=='nt' and name=='aapt2' else '.bat' if os.name=='nt' else ''))
 classes=WORK/'classes';classes.mkdir(exist_ok=True)
 production=['NebulaDraftVoicePolicy.java','NebulaDraftVoiceTranscription.java','NebulaDraftVoiceInlineHost.java','NebulaAiPanelUi.java','NebulaLocalAudioDecoder.java','NebulaLocalAudioPolicy.java','NebulaPcmResampler.java','NebulaRoundCameraSheet.java']
 run('javac','-encoding','UTF-8','-source','17','-target','17','-cp',platform,'-d',classes,*list((WORK/'src').rglob('*.java')),*[UI/n for n in production])
 jar=WORK/'widgets.jar';run('jar','cf',jar,'-C',classes,'.');run(tool('d8'),'--lib',platform,'--min-api','26','--output',WORK,jar)
 manifest=WORK/'AndroidManifest.xml';manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.nebulagram.uiqa"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:debuggable="true" android:label="Nebula widget QA" android:theme="@android:style/Theme.Material.Light.NoActionBar"><activity android:name=".MainActivity" android:exported="true" android:windowSoftInputMode="adjustResize"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity></application></manifest>')
 apk=WORK/'widgets-unsigned.apk';run(tool('aapt2'),'link','-I',platform,'--manifest',manifest,'-o',apk)
 with zipfile.ZipFile(apk,'a') as archive:archive.write(WORK/'classes.dex','classes.dex')
 key=WORK/'qa.p12'
 if not key.exists():run('keytool','-genkeypair','-keystore',key,'-alias','qa','-keyalg','RSA','-keysize','2048','-validity','7','-storepass','widgetqa','-keypass','widgetqa','-dname','CN=Temporary Widget QA')
 run(tool('apksigner'),'sign','--ks',key,'--ks-key-alias','qa','--ks-pass','pass:widgetqa','--out',WORK/'widgets.apk',apk)
 print('Built the credential-free production-widget fixture')

def render():
 adb=SDK/'platform-tools'/('adb.exe' if os.name=='nt' else 'adb')
 if os.environ.get('CI')!='true':raise SystemExit('Render only on the isolated CI emulator; never change a user device display')
 devices=subprocess.check_output([str(adb),'devices'],text=True)
 serials=[line.split()[0] for line in devices.splitlines()[1:] if line.strip().endswith('\tdevice')]
 assert len(serials)==1 and serials[0].startswith('emulator-'),devices
 def device(*args,**kw):return run(adb,'-s',serials[0],*args,**kw)
 device('install','-r',WORK/'widgets.apk')
 output=WORK/'screenshots';output.mkdir(exist_ok=True)
 try:
  for name,width,dark,scale,keyboard in [('dark',1080,True,1,False),('light',1080,False,1,False),('narrow-large',840,True,1.3,False),('keyboard',1080,True,1,True),('camera-dark-front',1080,True,1,False),('camera-light-rear',1080,False,1,False),('camera-narrow-large-rear',840,True,1.3,False)]:
   device('shell','am','force-stop','app.nebulagram.uiqa')
   device('shell','wm','size',f'{width}x1920');device('shell','wm','density','420');device('shell','settings','put','system','font_scale',str(scale));time.sleep(3)
   device('shell','input','keyevent','KEYCODE_WAKEUP');device('shell','wm','dismiss-keyguard')
   camera=name.startswith('camera-')
   device('shell','am','start','-W','-n','app.nebulagram.uiqa/.MainActivity','--es','case',name,'--ez','dark',str(dark).lower(),'--ez','keyboard',str(keyboard).lower(),'--ez','camera',str(camera).lower())
   report=None
   for attempt in range(15):
    time.sleep(1)
    try:report=json.loads(subprocess.check_output([str(adb),'-s',serials[0],'shell','run-as','app.nebulagram.uiqa','cat','files/layout.json'],stderr=subprocess.DEVNULL))
    except (ValueError,subprocess.CalledProcessError):continue
    if report.get('case')==name and abs(report['scale']-scale)<0.01 and report['dark']==dark and report['keyboardRequested']==keyboard:break
   assert report and report.get('case')==name and abs(report['scale']-scale)<0.01 and report['dark']==dark and report['keyboardRequested']==keyboard,report
   foreground=subprocess.check_output([str(adb),'-s',serials[0],'shell','dumpsys','activity','activities'],text=True)
   resumed=[line for line in foreground.splitlines() if 'mResumedActivity:' in line or 'topResumedActivity=' in line]
   assert any('app.nebulagram.uiqa/.MainActivity' in line for line in resumed),resumed
   if keyboard:
    assert report['editorRight']>report['editorLeft'] and report['editorBottom']>report['editorTop'],report
    device('shell','input','tap',str((report['editorLeft']+report['editorRight'])//2),str((report['editorTop']+report['editorBottom'])//2))
    for attempt in range(15):
     time.sleep(1)
     report=json.loads(subprocess.check_output([str(adb),'-s',serials[0],'shell','run-as','app.nebulagram.uiqa','cat','files/layout.json']))
     if report['imeVisible']:break
    assert report['imeVisible'],'Keyboard was requested but not actually shown'
   with (output/(name+'.png')).open('wb') as f:device('exec-out','screencap','-p',stdout=f)
   if camera:
    assert report.get('camera') and report['titleVisible'] and report['frontVisible'] and report['rearVisible'] and report['closeVisible'],report
    assert report['frontSelected']==('front' in name) and report['rearSelected']==('rear' in name),report
    assert report['callbackCount']==1 and report['closeCount']==1,report
   else:
    assert report['editorWidth']>0 and report['editorHeight']>0,report
    assert report['voiceChoiceVisible'] and report['textChoiceVisible'] and report['editorVisible'],report
    if not keyboard:assert report['retryVisible'],report
   (output/(name+'.json')).write_text(json.dumps(report,indent=2)+'\n')
  crash=subprocess.check_output([str(adb),'-s',serials[0],'logcat','-d','-b','crash'],text=True)
  (output/'crash.log').write_text(crash)
  assert 'FATAL EXCEPTION' not in crash,crash
 finally:
  device('shell','wm','size','reset');device('shell','wm','density','reset');device('shell','settings','put','system','font_scale','1')
 print('Rendered actual widgets in light/dark, narrow/large-font and keyboard states; screenshots require visual inspection')

if __name__=='__main__':build() if sys.argv[1]=='build' else render()
