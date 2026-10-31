"""Execute the actual native refresh method with a deterministic main queue."""
from pathlib import Path
import subprocess


def check(source: str, work: Path) -> None:
    start = source.index('    private func nebulaScheduleRootRefresh()')
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    method = source[start:end].replace('private func', 'func', 1)
    program = '''import Foundation
final class Queue {
    static let shared = Queue()
    var pending: [() -> Void] = []
    static func mainQueue() -> Queue { shared }
    func async(_ action: @escaping () -> Void) { pending.append(action) }
    func drain() { let actions = pending; pending.removeAll(); for action in actions { action() } }
}
final class Root {
    var nebulaNavigationRefreshScheduled = false
    var nebulaShowCalls = false
    var nebulaShowNews = false
    var updates: [[Bool]] = []
    func updateRootControllers(showCallsTab: Bool, showNewsTab: Bool) {
        updates.append([showCallsTab, showNewsTab])
    }
METHOD
}
let root = Root()
root.nebulaScheduleRootRefresh()
root.nebulaScheduleRootRefresh()
precondition(root.updates.isEmpty && Queue.shared.pending.count == 1, "no reentrant UI rebuild")
root.nebulaShowCalls = true
root.nebulaShowNews = true
Queue.shared.drain()
precondition(root.updates == [[true, true]] && !root.nebulaNavigationRefreshScheduled, "latest calls and news settings after UI transaction")
root.nebulaShowCalls = false
root.nebulaScheduleRootRefresh()
Queue.shared.drain()
precondition(root.updates == [[true, true], [false, true]], "can refresh again without losing news")
root.nebulaScheduleRootRefresh()
root.nebulaShowNews = false
Queue.shared.drain()
precondition(root.updates.last == [false, false], "news changes are read when the callback executes")
var transient: Root? = Root()
weak var weakRoot = transient
transient?.nebulaScheduleRootRefresh()
transient = nil
precondition(weakRoot == nil, "queued callback does not retain navigation root")
Queue.shared.drain()
print("OK: actual native navigation refresh coalesces, reads latest state, runs again and releases root")
'''.replace('METHOD', method)
    fixture = work / 'NavigationRefreshCheck.swift'
    fixture.write_text(program, encoding='utf-8')
    executable = work / 'navigation-refresh-check'
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', str(fixture), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)
