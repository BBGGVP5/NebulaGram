"""Execute the native formatting eligibility and frame expressions with Foundation."""
from pathlib import Path
import re
import subprocess
import tempfile


def check(source, swift=False):
    start = source.index('        var nebulaTextMode = false')
    eligibility = source[start:source.index('        let baseWidth =', start)]
    container = re.search(r'        let containerFrame = (CGRect[^\n]+)', source).group(1)
    toolbar = re.search(r'updateFrame\(view: toolbar, frame: (CGRect[\s\S]+?height: 44\))\)', source).group(1)
    frames = []
    for name in ['liveMicrophoneButton', 'starReactionButton', 'settingsButton']:
        frames.append(re.search(r'(?:var|let) ' + name + r'Frame = (CGRect[^\n]+)', source).group(1))
    assert source.count('y: formattingHeight + contentHeight - maxOverlayHeight)') == 2
    assert 'dy: self.glassBackgroundContainer.frame.minY + self.sendActionButtons.frame.minY)' in source
    for name in ['mediaActionButtons', 'sendActionButtons']:
        assert 'y: rect.origin.y + formattingHeight + ' + name + 'Frame.origin.y' in source
    assert 'return contentHeight + formattingHeight' in source
    if not swift:
        print('OK: iOS accessory, root controls, completion panels and absolute media/send anchors share the top inset')
        return
    program = r'''import Foundation
final class Store {static let shared=Store();var formattingToolbar=false}
typealias NebulaSettingsStore=Store
enum Mode {case text,other}
struct Interface {var inputMode:Mode}
final class NativeCheck {
 var isFocused=false,sendingTextDisabled=false
 func height(_ bits:Int)->CGFloat {
  Store.shared.formattingToolbar=bits&1 != 0;isFocused=bits&2 != 0
  let interfaceState=Interface(inputMode:bits&4 != 0 ? .text:.other)
  let mediaRecordingState:Int?=bits&8 != 0 ? 1:nil
  let hasMediaDraft=bits&16 != 0,isEditingMedia=bits&32 != 0,displayBotStartButton=bits&64 != 0
  sendingTextDisabled=bits&128 != 0
  ELIGIBILITY
  return formattingHeight
 }
}
func frames(width:CGFloat,contentHeight:CGFloat,formattingHeight:CGFloat) {
 let leftInset:CGFloat=8,rightInset:CGFloat=8,attachmentButtonX:CGFloat=0,nextRightActionButtonX=width-50
 let textInputContainerBackgroundFrame=CGRect(x:50,y:0,width:width-100,height:contentHeight)
 let attachmentButtonFrame=CGRect(x:0,y:contentHeight-44,width:40,height:44)
 let liveMicrophoneButtonSize=CGSize(width:44,height:44),starReactionButtonSize=CGSize(width:44,height:44),settingsButtonSize=CGSize(width:44,height:44)
 let containerFrame=CONTAINER
 let toolbarFrame=TOOLBAR
 let liveFrame=LIVE
 let starFrame=STAR
 let settingsFrame=SETTINGS
 if formattingHeight>0 {precondition(toolbarFrame.minY>=0 && toolbarFrame.maxY<=containerFrame.minY,"formatting strip is below or overlaps native input")}
 for frame in [liveFrame,starFrame,settingsFrame] {precondition(frame.maxY==containerFrame.minY+contentHeight,"root button separated from native field")}
 precondition(containerFrame.minY==formattingHeight)
}
let check=NativeCheck()
for bits in 0..<256 {precondition(check.height(bits)==(bits==7 ? 52:0),"recording/media/disabled keyboard shows formatting")}
for width in [CGFloat(240),320,390,720] {for height in [CGFloat(44),88,176,224] {for accessory in [CGFloat(0),52] {frames(width:width,contentHeight:height,formattingHeight:accessory)}}}
print("Native iOS formatting: 256 eligibility states, 32 toolbar/native/root-button frame combinations passed")
'''.replace('ELIGIBILITY', eligibility).replace('CONTAINER', container).replace('TOOLBAR', toolbar)
    for token, frame in zip(['LIVE', 'STAR', 'SETTINGS'], frames):
        program = program.replace(token, frame)
    with tempfile.TemporaryDirectory(prefix='nebula-ios-formatting-') as directory:
        path = Path(directory) / 'NativeFormatting.swift'
        path.write_text(program, encoding='utf-8')
        subprocess.run(['swift', str(path)], check=True)
