"""Export data: run the options, limits, names and escaping of NebulaExportOptions, then check the export task and its screen."""
from pathlib import Path
import json, os, re, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')

check = r'''import app.nebulagram.ui.NebulaExportOptions;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  NebulaExportOptions o=new NebulaExportOptions();
  check(o.has(NebulaExportOptions.INFO)&&o.has(NebulaExportOptions.PERSONAL)&&o.has(NebulaExportOptions.PHOTOS)&&!o.has(NebulaExportOptions.CHANNELS),"Desktop-like defaults");
  check(o.selected(0)==2&&o.selected(1)==1&&o.selected(2)==1&&o.valid(),"counters");
  o.setAll(0,false);o.setAll(1,false);check(!o.valid(),"media alone is not an export");
  o.setAll(2,true);check(o.selected(2)==7,"whole section");
  check(o.selected(3)==0,"Other is off by default");o.set(NebulaExportOptions.STICKER_SETS,true);check(o.valid()&&o.selected(3)==1,"Other alone is an export");o.setAll(3,false);o.set(199,true);check(!o.has(199),"unknown item ignored");
  check(NebulaExportOptions.limit(0)==NebulaExportOptions.MIN_LIMIT&&NebulaExportOptions.limit(1)==NebulaExportOptions.MAX_LIMIT,"slider ends 1 MB and 4 GB");
  check(Math.abs(NebulaExportOptions.position(NebulaExportOptions.limit(.37f))-.37f)<1e-3,"slider round trip");
  check(NebulaExportOptions.limit(.5f)>60L<<20&&NebulaExportOptions.limit(.5f)<70L<<20,"log scale middle is ~64 MB: "+NebulaExportOptions.limit(.5f));
  check(NebulaExportOptions.rounded(29L<<20)==(30L<<20)&&NebulaExportOptions.rounded(3L<<30)==(3L<<30)||NebulaExportOptions.rounded(3L<<30)>(2L<<30),"rounded steps: "+NebulaExportOptions.rounded(29L<<20));
  check(NebulaExportOptions.size(28L<<20).equals("28 MB")&&NebulaExportOptions.size(1536).equals("1.5 KB")&&NebulaExportOptions.size(4L<<30).equals("4 GB")&&NebulaExportOptions.size(381200).equals("372.3 KB"),"sizes");
  long now=1764000000000L;
  o.period=NebulaExportOptions.PERIOD_ALL;check(o.since(now)==0,"all time");
  o.period=NebulaExportOptions.PERIOD_MONTH;check(o.since(now)==(int)(now/1000-30*86400),"last month");
  check(NebulaExportOptions.safeName("a/b:c*?.jpg","x").equals("a_b_c__.jpg"),"unsafe characters");
  check(NebulaExportOptions.safeName("...hidden","x").equals("hidden")&&NebulaExportOptions.safeName("  ","file_1").equals("file_1"),"dots and empty");
  String longName=NebulaExportOptions.safeName("n".repeat(200)+".pdf","x");check(longName.length()==80&&longName.endsWith(".pdf"),"long names keep the extension");
  check(NebulaExportOptions.folderName(0).startsWith("NebulaGram Export "),"folder name");
  check(NebulaExportOptions.isoDate(0,java.util.TimeZone.getTimeZone("UTC")).equals("1970-01-01T00:00:00"),"Desktop date format");
  check(NebulaExportOptions.json("a\"b\\c\nd\u0001\u2028").equals("\"a\\\"b\\\\c\\nd\\u0001\\u2028\"")&&NebulaExportOptions.json(null).equals("null"),"JSON escaping: "+NebulaExportOptions.json("a\"b\\c\nd\u0001\u2028"));
  check(NebulaExportOptions.html("<script>&\"'\n").equals("&lt;script&gt;&amp;&quot;&#39;<br>"),"HTML escaping");
  check(NebulaExportOptions.href("chats/chat_001/photos/фото 1.jpg").equals("chats/chat_001/photos/%D1%84%D0%BE%D1%82%D0%BE%201.jpg"),"links: "+NebulaExportOptions.href("chats/chat_001/photos/фото 1.jpg"));
  check(NebulaExportOptions.percent(0,78,0)==0&&NebulaExportOptions.percent(39,78,0)==50&&NebulaExportOptions.percent(78,78,1)==100&&NebulaExportOptions.percent(1,0,0)==0,"progress");
  System.out.println("Data export: "+cases+" cases passed");
 }
}
'''

with tempfile.TemporaryDirectory(prefix='nebula-export-') as work:
    work = Path(work)
    (work / 'Check.java').write_bytes(check.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-implicit:none', '-d', str(work), str(work / 'Check.java'), str(ui / 'NebulaExportOptions.java')], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

task = (ui / 'NebulaExport.java').read_text(encoding='utf-8')
# result.json stays valid whatever is chosen: every section opens with a comma, none ends with one.
writes = re.findall(r'json\.write\("((?:[^"\\]|\\.)*)"', task)
assert not any(w.endswith(',\\n') for w in writes), [w for w in writes if w.endswith(',\\n')]
for section in ['personal_information', 'contacts', 'sessions', 'chats', 'profile_pictures', 'sticker_sets', 'stories']:
    assert f'json.write(",\\n \\"{section}\\"' in task, section
# Simulate the write order for every combination of sections and parse the result.
for mask in range(16):
    parts = ['{\n "about": "x"']
    if mask & 1: parts.append(',\n "personal_information": {"a": 1\n }')
    if mask & 2: parts.append(',\n "contacts": {\n  "list": [\n  ]\n }')
    if mask & 4: parts.append(',\n "sessions": {\n  "list": [\n  ]\n }')
    if mask & 8: parts.append(',\n "chats": {\n  "list": [\n  ]\n }')
    parts.append('\n}\n')
    json.loads(''.join(parts))
for part in ['MediaStore.Downloads.EXTERNAL_CONTENT_URI', 'MediaStore.MediaColumns.RELATIVE_PATH', 'Environment.DIRECTORY_DOWNLOADS + "/NebulaGram/"',
             'startsWith("FLOOD_WAIT_")', 'if (size <= 0 || size > options.sizeLimit) return "";', 'new TLRPC.TL_messages_getDialogs()', 'new TLRPC.TL_messages_getHistory()',
             'loader.cancelLoadFile(document)', 'running != null && !running.state.finished', 'java.util.Collections.reverse(messages);',
             'NebulaExportOptions.html(body)', 'NebulaExportOptions.json(body)', 'new TLRPC.TL_photos_getUserPhotos()', 'getStickerSets(org.telegram.messenger.MediaDataController.TYPE_IMAGE)',
             'https://t.me/addstickers/', 'Writer page = text("other.html", "text/html");', 'new org.telegram.tgnet.tl.TL_stories.TL_stories_getPinnedStories()', 'unique("stories", name)']:
    assert part in task, part
screen = (ui / 'NebulaExportFragment.java').read_text(encoding='utf-8')
for part in ['t("Что экспортировать", "What to export")', 't("Лимит размера", "Size limit")', 't("Сохранение", "Saving")', 't("Продолжить в фоне", "Continue in Background")',
             't("Остановить экспорт", "Stop Export")', 't("Другое", "Other")', 't("Наборы стикеров", "Sticker sets")', 'Manifest.permission.WRITE_EXTERNAL_STORAGE', 'Build.VERSION.SDK_INT < 29', 'NebulaExport.start(currentAccount, copy(options))']:
    assert part in screen, part
assert 'new NebulaExportFragment()' in (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'NebulaExport' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
print('Data export: task, screen and settings entry verified')
