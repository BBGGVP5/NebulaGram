"""Execute account search/order while preserving the native IDs used for switching."""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
production=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAccountList.java'
source=r'''
import app.nebulagram.ui.NebulaAccountList;import app.nebulagram.ui.NebulaAccountList.Entry;import java.util.*;
public class AccountListCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  ArrayList<Entry> accounts=new ArrayList<>();for(int id=0;id<128;id++)accounts.add(new Entry(id,"Person "+id,"member"+id,1000+id));
  List<Entry> recent=NebulaAccountList.visible(accounts,"",0);check(recent.size()==128&&recent.get(0).id==127&&recent.get(127).id==0);
  List<Entry> ids=NebulaAccountList.visible(accounts,"",2);for(int id=0;id<128;id++)check(ids.get(id).id==id);
  check(accounts.get(0).id==0&&accounts.get(127).id==127);
  List<Entry> found=NebulaAccountList.visible(accounts," @MEMBER127 ",1);check(found.size()==1&&found.get(0).id==127);
  check(NebulaAccountList.visible(accounts,"missing",0).isEmpty());
  accounts.add(new Entry(1000000,"Анна","БОТ",55));check(NebulaAccountList.visible(accounts,"анна",0).get(0).id==1000000);check(NebulaAccountList.visible(accounts,"@бот",0).get(0).id==1000000);
  ArrayList<Entry> tied=new ArrayList<>(Arrays.asList(new Entry(99,"same",null,0),new Entry(11,"same",null,0),new Entry(23,null,null,0)));
  check(NebulaAccountList.visible(tied,"same",1).get(0).id==11);
  check(NebulaAccountList.visible(tied,"",1).get(0).id==23);
  accounts.add(new Entry(2000000,"Élodie","",0));check(NebulaAccountList.visible(accounts,"E\u0301LODIE",0).get(0).id==2000000);
  System.out.println("Account switcher: 128 identities, sparse IDs, stable sorting, username/case/Unicode search and source preservation passed");
 }
}'''
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-account-list-') as work:
 work=Path(work);test=work/'AccountListCheck.java';test.write_text(source,encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'--release','8','-encoding','UTF-8','-d',str(work),str(production),str(test)],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'AccountListCheck'],check=True)
