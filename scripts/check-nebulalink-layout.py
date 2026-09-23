#!/usr/bin/env python3
"""Structural UI/lifecycle guards; not UIKit type checking or screenshot QA."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
android = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
ios = ROOT / 'platform/ios/overlay/submodules'
card = (android / 'NebulaConnectionCard.java').read_text(encoding='utf-8')
assert 'new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)' in card
assert 'labelParams.setMarginStart' in card
assert 'action.setEnabled(!busy)' in card and 'action.setEnabled(false)' in card
assert 'render(lastStatus)' in card and 'detail.setText(result.error)' not in card
assert 'NebulaLink.removeStatusListener(statusListener)' in card
assert 'NebulaLatency.format' in card or 'detail.setText(latency > 0 ?' in card
controller = (ios / 'NebulaLinkUI/Sources/NebulaLinkController.swift').read_text(encoding='utf-8')
status = controller.split('@objc private func statusUpdated()', 1)[1].split('@objc private func close()', 1)[0]
assert 'reloadPresentation()' not in status and 'tableView.reloadData()' not in status
if 'private var probeRequestId:' in controller:
    # Optional in-progress Nimbo work adds one diagnostic row, not another connect button.
    assert 'case 1: return 3' in controller
    assert 'case (1, 1): probeActiveConnection()' in controller
    assert 'case (1, 2): request("subscription.refreshAll")' in controller
    assert 'var changed = [IndexPath(row: 0, section: 1)]' in controller
    assert 'changed.append(IndexPath(row: index, section: 2))' in controller
    assert 'guard !ids.isEmpty else { return }' in controller
else:
    assert 'case 1: return 2' in controller
    assert 'case (1, 0):\n            // Explicit user action' in controller
    assert 'request("probe.url", ["url": "https://telegram.org"])' in controller
    assert 'case (1, 1): request("subscription.refreshAll")' in controller
assert 'overview.onAction = { [weak self]' in controller
assert 'guard let self = self, !self.busy else { return }' in controller
assert 'self.request("tunnel.start", ["id": self.selected])' in controller
assert 'systemLayoutSizeFitting' in controller
overview = (ios / 'NebulaLinkUI/Sources/NebulaLinkOverviewView.swift').read_text(encoding='utf-8')
assert 'action.isEnabled = !busy && (connected || connecting || hasSelection)' in overview
assert 'label.numberOfLines = 0' in overview
assert 'adjustsFontForContentSizeCategory = true' in overview
assert 'CADisplayLink' not in overview and 'UIVisualEffectView' not in overview
settings = (ios / 'SettingsUI/Sources/NebulaSettingsController.swift').read_text(encoding='utf-8')
order = settings.split('private var order: Int', 1)[1].split('var stableId:', 1)[0]
ids = settings.split('var stableId: Int32', 1)[1].split('static func <', 1)[0]
for block in (order, ids):
    values = re.findall(r'case \.(\w+): return (-?\d+)', block)
    assert len(values) == 22 and len({number for _, number in values}) == 22
assert 'entries.sort()' in settings and 'return lhs.order < rhs.order' in settings
assert 'case .link: return 7' in ids and 'case .hideCounters: return 1' in ids
print('OK: NebulaLink layout/lifecycle guards; 22 unique settings identities and order ranks')
