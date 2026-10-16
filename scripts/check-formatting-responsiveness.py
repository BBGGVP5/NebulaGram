"""Execute actual IME refresh, overlay measure, newline and nonblocking preference code."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native=(tree/'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
bar=(ui/'NebulaFormattingToolbar.java').read_text(encoding='utf-8')

def body(source, signature):
    start=source.index('{',source.index(signature));end=start+1;depth=1
    while depth:depth+=(source[end]=='{')-(source[end]=='}');end+=1
    return source[start+1:end-1]

window=body(native,'private void onWindowSizeChanged()')
keyboard=window[:window.index('        int size =')]
measure=body(bar,'protected void onMeasure(').replace('super.onMeasure(widthMeasureSpec, heightMeasureSpec);','childMeasures++;')
newline=body(bar,'private void insertNewLine()')
send=body(native,'protected boolean sendMessageInternal(')
preference=next(line.strip() for line in send.splitlines() if '.putBoolean("silent_"' in line)
assert 'commit(' not in preference
# Accessory code must not gate, queue or intercept transport or native send controls.
assert 'nebulaFormatting' not in send
assert 'sendMessage' not in bar and 'performEditorAction' not in bar and 'dispatchKeyEvent' not in bar
draft=(ui/'NebulaDraftTranslation.java').read_text(encoding='utf-8')
start=draft.index('        if (!allowed || !NebulaTranslationSettings.draft(account, dialog) || user <= 0')
guard=draft[start:draft.index('        CharSequence source =',start)]
program=r'''
import app.nebulagram.ui.NebulaFormattingSelection;
class ResponsivenessCheck {
 Object nebulaFormattingToolbar=new Object();boolean nebulaFormattingKeyboardVisible,keyboardVisible;int layouts,childMeasures;
 void requestLayout(){layouts++;}void keyboard(){KEYBOARD}
 static class MeasureSpec {static int EXACTLY=0x40000000;static int getMode(int s){return s&0xC0000000;}static int getSize(int s){return s&0x3FFFFFFF;}}
 void setMeasuredDimension(int w,int h){}void measure(int widthMeasureSpec,int heightMeasureSpec){MEASURE}
 static class Draft extends StringBuilderStub {Draft(String s){super(s);}}
 static class StringBuilderStub {StringBuilder b;StringBuilderStub(String s){b=new StringBuilder(s);}void replace(int a,int z,String s){b.replace(a,z,s);}}
 static class Editor {Draft draft;int a,z;Editor(String s,int a,int z){draft=new Draft(s);this.a=a;this.z=z;}int length(){return draft.length();}Draft getText(){return draft;}int getSelectionStart(){return a;}int getSelectionEnd(){return z;}void setSelection(int p){a=z=p;}}
 // CharSequence lets the production range helper inspect the same mutable test draft.
 Editor editor;void newline(){NEWLINE}
 static class Notifications {static int applies;Notifications edit(){return this;}Notifications putBoolean(String k,boolean v){if(!k.equals("silent_42"))throw new AssertionError();return this;}void apply(){applies++;}boolean commit(){throw new AssertionError("UI thread waits for disk");}}
 static class MessagesController {static Notifications getNotificationsSettings(int a){return new Notifications();}}
 int currentAccount;long dialog_id=42;boolean notify=true;void beforeSend(){PREFERENCE}
 static class NebulaTranslationSettings {static boolean enabled;static boolean draft(int a,long d){return enabled;}}
 static class DialogObject {static boolean isEncryptedDialog(long d){return d<0;}}
 static class Original {void clear(){}}
 Original original=new Original();int stops,snapshots;void stop(){stops++;}void draft(boolean allowed,int account,long dialog,long user){GUARD snapshots++;}
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 public static void main(String[]args){ResponsivenessCheck t=new ResponsivenessCheck();
 for(int i=0;i<1000;i++){t.keyboardVisible=true;t.keyboard();t.keyboard();t.keyboardVisible=false;t.keyboard();t.keyboard();}
 check(t.layouts==2000,"Repeated layout/typing schedules redundant IME refresh");
 for(int i=0;i<1000;i++){t.measure(MeasureSpec.EXACTLY|360,MeasureSpec.EXACTLY);t.measure(MeasureSpec.EXACTLY|360,MeasureSpec.EXACTLY|52);}
 check(t.childMeasures==1000,"Every tile measured at zero then final accessory height");
 t.editor=new Editor("one two",3,3);t.newline();check(t.editor.draft.b.toString().equals("one\n two")&&t.editor.a==4,"newline at caret");
 t.editor=new Editor("one two",7,4);t.newline();check(t.editor.draft.b.toString().equals("one \n"),"newline replaces reversed selection");
 t.editor=new Editor("a🚀b",2,3);t.newline();check(t.editor.draft.b.toString().equals("a\nb"),"newline leaves broken surrogate");
 t.editor=new Editor("",0,0);for(int i=0;i<1000;i++){t.beforeSend();t.draft(true,0,42,1);}
 check(Notifications.applies==1000&&t.snapshots==0,"disabled translation copies draft per layout");
 NebulaTranslationSettings.enabled=true;t.draft(true,0,42,1);check(t.snapshots==1,"enabled translation suppressed");
 System.out.println("1000 keyboard cycles, cached overlay measure, UTF-16/newline edits, immediate preference application and disabled-translation bypass passed");
 }
}
'''.replace('KEYBOARD',keyboard).replace('MEASURE',measure).replace('NEWLINE',newline).replace('PREFERENCE',preference).replace('GUARD',guard)
program=program.replace('static class Draft extends StringBuilderStub','static class Draft extends StringBuilderStub implements CharSequence')
program=program.replace('Draft(String s){super(s);}','Draft(String s){super(s);} public int length(){return b.length();}public char charAt(int i){return b.charAt(i);}public CharSequence subSequence(int a,int z){return b.subSequence(a,z);}')
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-format-responsive-') as work:
    work=Path(work);test=work/'ResponsivenessCheck.java';test.write_text(program,encoding='utf-8')
    subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(test),str(ui/'NebulaFormattingSelection.java')],check=True)
    subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'ResponsivenessCheck'],check=True)
print('Transport/server latency is outside this fixture; toolbar does not own the send path.')
