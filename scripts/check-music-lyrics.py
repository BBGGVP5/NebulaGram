"""Execute the LRC parser and the lrclib.net / iTunes Search request and reply handling."""
from pathlib import Path
import os,shutil,subprocess,tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
test=r'''package app.nebulagram.ui;
import java.util.*;
class NebulaText {static String text(String r,String e){return e;}}
public class MusicLyricsCheck {
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 public static void main(String[] args){
  String lrc="\uFEFF[ti:Song]\r\n[ar:Artist]\n[length: 03:20]\n[00:12.5]Second\n[00:01.00][00:30.123]Chorus <00:01.50>word\n[00:05:20]Third\n\n[99:99.00]bad seconds\nnot a lyric\n[00:40.00]\n";
  NebulaLrc parsed=NebulaLrc.parse(lrc);
  check(parsed.synced,"synced");
  check(parsed.lines.size()==5,"five timed lines, metadata and bad stamps ignored: "+parsed.lines.size());
  long[] times={1000,5200,12500,30123,40000};
  for(int i=0;i<times.length;i++)check(parsed.lines.get(i).time==times[i],"sorted time "+i+": "+parsed.lines.get(i).time);
  check(parsed.lines.get(0).text.equals("Chorus word")&&parsed.lines.get(3).text.equals("Chorus word"),"repeated stamps share text, word tags removed");
  check(parsed.lines.get(2).text.equals("Second")&&parsed.lines.get(4).text.isEmpty(),"text and empty breaks");
  check(parsed.index(0)==-1&&parsed.index(999)==-1,"before first line");
  check(parsed.index(1000)==0&&parsed.index(5199)==0&&parsed.index(5200)==1&&parsed.index(29999)==2&&parsed.index(3600000)==4,"binary search");
  NebulaLrc shifted=NebulaLrc.parse("[offset:+500]\n[00:01.00]a\n[00:00.20]b");
  check(shifted.lines.get(0).time==0&&shifted.lines.get(1).time==500,"positive offset shows lines earlier and clamps at zero");
  check(NebulaLrc.parse("[offset:-250]\n[00:01.00]a").lines.get(0).time==1250,"negative offset");
  NebulaLrc plain=NebulaLrc.parse("Just words\nNo stamps");
  check(!plain.synced&&plain.lines.size()==2&&plain.index(5000)==-1,"plain fallback");
  check(NebulaLrc.parse(null).isEmpty()&&NebulaLrc.parse("[ar:x]").isEmpty(),"empty input");
  check(NebulaLrc.plain("a\n\nb\n\n").lines.size()==3,"plain trims trailing breaks");

  check(NebulaMusicLookup.lrclibGet("Daft Punk","One More Time",320).equals("https://lrclib.net/api/get?artist_name=Daft%20Punk&track_name=One%20More%20Time&duration=320"),"lrclib get URL");
  check(NebulaMusicLookup.lrclibGet("Кино","Группа крови",0).equals("https://lrclib.net/api/get?artist_name=%D0%9A%D0%B8%D0%BD%D0%BE&track_name=%D0%93%D1%80%D1%83%D0%BF%D0%BF%D0%B0%20%D0%BA%D1%80%D0%BE%D0%B2%D0%B8"),"unknown duration omitted, Cyrillic encoded");
  check(NebulaMusicLookup.lrclibSearch("","A&B").equals("https://lrclib.net/api/search?track_name=A%26B"),"search without artist");
  check(NebulaMusicLookup.itunesSearch("Daft Punk","Digital Love","").equals("https://itunes.apple.com/search?term=Daft%20Punk%20Digital%20Love&media=music&entity=song&limit=10"),"iTunes search URL");
  var one=NebulaMusicLookup.lyrics("{\"id\":1,\"trackName\":\"X\",\"duration\":200.0,\"instrumental\":false,\"plainLyrics\":\"a\\nb\",\"syncedLyrics\":\"[00:01.00]a\\n[00:02.00]b\"}",200);
  check(one.lrc().synced&&one.lrc().lines.size()==2,"get reply prefers synced");
  var plainOnly=NebulaMusicLookup.lyrics("{\"plainLyrics\":\"a\\nb\",\"syncedLyrics\":null}",0);
  check(!plainOnly.lrc().synced&&plainOnly.lrc().lines.size()==2,"plain reply");
  var search=NebulaMusicLookup.lyrics("[{\"duration\":180,\"syncedLyrics\":\"[00:01.00]far\"},{\"duration\":211,\"plainLyrics\":\"close plain\"},{\"duration\":212,\"syncedLyrics\":\"[00:01.00]close\"},{\"duration\":210}]",210);
  check(search.lrc().lines.get(0).text.equals("close"),"closest duration, synced preferred");
  check(NebulaMusicLookup.lyrics("[{\"duration\":100,\"syncedLyrics\":\"[00:01.00]x\"}]",300)==null,"distant durations rejected");
  check(NebulaMusicLookup.lyrics("[]",300)==null,"no hits");
  check(NebulaMusicLookup.lyrics("{\"instrumental\":true}",0).instrumental,"instrumental");

  String itunes="{\"resultCount\":3,\"results\":[{\"artistName\":\"Other\",\"trackName\":\"Digital Love\",\"trackTimeMillis\":301000,\"artworkUrl100\":\"https://is1.mzstatic.com/a/100x100bb.jpg\"},"
   +"{\"artistName\":\"Daft Punk\",\"trackName\":\"Digital Love (Live)\",\"trackTimeMillis\":420000,\"artworkUrl100\":\"https://is1.mzstatic.com/live/100x100bb.jpg\"},"
   +"{\"artistName\":\"Daft Punk\",\"trackName\":\"Digital Love\",\"trackTimeMillis\":298000,\"artworkUrl100\":\"https://is1.mzstatic.com/right/source/100x100bb.jpg\"}]}";
  check("https://is1.mzstatic.com/right/source/1200x1200bb.jpg".equals(NebulaMusicLookup.artwork(itunes,"Daft Punk","Digital Love",301,1200)),"iTunes match by artist, title and duration");
  check(NebulaMusicLookup.artwork(itunes,"Nobody","Digital Love",301,1200)==null,"wrong artist is not a cover");
  check(NebulaMusicLookup.artwork("{\"resultCount\":0,\"results\":[]}","a","b",1,1200)==null,"no results");
  check(NebulaMusicLookup.artwork(itunes,"daft punk feat. someone","DIGITAL LOVE",0,600).endsWith("/600x600bb.jpg"),"normalised names, unknown duration");
  try{NebulaMusicLookup.lyrics("<html>",1);throw new AssertionError("html accepted");}catch(IllegalStateException expected){}
  System.out.println("Lyrics: LRC timing, offsets, plain fallback, lrclib requests/replies and iTunes cover matching passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-music-lyrics-') as folder:
 p=Path(folder);(p/'MusicLyricsCheck.java').write_text(test,encoding='utf-8')
 javac=shutil.which('javac') or str(java_home/'bin/javac.exe');java=shutil.which('java') or str(java_home/'bin/java.exe')
 subprocess.run([javac,'-nowarn','-encoding','UTF-8','-d',folder,*[str(ui/n) for n in ['NebulaLrc.java','NebulaMusicLookup.java','NebulaWebTranslation.java']],str(p/'MusicLyricsCheck.java')],check=True)
 subprocess.run([java,'-cp',folder,'app.nebulagram.ui.MusicLyricsCheck'],check=True)
