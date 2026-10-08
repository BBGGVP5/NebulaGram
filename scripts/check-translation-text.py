"""Exercise complete source recombination and actual translation transport selection."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
client=(ui/'NebulaTranslationClient.java').read_text(encoding='utf-8').replace('import android.content.SharedPreferences;','')
stubs=r'''package app.nebulagram.ui;
import java.util.*;
class SharedPreferences {int provider=4;int getInt(String k,int d){return provider;}String getString(String k,String d){return "configured";}}
class NebulaTranslationSettings {static boolean fast;static SharedPreferences p=new SharedPreferences();static SharedPreferences global(){return p;}static boolean local(){return fast;}}
class NebulaAiSecrets {static int reads;static String read(int p){reads++;return "key";}}
class NebulaText {static String text(String r,String e){return e;}}
class NebulaAiClient {static final int NANO=4;static int calls;void cancel(){}String generate(int p,String e,String k,String m,String prompt,String input){calls++;return input;}}
class NebulaNanoAi {static int calls,max;static boolean priority;static String generate(String p,String input,java.util.function.BooleanSupplier cancelled,boolean interactive){calls++;max=Math.max(max,input.length());priority=interactive;return input;}static String responseErrorText(Exception e){return "error";}}
class NebulaLocalTranslation {static int calls;static String translate(String text,String lang,java.util.function.BooleanSupplier cancel,java.util.function.Consumer<String> phase){calls++;return text;}static String errorText(Exception e){return "local";}}
public class TranslationTextCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  String longText=("  Привет 🌍, this is a paragraph.\n\nhttps://example.com/item @mention\n").repeat(180);
  int cases=0;
  for(int limit:new int[]{4,7,32,900,3500})for(String text:new String[]{""," \n\t ","hi",longText,"😀".repeat(800),"X".repeat(12000)}){
   List<String> parts=NebulaTranslationText.parts(text,limit);StringBuilder joined=new StringBuilder();
   for(String chunk:parts){check(chunk.length()<=limit,"bounded prompts");check(!Character.isLowSurrogate(chunk.charAt(0)),"split low surrogate");check(!Character.isHighSurrogate(chunk.charAt(chunk.length()-1)),"split high surrogate");joined.append(chunk);}
   check(text.equals(joined.toString()),"every input unit survives split");cases++;
  }
  NebulaTranslationClient transport=new NebulaTranslationClient();String answer=transport.translate(longText,"en",true,p->{});
  check(longText.equals(answer),"full text and whitespace survive transport");check(NebulaNanoAi.max<=900&&NebulaNanoAi.calls>10&&NebulaNanoAi.priority,"Nano chunk budget and interactive priority");
  check(NebulaAiSecrets.reads==0&&NebulaAiClient.calls==0,"Nano never requests a cloud key");
  NebulaTranslationSettings.fast=true;answer=new NebulaTranslationClient().translate(longText,"en",false,p->{});
  check(longText.equals(answer)&&NebulaLocalTranslation.calls>1&&NebulaAiSecrets.reads==0,"explicit local engine has no cloud fallback");
  NebulaTranslationSettings.fast=false;NebulaTranslationSettings.p.provider=0;answer=new NebulaTranslationClient().translate(longText,"en",false,p->{});
  check(longText.equals(answer)&&NebulaAiClient.calls>1&&NebulaAiSecrets.reads==1,"cloud remains selected and bounded");
  transport.cancel();int before=NebulaAiClient.calls;
  try{transport.translate("secret","en",true,p->{});throw new AssertionError("cancellation expected");}catch(java.io.InterruptedIOException expected){}
  check(before==NebulaAiClient.calls,"cancelled request never reaches provider");
  System.out.println(cases+" Unicode/whitespace chunk cases, full transport, selected engine and cancellation passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-translation-text-') as folder:
 p=Path(folder);(p/'NebulaTranslationClient.java').write_text(client,encoding='utf-8');(p/'TranslationTextCheck.java').write_text(stubs,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(ui/'NebulaTranslationText.java'),str(ui/'NebulaTranslationFormat.java'),str(p/'NebulaTranslationClient.java'),str(p/'TranslationTextCheck.java')],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.TranslationTextCheck'],check=True)
