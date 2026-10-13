"""Execute the production shared story/title slot and verify native integration."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
with tempfile.TemporaryDirectory(prefix='nebula-reference-home-') as directory:
    work = Path(directory)
    test = work / 'ReferenceHeaderCheck.java'
    test.write_text('''import app.nebulagram.ui.NebulaHomeTitleGeometry;
class ReferenceHeaderCheck {
 static int dp(float n,float density){return (int)Math.ceil(n*density);}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){int cases=0;
  for(float density:new float[]{1,1.5f,2,2.625f,3.5f})
  for(int screen:new int[]{320,360,393,448,600,900})
  for(int actions:new int[]{2,3,4,5})for(int count=0;count<=3;count++)
  for(int text:new int[]{60,96,160,260}) {
   int width=dp(screen,density),left=dp(84,density),right=dp(actions*44+9,density);
   int available=Math.max(0,width-left-right),title=Math.min(dp(text,density),available);
   int footprint=dp(34,density),step=dp(16,density),gap=dp(8,density);
   int visible=NebulaHomeTitleGeometry.visibleStories(count,available,title,footprint,step,gap);
   int prefix=NebulaHomeTitleGeometry.storyPrefix(visible,footprint,step,gap);
   check(visible>=0 && visible<=count,"Invalid story count");
   check(title+prefix<=available,"Story rings steal space from the title");
   if(visible<count)check(title+NebulaHomeTitleGeometry.storyPrefix(visible+1,footprint,step,gap)>available,"Unnecessarily hidden story");
   int previous=-1;
   for(int frame=0;frame<=60;frame++){
    float progress=frame/60f;int animated=Math.round(prefix*progress);
    int x=NebulaHomeTitleGeometry.groupLeft(width,left,right,title,prefix,progress);
    int titleLeft=x+animated;
    check(x>=left,"Stories overlap Edit");
    if(available>0)check(titleLeft+title<=width-right,"Title overlaps toolbar actions");
    if(previous>=0)check(Math.abs(titleLeft-previous)<=Math.ceil(prefix/60f)+1,"Title jumps during collapse");
    previous=titleLeft;
    if(frame==60 && visible>0)check(x+footprint+step*(visible-1)+gap==titleLeft,"Full ring footprint overlaps title");
    if(count==0)check(animated==0,"Hidden stories reserve phantom space");
    cases++;
   }
  }
  System.out.println(cases+" shared native story/title geometry cases passed");
 }
}''', encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaHomeTitleGeometry.java'),str(test)],check=True)
    subprocess.run(['java','-cp',str(work),'ReferenceHeaderCheck'],check=True)

tree=Path(sys.argv[1])
bar=(tree/'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
stories=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java').read_text(encoding='utf-8')
dialogs=(tree/'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')

def method(source, signature):
    start=source.index(signature)
    end=source.index('{',start)+1
    depth=1
    while depth:
        depth+=(source[end]=='{')-(source[end]=='}')
        end+=1
    return source[start:end]

methods='\n'.join(method(bar, signature) for signature in [
    'public void setNebulaHomeStories(', 'public int getNebulaHomeVisibleStories()',
    'private int nebulaHomeStoryPrefix()', 'private int nebulaHomeTitleWidth()',
    'public float getNebulaHomeStoriesLeft()', 'public float getNebulaHomeTitleLeft()',
    'private void applyNebulaHomeStoryLayout()',
])
actions=method(dialogs,'private void checkUi_nebulaHomeActions()')
with tempfile.TemporaryDirectory(prefix='nebula-native-home-') as directory:
    work=Path(directory)
    stubs={
      'app/nebulagram/ui/NebulaAppearance.java': 'package app.nebulagram.ui; public class NebulaAppearance {public static boolean camera,compose;public static boolean hideHomeCamera(){return camera;}public static boolean hideHomeCompose(){return compose;}}',
      'app/nebulagram/ui/NebulaAiSheet.java': 'package app.nebulagram.ui; public class NebulaAiSheet {public static boolean ai;public static boolean homeEnabled(){return ai;}}',
      'NativeHeaderCheck.java': '''import app.nebulagram.ui.*;
class NativeHeaderCheck {
 static final int GONE=8;
 static class SimpleTextView {int left=110,width=96,visibility;float translation;
  int getVisibility(){return visibility;}int getMeasuredWidth(){return width;}int getLeft(){return left;}
  void setTranslationX(float x){translation=x;}}
 static class Bar {boolean nebulaHomeLayout=true,nebulaHomeTabsGlass=false;int nebulaHomeStoryCount,nebulaHomeVisibleStories,layouts;
  float nebulaHomeStoryProgress;int width=393,end=97;
  SimpleTextView[] titleTextView={new SimpleTextView(),null};SimpleTextView additionalSubTitleOverlayContainer=new SimpleTextView();
  int dp(float x){return Math.round(x);}int getMeasuredWidth(){return width;}int nebulaTitleEndInset(){return end;}
  void requestLayout(){layouts++;}
''' + methods + '''
 }
 static class Item {float alpha;boolean enabled;void setEnabled(boolean v){enabled=v;}}
 static class FragmentFloatingButton {static void setAnimatedVisibility(Item i,float f){if(i!=null)i.alpha=f;}}
 static class Factor {float value;float getFloatValue(){return value;}}
 static class Host {float progressToActionMode,sliding;Factor animatorActionModeVisible=new Factor(),animatorSearchVisible=new Factor(),animatorDoneButtonVisible=new Factor();
  Item nebulaHomeStoryItem=new Item(),nebulaHomeComposeItem=new Item();float getRightSlidingProgress(){return sliding;}
''' + actions + '''
 }
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){Bar b=new Bar();
  b.setNebulaHomeStories(3,0);check(b.layouts==1,"Count must trigger measurement");b.nebulaHomeVisibleStories=3;
  for(int frame=0;frame<=60;frame++){
   b.setNebulaHomeStories(3,frame/60f);float x=b.titleTextView[0].left+b.titleTextView[0].translation;
   check(Math.abs(x-b.getNebulaHomeTitleLeft())<.001f,"Native title not at shared slot");
   check(b.additionalSubTitleOverlayContainer.left+b.additionalSubTitleOverlayContainer.translation==x,"Status subtitle detached");
   check(b.layouts==1,"Collapse animation repeatedly requests measurement");
  }
  b.setNebulaHomeStories(0,1);check(b.getNebulaHomeVisibleStories()==0 && b.layouts==2,"Removed stories retain space");
  b.setNebulaHomeStories(0,1);check(b.layouts==2,"Duplicate update relayouts");
  b.nebulaHomeLayout=false;b.setNebulaHomeStories(3,1);check(b.layouts==2,"Unrelated header changed");
  b.nebulaHomeLayout=true;
  for(boolean material:new boolean[]{false,true}){b.nebulaHomeTabsGlass=material;b.setNebulaHomeStories(2,1);check(b.getNebulaHomeVisibleStories()<=2,"Glass preference changes layout ownership");}
  int cases=0;
  for(int state=0;state<5;state++)for(int flags=0;flags<8;flags++) {
   Host h=new Host();NebulaAppearance.camera=(flags&1)!=0;NebulaAppearance.compose=(flags&2)!=0;NebulaAiSheet.ai=(flags&4)!=0;
   if(state==1)h.progressToActionMode=1;if(state==2)h.animatorSearchVisible.value=1;
   if(state==3)h.sliding=1;if(state==4)h.animatorDoneButtonVisible.value=1;
   h.checkUi_nebulaHomeActions();boolean story=state==0&&(!NebulaAppearance.camera||NebulaAiSheet.ai);
   boolean compose=state==0&&!NebulaAppearance.compose;
   check(h.nebulaHomeStoryItem.enabled==story && (h.nebulaHomeStoryItem.alpha>0)==story,"Story/AI lifecycle or preference mismatch");
   check(h.nebulaHomeComposeItem.enabled==compose && (h.nebulaHomeComposeItem.alpha>0)==compose,"Compose lifecycle or preference mismatch");cases++;
  }
  Host partial=new Host();partial.animatorSearchVisible.value=.5f;partial.checkUi_nebulaHomeActions();
  check(!partial.nebulaHomeComposeItem.enabled && !partial.nebulaHomeStoryItem.enabled,"Transition remains clickable");
  Host absent=new Host();absent.nebulaHomeStoryItem=absent.nebulaHomeComposeItem=null;absent.checkUi_nebulaHomeActions();
  System.out.println("61 native collapse frames avoid relayout; "+cases+" native toolbar action states passed");
 }
}''',
    }
    for name,code in stubs.items():
        file=work/name;file.parent.mkdir(parents=True,exist_ok=True);file.write_text(code,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaHomeTitleGeometry.java'),*[str(work/name) for name in stubs]],check=True)
    subprocess.run(['java','-cp',str(work),'NativeHeaderCheck'],check=True)

assert 'applyNebulaHomeStoryLayout()' in bar, 'Title does not follow the shared story slot'
assert 'actionBar.setNebulaHomeStories(count,' in method(stories,'private void publishNebulaHomeHeader()'), 'Collapse is disconnected from title geometry'
assert 'publishNebulaHomeHeader()' in method(stories,'private void checkCollapsedProgress()')
assert 'setNebulaHomeStories(' not in method(stories,'protected void dispatchDraw(Canvas canvas)'), 'Paint is mutating a previously painted sibling title'
assert 'getNebulaHomeStoriesLeft()' in stories and 'getNebulaHomeVisibleStories()' in stories
assert 'NebulaHomeTitleGeometry.avatarCenterY(' in stories, 'Story rings must follow the continuous header trajectory'
assert 'nebulaHomeStoriesClip' not in stories, 'Rectangular mask cuts story rings during expansion'
assert 'nebulaHomeStoryItem = menu.addItemWithWidth' in dialogs
assert 'nebulaHomeComposeItem = menu.addItemWithWidth' in dialogs
assert 'nebula_header_add_story' in dialogs and 'nebula_header_compose' in dialogs
assert 'checkUi_nebulaHomeActions()' in dialogs
assert 'nebulaHomeEditButton.setOnLongClickListener' in dialogs, 'Home options became inaccessible'
assert 'isNebulaSharedHeaderGlass() && !actionBar.isNebulaHomeTabsGlass()' in dialogs, 'Home still paints the whole rounded glass panel'
assert 'glassDrawableHomeLeading.draw(canvas)' in bar, 'Edit has no separate glass backing'
assert 'nebulaHomeTabsGlass && actionModeFactor < 0.01f' in bar
assert 'getBoolean("home_chats_title", false)' in (ui/'NebulaAppearance.java').read_text(encoding='utf-8')
assert '!NebulaAppearance.homeChatsTitle() && NebulaAppearance.folderTitle()' in (ui/'NebulaDialogsTitle.java').read_text(encoding='utf-8')
print('Reference Android toolbar, native actions and shared story slot are connected')

root_setup=dialogs[dialogs.index("        nebulaHomeEditButton = null;"):dialogs.index("        if (initialDialogsType == DIALOGS_TYPE_DEFAULT && !onlySelect && !inPreviewMode\n                && communityId != 0") ]
assert "&& app.nebulagram.ui.NebulaAppearance.homeGlassHeader()" not in root_setup
assert "setNebulaHomeGlass(homeMaterial)" in root_setup and "setNebulaHomeTabsGlass(homeMaterial)" in root_setup
assert "nebulaHomeLayout = view != null" in bar
print("Home composition and story slot remain active with the glass material disabled")
