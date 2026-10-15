"""Compile and execute the actual Foundation reply store on the iOS CI host."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
test = r'''
import Foundation
@main struct Check {
 static func check(_ value: Bool, _ why: String) { precondition(value, why) }
 static func main() {
  let suite = "nebula.reply-test." + UUID().uuidString
  let defaults = UserDefaults(suiteName: suite)!
  defer { defaults.removePersistentDomain(forName: suite) }
  let a = NebulaQuickReplyStore(account: "123", defaults: defaults)
  let b = NebulaQuickReplyStore(account: "456", defaults: defaults)
  check(a.save(id: nil, title: " Greeting ", text: "Hi\nNext line 🚀"), "save")
  check(a.list()[0].title == "Greeting" && a.list()[0].text == "Hi\nNext line 🚀", "round trip")
  check(b.list().isEmpty, "account isolation")
  let id = a.list()[0].id
  check(a.save(id: id, title: "Edit", text: "Updated") && a.list().count == 1, "edit duplicated template")
  check(!a.save(id: "missing", title: "t", text: "v"), "stale edit recreated deleted template")
  check(!a.save(id: nil, title: " ", text: "v") && !a.save(id: nil, title: "t", text: " "), "blank")
  check(!a.save(id: nil, title: String(repeating: "x", count: 81), text: "v"), "title limit")
  check(!a.save(id: nil, title: "t", text: String(repeating: "🚀", count: 2049)), "UTF16 limit")
  for index in 1..<100 { check(a.save(id: nil, title: "t\(index)", text: "v"), "fill") }
  check(!a.save(id: nil, title: "extra", text: "v"), "count limit")
  check(a.save(id: id, title: "editable at limit", text: "v"), "edit at limit")
  check(a.delete(id: id) && a.list().count == 99, "delete")
  defaults.set(Data("bad JSON".utf8), forKey: "nebula.quickReplies.456")
  check(b.list().isEmpty, "corrupt storage")
  let entry = NebulaQuickReply(id: "duplicate", title: "t", text: "v")
  defaults.set(try! JSONEncoder().encode([entry, entry, NebulaQuickReply(id: "bad", title: "", text: "v")]), forKey: "nebula.quickReplies.456")
  check(b.list() == [entry], "duplicates or invalid rows accepted")
  print("Swift quick replies: account isolation, edit/delete, limits, malformed storage passed")
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-swift-replies-') as directory:
    path = Path(directory); (path / 'Check.swift').write_text(test, encoding='utf-8')
    production = root / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaQuickReplies.swift'
    executable = path / 'check'
    subprocess.run(['swiftc', '-swift-version', '5', str(production), str(path / 'Check.swift'), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)
