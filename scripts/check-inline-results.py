"""Execute the inline-results engine (calculator, currencies, units, time zones) and the input-field hint on real examples."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

stubs = {
'android/text/TextWatcher.java': 'package android.text; public interface TextWatcher {void beforeTextChanged(CharSequence s,int a,int b,int c);void onTextChanged(CharSequence s,int a,int b,int c);void afterTextChanged(Editable s);}',
'android/text/Editable.java': 'package android.text; public interface Editable extends CharSequence {Editable replace(int a,int b,CharSequence t);}',
'android/text/SpannableStringBuilder.java': '''package android.text; import java.util.*;
public class SpannableStringBuilder implements Editable {
 private final StringBuilder b; public List<TextWatcher> watchers = new ArrayList<>();
 public SpannableStringBuilder(){b=new StringBuilder();} public SpannableStringBuilder(CharSequence s,int from,int to){b=new StringBuilder(s.subSequence(from,to));}
 public Editable replace(int from,int to,CharSequence t){List<TextWatcher> w=new ArrayList<>(watchers);for(TextWatcher x:w)x.beforeTextChanged(this,from,to-from,t.length());
  b.replace(from,to,t.toString());for(TextWatcher x:w)x.onTextChanged(this,from,to-from,t.length());for(TextWatcher x:w)x.afterTextChanged(this);return this;}
 public int length(){return b.length();} public char charAt(int i){return b.charAt(i);} public CharSequence subSequence(int s,int e){return b.subSequence(s,e);} public String toString(){return b.toString();}}''',
'android/text/Layout.java': 'package android.text; public class Layout {public int getLineCount(){return 0;}public float getLineRight(int l){return 0;}public int getLineBaseline(int l){return 0;}}',
'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {public void drawText(String s,float x,float y,Paint p){}}',
'android/graphics/Paint.java': 'package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1;public Paint(){}public Paint(int f){}public void set(Paint p){}public void setColor(int c){}public float measureText(String s){return 0;}}',
'android/widget/EditText.java': '''package android.widget; import android.text.*;
public class EditText {public final SpannableStringBuilder text=new SpannableStringBuilder();public int selection;
 public void addTextChangedListener(TextWatcher w){text.watchers.add(w);} public void removeTextChangedListener(TextWatcher w){text.watchers.remove(w);}
 public SpannableStringBuilder getText(){return text;} public void setSelection(int i){selection=i;} public Layout getLayout(){return null;} public void invalidate(){}
 public android.graphics.Paint getPaint(){return new android.graphics.Paint();} public int getCurrentHintTextColor(){return 0;} public int getCompoundPaddingLeft(){return 0;}
 public int getCompoundPaddingRight(){return 0;} public int getScrollX(){return 0;} public int getScrollY(){return 0;} public int getExtendedPaddingTop(){return 0;}
 public int getWidth(){return 0;} public int getHeight(){return 0;} public int getPaddingBottom(){return 0;}}''',
# The hint reads its switches here; the Android-backed class is replaced by a controllable one.
'app/nebulagram/ui/NebulaInlineSettings.java': '''package app.nebulagram.ui; public final class NebulaInlineSettings {public static boolean on=true;public static NebulaInlineResults.Options options=new NebulaInlineResults.Options();
 public static boolean enabled(){return on;} public static NebulaInlineResults.Options options(){options.ratesWanted=false;return options;} public static void load(Runnable done){}}''',
'Check.java': r'''import app.nebulagram.ui.*; import java.util.*;
public class Check {
 static int cases;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);cases++;}
 static NebulaInlineResults.Options options(){NebulaInlineResults.Options o=new NebulaInlineResults.Options();
  o.rates=new HashMap<>();o.rates.put("USD",1.0);o.rates.put("EUR",0.9);o.rates.put("RUB",90.0);o.rates.put("KZT",500.0);o.rates.put("UAH",40.0);o.rates.put("SEK",10.5);
  o.defaultCurrency="RUB";o.local=TimeZone.getTimeZone("Europe/Moscow");
  Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));c.clear();c.set(2026,Calendar.NOVEMBER,3,10,0,0);o.now=c.getTimeInMillis();return o;}
 static void is(NebulaInlineResults.Options o,String input,String expected){String got=NebulaInlineResults.compute(input,o);check(got.equals(expected),input+" => ["+got+"], expected ["+expected+"]");}
 static void is(String input,String expected){is(options(),input,expected);}
 public static void main(String[] args){
  // Calculator: the legacy unmarked arithmetic, then the "=" forms.
  is("2+2","4");is("2 + 3 * 4","14");is("(2+3)*4","20");is("1 / 4","0.25");is("200 * 15%","30");is("1,5 × 2","3");is("1-2=","-1");
  is("2^10 =","1024");is("-2^2 =","-4");is("2^-1 =","0.5");is("(1+2)*3^2 =","27");is("sqrt(144) + 2 =","14");is("sin(30) =","0.5");is("cos(90) =","0");is("tan(45) =","1");
  is("round(3.14159, 2) =","3.14");is("round(pi, 4) =","3.1416");is("min(4, 2, 8) =","2");is("max(4,9,2) =","9");is("abs(-5) =","5");is("log(1000) =","3");is("ln(e) =","1");
  is("pi =","3.14159265359");is("2pi =","6.28318530718");is("3(4+1) =","15");is("200 + 15% =","230");is("200 - 10% =","180");is("15% of 80 =","12");is("50% =","0.5");
  is("5k * 3 =","15000");is("2m / 4 =","500000");is("1.5к + 500 =","2000");is("hex(255) =","0xFF");is("bin(10) =","0b1010");is("oct(8) =","0o10");is("hex(-16) =","-0x10");
  is("dec(ff, 16) =","255");is("dec(101, 2) =","5");is("dec(0x1f) =","31");is("0xff + 1 =","256");is("0b101 * 2 =","10");is("10 mod 3 =","1");is("2^100 =","1.26765060023e30");
  NebulaInlineResults.Options radians=options();radians.degrees=false;is(radians,"sin(pi/2) =","1");is(radians,"atan(1) * 4 =","3.14159265359");
  NebulaInlineResults.Options grouped=options();grouped.group=true;is(grouped,"1000000 * 3 =","3"+NebulaMath.GROUP+"000"+NebulaMath.GROUP+"000");is(grouped,"1234 + 1 =","1235");
  is(grouped,"1"+NebulaMath.GROUP+"000"+NebulaMath.GROUP+"000 + 1 =","1"+NebulaMath.GROUP+"000"+NebulaMath.GROUP+"001");
  for(String quiet:new String[]{"12345","+79999999999","2026-10-05","hello 2+3","hello 2+3 =","1 / 0","1 / 0 =","2 +","(2+3","(()","eval(1)","eval(1) =","http://example/2","2 ** 3","3 =","=","sqrt(-1) =","ln(0) =","tan(90) =","hex(1.5) =","dec(2, 2) =","x =",
    "(".repeat(20)+"2+3"+")".repeat(20)+" =","1".repeat(130)+"*2"})is(quiet,"");
  NebulaInlineResults.Options off=options();off.calculator=false;is(off,"2+2 =","");
  // Currencies: symbol, code, name and Russian forms; without a target the default currency.
  is("100€ =","10000 ₽");is("$100 =","9000 ₽");is("100 dollars to euros =","90 €");is("100 usd in eur =","90 €");is("5k ₽ to $ =","55.56 $");
  is("100 рублей в доллары =","1.11 $");is("1000 рублей в тенге =","5555.56 ₸");is("$250 in uah =","10000 ₴");is("10 sek to usd =","0.952 $");is("100 ₽ =","1.11 $");
  is("2*50 eur to usd =","111.11 $");is("100 eur to eur =","");is("100 xyz to usd =","");
  NebulaInlineResults.Options none=options();none.rates=null;is(none,"100€ =","");check(none.ratesWanted,"missing rates must be requested");
  NebulaInlineResults.Options noCurrency=options();noCurrency.currency=false;is(noCurrency,"100€ =","");
  check("RUB".equals(NebulaInlineCurrency.byPhone("79161234567"))&&"KZT".equals(NebulaInlineCurrency.byPhone("+7 701 123 45 67"))&&"UAH".equals(NebulaInlineCurrency.byPhone("380501234567"))
    &&"USD".equals(NebulaInlineCurrency.byPhone("12025550100"))&&"EUR".equals(NebulaInlineCurrency.byPhone("4915112345678"))&&NebulaInlineCurrency.byPhone("")==null,"currency by phone prefix");
  // Units: explicit targets, "in" as both inch and connector, and the other system without a target.
  is("5 km =","3.10686 mi");is("5 km to mi =","3.10686 mi");is("5 in to cm =","12.7 cm");is("5 in in cm =","12.7 cm");is("6 ft to cm =","182.88 cm");is("70 kg =","154.324 lb");
  is("70 kg in lb =","154.324 lb");is("5 pounds to kg =","2.26796 kg");is("100°F =","37.7778°C");is("100 F to C =","37.7778°C");is("0 K to c =","-273.15°C");is("-300 C to K =","");
  is("120 km/h to mph =","74.5645 mph");is("90 min =","1 h 30 min");is("3 h to min =","180 min");is("1 h =","");is("2m to ft =","6.56168 ft");is("2m =","2000000");
  is("5 км в мили =","3.10686 mi");is("3 kg to km =","");
  NebulaInlineResults.Options ru=options();ru.russian=true;is(ru,"5 km to m =","5000 м");is(ru,"150 мин =","2 ч 30 мин");
  NebulaInlineResults.Options imperial=options();imperial.imperial=true;is(imperial,"300 K =","80.33°F");is(imperial,"10 kn =","11.5078 mph");
  NebulaInlineResults.Options noUnits=options();noUnits.units=false;is(noUnits,"5 km =","");
  // Time zones at 2026-11-03 10:00 UTC, phone in Moscow.
  is("15:00 msk in tokyo =","21:00");is("3pm est to msk =","23:00");is("23:00 msk to utc+3:30 =","23:30");is("now in london =","10:00");is("токио =","19:00");
  is("20:00 в нью-йорке в мск =","04:00 (+1)");is("01:00 msk in new york =","17:00 (−1)");is("15:00 in tokyo =","21:00");is("12am utc to msk =","03:00");is("15:00 msk =","");
  is("15:00 utc =","18:00");is("25:00 msk in tokyo =","");is("15:00 mars in tokyo =","");
  NebulaInlineResults.Options noZones=options();noZones.timezones=false;is(noZones,"токио =","");
  // Only the last line is read; very long drafts stay cheap.
  is("Итого:\n2+2 =","4");is("2+2 =\nпривет","");is("x".repeat(5000)+"\n7*6 =","42");is("7*6 ="+"x".repeat(200),"");

  // The hint in the input field: Space inserts, Backspace right after undoes.
  NebulaInlineSettings.options=options();
  android.widget.EditText field=new android.widget.EditText();NebulaMathHint hint=new NebulaMathHint();
  field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){hint.update(s);}public void afterTextChanged(android.text.Editable s){}});
  hint.draw(field,new android.graphics.Canvas());
  type(field,"2+2=");check(result(hint).equals("4"),"hint shows the result: "+result(hint));
  type(field," ");check(text(field).equals("2+2=4 ")&&field.selection==6,"space inserts: "+text(field));
  back(field);check(text(field).equals("2+2="),"backspace undoes: "+text(field));check(result(hint).equals("4"),"hint returns after undo");
  type(field," x");check(text(field).equals("2+2=4 x"),"insert again: "+text(field));back(field);check(text(field).equals("2+2=4 "),"backspace after typing is ordinary: "+text(field));
  clear(field);type(field,"2 + 2 =");type(field," ");check(text(field).equals("2 + 2 = 4 "),"spacing follows the expression: "+text(field));
  clear(field);type(field,"100 dollars to euros =");check(result(hint).equals("90 €"),"currency hint");type(field," ");check(text(field).equals("100 dollars to euros = 90 € "),text(field));
  clear(field);type(field,"hello =");type(field," ");check(text(field).equals("hello = "),"no result, ordinary space");
  clear(field);type(field,"2+2");check(result(hint).equals("4"),"unmarked hint");type(field," ");check(text(field).equals("2+2 "),"space without = stays a space");
  clear(field);type(field,"2+2=");field.getText().replace(2,3,"3");check(text(field).equals("2+3="),"mid-text edit");type(field," ");check(text(field).equals("2+3=5 "),"edit then insert");
  clear(field);NebulaInlineSettings.on=false;type(field,"2+2=");check(result(hint).isEmpty(),"master switch hides");type(field," ");check(text(field).equals("2+2= "),"master switch stops insertion");NebulaInlineSettings.on=true;
  hint.draw(field,new android.graphics.Canvas());check(field.getText().watchers.size()==2,"the hint attaches its watcher once");
  System.out.println("Inline results: "+cases+" calculator, currency, unit, time zone and input-field cases passed");
 }
 static void type(android.widget.EditText f,String s){for(char c:s.toCharArray())f.getText().replace(f.getText().length(),f.getText().length(),String.valueOf(c));}
 static void back(android.widget.EditText f){int n=f.getText().length();f.getText().replace(n-1,n,"");}
 static void clear(android.widget.EditText f){f.getText().replace(0,f.getText().length(),"");}
 static String text(android.widget.EditText f){return f.getText().toString();}
 static String result(NebulaMathHint h){try{java.lang.reflect.Field r=NebulaMathHint.class.getDeclaredField("result");r.setAccessible(true);return (String)r.get(h);}catch(Exception e){throw new RuntimeException(e);}}
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-inline-results-') as work:
    work = Path(work)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(source.encode('utf-8'))
    sources = [str(ui / (name + '.java')) for name in ['NebulaMath', 'NebulaInlineResults', 'NebulaInlineCurrency', 'NebulaInlineUnits', 'NebulaInlineTime', 'NebulaMathHint']]
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), *sources], check=True)
    subprocess.run([java, '-Dstdout.encoding=UTF-8', '-cp', str(work), 'Check'], check=True)

# The Android-backed settings: literal keys, rate source and cache lifetime.
settings = (ui / 'NebulaInlineSettings.java').read_text(encoding='utf-8')
assert 'https://open.er-api.com/v6/latest/USD' in settings and 'DAY = 24L * 60 * 60 * 1000' in settings and '"success".equals(json.optString("result"))' in settings
assert 'NebulaInlineCurrency.byPhone(user.phone)' in settings and 'NebulaChatPreferences.math()' in settings
screen = (ui / 'NebulaInlineResultsFragment.java').read_text(encoding='utf-8')
for key in ['inline_calculator', 'inline_currency', 'inline_units', 'inline_timezones', 'inline_group_digits', '"inline_math"']:
    assert key in screen, key
assert 'new NebulaInlineResultsFragment()' in (ui / 'NebulaFeatureControls.java').read_text(encoding='utf-8')
assert 'section == -62) f.presentFragment(new NebulaInlineResultsFragment())' in (ui / 'NebulaSettingsSearch.java').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    enter = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
    assert 'nebulaMathHint.update(charSequence)' in enter and 'nebulaMathHint.draw(this, canvas)' in enter
print('Inline results settings, screen routes and input-field hooks verified')
