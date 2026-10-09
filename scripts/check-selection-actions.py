"""Exercise the real bulk-selection hook and verify forwarding routes in the patched tree."""
from pathlib import Path
import subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
source=(Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
start=source.index('    private void nebulaSelectAllLoadedMessages()')
method=source[start:source.index('    private boolean nebulaWithoutAuthorAction()',start)]
java=r'''
import java.util.*;
class CheckSelectionActions {
 static class MessageObject {
  static final int TYPE_JOINED_CHANNEL=10,TYPE_GIFT_STARS=30,TYPE_SUGGEST_PHOTO=31;
  int id,type,kind=2;long dialog=1;boolean isDateObject,sending,editing,gift,wallpaper,sponsored,ephemeral;
  MessageObject(int i){id=i;}int getId(){return id;}long getDialogId(){return dialog;}
  boolean isSending(){return sending;}boolean isEditing(){return editing;}boolean isAnyGift(){return gift;}
  boolean isWallpaperAction(){return wallpaper;}boolean isSponsored(){return sponsored;}boolean isEphemeral(){return ephemeral;}
 }
 static class Bar {boolean active=true;boolean isActionModeShowed(){return active;}}
 Bar actionBar=new Bar();boolean report;long dialog_id=1;int finalized,titles,rows,reactions;
 ArrayList<MessageObject> messages=new ArrayList<>();
 HashMap<Integer,MessageObject>[] selectedMessagesIds=new HashMap[]{new HashMap<>(),new HashMap<>()};
 boolean isReport(){return report;}int getMessageType(MessageObject m){return m.kind;}
 void addToSelectedMessages(MessageObject m,boolean outside,boolean last){
  if(last){finalized++;return;}
  if(outside)throw new AssertionError("bulk selection must not toggle albums");
  var selected=selectedMessagesIds[m.dialog==dialog_id?0:1];
  if(selected.remove(m.id)!=null)throw new AssertionError("existing selection was toggled off");
  selected.put(m.id,m);
 }
 void updateActionModeTitle(){titles++;}void updateVisibleRows(){rows++;}void updateSelectedMessageReactions(){reactions++;}
 METHOD
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[]args){
  var c=new CheckSelectionActions();
  for(int i=1;i<=130;i++)c.messages.add(new MessageObject(i));
  c.selectedMessagesIds[0].put(1,c.messages.get(0));c.nebulaSelectAllLoadedMessages();
  check(c.selectedMessagesIds[0].size()==100&&c.finalized==1&&c.titles==1&&c.rows==1&&c.reactions==1);
  c.nebulaSelectAllLoadedMessages();check(c.selectedMessagesIds[0].size()==100);
  var blocked=new CheckSelectionActions();blocked.messages.add(null);blocked.messages.add(new MessageObject(0));
  for(int i=1;i<=13;i++) {var m=new MessageObject(i);blocked.messages.add(m);
   switch(i){case 1:m.isDateObject=true;break;case 2:m.sending=true;break;case 3:m.editing=true;break;
    case 4:m.gift=true;break;case 5:m.wallpaper=true;break;case 6:m.sponsored=true;break;case 7:m.ephemeral=true;break;
    case 8:m.type=10;break;case 9:m.type=30;break;case 10:m.kind=1;break;case 11:m.kind=20;break;case 12:m.kind=31;break;}
  }
  var merged=new MessageObject(13);merged.dialog=2;blocked.messages.add(merged);
  blocked.nebulaSelectAllLoadedMessages();check(blocked.selectedMessagesIds[0].size()==1&&blocked.selectedMessagesIds[1].size()==1);
  blocked.nebulaSelectAllLoadedMessages();check(blocked.selectedMessagesIds[0].size()==1&&blocked.selectedMessagesIds[1].size()==1);
  var hidden=new CheckSelectionActions();hidden.messages.add(new MessageObject(1));hidden.actionBar.active=false;
  hidden.nebulaSelectAllLoadedMessages();check(hidden.finalized==0&&hidden.selectedMessagesIds[0].isEmpty());
  hidden.actionBar.active=true;hidden.report=true;hidden.nebulaSelectAllLoadedMessages();check(hidden.finalized==0);
  System.out.println("Bulk selection: eligibility, native limit, existing selection, merged IDs and inactive/report modes passed");
 }
}
'''.replace('METHOD',method)
with tempfile.TemporaryDirectory(prefix='nebula-selection-actions-') as folder:
 p=Path(folder)/'CheckSelectionActions.java';p.write_text(java,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8',str(p)],check=True)
 subprocess.run(['java','-cp',folder,'CheckSelectionActions'],check=True)
# Regression guards on the actual patched paths: no mutable cross-picker flag;
# both draft targets and immediate/multi-target sends use the captured choice.
start=source.index('    private void openForward(boolean fromActionBar, boolean withoutAuthor)')
picker=source[start:source.index('    public void showBottomOverlayProgress',start)]
assert picker.index('isPeerNoForwards() || hasSelectedNoforwardsMessage()') < picker.index('new DialogsActivity')
assert 'nebulaDidSelectDialogs(picker, peers, comment, param, notify, date, repeat, topics, withoutAuthor)' in picker
assert 'return nebulaDidSelectDialogs(fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment, false);' in source
assert 'sendMessage(fmessages, did, withoutAuthor, false, notify, scheduleDate, scheduleRepeatPeriod' in source
assert 'chatActivity.nebulaShowForwardDraft(fmessages, withoutAuthor);' in source
assert '                    nebulaShowForwardDraft(fmessages, withoutAuthor);' in source
start=source.index('    private void nebulaShowForwardDraft(')
draft=source[start:source.index('    public void showFieldPanelForForward',start)]
assert draft.index('hideForwardSendersName = withoutAuthor') < draft.index('showFieldPanelForForward(true, messages)')
assert 'if (withoutAuthor) messagePreviewParams.hideCaption = false;' in draft
assert 'selectedMessagesCountTextView.setGravity(Gravity.CENTER)' in source
assert source.index('headerItem.lazilyAddSubItem(0x4e4753') < source.index('headerItem.lazilyAddSubItem(0x4e4443')
print('Forward routing/menu guards passed (not on-device interaction verification)')

start=source.index('    private boolean nebulaWithoutAuthorAction()')
mode=source[start:source.index('    private void addToSelectedMessages(', start)]
assert 'NebulaFeatureSettings.enabled("selection_without_author")' in mode
prefs=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFeatureSettings.java').read_text(encoding='utf-8')
assert '"selection_without_author".equals(key)' not in prefs  # no default-on exception
footer=(Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui/Components/chat/layouts/ChatActivityActionsButtonsLayout.java').read_text(encoding='utf-8')
assert 'if (withoutAuthorMode == enabled) return;' in footer
assert 'addTextView(replyButton, LocaleController.getString(R.string.Reply), R.drawable.input_reply, false);' in footer
assert 'addTextView(forwardButton, LocaleController.getString(R.string.Forward), R.drawable.input_forward, true);' in footer
assert 'enabled ? R.drawable.nebula_input_reply_solar : R.drawable.input_reply, enabled' in footer
assert 'enabled ? R.drawable.nebula_input_forward_solar : R.drawable.input_forward, !enabled' in footer
print('Native footer defaults and reversible opt-in guards passed')
