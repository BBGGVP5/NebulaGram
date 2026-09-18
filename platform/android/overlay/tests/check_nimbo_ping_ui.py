"""Scoped JVM behavior + mobile source contracts; no core build or release outputs."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
JAVA = HERE.parent / 'TMessagesProj/src/main/java/app/nebulagram'
UI = JAVA / 'ui'
IOS = ROOT / 'platform/ios/overlay/submodules/NebulaLinkUI/Sources'


def read(path):
    return path.read_text(encoding='utf-8')


def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


servers = read(UI / 'NebulaServersFragment.java')
bridge = read(JAVA / 'nebulalink/NebulaLink.java')
controller = read(IOS / 'NebulaLinkController.swift')
service = read(IOS / 'NebulaLinkService.swift')

for filename in ['NebulaServersFragment.java', 'NebulaConnectFragment.java', 'NebulaConnectionCard.java']:
    source = read(UI / filename)
    assert 'NebulaLatency.format(' in source
    assert 'optString("latency_method")' in source and 'optLong("checked_at")' in source
    assert 'ping_type' not in source, 'cached measurements cannot inherit the current preference'
shortcut = read(UI / 'NebulaLinkShortcut.java')
assert 'NebulaLatency.format(' in shortcut and 'payload.put("method", method)' in shortcut
assert 'cancelProbe();' in method(shortcut, 'protected void onDetachedFromWindow()')
assert 'probeRequestId = null' in method(shortcut, 'private void cancelProbe()')
assert 'requestId.equals(probeRequestId)' in method(shortcut, 'private void probe()')
assert 'cancelProbe();' in method(servers, 'public void onFragmentDestroy()')
assert 'removeProbeListener' in method(servers, 'public void onFragmentDestroy()')
progress = method(servers, 'private void onProbeProgress(')
assert 'request_id' in progress and 'latency_method' in progress and 'checked_at' in progress
assert 'render(' not in progress and 'load(' not in progress
assert 'probe.progress' in bridge and 'AndroidUtilities.runOnUIThread' in method(bridge, 'private static void handleEvent(')
assert 'FileLog' not in method(bridge, 'private static void handleEvent(')
assert '"probe.cancel"' not in method(read(JAVA / 'nebulalink/NebulaCommandQueue.java'), 'public void execute(String method,')

for locale in ['values', 'values-ru']:
    tree = ET.parse(HERE.parent / 'TMessagesProj/src/main/res' / locale / 'strings_nebula_menu.xml')
    strings = {node.attrib['name']: node.text for node in tree.getroot()}
    assert strings['nl_ping_nimbo'] == 'Nimbo Ping'
    assert '≈' in strings['nl_ping_estimate']
    for name in ['nl_ping_tcp', 'nl_ping_http', 'nl_ping_url']:
        assert name in strings
menu = read(UI / 'NebulaMenuFragment.java')
assert 'nl_ping_estimate' in menu and '"ping_type".equals(key) && value.isEmpty() ? "nimbo"' in menu

probe = method(controller, 'private func probeVisibleServers()')
assert 'servers.compactMap' in probe and 'guard !ids.isEmpty' in probe
assert '"method": "nimbo"' in probe and '"timeout": 5' in probe and '"request_id": requestId' in probe
assert 'busy =' not in probe and 'request("probe.' not in controller
assert 'private func probeActiveConnection()' in controller and 'service.call("probe.url"' in controller
assert 'abandonProbes()' in method(controller, '@objc private func close()')
assert 'abandonProbes()' in method(controller, 'public override func viewDidDisappear(')
assert 'abandonProbes(); page =' in controller
assert 'progress["request_id"] as? String == requestId' in controller
assert 'NebulaLatency.format' in controller and 'server["latency_method"]' in controller
assert 'server["checked_at"]' in controller and 'case 1: return 5' in controller
call = method(service, 'public func call(')
assert call.index('try self.initializeCore()') < call.index('self.probeQueue.async')
assert 'method == "probe.servers" || method == "probe.url"' in call
assert call.index('if method == "tunnel.stop"') < call.index('self.probeQueue.async')
assert 'NebulalinkSetEventSink(sink)' in service and 'func onEvent(_ json: String?)' in service
assert 'NotificationCenter.default.post(name: NebulaLinkService.probeProgress' in service
print('PASS: Android/iOS provenance, localization, visible-page scope, progress, lifecycle and queue contracts')

json_jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'))
if os.environ.get('JSON_JAR'):
    json_jars = [Path(os.environ['JSON_JAR'])]
assert json_jars, 'Set JSON_JAR to a local org.json jar for UI callback tests'
json_jar = json_jars[0]

# Run the actual screen methods with a deterministic callback transport and row recorder.
harness = r'''
import java.util.*;
import org.json.*;
public class NebulaProbeUiTest {
 static class NebulaRow {String title; NebulaRow title(String value){title=value;return this;}}
 static class NebulaText {static String text(String ru,String en){return en;}}
 static class LocaleController {static String getString(int id){return "Probe";}}
 static class R {static class string {static int NebulaProbe=1;}}
 static class Result {String error="fixture error";boolean ok;Result(boolean value){ok=value;}}
 interface Callback {void done(Result value);}
 static class Call {String method;JSONObject payload;Callback callback;Call(String m,JSONObject p,Callback c){method=m;payload=p;callback=c;}}
 static class NebulaLink {
  static List<Call> calls=new ArrayList<>();
  static void call(String m,JSONObject p,Callback c){calls.add(new Call(m,p,c));}
 }
 static class Screen {
  boolean probing,cancelling;String probeRequestId,selectedId;int probeCompleted,probeTotal,loads,reports,badges;
  Object content=new Object();JSONObject lastData;NebulaRow probeAction=new NebulaRow();
  HashMap<String,NebulaRow> serverRows=new HashMap<>();
  void load(){loads++;}void report(String message){reports++;}
  void updateLatency(NebulaRow row,JSONObject data){badges++;}
  METHODS
 }
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception {
  Screen s=new Screen();s.lastData=new JSONObject().put("servers",new JSONArray());
  s.probe();check(NebulaLink.calls.isEmpty(),"empty page must not probe whole store");
  JSONObject a=new JSONObject().put("id","a").put("latency_ms",99).put("latency_method","tcp");
  JSONObject b=new JSONObject().put("id","b").put("latency_ms",88);
  s.lastData.put("servers",new JSONArray().put(a).put(b));s.serverRows.put("a",new NebulaRow());
  s.probe();Call batch=NebulaLink.calls.get(0);String id=s.probeRequestId;
  check(batch.method.equals("probe.servers") && batch.payload.getJSONArray("ids").length()==2,"existing page IDs");
  check(batch.payload.getInt("timeout")==5 && id.equals(batch.payload.getString("request_id")),"seconds and owned ID");
  s.probe();check(NebulaLink.calls.size()==1,"one owned batch");
  JSONObject event=new JSONObject().put("request_id","other").put("id","a").put("latency_ms",330)
    .put("latency_method","nimbo").put("checked_at",123).put("completed",1).put("total",2);
  s.onProbeProgress(event);check(s.badges==0 && a.getInt("latency_ms")==99,"foreign event ignored");
  event.put("request_id",id);s.onProbeProgress(event);
  check(s.badges==1 && a.getInt("latency_ms")==330 && a.getString("latency_method").equals("nimbo"),"raw result and provenance");
  check(s.loads==0 && s.probeCompleted==1 && s.probeTotal==2,"incremental progress without full reload");
  check(b.getInt("latency_ms")==88,"unvisited server untouched");
  s.select("b");check(NebulaLink.calls.get(1).method.equals("server.select"),"selection while probing");
  s.cancelProbe();s.cancelProbe();check(NebulaLink.calls.size()==3,"cancel sent once");
  check(NebulaLink.calls.get(2).payload.getString("request_id").equals(id),"cancel only owned request");
  batch.callback.done(new Result(true));check(!s.probing && s.probeRequestId==null && s.loads==1,"completion unlocks and reloads");
  s.onProbeProgress(event);check(s.badges==1,"late event ignored");
  s.probe();Call next=NebulaLink.calls.get(3);check(!s.probeRequestId.equals(id),"unique next request");
  batch.callback.done(new Result(false));check(s.probing && s.reports==0,"stale callback cannot clear next request");
  next.callback.done(new Result(false));check(!s.probing && s.reports==1,"failure unlocks with generic message");
  System.out.println("PASS: actual Android probe/progress/cancel callbacks, raw cache, selection and stale events");
 }
}
'''.replace('METHODS', '\n'.join(method(servers, signature) for signature in [
    'private List<JSONObject> serverList(', 'private void probe()', 'private void cancelProbe()',
    'private void onProbeProgress(', 'private void updateProbeAction()', 'private void select(']))

with tempfile.TemporaryDirectory(prefix='.nimbo-test-', dir=HERE) as temporary:
    out = Path(temporary).resolve()
    assert out.is_relative_to(HERE)
    generated = out / 'NebulaProbeUiTest.java'
    generated.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(json_jar), '-d', str(out),
                    str(UI / 'NebulaLatency.java'), str(JAVA / 'nebulalink/NebulaCommandQueue.java'),
                    str(HERE / 'NebulaLatencyTest.java'), str(HERE / 'NebulaProbeQueueTest.java'), str(generated)], check=True)
    for name in ['NebulaLatencyTest', 'app.nebulagram.nebulalink.NebulaProbeQueueTest', 'NebulaProbeUiTest']:
        subprocess.run(['java', '-cp', os.pathsep.join([str(out), str(json_jar)]), name], check=True, timeout=15)
    if shutil.which('swiftc'):
        executable = out / ('swift-latency.exe' if os.name == 'nt' else 'swift-latency')
        subprocess.run(['swiftc', str(IOS / 'NebulaLatency.swift'),
                        str(ROOT / 'platform/ios/overlay/tests/NebulaLatencyTests.swift'), '-o', str(executable)], check=True)
        subprocess.run([str(executable)], check=True)
    else:
        print('SKIP: Swift compiler/Mac unavailable; Swift test source provided, UIKit not compiled')
