"""Run popup geometry over screen/message sizes and protect native icon exclusions."""
from pathlib import Path
import subprocess, re, sys, xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent.parent
overlay=r/'platform/android/overlay/TMessagesProj/src/main'
work=r/'build/appearance-controls-check';work.mkdir(parents=True,exist_ok=True)
source=work/'CheckAppearance.java'
source.write_text('''import app.nebulagram.ui.NebulaMessageMenuLayout;
public class CheckAppearance {
public static void main(String[] args) {
 int tested=0;
 for(float screen:new float[]{320,480,640,800,1200,2000})
 for(float source:new float[]{-400,0,50,300,700,1600})
 for(float message:new float[]{24,80,200,700,2000,6000})
 for(float menu:new float[]{60,100,180,300,700}) {
  float boundedMenu=Math.min(menu,screen-160);
  var layout=NebulaMessageMenuLayout.calculate(source,message,32,screen-16,boundedMenu,8);
  if(layout.top<31.99 || layout.scale<=0 || layout.scale>1 || layout.menuTop+boundedMenu>screen-15.99
    || Math.abs(layout.menuTop-layout.top-message*layout.scale-8)>.01)
      throw new AssertionError("message/menu overlap or off-screen menu");
  tested++;
 }
 System.out.println("Appearance geometry passed: "+tested+" screen/message/menu combinations");
}}
''',encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(source),str(overlay/'java/app/nebulagram/ui/NebulaMessageMenuLayout.java')],check=True)
subprocess.run(['java','-cp',str(work),'CheckAppearance'],check=True)
icons=(overlay/'java/app/nebulagram/ui/NebulaIcons.java').read_text(encoding='utf-8')
names=re.findall(r'ICONS.put\(R.drawable.(\w+)',icons)
for name in names:
    assert not any(k in name for k in ['check','emoji','sticker','smile','download_settings','clock']),name
for p in overlay.glob('res/drawable/nebula_cupertino_*.xml'):
    root=ET.parse(p).getroot()
    assert root.attrib['{http://schemas.android.com/apk/res/android}viewportWidth']=='24',p
print(f'Icon exclusions and {len(list(overlay.glob("res/drawable/nebula_cupertino_*.xml")))} original vectors passed')

if len(sys.argv) > 1:
    native=Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java'
    code=native.read_text(encoding='utf-8')
    block=code[code.index('    private int nebulaObservedAccount'):code.index('    public void setNebulaFloatingChatHeader(')]
    target=work/'CheckUnread.java'
    target.write_text('''import java.util.*;
public class CheckUnread {
boolean attached,nebulaFloatingChatHeader; int nebulaHeaderAccount,invalidations;
void invalidate(){invalidations++;}
static class NotificationCenter {
 static final int dialogsNeedReload=1,updateInterfaces=2;
 interface NotificationCenterDelegate {void didReceivedNotification(int id,int account,Object... args);}
 static Map<Integer,NotificationCenter> centers=new HashMap<>();
 Map<Integer,Set<NotificationCenterDelegate>> observers=new HashMap<>();
 static NotificationCenter getInstance(int a){return centers.computeIfAbsent(a,k->new NotificationCenter());}
 void addObserver(NotificationCenterDelegate d,int id){observers.computeIfAbsent(id,k->new HashSet<>()).add(d);}
 void removeObserver(NotificationCenterDelegate d,int id){observers.getOrDefault(id,new HashSet<>()).remove(d);}
 void send(int id){for(var d:observers.getOrDefault(id,new HashSet<>()))d.didReceivedNotification(id,0);}
}
'''+block+'''
public static void main(String[] args){
 CheckUnread bar=new CheckUnread();bar.attached=true;bar.nebulaFloatingChatHeader=true;
 bar.updateNebulaUnreadObserver();bar.updateNebulaUnreadObserver();
 NotificationCenter.getInstance(0).send(1);if(bar.invalidations!=1)throw new AssertionError("missing or duplicate observer");
 bar.nebulaHeaderAccount=1;bar.updateNebulaUnreadObserver();
 NotificationCenter.getInstance(0).send(1);NotificationCenter.getInstance(1).send(2);
 if(bar.invalidations!=2)throw new AssertionError("counter follows wrong account");
 bar.attached=false;bar.updateNebulaUnreadObserver();NotificationCenter.getInstance(1).send(1);
 if(bar.invalidations!=2)throw new AssertionError("observer leaked after detach");
 System.out.println("Unread counter passed: live updates, account switch and detach cleanup");
}}
''',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target)],check=True)
    subprocess.run(['java','-cp',str(work),'CheckUnread'],check=True)
