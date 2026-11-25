"""Pill Stack: run the pill format, conditions, templates and import against a tiny org.json, then check the wiring and the 0379 hook."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

# Android ships org.json; the JDK does not. This is just enough of it, written for the check.
JSON = r'''package org.json;
import java.util.*;
public class JSONObject {
 public static final Object NULL=new Object(){public String toString(){return "null";}};
 final LinkedHashMap<String,Object> map=new LinkedHashMap<>();
 public JSONObject(){}
 public JSONObject(Map<String,String> m){map.putAll(m);}
 public JSONObject(String s) throws JSONException{Object v=new JSONTokener(s).nextValue();if(!(v instanceof JSONObject))throw new JSONException("not an object");map.putAll(((JSONObject)v).map);}
 public JSONObject put(String k,Object v){map.put(k,v);return this;}
 public JSONObject put(String k,int v){map.put(k,v);return this;}
 public JSONObject put(String k,boolean v){map.put(k,v);return this;}
 public boolean has(String k){return map.containsKey(k);}
 public Object opt(String k){return map.get(k);}
 public String optString(String k,String d){Object v=map.get(k);return v==null||v==NULL?d:v.toString();}
 public String optString(String k){return optString(k,"");}
 public int optInt(String k,int d){Object v=map.get(k);if(v instanceof Number)return ((Number)v).intValue();try{return Integer.parseInt(String.valueOf(v));}catch(Exception e){return d;}}
 public boolean optBoolean(String k,boolean d){Object v=map.get(k);return v instanceof Boolean?(Boolean)v:d;}
 public JSONArray optJSONArray(String k){Object v=map.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
 public Iterator<String> keys(){return map.keySet().iterator();}
 public String toString(){StringBuilder b=new StringBuilder("{");boolean f=true;for(Map.Entry<String,Object> e:map.entrySet()){if(!f)b.append(',');f=false;b.append(JSONTokener.quote(e.getKey())).append(':').append(JSONTokener.write(e.getValue()));}return b.append('}').toString();}
}
'''
ARRAY = r'''package org.json;
import java.util.*;
public class JSONArray {
 final ArrayList<Object> list=new ArrayList<>();
 public JSONArray(){}
 public JSONArray(String s) throws JSONException{Object v=new JSONTokener(s).nextValue();if(!(v instanceof JSONArray))throw new JSONException("not an array");list.addAll(((JSONArray)v).list);}
 public int length(){return list.size();}
 public Object opt(int i){return i>=0&&i<list.size()?list.get(i):null;}
 public JSONObject optJSONObject(int i){Object v=opt(i);return v instanceof JSONObject?(JSONObject)v:null;}
 public JSONArray put(Object v){list.add(v);return this;}
 public String toString(){StringBuilder b=new StringBuilder("[");for(int i=0;i<list.size();i++){if(i>0)b.append(',');b.append(JSONTokener.write(list.get(i)));}return b.append(']').toString();}
}
'''
TOKENER = r'''package org.json;
public class JSONTokener {
 final String s;int i;
 public JSONTokener(String s){this.s=s;}
 void ws(){while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;}
 public Object nextValue() throws JSONException{ws();if(i>=s.length())throw new JSONException("end");char c=s.charAt(i);
  if(c=='{'){i++;JSONObject o=new JSONObject();ws();if(s.charAt(i)=='}'){i++;return o;}while(true){ws();String k=(String)nextValue();ws();if(s.charAt(i++)!=':')throw new JSONException(":");o.put(k,nextValue());ws();char d=s.charAt(i++);if(d=='}')return o;if(d!=',')throw new JSONException(",");}}
  if(c=='['){i++;JSONArray a=new JSONArray();ws();if(s.charAt(i)==']'){i++;return a;}while(true){a.put(nextValue());ws();char d=s.charAt(i++);if(d==']')return a;if(d!=',')throw new JSONException(",");}}
  if(c=='"'){i++;StringBuilder b=new StringBuilder();while(true){char d=s.charAt(i++);if(d=='"')return b.toString();if(d=='\\'){char e=s.charAt(i++);if(e=='n')b.append('\n');else if(e=='t')b.append('\t');else if(e=='u'){b.append((char)Integer.parseInt(s.substring(i,i+4),16));i+=4;}else b.append(e);}else b.append(d);}}
  int st=i;while(i<s.length()&&",}] \n\r\t".indexOf(s.charAt(i))<0)i++;String w=s.substring(st,i);
  if(w.equals("true"))return true;if(w.equals("false"))return false;if(w.equals("null"))return JSONObject.NULL;
  try{if(w.contains(".")||w.contains("e")||w.contains("E"))return Double.parseDouble(w);long l=Long.parseLong(w);return l>=Integer.MIN_VALUE&&l<=Integer.MAX_VALUE?(Object)(int)l:(Object)l;}catch(NumberFormatException e){throw new JSONException(w);}}
 static String quote(String v){return "\""+v.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";}
 static String write(Object v){return v instanceof String?quote((String)v):String.valueOf(v);}
}
'''

stubs = {
'org/json/JSONObject.java': JSON, 'org/json/JSONArray.java': ARRAY, 'org/json/JSONTokener.java': TOKENER,
'org/json/JSONException.java': 'package org.json; public class JSONException extends Exception {public JSONException(String m){super(m);}}',
'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public final class NebulaText {public static String text(String ru,String en){return en;}}',
'Check.java': r'''import app.nebulagram.ui.*; import java.util.*;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 static void is(String got,String want,String m){check(want.equals(got),m+": ["+got+"] expected ["+want+"]");}
 public static void main(String[] a) throws Exception {
  long now=1700000000000L;
  NebulaPillFormat.Response rates=new NebulaPillFormat.Response(200,1467,now,"{\"result\":\"success\",\"rates\":{\"EUR\":0.91642,\"RUB\":90.5},\"list\":[{\"p\":1.5},{\"p\":2.25}],\"big\":1234567,\"name\":\"Nebula\",\"nothing\":null}");
  is(NebulaPillFormat.render("{rates.EUR|fixed:3} €",rates,now),"0.916 €","fixed");
  is(NebulaPillFormat.render("{list[1].p}|{list[-1].p|round}|{list.length}",rates,now),"2.25|2|2","arrays");
  is(NebulaPillFormat.render("{big|short} {name|upper} {name|max:3}",rates,now),"1.2M NEBULA Neb…","short, upper, max");
  is(NebulaPillFormat.render("{missing} {missing|default:n/a} {nothing|default:0}",rates,now),"— n/a 0","missing values");
  is(NebulaPillFormat.render("{code} · {latency} ms",rates,now),"200 · 1467 ms","built-ins");
  is(NebulaPillFormat.render("{{literal}} {rates}",rates,now),"{literal} …","escapes and objects");
  is(NebulaPillFormat.render("{rates.EUR|prefix:€|suffix:!}",rates,now),"€0.91642!","prefix and suffix");
  NebulaPillFormat.Response text=new NebulaPillFormat.Response(503,90,now,"Service down");
  is(NebulaPillFormat.render("{code} {body}",text,now),"503 Service down","non-JSON body");
  is(NebulaPillFormat.render("{rates.EUR}",null,now),"—","no reply yet");
  check(NebulaPillFormat.render("x".repeat(500),null,now).length()==120,"length limit");
  check(NebulaPillFormat.placeholders("{a.b|fixed:2} and {{x}} {c}").equals(Arrays.asList("a.b|fixed:2","c")),"placeholder list");
  // Countdown.
  Calendar cal=Calendar.getInstance();cal.setTimeInMillis(now);cal.add(Calendar.DAY_OF_YEAR,3);cal.add(Calendar.HOUR_OF_DAY,2);
  String day=String.format("%04d-%02d-%02dT%02d:%02d",cal.get(Calendar.YEAR),cal.get(Calendar.MONTH)+1,cal.get(Calendar.DAY_OF_MONTH),cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE));
  String left=NebulaPillFormat.render("{countdown:"+day+"}",null,now);
  check(left.startsWith("3 d 2 h")||left.startsWith("3 d 1 h"),"countdown: "+left);
  is(NebulaPillFormat.render("{countdown:2000-01-01}",null,now),"0 min","past date");
  is(NebulaPillFormat.render("{countdown:not a date}",null,now),"—","bad date");
  // Conditions.
  check(NebulaPillFormat.matches("rates.EUR","<","1",rates,now)&&!NebulaPillFormat.matches("rates.EUR",">","1",rates,now),"numeric compare");
  check(NebulaPillFormat.matches("code",">=","400",text,now)&&NebulaPillFormat.matches("body","contains","DOWN",text,now),"code and contains");
  check(NebulaPillFormat.matches("name","=","Nebula",rates,now)&&NebulaPillFormat.matches("missing","empty","",rates,now)&&!NebulaPillFormat.matches("name","??","x",rates,now),"text, empty and unknown ops");
  // Secrets and variables.
  Map<String,String> secrets=new HashMap<>();secrets.put("token","s3cr3t");
  is(NebulaPillFormat.secrets("Bearer {secret.token}",secrets,true),"Bearer s3cr3t","secret filled");
  is(NebulaPillFormat.secrets("Bearer {secret.token}",secrets,false),"Bearer ","secret withheld");
  check(NebulaPillFormat.secretsAllowed("https://api.example.com")&&NebulaPillFormat.secretsAllowed("http://192.168.1.5:8080/x")&&NebulaPillFormat.secretsAllowed("http://localhost/x")&&NebulaPillFormat.secretsAllowed("http://10.0.0.2")&&NebulaPillFormat.secretsAllowed("http://172.20.1.1"),"https and local http");
  check(!NebulaPillFormat.secretsAllowed("http://example.com")&&!NebulaPillFormat.secretsAllowed("http://172.40.1.1")&&!NebulaPillFormat.secretsAllowed("ftp://localhost")&&!NebulaPillFormat.secretsAllowed("http://8.8.8.8"),"no secrets over public http");
  is(NebulaPillFormat.variables("?l={lang}&c={currency}&{lat},{lon}","ru","RUB","55.75","37.62"),"?l=ru&c=RUB&55.75,37.62","variables");
  // Pills, conditions and templates.
  NebulaPill pill=NebulaPill.template(2,false);
  check(pill.request&&pill.url.startsWith("https://open.er-api.com")&&pill.shown(rates,now).equals("0.916 €"),"exchange template: "+pill.shown(rates,now));
  check(pill.shown(null,now).equals("…"),"waiting for the first reply");
  NebulaPill server=NebulaPill.template(4,false);
  check(server.shownColor(text,now)==NebulaPill.COLORS[1]&&server.shownColor(rates,now)==server.color,"red when down");
  NebulaPill.Condition own=new NebulaPill.Condition();own.field="code";own.op=">=";own.value="500";own.text="DOWN";server.conditions.add(0,own);
  is(server.shown(text,now),"DOWN","condition text");
  for(int i=0;i<NebulaPill.TEMPLATES;i++){NebulaPill t=NebulaPill.template(i,true);check(!t.name.isEmpty()&&!t.text.isEmpty()&&(!t.request||t.url.startsWith("https://")),"template "+i);}
  check(!NebulaPill.template(7,false).request&&!NebulaPill.template(7,false).shown(null,now).equals("—"),"countdown template works offline");
  // JSON round trip and import.
  String json=server.json().toString();
  NebulaPill back=NebulaPill.of(new org.json.JSONObject(json),true);
  check(back.id.equals(server.id)&&back.conditions.size()==2&&back.conditions.get(0).text.equals("DOWN")&&back.url.equals(server.url),"round trip");
  NebulaPill imported=NebulaPillShare.parse("look: "+json+" thanks");
  check(imported!=null&&!imported.id.equals(server.id)&&imported.text.equals(server.text),"import gets a new id");
  check(NebulaPillShare.parse("hello")==null&&NebulaPillShare.parse("{\"a\":1}")==null&&NebulaPillShare.parse(null)==null,"not a pill");
  NebulaPill odd=NebulaPill.of(new org.json.JSONObject("{\"text\":\"x\",\"interval\":7,\"method\":\"DELETE\",\"action\":9}"),false);
  check(odd.interval==60&&odd.method.equals("GET")&&odd.action==NebulaPill.ACTION_REFRESH,"imported values are clamped");
  is(NebulaPillShare.coordinate(" 55,7512345 ",90),"55.7512","coordinate");
  check(NebulaPillShare.coordinate("95",90).isEmpty()&&NebulaPillShare.coordinate("abc",180).isEmpty(),"bad coordinates");
  Map<String,String> parsed=NebulaPillShare.secrets("token = abc\nbad name=x\n=nothing\nkey_2=v=w");
  check(parsed.size()==2&&parsed.get("token").equals("abc")&&parsed.get("key_2").equals("v=w"),"secret lines: "+parsed);
  System.out.println("Pill Stack: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-pills-') as work:
    work = Path(work)
    for name, text in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8'))
    sources = [str(ui / f'{n}.java') for n in ['NebulaPillFormat', 'NebulaPill', 'NebulaPillShare']]
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), *sources], check=True)
    subprocess.run([java, '-Dstdout.encoding=UTF-8', '-cp', str(work), 'Check'], check=True)

pills = (ui / 'NebulaPills.java').read_text(encoding='utf-8')
for part in ['getBoolean(KEY, false)', 'KEY = "pills_enabled"', 'MAX_BYTES = 256 * 1024', 'TIMEOUT_MS = 10000', 'field.addAdditionalIcon(stack);',
             'NebulaPillFormat.secretsAllowed(url)', 'if (enabled()) {', 'field.editText.isFocused() || field.editText.length() > 0']:
    assert part in pills, part
# Nothing is requested while the pills are off: fetch is only reached from rebuild() inside "if (enabled())".
rebuild = pills[pills.index('void rebuild() {'):pills.index('private void tap(')]
assert rebuild.index('if (enabled()) {') < rebuild.index('fetch(pill, false, null);')
editor = (ui / 'NebulaPillEditorFragment.java').read_text(encoding='utf-8')
for part in ['t("Внешний вид", "Appearance")', 't("Источник данных", "Data source")', 't("Текст пилюли", "Pill text")', 't("Условия", "Conditions")', 't("По нажатию", "On tap")', 'NebulaPills.put(pill)']:
    assert part in editor, part
screen = (ui / 'NebulaPillsFragment.java').read_text(encoding='utf-8')
assert 't("Шаблоны", "Templates")' in screen and 'presentFragment(new NebulaPillEditorFragment(pill));' in screen  # imports open in the editor first
assert 'new NebulaPillsFragment()' in (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert '0379-pill-stack.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    dialogs = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
    block = dialogs[dialogs.index('if (initialDialogsType == DIALOGS_TYPE_DEFAULT) {\n            speedItem = menu.addItem(-47'):]
    assert block.index('app.nebulagram.ui.NebulaPills.attach(this, fragmentSearchField);') < block.index('fragmentSearchField.setCloseButtonOnClickListener(')
    field = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components/FragmentSearchField.java').read_text(encoding='utf-8')
    assert 'public void addAdditionalIcon(View icon) {' in field and 'public final EditTextBoldCursor editText;' in field
print('Pill Stack: wiring, screens and 0379 hook verified')
