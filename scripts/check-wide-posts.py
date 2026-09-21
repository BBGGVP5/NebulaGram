"""Run real wide-post preferences and native text measurement without an emulator."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java'
source = (native / 'org/telegram/messenger/MessageObject.java').read_text(encoding='utf-8')
a = source.index('            final boolean needDrawAvatarInternal = needDrawAvatarInternal();', source.index('public int getMaxMessageTextWidth()'))
b = source.index('\n        }', a)
width = source[a:b]
cell = (native / 'org/telegram/ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
a = cell.index('        boolean widePosts = ', cell.index('private void setMessageContent('))
refresh = cell[a:cell.index('        messageObject.isOutOwnerCached', a)]
with tempfile.TemporaryDirectory(prefix='nebula-wide-') as directory:
    tree = Path(directory)
    def put(path, text):
        p = tree / path
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text, encoding='utf-8')
    put('app/nebulagram/ui/NebulaWidePosts.java', (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaWidePosts.java').read_text(encoding='utf-8'))
    put('android/content/SharedPreferences.java', r"""package android.content;
public class SharedPreferences {
 public boolean value; public int reads, registrations; OnSharedPreferenceChangeListener listener;
 public interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String k);}
 public boolean getBoolean(String key,boolean fallback){reads++;return value;}
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listener=l;registrations++;}
 public SharedPreferences edit(){return this;}
 public SharedPreferences putBoolean(String key,boolean v){value=v;return this;}
 public void apply(){if(listener!=null)listener.onSharedPreferenceChanged(this,"wide_posts");}
 public void clear(){value=false;if(listener!=null)listener.onSharedPreferenceChanged(this,null);}
}""")
    put('org/telegram/messenger/ApplicationLoader.java', r"""package org.telegram.messenger;
public class ApplicationLoader {
 public static class Context {public android.content.SharedPreferences p=new android.content.SharedPreferences();
  public android.content.SharedPreferences getSharedPreferences(String name,int mode){return p;}}
 public static Context applicationContext=new Context();
}""")
    put('WideCheck.java', r"""
import app.nebulagram.ui.NebulaWidePosts;
import org.telegram.messenger.ApplicationLoader;
class WideCheck {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static float density=1;static int dp(int v){return (int)Math.ceil(v*density);}
 static final int TYPE_ARTICLE=1;
 static class TLRPC {static class TL_messageMediaGame {}}
 int generatedWithMinSize,type;boolean generatedWithWidePosts,sideMenuEnabled,isSaved,out,share,avatar,game;
 static class Owner {boolean isThreadMessage;}
 Owner messageOwner=new Owner();
 boolean isSponsored(){return false;}boolean isOutOwner(){return out;}
 boolean needDrawAvatarInternal(){return avatar;}boolean needDrawShareButton(){return share;}
 Object getMedia(Owner o){return game?new TLRPC.TL_messageMediaGame():null;}
 int width(){int maxWidth=0;WIDTH return maxWidth;}
 static class AndroidUtilities {static class Size{int y=800;}static Size displaySize=new Size();}
 static class Message {int checks;boolean stale;boolean checkLayout(){checks++;return stale;}}
 boolean nebulaWidePosts;Object currentMessageObject=new Object(),currentPosition;int lastHeight=800;
 void refresh(Message messageObject){REFRESH}
 public static void main(String[] args){
  var prefs=ApplicationLoader.applicationContext.p;
  check(!NebulaWidePosts.enabled(),"default must preserve Telegram layout");
  int reads=prefs.reads;for(int i=0;i<10000;i++)NebulaWidePosts.enabled();
  check(prefs.reads==reads,"layout repeatedly reads preferences");
  NebulaWidePosts.setEnabled(true);check(NebulaWidePosts.enabled(),"toggle ignored");
  prefs.edit().putBoolean("wide_posts",false).apply();check(!NebulaWidePosts.enabled(),"import ignored");
  NebulaWidePosts.setEnabled(true);prefs.clear();check(!NebulaWidePosts.enabled(),"reset ignored");
  check(prefs.registrations==1,"duplicate preference listeners");
  int cases=0;
  for(float scale:new float[]{1,1.5f,2.75f,3})for(int screen:new int[]{240,320,360,480,768})for(int flags=0;flags<128;flags++){
   density=scale;WideCheck c=new WideCheck();c.generatedWithMinSize=dp(screen);
   c.sideMenuEnabled=(flags&1)!=0;c.avatar=(flags&2)!=0;c.share=(flags&4)!=0;c.out=(flags&8)!=0;c.isSaved=(flags&16)!=0;c.messageOwner.isThreadMessage=(flags&32)!=0;c.type=(flags&64)!=0?TYPE_ARTICLE:0;
   int expected=dp(screen)-dp(c.type==TYPE_ARTICLE?40:80);
   expected-=c.sideMenuEnabled?dp(64):c.avatar&&!c.out&&!c.messageOwner.isThreadMessage?dp(52):0;
   expected-=c.share&&(c.isSaved||!c.out)?dp(c.isSaved&&c.out?40:14):0;
   int normal=c.width();check(normal==expected,"disabled measurement drift");
   c.generatedWithWidePosts=true;int wide=c.width();
   check(wide>=normal,"wide mode shrinks text");check(wide<dp(screen),"text reaches screen edge");
   if(c.type!=TYPE_ARTICLE){
    check(wide>normal,"wide mode has no effect");
    int occupied=wide+dp(31)+(c.sideMenuEnabled?dp(64):c.avatar&&!c.out&&!c.messageOwner.isThreadMessage?dp(52):0);
    if(c.share&&(c.isSaved||!c.out))occupied+=dp(40);
    check(occupied<=dp(screen)-dp(4),"bubble overlaps avatar/share/right edge");
   }
   cases++;
  }
  WideCheck c=new WideCheck();Message message=new Message();
  NebulaWidePosts.setEnabled(true);c.refresh(message);check(c.currentMessageObject==null&&message.checks==1,"existing cell/text not invalidated");
  c.currentMessageObject=new Object();c.refresh(message);check(c.currentMessageObject!=null,"stable cell rebuilt");
  NebulaWidePosts.setEnabled(false);c.refresh(message);check(c.currentMessageObject==null,"disable did not restore cached cell");
  System.out.println(cases+" native text geometry cases; preference cache/import/reset and cell invalidation passed");
 }
}
""".replace('WIDTH', width).replace('REFRESH', refresh))
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(tree), *map(str, tree.rglob('*.java'))], check=True)
    subprocess.run(['java', '-cp', str(tree), 'WideCheck'], check=True)
