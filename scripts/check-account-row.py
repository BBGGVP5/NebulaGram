"""Typecheck the actual account row with Android SDK and execute its avatar boundary."""
from pathlib import Path
import os, shutil, subprocess, tempfile

root=Path(__file__).resolve().parents[1]
row=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAccountRow.java'
text=row.read_text(encoding='utf-8')
binding=text[text.index('        if (masked)'):text.index('        LayoutParams photo')]
harness=r'''
class AccountAvatarCheck {
 static class R {static class drawable {static int msg_secret=1;}}
 static class User {}static class Theme {int onSurfaceVariant(){return 5;}}
 static class AvatarDrawable {static int infoCalls,lastAccount;void setInfo(int a,User u){infoCalls++;lastAccount=a;}}
 static class Image {int placeholders,requests,receiverCalls,owner;User loaded;
  void setImageResource(int id,int color){placeholders++;}Image getImageReceiver(){return this;}
  void setCurrentAccount(int account){receiverCalls++;owner=account;}void setForUserOrChat(User u,AvatarDrawable d){requests++;loaded=u;}}
 static Image bind(int account,User user,boolean masked){Image avatar=new Image();Theme theme=new Theme();BIND return avatar;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){User user=new User();
  for(int id:new int[]{0,9,127,1000000,Integer.MAX_VALUE}){
   Image normal=bind(id,user,false);check(normal.owner==id&&normal.requests==1&&normal.loaded==user&&normal.receiverCalls==1&&normal.placeholders==0&&AvatarDrawable.lastAccount==id);
   int calls=AvatarDrawable.infoCalls;Image masked=bind(id,user,true);check(masked.placeholders==1&&masked.requests==0&&masked.receiverCalls==0&&AvatarDrawable.infoCalls==calls);
  }
  System.out.println("Account row: real avatar binding preserves sparse account ownership; masking performs no photo/name placeholder request");
 }
}
'''.replace('BIND',binding)
stubs={
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger;public class AndroidUtilities {public static int dp(float v){return Math.round(v);}public static android.graphics.Typeface bold(){return android.graphics.Typeface.DEFAULT_BOLD;}}',
 'org/telegram/messenger/LocaleController.java': 'package org.telegram.messenger;public class LocaleController {public static boolean isRTL;}',
 'org/telegram/messenger/R.java': 'package org.telegram.messenger;public class R {public static class drawable {public static int msg_secret=1,account_check=2;}}',
 'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet;public class TLRPC {public static class User {}}',
 'org/telegram/ui/Components/AvatarDrawable.java': 'package org.telegram.ui.Components;public class AvatarDrawable extends android.graphics.drawable.ColorDrawable {public void setInfo(int a,org.telegram.tgnet.TLRPC.User u){}}',
 'org/telegram/ui/Components/BackupImageView.java': '''package org.telegram.ui.Components;public class BackupImageView extends android.view.View {
 public BackupImageView(android.content.Context c){super(c);}public void setRoundRadius(int r){}
 public void setImageResource(int r,int c){}public void setForUserOrChat(org.telegram.tgnet.TLRPC.User u,AvatarDrawable p){}
 public Receiver getImageReceiver(){return new Receiver();}public static class Receiver {public void setCurrentAccount(int a){}}}''',
 'app/nebulagram/ui/NebulaTheme.java': '''package app.nebulagram.ui;public class NebulaTheme {public static NebulaTheme of(android.content.Context c){return new NebulaTheme();}
 public int primary(){return 0;}public int onSurface(){return 0;}public int onSurfaceVariant(){return 0;}public static int stateLayer(int c,float a){return c;}}''',
}
sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or 'C:/Users/Danila/AppData/Local/Android/Sdk')
android_jar=sorted(sdk.glob('platforms/android-*/android.jar'))[-1]
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
javac=shutil.which('javac') or str(java_home/'bin/javac.exe');java=shutil.which('java') or str(java_home/'bin/java.exe')
with tempfile.TemporaryDirectory(prefix='nebula-account-row-') as directory:
 work=Path(directory);test=work/'AccountAvatarCheck.java';test.write_text(harness,encoding='utf-8')
 subprocess.run([javac,'-encoding','UTF-8','-d',str(work),str(test)],check=True)
 subprocess.run([java,'-cp',str(work),'AccountAvatarCheck'],check=True)
 for name,source in stubs.items():
  path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source,encoding='utf-8')
 subprocess.run([javac,'-encoding','UTF-8','-cp',str(android_jar),'-d',str(work),str(row),*map(str,(work/'org').rglob('*.java')),*map(str,(work/'app').rglob('*.java'))],check=True)
print('Actual account row compiles against Android SDK; rendered RTL/large-text acceptance remains a device check')
