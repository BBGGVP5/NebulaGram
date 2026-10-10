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
assert 'section == 12 || section >= 0 && section <= 9' in links
assert 'SECTION_GLASS = 12' in section
assert 'buildChats(context); buildMessages(context); buildProfile(context)' not in section
assert 'buildTabs(context); buildFolders(context)' not in section
assert 'focusRowIndex(content, new int[]{0}, focusIndex)' in privacy
assert len('tg://settings/nebula?s=-15&r=2') <= 40

chat = patch[patch.index('diff --git a/TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java'):]
assert 'R.drawable.msg_delete' not in chat
gap = chat.index('headerItem.lazilyAddColoredGap()')
action = chat.index('headerItem.lazilyAddSubItem(0x4e4443, R.drawable.msg_clearcache')
assert gap < action
assert 'NebulaText.text("Очистить удаленки", "Clear deleted messages")' in chat
assert chat.index('closeTopicItem =') < gap
print('Compact setting links, legacy-link compatibility, row focus, and separated cache action passed')


# Execute the actual NebulaRow listener ownership methods through an Android View fixture.
import subprocess
row = (ui / 'NebulaRow.java').read_text(encoding='utf-8')
methods = row[row.index('    private View.OnLongClickListener featureLongClick'):row.index('    public NebulaRow(@NonNull Context context)')]
assert 'row.bindSettingsLinkLongClick' in links
work = ROOT / 'build/row-long-press-check'
work.mkdir(parents=True, exist_ok=True)
source = 'class View {interface OnLongClickListener {boolean onLongClick(View v);} OnLongClickListener listener; public void setOnLongClickListener(OnLongClickListener value){listener=value;} boolean performLongClick(){return listener!=null&&listener.onLongClick(this);}} class Row extends View {' + methods + '} public class RowLongPressCheck {public static void main(String[] args){int[] calls={0,0}; Row row=new Row(); View.OnLongClickListener edit=v->{calls[0]++;return true;}; View.OnLongClickListener copy=v->{calls[1]++;return true;}; row.setOnLongClickListener(edit); for(int i=0;i<4;i++){row.bindSettingsLinkLongClick(copy);row.performLongClick();} if(calls[0]!=4||calls[1]!=0)throw new AssertionError("Binding stole feature long press"); row.setOnLongClickListener(null);row.performLongClick();if(calls[1]!=1)throw new AssertionError("Fallback not restored"); row.setOnLongClickListener(edit);row.performLongClick();if(calls[0]!=5)throw new AssertionError("Late feature listener ignored"); Row plain=new Row();plain.bindSettingsLinkLongClick(copy);plain.performLongClick();if(calls[1]!=2)throw new AssertionError("Plain row link missing"); plain.bindSettingsLinkLongClick(null);if(plain.performLongClick())throw new AssertionError("Removed fallback active");System.out.println("Custom long presses survive repeated link binding; plain-row links still work");}}'
(work / 'RowLongPressCheck.java').write_text(source, encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(work/'RowLongPressCheck.java')],check=True)
subprocess.run(['java','-cp',str(work),'RowLongPressCheck'],check=True)
