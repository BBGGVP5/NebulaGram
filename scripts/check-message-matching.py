from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
source=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFilterMatcher.java'
fixture='''package app.nebulagram.ui;
public class MatcherCheck {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  check(NebulaFilterMatcher.matches("КУПИ сейчас",new String[]{"купи"},false,true),"case-insensitive Cyrillic");
  check(!NebulaFilterMatcher.matches("покупка",new String[]{"куп"},false,true),"whole words");
  check(NebulaFilterMatcher.matches("Реклама!",new String[]{"reklama"},true,true),"transliteration");
  check(NebulaFilterMatcher.matches("reklama!",new String[]{"реклама"},true,true),"transliteration both directions");
  check(!NebulaFilterMatcher.matches("anything",new String[]{".*"},false,false),"literal rather than regex");
  check(!NebulaFilterMatcher.matches("рекламный",new String[]{"реклама"},true,true),"transliterated boundary");
  check(NebulaFilterMatcher.matches("😀 sale!",new String[]{"sale"},false,true),"surrogate boundaries");
  check(!NebulaFilterMatcher.matches("sales",new String[]{"sale"},false,true),"suffix boundary");
  check(!NebulaFilterMatcher.matches("x_sale",new String[]{"sale"},false,true),"identifier boundary");
  check(!NebulaFilterMatcher.matches("text",new String[]{"","  "},true,true),"empty rules");
  check(NebulaFilterMatcher.matches("ＳＡＬＥ",new String[]{"sale"},false,true),"compatibility normalization");
  check(NebulaFilterMatcher.matches("Café",new String[]{"café"},false,true),"combining characters");
  System.out.println("Unicode, literal rules, transliteration and whole-word boundaries passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-filter-') as folder:
 p=Path(folder)/'MatcherCheck.java';p.write_text(fixture,encoding='utf8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(source),str(p)],check=True)
 subprocess.run(['java','-cp',folder,'app.nebulagram.ui.MatcherCheck'],check=True)
