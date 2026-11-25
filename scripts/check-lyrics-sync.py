"""Word sync, duets, background vocals, transliteration and lyrics sources: run the pure parts, then check the player wiring."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

stubs = {
'app/nebulagram/ui/NebulaText.java': 'package app.nebulagram.ui; public final class NebulaText {public static String text(String ru,String en){return en;}}',
'Check.java': r'''import app.nebulagram.ui.*; import java.util.*;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  String lrc="[ti:Song]\n[00:10.00]<00:10.00>Hello <00:10.50>bright <00:11.20>world<00:12.00>\n[00:12.00]v2: <00:12.00>Second <00:12.40>voice\n[00:14.00]Plain line (ooh, yeah)\n[00:16.00]D: Together\n";
  // Classic parse is unchanged: tags and markers stay as before.
  NebulaLrc classic=NebulaLrc.parse(lrc);
  check(classic.synced&&classic.lines.size()==4,"classic lines");
  check(classic.lines.get(0).text.equals("Hello bright world")&&!classic.lines.get(0).hasWords(),"classic strips word tags, keeps no words");
  check(classic.lines.get(1).text.equals("v2: Second voice")&&classic.lines.get(1).voice==0,"classic keeps duet markers as text");
  // Rich parse.
  NebulaLrc rich=NebulaLrc.parse(lrc,true);
  NebulaLrc.Line first=rich.lines.get(0);
  check(first.text.equals("Hello bright world"),"rich text: "+first.text);
  check(Arrays.equals(first.wordTimes,new long[]{10000,10500,11200})&&Arrays.equals(first.wordOffsets,new int[]{0,6,13}),"word times and offsets: "+Arrays.toString(first.wordTimes)+Arrays.toString(first.wordOffsets));
  NebulaLrc.Line second=rich.lines.get(1);
  check(second.voice==1&&second.text.equals("Second voice")&&second.wordOffsets[1]==7,"second voice: "+second.text);
  check(rich.lines.get(2).voice==0&&!rich.lines.get(2).hasWords(),"plain line in a rich file");
  check(rich.lines.get(3).voice==2&&rich.lines.get(3).text.equals("Together"),"both voices");
  // [offset] shifts word times too.
  NebulaLrc shifted=NebulaLrc.parse("[offset:+500]\n[00:10.00]<00:10.00>a <00:11.00>b",true);
  check(shifted.lines.get(0).time==9500&&shifted.lines.get(0).wordTimes[1]==10500,"offset applies to words");
  // Sung characters.
  check(NebulaLrc.sung(first,9000,12000)==-1f,"before the first word");
  check(NebulaLrc.sung(first,10000,12000)==0f,"first word starts");
  check(Math.abs(NebulaLrc.sung(first,10250,12000)-3f)<1e-4,"half of 'Hello ' at 10.25 s: "+NebulaLrc.sung(first,10250,12000));
  check(NebulaLrc.sung(first,10500,12000)==6f,"second word starts");
  check(NebulaLrc.sung(first,20000,12000)==first.text.length(),"whole line after its end");
  check(NebulaLrc.sung(rich.lines.get(2),15000,16000)==-1f,"no words, no fill");
  // Background vocals.
  check(Arrays.equals(NebulaLrc.backgroundRanges("Plain line (ooh, yeah)"),new int[]{11,22}),"one range");
  check(Arrays.equals(NebulaLrc.backgroundRanges("(a) b (c (d))"),new int[]{0,3,6,13}),"nested ranges");
  check(NebulaLrc.backgroundRanges("no brackets) here(").length==0,"unbalanced brackets ignored");
  // Transliteration need.
  check(NebulaLyricsText.needsReading("Привет")&&NebulaLyricsText.needsReading("こんにちは")&&NebulaLyricsText.needsReading("Hello мир"),"other scripts");
  check(!NebulaLyricsText.needsReading("Hello, world! 123")&&!NebulaLyricsText.needsReading("Ça va, señor")&&!NebulaLyricsText.needsReading(null),"Latin needs no reading");
  // Translation chunks.
  List<String> lines=Arrays.asList("aaaa","bbbb","cc","dddddddddd","e");
  List<int[]> chunks=NebulaLyricsText.chunks(lines,10);
  check(chunks.size()==4&&chunks.get(0)[1]==2&&chunks.get(1)[0]==2&&chunks.get(1)[1]==3&&chunks.get(2)[1]==4&&chunks.get(3)[1]==5,"chunks fit the limit");
  check(NebulaLyricsText.chunks(new ArrayList<>(),10).isEmpty(),"no lines, no chunks");
  check(Arrays.equals(NebulaLyricsText.split("a\nb\n",2),new String[]{"a","b"})&&NebulaLyricsText.split("a b",2)==null&&NebulaLyricsText.split(null,1)==null,"split keeps the line count");
  check(NebulaLyricsText.useful("Hello","Привет")&&!NebulaLyricsText.useful("Hello"," hello ")&&!NebulaLyricsText.useful("x","  ")&&!NebulaLyricsText.useful("x",null),"useful translations");
  // Sources.
  NebulaLrc net=NebulaLrc.parse("[00:01.00]net"),file=NebulaLrc.parse("[00:01.00]file");
  check(NebulaLyricsSources.choose(NebulaLyricsSources.LRCLIB,net,file)==net&&NebulaLyricsSources.choose(NebulaLyricsSources.LRCLIB,null,file)==null,"lrclib only, as before");
  check(NebulaLyricsSources.choose(NebulaLyricsSources.FILE_THEN_LRCLIB,net,file)==file&&NebulaLyricsSources.choose(NebulaLyricsSources.FILE_THEN_LRCLIB,net,null)==net,"file first");
  check(NebulaLyricsSources.choose(NebulaLyricsSources.LRCLIB_THEN_FILE,net,file)==net&&NebulaLyricsSources.choose(NebulaLyricsSources.LRCLIB_THEN_FILE,null,file)==file,"file as fallback");
  check(NebulaLyricsSources.choose(NebulaLyricsSources.FILE_ONLY,net,null)==null&&!NebulaLyricsSources.usesNetwork(NebulaLyricsSources.FILE_ONLY),"file only never asks the network");
  check(!NebulaLyricsSources.usesFile(NebulaLyricsSources.LRCLIB)&&NebulaLyricsSources.usesNetwork(9)&&!NebulaLyricsSources.usesFile(-3),"unknown values mean lrclib");
  check(NebulaLyricsSources.fromFile(null,false)==null&&NebulaLyricsSources.fromFile("  ",false)==null,"no embedded lyrics");
  NebulaLrc embedded=NebulaLyricsSources.fromFile("First line\r\nSecond line",false);
  check(embedded!=null&&!embedded.synced&&embedded.lines.size()==2,"plain embedded lyrics");
  check(NebulaLyricsSources.fromFile("[00:05.00]Timed",true).synced,"timed embedded lyrics");
  System.out.println("Lyrics sync: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-lyrics-sync-') as work:
    work = Path(work)
    for name, text in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8'))
    sources = [str(ui / f'{n}.java') for n in ['NebulaLrc', 'NebulaLyricsText', 'NebulaLyricsSources']]
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), *map(str, work.rglob('*.java')), *sources], check=True)
    subprocess.run([java, '-Dstdout.encoding=UTF-8', '-cp', str(work), 'Check'], check=True)

prefs = (ui / 'NebulaMediaPreferences.java').read_text(encoding='utf-8')
for default in ['getBoolean("player_word_sync", false)', 'getBoolean("player_transliteration", false)', 'getInt("player_lyrics_source", NebulaLyricsSources.LRCLIB)']:
    assert default in prefs, default
player = (ui / 'NebulaMusicPlayer.java').read_text(encoding='utf-8')
for hook in ['lyricsView.setRich(NebulaMediaPreferences.wordSync(), NebulaMediaPreferences.transliteration());',
             'NebulaLyricsSources.fromFile(info != null ? info.getLyrics() : null, NebulaMediaPreferences.wordSync())',
             'NebulaLyricsSources.choose(source, lrc, fromFile)', '!NebulaLyricsSources.usesNetwork(source)']:
    assert hook in player, hook
assert 'NebulaLrc.parse(value.substring(1), NebulaMediaPreferences.wordSync())' in (ui / 'NebulaMusicService.java').read_text(encoding='utf-8')
view = (ui / 'NebulaLyricsView.java').read_text(encoding='utf-8')
for part in ['Layout.Alignment.ALIGN_OPPOSITE', 'NebulaLrc.backgroundRanges(line.text)', 'NebulaLrc.sung(line, now(), end)',
             'android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")', 'android.os.Build.VERSION.SDK_INT >= 29', 'private void drawSung(']:
    assert part in view, part
assert 'getBoolean("player_translation", false)' in prefs
service = (ui / 'NebulaMusicService.java').read_text(encoding='utf-8')
assert 'NebulaLyricsText.chunks(texts, NebulaWebTranslation.chunkLimit(provider) - 50)' in service and 'NebulaWebTranslation.translate(provider,' in service
assert player.count('translateLyrics(chosen, key);') == 2 and 'if (lyrics == null || !NebulaMediaPreferences.lyricsTranslation()) return;' in player
assert 'public void setTranslations(String[] values)' in view
screen = (ui / 'NebulaMediaFeaturesFragment.java').read_text(encoding='utf-8')
assert 't("Перевод текстов", "Lyrics translation")' in screen
for row in ['t("Пословная синхронизация", "Word-by-word sync")', 't("Транслитерация", "Transliteration")', 't("Источники текстов", "Lyrics sources")']:
    assert row in screen, row
print('Lyrics sync: settings, player wiring and drawing verified')
# Live background: the Apple backdrop behind the Expressive player, drifting only while music plays.
assert 'getBoolean("player_live_background", false)' in prefs
assert 'private final boolean live = apple || NebulaMediaPreferences.liveBackground();' in player
assert 'setBackground(live ? backdrop : background);' in player and 'if (live) backdrop.setCover(receiver.getBitmap());' in player
assert '(apple || !MediaController.getInstance().isMessagePaused())) postInvalidateOnAnimation();' in player and 'if (live) invalidate();' in player
assert 't("Живой фон", "Live background")' in screen
print('Live background verified')
