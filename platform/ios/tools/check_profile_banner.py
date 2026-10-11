"""Check native banner layering and execute its actual Swift update/callback bodies."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[3]
POLICY = ROOT / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaProfileBannerPolicy.swift'


def body(source, start):
    opening = source.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[opening + 1:end - 1], end


def check(header: str, work: Path, execute: bool) -> None:
    assert 'self.avatarListNode.listContainerNode.addSubnode(self.nebulaBannerFade)' not in header, 'fade shrinks/hides with the native gallery'
    photo = header.index('self.regularContentNode.addSubnode(self.avatarClippingNode)')
    fade = header.index('self.regularContentNode.addSubnode(self.nebulaBannerFade)')
    controls = header.index('self.regularContentNode.addSubnode(self.avatarListNode.listContainerNode.controlsClippingOffsetNode)')
    assert photo < fade < controls < header.index('self.regularContentNode.addSubnode(self.titleNodeContainer)')
    call = header.index('self.nebulaUpdateBanner(width: width, bottom: apparentBackgroundHeight,')
    assert call > header.index('let apparentBackgroundHeight =')
    assert 'protectedBottom: self.isAvatarExpanded ? 0.0 : apparentAvatarFrame.maxY' in header
    assert 'peer?.profileImageRepresentations.isEmpty == false && self.forumTopicThreadId == nil' in header
    assert 'let showFade = self.isAvatarExpanded' not in header
    start = header.index('    private func nebulaUpdateBanner(')
    _, end = body(header, start)
    update = header[start:end].replace('private func', 'func', 1)
    start = header.index('    func updateIsAvatarExpanded(')
    _, end = body(header, start)
    expansion = header[start:end]
    start = header.index('completion: { [weak self] finished in', header.index('let collapseGeneration = self.nebulaAvatarTransitionGeneration'))
    callback, _ = body(header, start)
    callback = callback[callback.index('\n'):]
    print('OK: native profile fade follows header bottom outside the shrinking gallery, behind controls/title', flush=True)
    if not execute:
        return
    program = '''import Foundation
let UIScreenPixel: CGFloat = 1.0 / 3.0
struct UIColor: Equatable {
 let id: Int;var alpha: CGFloat = 1
 func withAlphaComponent(_ a: CGFloat) -> UIColor { UIColor(id: id, alpha: a) }
 var cgColor: UIColor { self }
}
class Layer {var frame = CGRect.zero}
class Gradient: Layer {
 var writes = 0
 var colors: [UIColor]? {didSet {precondition(CATransaction.disabled && CATransaction.depth == 1);writes += 1}}
}
class ASDisplayNode {var frame = CGRect.zero;var alpha: CGFloat = 0}
enum UIAccessibility {static var isReduceMotionEnabled = false}
enum CATransaction {
 static var disabled = false;static var depth = 0
 static func begin(){depth += 1}
 static func setDisableActions(_ value: Bool){disabled=value}
 static func commit(){depth -= 1;disabled=false}
}
enum ContainedViewLayoutTransition {
 case immediate, animated
 static var animationCalls = 0
 func record(_ current: Bool){precondition(current,"transition snaps instead of continuing presentation state");if case .animated = self {Self.animationCalls += 1}}
 func updateFrame(node: ASDisplayNode, frame: CGRect, beginWithCurrentState: Bool){record(beginWithCurrentState);node.frame=frame}
 func updateFrame(layer: Layer, frame: CGRect, beginWithCurrentState: Bool){record(beginWithCurrentState);layer.frame=frame}
 func updateAlpha(node: ASDisplayNode, alpha: CGFloat, beginWithCurrentState: Bool){record(beginWithCurrentState);node.alpha=alpha}
}
final class NebulaSettingsStore {
 static let shared = NebulaSettingsStore();var profileStyle=true;var profilePhotoBanner=true
}
final class DispatchQueue {
 static let main = DispatchQueue();var pending: [() -> Void] = []
 func async(_ action: @escaping () -> Void){pending.append(action)}
 func drain(){let actions=pending;pending.removeAll();for action in actions {action()}}
}
class Container {var isHidden=false;var isCollapsing=true;var selections=0;func selectFirstItem(){selections += 1}}
class Avatar {var canAttachVideo=false}
class ListNode {
 let listContainerNode=Container();let avatarContainerNode=Avatar();var collapses=0
 func animateAvatarCollapse(transition: ContainedViewLayoutTransition){collapses += 1}
}
final class Header {
 var isAvatarExpanded=false;var nebulaAvatarTransitionGeneration=0;var skipCollapseCompletion=false
 let avatarListNode=ListNode();let nebulaBannerFade=ASDisplayNode();let nebulaBannerGradient=Gradient();var nebulaBannerColor: UIColor?
 func updateAvatarMask(transition: ContainedViewLayoutTransition){}
 UPDATE
 EXPANSION
 func completion() -> (Bool) -> Void {
  let collapseGeneration=nebulaAvatarTransitionGeneration
  return { [weak self] finished in CALLBACK }
 }
}
let header=Header();var cases=0
for phase in 0...100 {
 let bottom=430.5+(704.25-430.5)*CGFloat(phase)/100
 header.nebulaUpdateBanner(width:393,bottom:bottom,protectedBottom:186,hasPhoto:true,editing:false,color:UIColor(id:1),transition:.animated)
 precondition(header.nebulaBannerFade.alpha==1 && abs(header.nebulaBannerFade.frame.maxY-bottom-UIScreenPixel)<0.00001)
 precondition(abs(header.nebulaBannerGradient.frame.height+header.nebulaBannerFade.frame.minY-bottom)<0.00001,"gradient misses the real page edge")
 cases += 1
}
precondition(header.nebulaBannerGradient.writes==1 && CATransaction.depth==0,"unchanged theme allocates or leaves transaction open")
header.nebulaUpdateBanner(width:393,bottom:500,protectedBottom:186,hasPhoto:true,editing:false,color:UIColor(id:2),transition:.animated)
precondition(header.nebulaBannerGradient.writes==2 && header.nebulaBannerGradient.colors?.last==UIColor(id:2))
UIAccessibility.isReduceMotionEnabled=true;let animations=ContainedViewLayoutTransition.animationCalls
header.nebulaUpdateBanner(width:393,bottom:500,protectedBottom:186,hasPhoto:true,editing:false,color:UIColor(id:2),transition:.animated)
precondition(ContainedViewLayoutTransition.animationCalls==animations,"Reduce Motion starts a decorative animation")
UIAccessibility.isReduceMotionEnabled=false
for mode in 0...4 {
 NebulaSettingsStore.shared.profileStyle=mode != 0;NebulaSettingsStore.shared.profilePhotoBanner=mode != 1
 header.nebulaUpdateBanner(width:393,bottom:mode==4 ? 88:500,protectedBottom:186,hasPhoto:mode != 2,editing:mode==3,color:UIColor(id:2),transition:.animated)
 precondition(header.nebulaBannerFade.alpha==0,"disabled/no-photo/editing/compact state keeps material")
 cases += 1
}
NebulaSettingsStore.shared.profileStyle=true;NebulaSettingsStore.shared.profilePhotoBanner=true
let cancelled=header.completion();cancelled(false)
precondition(!header.avatarListNode.listContainerNode.isHidden && !header.avatarListNode.avatarContainerNode.canAttachVideo)
let stale=header.completion();header.updateIsAvatarExpanded(true,transition:.animated);stale(true)
precondition(!header.avatarListNode.listContainerNode.isHidden && !header.avatarListNode.avatarContainerNode.canAttachVideo,"late collapse hides reopened gallery")
header.updateIsAvatarExpanded(false,transition:.animated);stale(true)
precondition(!header.avatarListNode.listContainerNode.isHidden,"old completion can hide a new close/reopen sequence")
header.completion()(true)
precondition(header.avatarListNode.listContainerNode.isHidden && header.avatarListNode.avatarContainerNode.canAttachVideo && DispatchQueue.main.pending.count==1)
header.updateIsAvatarExpanded(true,transition:.animated);header.updateIsAvatarExpanded(false,transition:.animated)
DispatchQueue.main.drain();precondition(header.avatarListNode.listContainerNode.isCollapsing,"queued old reset affects a later close")
header.completion()(true);DispatchQueue.main.drain();precondition(!header.avatarListNode.listContainerNode.isCollapsing)
header.updateIsAvatarExpanded(true,transition:.animated);NebulaSettingsStore.shared.profilePhotoBanner=false
let before=header.avatarListNode.collapses
header.updateIsAvatarExpanded(true,transition:.animated)
precondition(!header.isAvatarExpanded && header.avatarListNode.collapses==before+1,"setting-gated requested expansion skips collapse")
let generation=header.nebulaAvatarTransitionGeneration
header.updateIsAvatarExpanded(true,transition:.animated);precondition(header.nebulaAvatarTransitionGeneration==generation)
var transient: Header?=Header();weak var weakHeader=transient
let weakCompletion=transient!.completion();weakCompletion(true);transient=nil
precondition(weakHeader==nil,"pending fade/collapse work retains header")
weakCompletion(true);DispatchQueue.main.drain()
print("OK: \\(cases) actual banner-update states; cached theme, current-state frames, Reduce Motion, cancelled/reversed/queued collapse and weak lifetime")
'''.replace('UPDATE', update).replace('EXPANSION', expansion).replace('CALLBACK', callback)
    native_work = work / 'profile-banner-native'
    native_work.mkdir()
    fixture = native_work / 'main.swift'
    fixture.write_text(program, encoding='utf-8')
    executable = work / 'profile-banner-native-check'
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', str(POLICY), str(fixture), '-o', str(executable)], check=True)
    subprocess.run([str(executable)], check=True)

    # Typecheck the production helper against UIKit/CAGradientLayer and the
    # pinned public transition signatures; full native module linkage is in IPA CI.
    sdk = subprocess.check_output(['xcrun', '--sdk', 'iphonesimulator', '--show-sdk-path'], text=True).strip()
    sdk_fixture = work / 'ProfileBannerUIKitCheck.swift'
    sdk_fixture.write_text('''import UIKit
let UIScreenPixel: CGFloat = 1.0 / 3.0
final class ASDisplayNode {}
struct ContainedViewLayoutTransition {
 static let immediate = ContainedViewLayoutTransition()
 func updateFrame(node: ASDisplayNode, frame: CGRect, beginWithCurrentState: Bool){}
 func updateFrame(layer: CALayer, frame: CGRect, beginWithCurrentState: Bool){}
 func updateAlpha(node: ASDisplayNode, alpha: CGFloat, beginWithCurrentState: Bool){}
}
final class NebulaSettingsStore {static let shared=NebulaSettingsStore();let profileStyle=true;let profilePhotoBanner=true}
final class Header {
 let nebulaBannerFade=ASDisplayNode();let nebulaBannerGradient=CAGradientLayer();var nebulaBannerColor: UIColor?
UPDATE
}
'''.replace('UPDATE', update), encoding='utf-8')
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', '-sdk', sdk,
                    '-target', 'arm64-apple-ios13.0-simulator', '-typecheck', str(POLICY), str(sdk_fixture)], check=True)
    print('OK: actual profile fade helper typechecked against UIKit/Core Animation', flush=True)


if __name__ == '__main__':
    import sys
    with tempfile.TemporaryDirectory(prefix='nebula-ios-banner-') as folder:
        check(Path(sys.argv[1]).read_text(encoding='utf-8'), Path(folder), '--swift' in sys.argv)
