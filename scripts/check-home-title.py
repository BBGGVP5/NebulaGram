"""Run production title geometry and check native measure/layout integration (not pixels)."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
helper = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaHomeTitleGeometry.java'
assert helper.exists(), 'Missing adaptive centered title geometry: hidden stories still reserve phantom left buttons'
with tempfile.TemporaryDirectory(prefix='nebula-home-title-') as directory:
    work = Path(directory)
    test = work / 'TitleCheck.java'
    test.write_text('''import app.nebulagram.ui.NebulaHomeTitleGeometry;
class TitleCheck {
 static void check(boolean ok) { if (!ok) throw new AssertionError(); }
 public static void main(String[] args) {
  check(NebulaHomeTitleGeometry.width(448,18,160,150)==150);
  check(NebulaHomeTitleGeometry.left(448,18,160,150)==138);
  check(NebulaHomeTitleGeometry.width(448,18,160,400)==270);
  check(NebulaHomeTitleGeometry.left(448,18,160,270)==18);
  check(NebulaHomeTitleGeometry.width(448,18,160,80)==128);
  check(NebulaHomeTitleGeometry.left(448,18,160,128)==160);
  int cases=0;
  for(int density:new int[]{1,2,3,4}) for(int screen:new int[]{100,320,360,393,448,600,900})
  for(int start:new int[]{18,72,160}) for(int end:new int[]{16,64,112,160})
  for(int text:new int[]{0,40,120,150,220,500}) {
   int w=screen*density, l=start*density, r=end*density, content=text*density;
   int available=Math.max(0,w-l-r);
   int size=NebulaHomeTitleGeometry.width(w,l,r,content);
   int x=NebulaHomeTitleGeometry.left(w,l,r,size);
   check(size>=0 && size<=available);
   check(size>=Math.min(content,available));
   check(x>=l);
   if(available>0) check(x+size<=w-r);
   int ideal=(w-size)/2;
   if(ideal>=l && ideal+size<=w-r) check(x==ideal);
   cases++;
  }
  System.out.println("PASS: "+cases+" production title geometry cases, including hidden-story regression");
 }
}''', encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(helper), str(test)], check=True)
    subprocess.run(['java', '-cp', str(work), 'TitleCheck'], check=True)

bar = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
assert 'width - 2 * Math.max(textLeft, end)' not in bar, 'Obsolete symmetric-only measurement'
assert 'NebulaHomeTitleGeometry.width(' in bar and 'NebulaHomeTitleGeometry.left(' in bar
assert 'titleTextView[i].getTextWidth() + titleTextView[i].getSideDrawablesSize()' in bar
# Definition, title measurement, title layout, and the subtitle overlay laid
# out under that title: one boundary for everything sharing the centred slot.
assert bar.count('nebulaTitleEndInset()') == 4, 'Measurement/layout must use the same menu boundary'
assert 'additionalSubTitleOverlayContainer.getMeasuredWidth())' in bar, 'Overlay subtitle must sit under the centred title'
assert 'titlesContainer.getTranslationX()' in bar
assert 'if (nebulaCenterTitle())' in bar
assert 'glassMode && !nebulaHomeGlass ? 17' in bar, 'Glass home title must keep normal title size'
dialogs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
assert 'setNebulaHomeGlass(true)' in dialogs and 'setNebulaHomeTabsGlass(true)' in dialogs
assert 'nebulaHomeTabsGlass ? 4 : 16' in bar, 'Home title must use the compact grouped-action boundary'
assert 'int first = Integer.MAX_VALUE;' in bar and 'int last = Integer.MIN_VALUE;' in bar
assert 'glassDrawableMenu.setBounds(Math.max(0, first - dp(4)), t,' in bar
assert 'glassDrawable.setAlpha(0);' in bar, 'Home actions own separate glass surfaces'
assert 'nebulaHomeTabsGlass && actionModeFactor > 0f && actionMode != null' in bar
assert 'glassDrawableBack.setAlpha(Math.round(255 * actionModeFactor))' in bar
assert 'actionBar.setSearchFactor(searchFieldVisible)' in dialogs
assert 'NebulaLinkShortcut.setHeaderSearchProgress(this, searchFieldVisible)' in dialogs
assert 'showOrUpdateActionMode(0, null)' in dialogs, 'Home Edit must enter chat selection'
assert 'selectedDialogs.isEmpty() && !nebulaHomeEditing' in dialogs, 'Edit mode must remain open with no chats selected'
assert 'initialDialogsType == DIALOGS_TYPE_DEFAULT && nebulaHomeEditButton == null' in dialogs, 'Main glass header must omit overflow'
assert bar.count('if (nebulaHomeTabsGlass) textLeft = Math.max(textLeft, dp(84));') == 2, 'The wider Edit button needs matching measure and layout insets'
folder_title = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFolderTitleView.java').read_text(encoding='utf-8')
assert 'AndroidUtilities.displaySize.x / 2' not in folder_title, 'Folder title must use available parent width'
print('PASS: native title slots include status drawable and share menu/translation bounds')

assert "titleTop + titleTextView[i].getMeasuredHeight()" in bar, "Emoji title must retain both measured paddings"
assert "actionBar.setNebulaTitle(title, targetStatus, changed)" in (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaDialogsTitle.java').read_text(encoding='utf-8')
subprocess.run([sys.executable, str(root / 'scripts/check-title-animation.py'), str(tree)], check=True)
