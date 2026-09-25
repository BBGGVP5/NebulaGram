#!/usr/bin/env python3
"""Verify compact Nebula settings links and the separated deleted-copy action."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ui = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
links = (ui / 'NebulaSettingsLinks.java').read_text(encoding='utf-8')
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
privacy = (ui / 'NebulaPrivacyFragment.java').read_text(encoding='utf-8')
patch = (ROOT / 'patches/android/0081-inline-deleted-messages.patch').read_text(encoding='utf-8')

assert '.appendQueryParameter("s", Integer.toString(section))' in links
assert '.appendQueryParameter("r", Integer.toString(index))' in links
assert 'uri.getQueryParameter("s")' in links and 'uri.getQueryParameter("section")' in links
assert 'uri.getQueryParameter("focus")' in links and 'uri.getQueryParameter("key")' in links
assert '.focusRowIndex(row)' in links and 'focusRowIndex(content, new int[]{0}, focusIndex)' in section
assert 'focusRowIndex(content, new int[]{0}, focusIndex)' in privacy
assert len('tg://settings/nebula?s=-15&r=2') <= 40

chat = patch[patch.index('diff --git a/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java'):]
assert 'R.drawable.msg_delete' not in chat
gap = chat.index('headerItem.lazilyAddColoredGap()')
action = chat.index('headerItem.lazilyAddSubItem(0x4e4443, R.drawable.msg_clearcache')
assert gap < action
assert 'NebulaText.text("Очистить копии", "Clear copies")' in chat
assert chat.index('closeTopicItem =') < gap
print('Compact setting links, legacy-link compatibility, row focus, and separated cache action passed')
