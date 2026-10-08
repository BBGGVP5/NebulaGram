"""Execute production UTF-16 translation mapping and malformed-response recovery."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
test=r'''package app.nebulagram.ui;
import java.util.*;
public class TranslationFormatCheck {
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 static String translate(String input){return input.replace("Привет","Hello").replace("друг","friend").replace("Ссылка","Link");}
 public static void main(String[] args)throws Exception{
  String source="  Привет друг 👩🏽‍💻\nСсылка https://example.com/x @name 🇷🇺 1️⃣  ";
  int friend=source.indexOf("друг"),emoji=source.indexOf("👩"),label=source.indexOf("Ссылка"),url=source.indexOf("https");
  var spans=List.of(new NebulaTranslationFormat.Range(2,emoji-1,false),new NebulaTranslationFormat.Range(friend,emoji-1,false),
      new NebulaTranslationFormat.Range(friend,label+6,false),new NebulaTranslationFormat.Range(label,label+6,false),new NebulaTranslationFormat.Range(emoji,emoji+7,true));
  int[] calls={0};var result=NebulaTranslationFormat.translate(source,spans,s->{calls[0]++;return translate(s).replace("👩🏽‍💻","REMOVED");},true,3500);
  check(calls[0]==1,"preserve sentence context in one validated request");
  check(result.text.equals(translate(source)),"emoji/URL/mention/newline/whitespace preserved despite model editing protected text");
  check(result.text.substring(result.offset(friend),result.offset(emoji-1)).equals("friend"),"nested/overlapping style offsets");
  check(result.text.substring(result.offset(label),result.offset(label+6)).equals("Link"),"translated link label range");
  check(result.text.substring(result.offset(emoji),result.offset(emoji+7)).equals("👩🏽‍💻"),"custom emoji range survives UTF-16 change");
  for(int kind=0;kind<5;kind++){
   final int failure=kind;calls[0]=0;
   result=NebulaTranslationFormat.translate(source,spans,s->{calls[0]++;if(s.startsWith("[[[N")){
     if(failure==0)return translate(s).replace("[[[N0]]]","");
     if(failure==1)return translate(s)+"[[[N0]]]";
     if(failure==2)return translate(s).replace("[[[N0]]]","[[[N999]]]");
     if(failure==3)return "preface "+translate(s);
     return null;
    }return translate(s);},true,3500);
   check(calls[0]>1&&result.text.equals(translate(source)),"malformed markers safely fall back: "+kind);
  }
  calls[0]=0;result=NebulaTranslationFormat.translate(source,spans,s->{calls[0]++;check(!s.contains("[[[N"),"ordinary engine sees no markup");return translate(s);},false,3500);
  check(result.text.equals(translate(source)),"on-device translation preserves formatting");
  result=NebulaTranslationFormat.translate("[[[N0]]] Привет",List.of(new NebulaTranslationFormat.Range(9,15,false)),TranslationFormatCheck::translate,true,3500);
  check(result.text.equals("[[[N0]]] Hello"),"input markers cannot collide with protocol");
  int[] modes={0,0};
  var routed=new NebulaTranslationFormat.Translator(){
   public String translate(String text){modes[0]++;return TranslationFormatCheck.translate(text);}
   public String translateStructured(String text){modes[1]++;return "malformed response";}
  };
  result=NebulaTranslationFormat.translate("Привет",List.of(),routed,true,3500);
  check(result.text.equals("Hello")&&modes[0]==1&&modes[1]==0,"plain draft receives only plain instructions");
  modes[0]=modes[1]=0;
  result=NebulaTranslationFormat.translate(source,spans,routed,true,3500);
  check(result.text.equals(translate(source))&&modes[1]==1&&modes[0]>1,"malformed structured response falls back to plain instructions");
  for(String token:List.of("[[[N...]]]","[[[N0]]]","[[[NN3]]]")){
   try{NebulaTranslationFormat.translate("Привет",List.of(),s->token+" Hello",true,3500);throw new AssertionError("invented marker accepted");}
   catch(IllegalStateException expected){check(expected.getMessage().equals("INVALID_TRANSLATION_FORMAT"),"leaked marker rejected");}
   check(NebulaTranslationFormat.checkedPlain(token+" Привет",token+" Hello").equals(token+" Hello"),"literal user marker retained");
   try{NebulaTranslationFormat.checkedPlain(token+" Привет",token+token+" Hello");throw new AssertionError("duplicated marker accepted");}
   catch(IllegalStateException expected){check(expected.getMessage().equals("INVALID_TRANSLATION_FORMAT"),"duplicated marker rejected");}
  }
  String protectedOnly="👩🏽‍💻 🇷🇺 1️⃣\r\nhttps://example.com @name";
  result=NebulaTranslationFormat.translate(protectedOnly,List.of(),s->{throw new AssertionError("protected-only text must not reach engine");},true,3500);
  check(result.text.equals(protectedOnly),"all-protected text needs no request");
  check(!NebulaTranslationFormat.valid("😀",1,2)&&!NebulaTranslationFormat.valid("😀",0,1),"no split surrogate range");
  calls[0]=0;
  try{NebulaTranslationFormat.translate(source,spans,s->{calls[0]++;throw new java.io.InterruptedIOException();},true,3500);throw new AssertionError("expected cancellation");}
  catch(java.io.InterruptedIOException expected){check(calls[0]==1,"cancellation never retries pieces");}
  String longText="Привет ".repeat(1000);calls[0]=0;
  result=NebulaTranslationFormat.translate(longText,List.of(new NebulaTranslationFormat.Range(0,7,false)),s->{calls[0]++;check(!s.contains("[[[N"),"bounded structured request");return translate(s);},true,900);
  check(result.text.equals(translate(longText)),"large formatted text falls back without losing runs");
  System.out.println("Formatted translation: nested/overlapping ranges, UTF-16, emoji, links, line breaks, malformed markers, local engine, budget and cancellation passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-rich-translation-') as folder:
 p=Path(folder);(p/'TranslationFormatCheck.java').write_text(test,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(ui/'NebulaTranslationFormat.java'),str(ui/'NebulaTranslationText.java'),str(p/'TranslationFormatCheck.java')],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.TranslationFormatCheck'],check=True)
