"""Check trailing hit areas and the native measure/sheet integration (not device pixels)."""
from pathlib import Path
import subprocess, sys, tempfile
root = Path(__file__).resolve().parents[1]
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
with tempfile.TemporaryDirectory(prefix='nebula-composer-') as tmp:
    test = Path(tmp) / 'SlotsCheck.java'
    test.write_text(r'''import app.nebulagram.ui.NebulaComposerSlots;
class SlotsCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
  for(int density:new int[]{1,2,3,4})for(boolean ios:new boolean[]{false,true})
  for(int width:new int[]{260,320,393,600})for(int nativeInset:new int[]{2,50,98,146})for(int gift:new int[]{0,48}){
   int w=width*density, base=nativeInset*density;
   int inset=NebulaComposerSlots.toolsInset(base,50*density,6*density,ios,gift*density);
   int right=w-inset, left=right-44*density, textRight=w-inset-44*density-6*density;
   check(right<=w-base-gift*density-6*density,"AI overlaps native actions");
   if(ios)check(right<=w-50*density-gift*density-6*density,"AI overlaps moved emoji/gift");
   check(textRight<left,"text overlaps shortcut hit area");
   for(int frame=0;frame<200;frame++)check(inset==NebulaComposerSlots.toolsInset(base,50*density,6*density,ios,gift*density),"layout accumulates inset");
   cases++;
  }
  System.out.println(cases+" trailing-control cases across empty/typed/action/gift states passed");
 }
}''',encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',tmp,str(ui/'NebulaComposerSlots.java'),str(test)],check=True)
    subprocess.run(['java','-cp',tmp,'SlotsCheck'],check=True)
composer=(native/'Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
measure=composer[composer.index('\n    protected void onMeasure(int widthMeasureSpec'):composer.index('\n    protected void onLayout(boolean changed')]
layout=composer[composer.index('\n    protected void onLayout(boolean changed'):]
assert measure.index('updateFieldRight(lastAttachVisible)') < measure.index('nebulaComposerStyle.prepare(') < measure.index('super.onMeasure(')
assert 'nebulaComposerStyle.layout(emojiButton, attachButton, senderSelectView, aiButton, richButton);\n        updateFieldRight' not in layout
assert 'NebulaMessageToolsFragment.show(parentFragment' in composer
assert 'nebula_ai_outline' in composer
assert '!nebulaToolsVisible && !recordingAudioVideo' in composer
assert 'if (onDismiss != null) onDismiss.run()' in (ui/'NebulaMessageToolsFragment.java').read_text(encoding='utf-8')
fragment=(native/'ActionBar/BaseFragment.java').read_text(encoding='utf-8')
assert 'params.maxHeightFraction > 0f' in fragment and 'Math.min(available,' in fragment
assert 'fragment.onPause();' in fragment and 'fragment.onFragmentDestroy();' in fragment
home=(native/'DialogsActivity.java').read_text(encoding='utf-8')
assert home.index('nebulaHeaderGlass.draw(canvas)') < home.index('if (top && nebulaFlatHomeHeader())')
assert 'if (actionBar.isNebulaHomeTabsGlass()) containersAlpha = 1f;' in home
print('Native pre-measure ownership, attached-sheet lifecycle and full-header material wired')
