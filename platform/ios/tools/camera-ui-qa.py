"""Render the production camera controller in an isolated CI iOS simulator.

Only the Telegram palette is adapted. This is UIKit widget QA, not camera,
Telegram account, audio or physical iPhone acceptance.
"""
from pathlib import Path
import json, os, platform, plistlib, shutil, subprocess, time

ROOT = Path(__file__).resolve().parents[3]
WORK = ROOT / 'build/ios-camera-widget-qa'
BUNDLE = 'app.nebulagram.cameraqa'

def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)

def output(*args):
    return subprocess.check_output(list(map(str, args)), text=True)

def main():
    assert os.environ.get('CI') == 'true' and platform.system() == 'Darwin', 'Use only an owned CI simulator'
    WORK.mkdir(parents=True, exist_ok=True)
    app = WORK / 'CameraWidgets.app'; app.mkdir(exist_ok=True)
    source = (ROOT / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaRoundCameraController.swift').read_text(encoding='utf-8')
    assert source.count('import TelegramPresentationData') == 1
    actual = WORK / 'CameraController.swift'
    actual.write_text(source.replace('import TelegramPresentationData\n', '', 1), encoding='utf-8')
    harness = WORK / 'CameraWidgetApp.swift'
    harness.write_text(r'''import UIKit
public struct PresentationThemeList {
 public let blocksBackgroundColor:UIColor, itemAccentColor:UIColor, itemPrimaryTextColor:UIColor, itemSecondaryTextColor:UIColor
 init(dark:Bool) {
  blocksBackgroundColor=dark ? UIColor(white:0.12,alpha:1):UIColor(white:0.95,alpha:1)
  itemAccentColor=dark ? UIColor(red:0.39,green:0.80,blue:0.82,alpha:1):UIColor(red:0,green:0.42,blue:0.45,alpha:1)
  itemPrimaryTextColor=dark ? .white:.black; itemSecondaryTextColor=dark ? .lightGray:.darkGray
 }
}
public final class PresentationTheme {
 public let overallDarkAppearance:Bool, list:PresentationThemeList
 init(dark:Bool){overallDarkAppearance=dark;list=PresentationThemeList(dark:dark)}
}
@main final class AppDelegate:UIResponder,UIApplicationDelegate {
 func application(_ application:UIApplication,configurationForConnecting session:UISceneSession,options:UIScene.ConnectionOptions)->UISceneConfiguration {
  let configuration=UISceneConfiguration(name:"Camera QA",sessionRole:session.role);configuration.delegateClass=SceneDelegate.self;return configuration
 }
}
final class SceneDelegate:UIResponder,UIWindowSceneDelegate {
 var window:UIWindow?;let root=UIViewController();var calls=0;let composer=UIView()
 let name=ProcessInfo.processInfo.arguments.dropFirst().first ?? "dark-front"
 var theme:PresentationTheme {PresentationTheme(dark: !name.contains("light"))}
 func scene(_ scene:UIScene,willConnectTo session:UISceneSession,options:UIScene.ConnectionOptions) {
  guard let scene=scene as? UIWindowScene else{return}
  let window=UIWindow(windowScene:scene);self.window=window;window.rootViewController=root
  root.view.backgroundColor=theme.overallDarkAppearance ? UIColor(white:0.05,alpha:1):UIColor(white:0.88,alpha:1)
  composer.backgroundColor=theme.list.itemAccentColor.withAlphaComponent(0.3);composer.layer.cornerRadius=24
  root.view.addSubview(composer)
  let title=UILabel();title.text="Чат · Предпросмотр выбора камеры";title.textColor=theme.list.itemPrimaryTextColor
  title.frame=CGRect(x:16,y:100,width:340,height:50);root.view.addSubview(title)
  window.makeKeyAndVisible();UserDefaults.standard.set(name.contains("front"),forKey:"nebula.round.last_front")
  DispatchQueue.main.asyncAfter(deadline:.now()+0.8){
   self.composer.frame=CGRect(x:12,y:self.root.view.bounds.height-self.root.view.safeAreaInsets.bottom-68,width:self.root.view.bounds.width-24,height:56)
   self.presentCamera()
   if self.name.contains("large"){self.root.presentedViewController?.presentationController?.overrideTraitCollection=UITraitCollection(preferredContentSizeCategory:.extraExtraExtraLarge)}
   DispatchQueue.main.asyncAfter(deadline:.now()+2){self.report()}
  }
  _ = Timer.scheduledTimer(timeInterval:0.2,target:self,selector:#selector(checkTrigger),userInfo:nil,repeats:true)
 }
 func presentCamera(){nebulaPresentRecordingCamera(from:root,russian:true,theme:theme,inputFrame:{self.composer.frame}){index in
  precondition(index==1,"Incorrect selected camera");self.calls+=1
 }}
 func find(_ view:UIView,_ id:String)->UIView? {
  if view.accessibilityIdentifier==id{return view}
  for child in view.subviews {if let result=find(child,id){return result}};return nil
 }
 func visible(_ view:UIView)->Bool {
  guard let window=view.window,!view.isHidden,view.alpha>0,view.bounds.width>0,view.bounds.height>0 else{return false}
  if !window.bounds.contains(view.convert(view.bounds,to:window)){return false}
  var parent=view.superview
  while let current=parent {if current.clipsToBounds && !current.bounds.insetBy(dx:-1,dy:-1).contains(view.convert(view.bounds,to:current)){return false};parent=current.superview}
  return true
 }
 func save(_ name:String,_ report:[String:Any]) {
  let url=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent(name)
  try! JSONSerialization.data(withJSONObject:report,options:[.prettyPrinted,.sortedKeys]).write(to:url)
 }
 func report() {
  guard let controller=root.presentedViewController,let front=find(controller.view,"nebula.camera.front") as? UIControl,
        let rear=find(controller.view,"nebula.camera.rear") as? UIControl,let close=find(controller.view,"nebula.camera.close") else{fatalError("Camera controls missing")}
  let large=controller.traitCollection.preferredContentSizeCategory == .extraExtraExtraLarge
  guard let card=find(controller.view,"nebula.camera.card"),let backdrop=find(controller.view,"nebula.camera.backdrop") else{fatalError("Missing inline card or dim backdrop")}
  save("layout.json",["case":name,"frontVisible":visible(front),"rearVisible":visible(rear),"closeVisible":visible(close),
       "frontSelected":front.isSelected,"rearSelected":rear.isSelected,"largeFont":large,
       "frontHeight":front.bounds.height,"rearHeight":rear.bounds.height,"panelHeight":card.bounds.height,
       "aboveComposer":card.frame.maxY<=composer.frame.minY-7.5,"dimAmount":backdrop.backgroundColor?.cgColor.alpha ?? 0])
 }
 @objc func checkTrigger() {
  let trigger=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("verify.trigger")
  guard (try? String(contentsOf:trigger,encoding:.utf8))==name else{return}
  try! FileManager.default.removeItem(at:trigger);verifyCallbacks()
 }
 func verifyCallbacks() {
  save("callback-stage.json",["case":name,"stage":"start","hostInWindow":root.view.window != nil])
  guard let controller=root.presentedViewController,
        let rear=find(controller.view,"nebula.camera.rear") as? UIControl else{fatalError("No camera to verify")}
  guard let card=find(controller.view,"nebula.camera.card") else{fatalError("No inline card")}
  let oldBottom=card.frame.maxY;composer.frame.origin.y-=60
  DispatchQueue.main.asyncAfter(deadline:.now()+0.3){
   precondition(abs(oldBottom-card.frame.maxY-60)<1,"Inline card did not follow native input")
   rear.sendActions(for:.touchUpInside);rear.sendActions(for:.touchUpInside)
   self.waitForDismiss(stage:"choice",attempt:0){
   self.presentCamera()
   DispatchQueue.main.asyncAfter(deadline:.now()+1){
    guard let controller=self.root.presentedViewController,let close=self.find(controller.view,"nebula.camera.close") as? UIControl else{fatalError("No close control")}
    close.sendActions(for:.touchUpInside)
    self.waitForDismiss(stage:"close",attempt:0){
     self.presentCamera()
     DispatchQueue.main.asyncAfter(deadline:.now()+0.5){
      guard let controller=self.root.presentedViewController,let backdrop=self.find(controller.view,"nebula.camera.backdrop") as? UIControl else{fatalError("No backdrop")}
      backdrop.sendActions(for:.touchUpInside)
      self.waitForDismiss(stage:"outside",attempt:0){self.save("callbacks.json",["case":self.name,"selections":self.calls,"cancelDidNotSelect":true,"anchorFollowed":true])}
     }
    }
   }
  }
  }
 }
 func waitForDismiss(stage:String,attempt:Int,completion:@escaping()->Void) {
  if calls==1 && root.presentedViewController==nil{completion();return}
  save("callback-stage.json",["case":name,"stage":stage,"attempt":attempt,"selections":calls,
       "stillPresented":root.presentedViewController != nil,"hostInWindow":root.view.window != nil])
  precondition(attempt<60 && calls<=1,"Incorrect native camera callback or dismissal")
  DispatchQueue.main.asyncAfter(deadline:.now()+0.2){self.waitForDismiss(stage:stage,attempt:attempt+1,completion:completion)}
 }
}
''', encoding='utf-8')
    sdk = output('xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path').strip()
    run('xcrun', '--sdk', 'iphonesimulator', 'swiftc', '-swift-version', '5', '-warnings-as-errors', '-sdk', sdk,
        '-target', platform.machine() + '-apple-ios15.0-simulator', actual, harness, '-o', app / 'CameraWidgets')
    info = dict(CFBundleExecutable='CameraWidgets', CFBundleIdentifier=BUNDLE, CFBundleName='Camera widget QA',
                CFBundlePackageType='APPL', CFBundleVersion='1', CFBundleShortVersionString='1.0', MinimumOSVersion='15.0',
                UILaunchScreen={}, UIApplicationSceneManifest={'UIApplicationSupportsMultipleScenes': False})
    (app / 'Info.plist').write_bytes(plistlib.dumps(info)); run('codesign', '--force', '--sign', '-', app)
    runtimes = json.loads(output('xcrun', 'simctl', 'list', 'runtimes', '--json'))['runtimes']
    runtime = max((r for r in runtimes if r.get('isAvailable') and r['name'].startswith('iOS ')),
                  key=lambda r:tuple(map(int,r['version'].split('.'))))['identifier']
    types = json.loads(output('xcrun', 'simctl', 'list', 'devicetypes', '--json'))['devicetypes']
    device_type = next(t['identifier'] for t in types if t['name']=='iPhone SE (3rd generation)')
    device = output('xcrun', 'simctl', 'create', 'Nebula camera widget QA', device_type, runtime).strip()
    images = WORK / 'screenshots'; images.mkdir(exist_ok=True)
    try:
        run('xcrun', 'simctl', 'boot', device); run('xcrun', 'simctl', 'bootstatus', device, '-b')
        run('xcrun', 'simctl', 'install', device, app)
        container = Path(output('xcrun', 'simctl', 'get_app_container', device, BUNDLE, 'data').strip()) / 'Documents'
        for name in ['dark-front', 'light-rear', 'narrow-large-rear']:
            subprocess.run(['xcrun','simctl','terminate',device,BUNDLE],check=False)
            run('xcrun','simctl','launch',device,BUNDLE,name)
            report = wait_report(container/'layout.json',name)
            assert all(report[k] for k in ['frontVisible','rearVisible','closeVisible']),report
            assert report['frontSelected']==('front' in name) and report['rearSelected']==('rear' in name),report
            assert report['largeFont']==('large' in name),report
            assert report['aboveComposer'] and abs(report['dimAmount']-0.55)<0.01,report
            run('xcrun','simctl','io',device,'screenshot',images/(name+'.png'))
            (images/(name+'.json')).write_text(json.dumps(report,indent=2)+'\n')
            (container/'verify.trigger').write_text(name)
            callbacks = wait_report(container/'callbacks.json',name)
            assert callbacks['selections']==1 and callbacks['cancelDidNotSelect'] and callbacks['anchorFollowed'],callbacks
            (images/(name+'-callbacks.json')).write_text(json.dumps(callbacks,indent=2)+'\n')
        print('Production UIKit camera sheet: visible controls, Dynamic Type, repeated choice and cancellation passed')
    except Exception:
        if 'container' in locals() and container.exists():
            for report in container.glob('*.json'):shutil.copyfile(report,images/('failed-'+report.name))
        with (images/'simulator-app.log').open('w') as log:
            subprocess.run(['xcrun','simctl','spawn',device,'log','show','--last','2m','--style','compact',
                            '--predicate','process == "CameraWidgets"'],stdout=log,stderr=subprocess.STDOUT,check=False)
        raise
    finally:
        subprocess.run(['xcrun','simctl','shutdown',device],check=False)
        run('xcrun','simctl','delete',device)

def wait_report(path, name):
    for _ in range(30):
        time.sleep(1)
        try:
            report=json.loads(path.read_text())
            if report.get('case')==name:return report
        except (FileNotFoundError,ValueError):pass
    raise AssertionError(f'No current report for {name}: {path}')

if __name__ == '__main__':main()
