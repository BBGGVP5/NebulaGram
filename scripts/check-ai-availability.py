"""Verify message-menu availability using the real preference gate and endpoint validation."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
work = root / 'build/ai-availability-check'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
client = (ui / 'NebulaAiClient.java').read_text(encoding='utf-8')
base = client[client.index('    public static String base('):client.index('    public static JSONObject payload(')]
sources = {
    'android/content/SharedPreferences.java': '''package android.content;
public class SharedPreferences {
 public final java.util.Map<String,Object> data = new java.util.HashMap<>();
 public int getInt(String k,int d){return (Integer)data.getOrDefault(k,d);}
 public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
 public boolean getBoolean(String k,boolean d){return (Boolean)data.getOrDefault(k,d);}
 public SharedPreferences edit(){return this;}
 public SharedPreferences putBoolean(String k,boolean v){data.put(k,v);return this;}
 public void apply(){}
}''',
    'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader {
 public static final Context applicationContext = new Context();
 public static class Context {
  public final android.content.SharedPreferences prefs = new android.content.SharedPreferences();
  public android.content.SharedPreferences getSharedPreferences(String n,int m){return prefs;}
 }
}''',
    'app/nebulagram/ui/NebulaAiClient.java': 'package app.nebulagram.ui; import java.net.*; import java.io.*; public class NebulaAiClient { public static final int OPENAI=0,CLAUDE=1,GEMINI=2;\n' + base + '}',
    'app/nebulagram/ui/NebulaAiSecrets.java': '''package app.nebulagram.ui;
public class NebulaAiSecrets {public static boolean stored; public static boolean exists(int provider){return stored;}}''',
    'Check.java': '''import app.nebulagram.ui.*;
import org.telegram.messenger.ApplicationLoader;
public class Check {
 public static void main(String[] args) {
  var p=ApplicationLoader.applicationContext.prefs;
  int cases=0;
  for(int provider=0;provider<4;provider++) for(boolean enabled:new boolean[]{false,true})
   for(boolean key:new boolean[]{false,true}) for(String model:new String[]{""," ","demo-model"}) {
    p.data.clear(); p.data.put("provider",provider);p.data.put("model_"+provider,model);
    p.data.put("endpoint","https://example.com/v1");NebulaAiSecrets.stored=key;
    NebulaAiAvailability.setEnabled(enabled);
    if(NebulaAiAvailability.available() != (enabled && key && !model.trim().isEmpty())) throw new AssertionError("Readiness combination");
    cases++;
   }
  for(String url:new String[]{"", "http://example.com", "https://user:pass@example.com", "https://example.com?q=1"}) {
   p.data.put("endpoint",url);
   if(NebulaAiAvailability.available()) throw new AssertionError("Invalid custom endpoint"); cases++;
  }
  p.data.put("provider",8);
  if(NebulaAiAvailability.available()) throw new AssertionError("Unknown provider");
  System.out.println("AI availability: "+(cases+1)+" cases passed; no network or real credentials used");
 }
}'''
}
paths=[]
for name, content in sources.items():
    path=work/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(content,encoding='utf-8');paths.append(str(path))
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*paths,str(ui/'NebulaAiAvailability.java')],check=True)
subprocess.run(['java','-cp',str(work),'Check'],check=True)
