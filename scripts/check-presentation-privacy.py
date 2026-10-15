"""Execute the production visual-mask state/cache/lifetime without personal data."""
from pathlib import Path
import os, re, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

def unit(text, signature):
    start = text.index(signature); brace = text.index('{', start); depth = 1; end = brace + 1
    while depth: depth += (text[end] == '{') - (text[end] == '}'); end += 1
    return text[start:end]

with tempfile.TemporaryDirectory(prefix='nebula-presentation-privacy-') as temporary:
    work = Path(temporary)
    if '--swift' in sys.argv:
        privacy = (root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaPresentationPrivacy.swift').read_text(encoding='utf-8')
        store = (root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaSettingsStore.swift').read_text(encoding='utf-8')
        observation = unit(store, 'public final class SettingsObservation')
        row = (root / 'platform/ios/overlay/submodules/ChatListUI/Sources/NebulaChatListPrivacy.swift').read_text(encoding='utf-8')
        lifetime = unit(row, 'private final class NebulaPrivateRowLifetime')
        manager = unit(row, 'enum NebulaChatListPrivacy')
        stubs = r'''
import Foundation
#if canImport(CoreGraphics)
import CoreGraphics
#endif
final class NebulaSettingsStore {
 static let shared=NebulaSettingsStore();private let center=NotificationCenter();private let changed=Notification.Name("changed");var calls=0
 func refreshPresentation(){center.post(name:changed,object:nil)}
 func observe(_ callback:@escaping()->Void)->SettingsObservation{let token=center.addObserver(forName:changed,object:nil,queue:nil){[weak self]_ in self?.calls += 1;callback()};return SettingsObservation(center:center,token:token)}
}
class UIView {var frame:CGRect;var isHidden=false;var children:[UIView]=[];init(frame:CGRect = .zero){self.frame=frame};func addSubview(_ child:UIView){children.append(child)};static func performWithoutAnimation(_ body:()->Void){body()}}
final class ASDisplayNode {let view=UIView();var association:AnyObject?}
final class PresentationTheme {let id:Int;init(_ n:Int){id=n}}
final class NebulaPrivateChatRow:UIView {static var created=0;var lastTheme=0;var russian=false;var archive=false;override init(frame:CGRect = .zero){Self.created += 1;super.init(frame:frame)};func update(theme:PresentationTheme,russian:Bool,archive:Bool){lastTheme=theme.id;self.russian=russian;self.archive=archive}}
private var nebulaPrivacyAssociation:UInt8=0
enum AssociationPolicy {case OBJC_ASSOCIATION_RETAIN_NONATOMIC}
func objc_getAssociatedObject(_ node:ASDisplayNode,_ key:UnsafeRawPointer)->Any?{node.association}
func objc_setAssociatedObject(_ node:ASDisplayNode,_ key:UnsafeRawPointer,_ value:Any,_ policy:AssociationPolicy){node.association=value as AnyObject}
'''
        test = r'''
@main struct Check {static func main(){
 let original=NebulaPresentationPrivacy.enabled;defer{NebulaPresentationPrivacy.setEnabled(original)};NebulaPresentationPrivacy.setEnabled(false)
 precondition(NebulaPresentationPrivacy.showStories(preference:true) && !NebulaPresentationPrivacy.showStories(preference:false))
 let store=NebulaSettingsStore.shared;var node:ASDisplayNode?=ASDisplayNode();let theme=PresentationTheme(1)
 for _ in 0..<1000 {NebulaChatListPrivacy.update(node:node!,size:CGSize(width:320,height:76),theme:theme,russian:true,archive:false)}
 precondition(node!.view.children.isEmpty && NebulaPrivateChatRow.created==0,"Disabled rows allocated UIKit masks")
 let before=store.calls;NebulaPresentationPrivacy.setEnabled(true);precondition(store.calls==before+1,"Repeated native layouts leaked observers")
 let mask=node!.view.children.first as! NebulaPrivateChatRow;precondition(!mask.isHidden && mask.russian && !mask.archive && mask.frame.width==320)
 precondition(!NebulaPresentationPrivacy.showStories(preference:true) && !NebulaPresentationPrivacy.showStories(preference:false))
 for _ in 0..<1000 {NebulaChatListPrivacy.update(node:node!,size:CGSize(width:320,height:76),theme:theme,russian:true,archive:false)}
 precondition(NebulaPrivateChatRow.created==1,"Idle layouts rebuilt the mask")
 NebulaChatListPrivacy.update(node:node!,size:CGSize(width:700,height:90),theme:PresentationTheme(2),russian:false,archive:true)
 precondition(mask.frame.width==700 && mask.lastTheme==2 && mask.archive && !mask.russian)
 NebulaPresentationPrivacy.setEnabled(false);precondition(mask.isHidden && NebulaPresentationPrivacy.showStories(preference:true))
 precondition(NebulaPresentationPrivacy.accountLabel(position:0,russian:true)=="Аккаунт 1" && NebulaPresentationPrivacy.accountLabel(position:2,russian:false)=="Account 3")
 node=nil;let delivered=store.calls;store.refreshPresentation();precondition(store.calls==delivered,"Destroyed row retained its observer")
 for _ in 0..<1000 {var current:ASDisplayNode?=ASDisplayNode();NebulaChatListPrivacy.update(node:current!,size:.zero,theme:theme,russian:true,archive:false);current=nil}
 let final=store.calls;store.refreshPresentation();precondition(store.calls==final,"Recycled rows leaked observation")
 print("Swift presentation privacy: default/restore stories, 1000 idle layouts without mask allocation, one live observer, 1000 destroyed rows, enable/disable, theme/size/locale and generic aliases passed")
}}
'''
        path = work / 'Check.swift'; path.write_text(stubs + '\n' + observation + '\n' + privacy + '\n' + lifetime + '\n' + manager + '\n' + test, encoding='utf-8')
        binary = work / 'check'; subprocess.run(['swiftc', '-parse-as-library', str(path), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
    else:
        files = {
            'android/graphics/Paint.java': '''package android.graphics;public class Paint{public static int created;public static final int ANTI_ALIAS_FLAG=1;public enum Align{LEFT,RIGHT}public int color;public float size;public Paint(int f){created++;}public void setColor(int c){color=c;}public void setTextSize(float n){size=n;}public void setTextAlign(Align a){}}''',
            'android/graphics/Canvas.java': '''package android.graphics;import java.util.*;public class Canvas{public List<String>text=new ArrayList<>();public int background;public void drawRect(float a,float b,float c,float d,Paint p){background=p.color;}public void drawRoundRect(float a,float b,float c,float d,float rx,float ry,Paint p){background=p.color;}public void drawCircle(float a,float b,float c,Paint p){}public void drawText(String s,float a,float b,Paint p){text.add(s);}}''',
            'android/text/TextPaint.java': '''package android.text;public class TextPaint extends android.graphics.Paint{public TextPaint(int f){super(f);}}''',
            'android/text/TextUtils.java': '''package android.text;public class TextUtils{public static int calls;public enum TruncateAt{END}public static CharSequence ellipsize(String s,TextPaint p,float width,TruncateAt mode){calls++;return s;}}''',
            'android/view/View.java': '''package android.view;public class View{public int width=320,height=76;public final Resources r=new Resources();public Object getContext(){return null;}public Resources getResources(){return r;}public int getWidth(){return width;}public int getHeight(){return height;}public static class Resources{public final Metrics m=new Metrics();public Metrics getDisplayMetrics(){return m;}}public static class Metrics{public float density=1,scaledDensity=1;}}''',
            'org/telegram/messenger/LocaleController.java': '''package org.telegram.messenger;public class LocaleController{public static boolean isRTL;}''',
            'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig{public static int[]getAccountIds(){return new int[]{0,127};}}''',
            'org/telegram/messenger/NotificationCenter.java': '''package org.telegram.messenger;public class NotificationCenter{public static int calls;public static final int dialogsNeedReload=1,storiesUpdated=2;public static NotificationCenter getInstance(int a){return new NotificationCenter();}public void postNotificationName(int id){calls++;}}''',
            'app/nebulagram/ui/NebulaFeatureSettings.java': '''package app.nebulagram.ui;public class NebulaFeatureSettings{static boolean mask;public static boolean enabled(String k){return mask;}public static void set(String k,boolean v){mask=v;}}''',
            'app/nebulagram/ui/NebulaText.java': '''package app.nebulagram.ui;public class NebulaText{static boolean ru=true;public static String text(String a,String b){return ru?a:b;}}''',
            'app/nebulagram/ui/NebulaTheme.java': '''package app.nebulagram.ui;public class NebulaTheme{static int ink=2;static final NebulaTheme value=new NebulaTheme();public static NebulaTheme of(Object c){return value;}public int surface(){return 0x66123456;}public int onSurface(){return ink;}public int onSurfaceVariant(){return 3;}}''',
            'app/nebulagram/ui/Check.java': '''package app.nebulagram.ui;import android.view.View;import android.graphics.*;import android.text.*;import org.telegram.messenger.*;public class Check{static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[]a){
 check(!NebulaPresentationPrivacy.enabled());NebulaPresentationPrivacy.setEnabled(true);check(NebulaPresentationPrivacy.enabled()&&NotificationCenter.calls==4);
 View row=new View();Canvas canvas=new Canvas();NebulaPresentationPrivacy.draw(canvas,row,false);int paints=Paint.created,fits=TextUtils.calls;
 for(int i=0;i<1000;i++)NebulaPresentationPrivacy.draw(canvas,row,false);check(Paint.created==paints&&TextUtils.calls==fits);check(canvas.background==0xff123456);for(String s:canvas.text)check(s.equals("Чат скрыт")||s.equals("Предпросмотр скрыт"));
 NebulaTheme.ink=7;NebulaPresentationPrivacy.draw(canvas,row,false);check(TextUtils.calls==fits);row.r.m.density=2;NebulaPresentationPrivacy.draw(canvas,row,false);check(TextUtils.calls==fits+2);
 row.width=100;row.r.m.scaledDensity=2;LocaleController.isRTL=true;NebulaText.ru=false;NebulaPresentationPrivacy.draw(canvas,row,true);check(canvas.text.get(canvas.text.size()-2).equals("Archive"));
 NebulaPresentationPrivacy.setEnabled(false);check(!NebulaPresentationPrivacy.enabled());
 System.out.println("Java production privacy renderer: opaque cover, generic text only, 1000 cached draws, foreground-only theme update, density/width/font/RTL/locale and live refresh passed");}}
'''
        }
        sources = [str(ui / 'NebulaPresentationPrivacy.java')]
        for name, value in files.items():
            path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(value, encoding='utf-8'); sources.append(str(path))
        home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work), *sources], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', str(work), 'app.nebulagram.ui.Check'], check=True)
        sdk = Path(os.environ.get('ANDROID_HOME') or str(Path.home() / 'AppData/Local/Android/Sdk'))
        jars = [path for path in (sdk / 'platforms').glob('android-*/android.jar') if re.fullmatch(r'android-\d+(?:\.\d+)*', path.parent.name)]
        if not jars: raise SystemExit('Android SDK required for the production renderer API check')
        jar = max(jars, key=lambda path: tuple(map(int, path.parent.name.removeprefix('android-').split('.'))))
        sdk_sources = [str(ui / 'NebulaPresentationPrivacy.java')] + [str(work / name) for name in files if not name.startswith('android/') and not name.endswith('/Check.java')]
        output = work / 'sdk'; output.mkdir()
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(output), *sdk_sources], check=True)
        print('Actual production privacy renderer compiles against Android SDK')

android_patch = (root / 'patches/android/0226-chat-list-presentation-privacy.patch').read_text(encoding='utf-8')
assert all(value in android_patch for value in ['onDraw(Canvas canvas)', 'info.setText(label)', 'event.getText().clear()', 'event.setContentDescription(label)'])
assert 'if (!nebulaMask)' in android_patch and 'cornersRadius)' in android_patch
ios_patch = (root / 'patches/ios/0114-chat-list-presentation-privacy.patch').read_text(encoding='utf-8')
assert 'NebulaChatListPrivacy.update(node:' in ios_patch and 'if NebulaPresentationPrivacy.enabled { return nil }' in ios_patch
assert 'update(node: strongSelf.contextContainer' in ios_patch
for path in ['platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaAccountsFragment.java', 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaAccountsController.swift']:
    source = (root / path).read_text(encoding='utf-8'); assert 'NebulaPresentationPrivacy.enabled' in source and ('!masked && !entry.username' in source or '!NebulaPresentationPrivacy.enabled, let username' in source)
print('Both native row accessibility paths and account-alias boundaries are wired; rendered/device and screen-reader acceptance remain separate')
