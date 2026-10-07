"""Exercise native AI composer/edit eligibility, provider fallback and caption spacing."""
from pathlib import Path
import subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1];native=Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui';ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
def method(source,signature):
 start=source.index(signature);end=source.index('{',start)+1;depth=1
 while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
replacement=method((ui/'NebulaAiReplacements.java').read_text(encoding='utf-8'),'public static boolean editor(')
description=method((ui/'NebulaSettingsHero.java').read_text(encoding='utf-8'),'public static String descriptionText(')
source=r"""import app.nebulagram.ui.NebulaComposerAi;import app.nebulagram.ui.NebulaComposerSlots;import java.util.*;
class ControlCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class Prefs {Map<String,Boolean> values=new HashMap<>();boolean getBoolean(String k,boolean fallback){return values.getOrDefault(k,fallback);}}
 static class NebulaAiOptions {static Prefs p=new Prefs();static Prefs prefs(){return p;}}
 static class NebulaTranslationSettings {static boolean legacy;static boolean shortcut(){return legacy;}}
 REPLACEMENT
 DESCRIPTION
 public static void main(String[]args){
  boolean[][] cases={
   {true,false,true,false,true,false,true},
   {false,false,true,false,true,false,false},
   {false,true,true,false,true,false,true},
   {true,true,false,false,true,false,false},
   {false,false,false,true,true,false,true},
   {true,true,true,false,false,false,false},
   {true,true,true,false,true,true,false},
   {false,true,true,true,true,true,false},
   {false,false,false,false,true,false,false}};
  for(boolean[] c:cases)check(NebulaComposerAi.visible(c[0],c[1],c[2],c[3],c[4],c[5])==c[6],"native/edit/rich/secret/pause eligibility");
  for(boolean legacy:new boolean[]{false,true}){
   NebulaTranslationSettings.legacy=legacy;NebulaAiOptions.p.values.clear();check(editor()==legacy,"retain previous explicit shortcut opt-in");
   NebulaAiOptions.p.values.put("replace_editor",false);check(!editor(),"explicit off always restores Telegram, even with old shortcut on");
   NebulaAiOptions.p.values.put("replace_editor",true);check(editor(),"explicit on routes editor to Nebula");
  }
  check(descriptionText(null).isEmpty()&&descriptionText("  ").isEmpty(),"empty description");
  check(descriptionText(" Настройте приложение. ").equals("Настройте приложение"),"remove terminal description dot");
  check(descriptionText("Настройте v1.2. Сейчас.").equals("Настройте v1.2. Сейчас"),"retain meaningful internal punctuation");
  var modals=new NebulaComposerAi.Modals();modals.opened();modals.opened();check(modals.closed()&&modals.active(),"closing tools must not resume translation while the editor is still open");check(!modals.closed()&&!modals.closed(),"editor close resumes once without negative state");
  int captions=0;
  for(int density:new int[]{1,2,3,4})for(int width:new int[]{260,320,393,600})for(int margin:new int[]{12,32,65,100}) {
   int nativeRight=margin*density,inset=NebulaComposerSlots.captionInset(nativeRight,52*density,4*density);
   check(width*density-nativeRight-inset<=width*density-52*density-4*density,"caption controls retain confirmation spacing");captions++;
  }
  System.out.println(cases.length+" native AI edit/compose states, provider on/off compatibility, description punctuation and "+captions+" caption layouts passed");
 }
}""".replace('REPLACEMENT',replacement).replace('DESCRIPTION',description)
with tempfile.TemporaryDirectory(prefix='nebula-ai-control-') as folder:
 target=Path(folder)/'ControlCheck.java';target.write_text(source,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(target),str(ui/'NebulaComposerAi.java'),str(ui/'NebulaComposerSlots.java')],check=True)
 subprocess.run(['java','-cp',folder,'ControlCheck'],check=True)
composer=(native/'Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
measure=composer[composer.index('\n    protected void onMeasure(int widthMeasureSpec'):composer.index('\n    protected void onLayout(boolean changed')]
layout=composer[composer.index('\n    protected void onLayout(boolean changed'):]
assert measure.index('updateFieldRight(lastAttachVisible)') < measure.index('nebulaComposerStyle.prepare(') < measure.index('super.onMeasure(')
assert 'nebulaComposerStyle.layout(emojiButton, attachButton, senderSelectView, aiButton, richButton);\n        updateFieldRight' not in layout
assert 'NebulaMessageToolsFragment.show(parentFragment' in composer
assert 'nebula_ai_outline' in composer and 'nebulaToolsButton' not in composer
assert 'aiButton.setOnLongClickListener' in composer
show=composer[composer.index('private void showAiButton('):composer.index('private boolean shownRichButton')]
assert show.index('aiButton.setImageDrawable(aiButtonIcon)') < show.index('if (shownAiButton == show) return')
assert 'NebulaComposerAi.visible' in show and 'NebulaTranslationSettings.shortcut()' not in show
assert 'aiButton.animate().cancel();' in show and 'if (!show && !shownAiButton)' in show
assert 'aiButton.setEnabled(show)' in show
assert 'editingMessageObject != null' in show
assert 'recordingAudioVideo || isPaused' in show
assert 'doneEditingMessage();' in composer and '.setOnUseRich(this::saveRichDraft)' in composer
assert 'nebulaAiModals.opened()' in composer and 'nebulaToolsVisible = nebulaAiModals.closed()' in composer
assert '!nebulaToolsVisible && !recordingAudioVideo' in composer
assert 'if (onDismiss != null) onDismiss.run()' in (ui/'NebulaMessageToolsFragment.java').read_text(encoding='utf-8')
fragment=(native/'ActionBar/BaseFragment.java').read_text(encoding='utf-8')
assert 'params.maxHeightFraction > 0f' in fragment and 'Math.min(available,' in fragment
assert 'fragment.onPause();' in fragment and 'fragment.onFragmentDestroy();' in fragment
home=(native/'DialogsActivity.java').read_text(encoding='utf-8')
assert home.index('nebulaHeaderGlass.draw(canvas)') < home.index('if (top && nebulaFlatHomeHeader())')
assert 'if (actionBar.isNebulaSharedHeaderGlass()) containersAlpha = 1f;' in home
print('Native pre-measure ownership, attached-sheet lifecycle and full-header material wired')

caption=(native/'Components/CaptionPhotoViewer.java').read_text(encoding='utf-8')
measure=caption[caption.index('protected void onMeasure('):caption.index('private void updateNebulaAiInsets()')]
assert measure.index('updateNebulaAiInsets();') < measure.index('super.onMeasure(')
assert 'captionInset(container.rightMargin, dp(52), dp(4))' in caption
assert 'nebulaOriginalRightMargin + dp(44) + inset' in caption
assert 'Gravity.TOP : Gravity.BOTTOM' in caption
print('Caption pre-measure action spacing and stable original text margin wired')
