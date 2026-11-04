"""Execute web translator request building, reply parsing and formatted entity re-mapping."""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
test=r'''package app.nebulagram.ui;
import java.util.*;
class NebulaText {static String text(String r,String e){return e;}}
public class WebTranslationCheck {
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 static String fake(String s){return s.replace("Привет","Hello").replace("жирный","bold").replace("мир","world").replace("ссылка","link");}
 public static void main(String[] args)throws Exception{
  var google=NebulaWebTranslation.request("google","Привет & мир","ru",null,false);
  check(google.method.equals("GET")&&google.body==null,"Google uses a keyless GET");
  check(google.url.startsWith("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=ru&dt=t"),"Google endpoint and target");
  check(google.url.endsWith("&q=%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82+%26+%D0%BC%D0%B8%D1%80"),"Google query is URL-encoded");
  check(NebulaWebTranslation.request("google","x","zh-hant",null,false).url.contains("tl=zh-TW"),"Google traditional Chinese");
  var microsoft=NebulaWebTranslation.request("microsoft","Say \"hi\"\n","zh","TOKEN",true);
  check(microsoft.method.equals("POST")&&microsoft.url.equals("https://api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-Hans&textType=html"),"Microsoft endpoint, mapped language and HTML mode");
  check(microsoft.body.equals("[{\"Text\":\"Say \\\"hi\\\"\\n\"}]"),"Microsoft JSON body is escaped: "+microsoft.body);
  check("Bearer TOKEN".equals(microsoft.headers.get("Authorization"))&&microsoft.contentType.startsWith("application/json"),"Microsoft bearer token");
  check(!NebulaWebTranslation.request("microsoft","x","no","T",false).url.contains("textType")&&NebulaWebTranslation.request("microsoft","x","no","T",false).url.contains("to=nb"),"Microsoft plain mode and Norwegian code");
  check(NebulaWebTranslation.microsoftAuth().url.equals("https://edge.microsoft.com/translate/auth"),"Microsoft token endpoint");
  var yandex=NebulaWebTranslation.request("yandex","a b","pt-BR",null,true);
  check(yandex.url.matches("https://translate\\.yandex\\.net/api/v1/tr\\.json/translate\\?id=[0-9a-f]{32}-0-0&srv=android"),"Yandex endpoint: "+yandex.url);
  check(yandex.body.equals("lang=pt&format=html&text=a+b")&&yandex.contentType.startsWith("application/x-www-form-urlencoded"),"Yandex form body: "+yandex.body);
  try{NebulaWebTranslation.request("deepl","x","en",null,false);throw new AssertionError("unknown provider accepted");}catch(IllegalArgumentException expected){}
  check(NebulaWebTranslation.known("google")&&!NebulaWebTranslation.known("")&&!NebulaWebTranslation.known(null),"provider allowlist");

  check(NebulaWebTranslation.parse("google","[[[\"Hello \",\"Привет \",null,null,10],[\"world\",\"мир\",null,null,3]],null,\"ru\"]").equals("Hello world"),"Google sentences joined");
  check(NebulaWebTranslation.parse("microsoft","[{\"detectedLanguage\":{\"language\":\"ru\",\"score\":1.0},\"translations\":[{\"text\":\"Hi \\u00e9\\n\",\"to\":\"en\"}]}]").equals("Hi \u00e9\n"),"Microsoft reply and escapes");
  check(NebulaWebTranslation.parse("yandex","{\"code\":200,\"lang\":\"ru-en\",\"text\":[\"Hello\"]}").equals("Hello"),"Yandex reply");
  for(String[] bad:new String[][]{{"yandex","{\"code\":413,\"text\":[]}"},{"microsoft","{\"error\":{\"code\":401000,\"message\":\"no\"}}"},{"google","[[],null]"},{"google","<html>"},{"yandex","{\"code\":200,\"text\":[\"x\"]"}}){
   try{NebulaWebTranslation.parse(bad[0],bad[1]);throw new AssertionError("bad reply accepted: "+bad[1]);}catch(IllegalStateException expected){}
  }

  String payload="[[[N0]]]Привет [[[N1]]]жирный <b>[[[N2]]] мир\n[[[N3]]]";
  String html=NebulaWebTranslation.toMarkup(payload);
  check(html.equals("<span id=\"n0\">Привет </span><span id=\"n1\">жирный &lt;b&gt;</span><span id=\"n2\"> мир<br></span>"),"delimiters become spans: "+html);
  check(NebulaWebTranslation.fromMarkup(payload,fake(html)).equals("[[[N0]]]Hello [[[N1]]]bold <b>[[[N2]]] world\n[[[N3]]]"),"spans return to delimiters");
  check(NebulaWebTranslation.fromMarkup(payload,"<span id=\"n1\">a</span><span id=\"n0\">b</span><span id=\"n2\">c</span>")==null,"reordered spans rejected");
  check(NebulaWebTranslation.fromMarkup(payload,"<span id=\"n0\">a</span><span id=\"n1\">b</span>")==null,"dropped span rejected");
  check(NebulaWebTranslation.fromMarkup(payload,"<span id=n0>a</span> <SPAN id=\"n1\"><b>b</b></SPAN><span id=\"n2\">c&amp;d</span>").equals("[[[N0]]]a[[[N1]]]b[[[N2]]]c&d[[[N3]]]"),"service markup normalised");

  // Entities: "Привет" bold, "жирный" italic inside a link, custom emoji locked.
  String source="Привет 👍 жирный ссылка мир";
  int emoji=source.indexOf("👍"),bold=source.indexOf("жирный"),link=source.indexOf("ссылка");
  var ranges=List.of(new NebulaTranslationFormat.Range(0,6,false),new NebulaTranslationFormat.Range(emoji,emoji+2,true),
   new NebulaTranslationFormat.Range(bold,link+6,false),new NebulaTranslationFormat.Range(bold,bold+6,false));
  int[] calls={0,0};
  var markup=new NebulaTranslationFormat.Translator(){
   public String translate(String text){calls[0]++;return fake(text);}
   public String translateStructured(String text){calls[1]++;String sent=NebulaWebTranslation.toMarkup(text);check(sent!=null,"structured payload converts");return NebulaWebTranslation.fromMarkup(text,fake(sent).replace("👍","(thumb)"));}
  };
  var result=NebulaTranslationFormat.translate(source,ranges,markup,true,3500);
  check(calls[1]==1&&calls[0]==0,"HTML-capable service: one request keeps sentence context");
  check(result.text.equals("Hello 👍 bold link world"),"translated text: "+result.text);
  check(result.text.substring(result.offset(0),result.offset(6)).equals("Hello"),"bold remapped");
  check(result.text.substring(result.offset(emoji),result.offset(emoji+2)).equals("👍"),"custom emoji kept even if the service rewrote it");
  check(result.text.substring(result.offset(bold),result.offset(link+6)).equals("bold link"),"link remapped");
  check(result.text.substring(result.offset(bold),result.offset(bold+6)).equals("bold"),"nested italic remapped");
  calls[0]=calls[1]=0;
  var plain=new NebulaTranslationFormat.Translator(){public String translate(String text){calls[0]++;check(!text.contains("[[[N")&&!text.contains("<span"),"Google sees no markup");return fake(text);}};
  result=NebulaTranslationFormat.translate(source,ranges,plain,false,1000);
  check(result.text.equals("Hello 👍 bold link world")&&calls[0]==4,"segment mode translates each run: "+calls[0]);
  check(result.text.substring(result.offset(bold),result.offset(link+6)).equals("bold link"),"segment mode remaps entities");
  calls[0]=0;
  var broken=new NebulaTranslationFormat.Translator(){
   public String translate(String text){calls[0]++;return fake(text);}
   public String translateStructured(String text){return NebulaWebTranslation.fromMarkup(text,"<span id=\"n1\">x</span>");}
  };
  result=NebulaTranslationFormat.translate(source,ranges,broken,true,3500);
  check(result.text.equals("Hello 👍 bold link world")&&calls[0]>1,"mangled HTML falls back to runs");
  check(NebulaWebTranslation.chunkLimit("google")==1000&&!NebulaWebTranslation.markup("google")&&NebulaWebTranslation.markup("yandex"),"per-service limits");
  System.out.println("Web translators: Google/Microsoft/Yandex requests, replies, HTML spans, entity re-mapping and fallbacks passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-web-translation-') as folder:
 p=Path(folder);(p/'WebTranslationCheck.java').write_text(test,encoding='utf-8')
 javac=shutil.which('javac') or str(java_home/'bin/javac.exe');java=shutil.which('java') or str(java_home/'bin/java.exe')
 subprocess.run([javac,'-encoding','UTF-8','-d',folder,*[str(ui/n) for n in ['NebulaWebTranslation.java','NebulaTranslationFormat.java','NebulaTranslationText.java']],str(p/'WebTranslationCheck.java')],check=True)
 subprocess.run([java,'-cp',folder,'app.nebulagram.ui.WebTranslationCheck'],check=True)
