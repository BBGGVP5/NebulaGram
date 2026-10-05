"""Execute profile migration, credential isolation and role selection on the real stores."""
from pathlib import Path
import os, subprocess

root=Path(__file__).resolve().parents[1]
work=root/'build/ai-services-check'; work.mkdir(parents=True,exist_ok=True)
jar=root/'build/ai-protocol-check/json-20240303.jar'
if not jar.exists():
    jar.parent.mkdir(parents=True,exist_ok=True)
    import urllib.request,hashlib
    urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',jar)
    assert hashlib.sha256(jar.read_bytes()).hexdigest()=='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
sources={
'android/content/SharedPreferences.java':'''package android.content; public interface SharedPreferences {
String getString(String k,String d); int getInt(String k,int d); boolean getBoolean(String k,boolean d); Editor edit();
interface Editor {Editor putString(String k,String v); Editor putInt(String k,int v); Editor putBoolean(String k,boolean v); Editor remove(String k); void apply();}}
''',
'org/telegram/messenger/ApplicationLoader.java':'''package org.telegram.messenger; import java.util.*;import android.content.SharedPreferences;
public class ApplicationLoader {public static final C applicationContext=new C(); public static class C {Map<String,P> stores=new HashMap<>(); public P getSharedPreferences(String n,int m){return stores.computeIfAbsent(n,k->new P());}}
public static class P implements SharedPreferences,SharedPreferences.Editor {Map<String,Object> values=new HashMap<>();public String getString(String k,String d){return (String)values.getOrDefault(k,d);}public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}public Editor edit(){return this;}public Editor putString(String k,String v){values.put(k,v);return this;}public Editor putInt(String k,int v){values.put(k,v);return this;}public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public Editor remove(String k){values.remove(k);return this;}public void apply(){}}
}''',
'app/nebulagram/ui/NebulaAiSecrets.java':'''package app.nebulagram.ui;import java.util.*;public class NebulaAiSecrets {
static Map<String,String> values=new HashMap<>();public static void save(int p,String s){save("p"+p,s);}public static String read(int p){return read("p"+p);}public static boolean exists(int p){return !read(p).isEmpty();}
public static void save(String k,String s){if(s.isEmpty())values.remove(k);else values.put(k,s);}public static String read(String k){return values.getOrDefault(k,"");}public static boolean exists(String k){return !read(k).isEmpty();}}
''',
'app/nebulagram/ui/NebulaNanoAi.java':'''package app.nebulagram.ui;public class NebulaNanoAi {public static String generate(String p,String i,java.util.function.BooleanSupplier c){return i;}}''',
'app/nebulagram/ui/NebulaText.java':'''package app.nebulagram.ui;public class NebulaText {public static String text(String r,String e){return r;}}''',
'CheckServices.java':'''import app.nebulagram.ui.*;import org.telegram.messenger.ApplicationLoader;public class CheckServices {
static void check(boolean x,String n){if(!x)throw new AssertionError(n);}
public static void main(String[] a)throws Exception{
var p=ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_settings",0);p.edit().putInt("provider",3).putString("model_3","model-old").putString("endpoint","https://old.example/v1").apply();NebulaAiSecrets.save(3,"old-secret");
var old=NebulaAiServices.list();check(old.size()>=2,"legacy and local services");check(p.getInt("provider",0)==3&&NebulaAiSecrets.read(3).equals("old-secret"),"migration preserves connection");
var first=NebulaAiServices.save("","One",3,"https://one.example/v1","one","secret-one");
var second=NebulaAiServices.save("","Two",3,"https://two.example/v1","two","secret-two");
NebulaAiServices.select(first.id);check(NebulaAiSecrets.read(3).equals("secret-one"),"first key");check(p.getString("model_3","").equals("one"),"first model");
NebulaAiServices.select(second.id);check(NebulaAiSecrets.read(3).equals("secret-two"),"same provider isolated");
NebulaAiServices.select(first.id);check(NebulaAiSecrets.read(3).equals("secret-one"),"round trip");NebulaAiServices.delete(second.id);check(NebulaAiSecrets.read(3).equals("secret-one"),"other deletion preserves active key");
check(!ApplicationLoader.applicationContext.getSharedPreferences("nebula_ai_services",0).getString("profiles","").contains("secret-one"),"no plaintext key metadata");
NebulaAiServices.select("nano");check(p.getInt("provider",0)==4,"local has no cloud credential requirement");
NebulaAiRoles.select("proofreader");check(!NebulaAiRoles.prompt().isEmpty(),"preset role");
var role=NebulaAiRoles.save("","Custom","Reply briefly","🤖");NebulaAiRoles.select(role.id);check(NebulaAiRoles.prompt().equals("Reply briefly"),"custom prompt");NebulaAiRoles.delete(role.id);check(NebulaAiRoles.selected().equals("assistant"),"deleted selection fallback");
System.out.println("AI services: legacy migration, same-provider keys, Nano, deletion and custom roles passed");}}
'''}
for name,content in sources.items():
    path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8')
overlay=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
files=[str(work/name) for name in sources]+[str(overlay/(name+'.java')) for name in ['NebulaAiServices','NebulaAiRoles','NebulaAiClient']]
subprocess.run(['javac','-encoding','UTF-8','-cp',str(jar),'-d',str(work),*files],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(work),str(jar)]),'CheckServices'],check=True)
