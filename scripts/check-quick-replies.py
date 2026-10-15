"""Execute account-scoped template storage using real JSON and in-memory preferences."""
from pathlib import Path
import os
import subprocess
import tempfile
import urllib.request

root = Path(__file__).resolve().parents[1]
jar = root / 'build/ai-protocol-check/json-20240303.jar'
if not jar.exists():
    jar.parent.mkdir(parents=True, exist_ok=True)
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
sources = {
'android/content/SharedPreferences.java': '''package android.content;import java.util.*;public class SharedPreferences {public Map<String,String> data=new HashMap<>();public String getString(String k,String d){return data.getOrDefault(k,d);}public Editor edit(){return new Editor();}public class Editor {public Editor putString(String k,String v){data.put(k,v);return this;}public void apply(){}}}''',
'android/content/Context.java': '''package android.content;import java.util.*;public class Context {Map<String,SharedPreferences> stores=new HashMap<>();public SharedPreferences getSharedPreferences(String n,int m){return stores.computeIfAbsent(n,k->new SharedPreferences());}}''',
'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
'QuickRepliesCheck.java': r'''
import app.nebulagram.ui.NebulaQuickReplies;import org.telegram.messenger.ApplicationLoader;
public class QuickRepliesCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static void rejects(RunnableWithException r)throws Exception{try{r.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException|IllegalStateException expected){}}
 interface RunnableWithException{void run()throws Exception;}
 public static void main(String[] args)throws Exception {
  NebulaQuickReplies.put(123,null," Greeting ","Hi\nNext line 🚀");
  var a=NebulaQuickReplies.list(123);check(a.size()==1&&a.get(0).title.equals("Greeting")&&a.get(0).text.equals("Hi\nNext line 🚀"),"round trip");
  check(NebulaQuickReplies.list(456).isEmpty(),"account leak");String id=a.get(0).id;
  NebulaQuickReplies.put(123,id,"Updated","Changed");check(NebulaQuickReplies.list(123).get(0).id.equals(id),"edit duplicated template");
  rejects(()->NebulaQuickReplies.put(123,"missing","t","v"));rejects(()->NebulaQuickReplies.put(0,null,"t","v"));
  rejects(()->NebulaQuickReplies.put(123,null," ","v"));rejects(()->NebulaQuickReplies.put(123,null,"t"," "));
  rejects(()->NebulaQuickReplies.put(123,null,"x".repeat(81),"v"));rejects(()->NebulaQuickReplies.put(123,null,"t","x".repeat(4097)));
  for(int i=1;i<100;i++)NebulaQuickReplies.put(123,null,"t"+i,"x".repeat(4096));
  rejects(()->NebulaQuickReplies.put(123,null,"extra","v"));NebulaQuickReplies.put(123,id,"still editable","v");
  NebulaQuickReplies.delete(123,id);check(NebulaQuickReplies.list(123).size()==99,"delete");
  var prefs=ApplicationLoader.applicationContext.getSharedPreferences("nebula_quick_replies_789",0);
  prefs.edit().putString("entries","invalid JSON").apply();check(NebulaQuickReplies.list(789).isEmpty(),"bad JSON crashed");
  prefs.edit().putString("entries","[{},null,{\"id\":\"1\",\"title\":\"t\",\"text\":\"v\"},{\"id\":\"1\",\"title\":\"duplicate\",\"text\":\"v\"}]").apply();
  check(NebulaQuickReplies.list(789).size()==1,"invalid or duplicate rows accepted");
  System.out.println("Quick replies: account isolation, round trips, stable edit IDs, limits, delete and malformed storage passed");
 }
}'''
}
with tempfile.TemporaryDirectory(prefix='nebula-replies-') as directory:
    path = Path(directory)
    for name, content in sources.items():
        file = path / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_text(content, encoding='utf-8')
    production = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaQuickReplies.java'
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', directory, *map(str, path.rglob('*.java')), str(production)], check=True)
    subprocess.run(['java', '-cp', os.pathsep.join([directory, str(jar)]), 'QuickRepliesCheck'], check=True)
