"""Execute the actual native reviewed-recording capture/payment callback on macOS."""
from pathlib import Path
import subprocess

def check(tree: Path) -> None:
    source=(tree/'submodules/TelegramUI/Sources/Chat/ChatControllerMediaRecording.swift').read_text(encoding='utf-8')
    start=source.index('    func nebulaRecordingSendAction()')
    stop=source.index('    func sendMediaRecording(',start)
    method=source[start:stop]
    assert source.index('if let nebulaText {',stop)<source.index('let resource: TelegramMediaResource',stop)
    assert 'mediaReference: nil' in source and 'self.transformEnqueueMessages(messages' in source
    assert 'self.nebulaRecordedSendingDraft = recordedMediaPreview' in source
    preview=(tree/'submodules/TelegramUI/Components/Chat/ChatRecordingPreviewInputPanelNode/Sources/ChatRecordingPreviewInputPanelNode.swift').read_text(encoding='utf-8')
    assert 'self.nebulaDraftServiceKey() == service' in preview
    program=r'''import Foundation
struct ChatSendMessageEffect {}
final class Account { var peerId=1 }
final class Context { let account=Account() }
final class Location { var peerId:Int?=2 }
final class InterfaceState { var mediaDraftState:Int?=10 }
final class Presentation { let interfaceState=InterfaceState() }
final class Interaction { var nebulaRecordedText:(()->(selected:Bool,text:String?,isCurrent:()->Bool))? }
func trimChatInputText(_ text:NSAttributedString)->NSAttributedString { text }
func breakChatInputText(_ text:NSAttributedString)->[NSAttributedString] {
 var result:[NSAttributedString]=[];var remaining=text.string
 while !remaining.isEmpty { let part=String(remaining.prefix(4096));result.append(NSAttributedString(string:part));remaining.removeFirst(part.count) }
 return result
}
final class NativeRecorder {
 let context=Context(),chatLocation=Location(),presentationInterfaceState=Presentation()
 var interfaceInteraction:Interaction?=Interaction(),nebulaRecordedSendGeneration=0,nebulaRecordedSendingDraft:Int?
 var counts:[Int]=[],callbacks:[(Bool)->Void]=[],sent:[(String?,Bool,Int32?,Int32?)]=[]
 func presentPaidMessageAlertIfNeeded(count:Int,completion:@escaping(Bool)->Void) { counts.append(count);callbacks.append(completion) }
 func sendMediaRecording(silentPosting:Bool?,scheduleTime:Int32?,repeatPeriod:Int32?,viewOnce:Bool,messageEffect:ChatSendMessageEffect?,postpone:Bool,nebulaText:String?) {
  nebulaRecordedSendingDraft=presentationInterfaceState.interfaceState.mediaDraftState
  sent.append((nebulaText,viewOnce,scheduleTime,repeatPeriod))
 }
__METHOD__
}
var cases=0
func verify(_ yes:Bool,_ reason:String) { precondition(yes,reason);cases+=1 }
do {
 let recorder=NativeRecorder();recorder.interfaceInteraction?.nebulaRecordedText={ (true,nil,{true}) }
 verify(recorder.nebulaRecordingSendAction()==nil && recorder.callbacks.isEmpty,"Pending recognition must not send/discard voice")
}
do {
 let recorder=NativeRecorder();let action=recorder.nebulaRecordingSendAction()!
 action(nil,nil,nil,true,nil);verify(recorder.counts==[1],"Voice uses native one-message charge")
 recorder.callbacks[0](false);verify(recorder.sent[0].0==nil && recorder.sent[0].1,"Original view-once voice preserved")
 recorder.callbacks[0](false);verify(recorder.sent.count==1,"Duplicate confirmation cannot send twice")
 verify(recorder.nebulaRecordingSendAction()==nil,"New tap cannot enqueue the same draft twice")
}
do {
 let recorder=NativeRecorder();var value=String(repeating:"a",count:8193)
 recorder.interfaceInteraction?.nebulaRecordedText={ (true,value,{true}) }
 let action=recorder.nebulaRecordingSendAction()!;action(false,100,60,true,nil)
 verify(recorder.counts==[3],"Paid count matches native text splitting")
 value="edited after payment opened";recorder.callbacks[0](true)
 verify(recorder.sent[0].0?.count==8193,"Confirmation sends the immutable reviewed text")
 verify(!recorder.sent[0].1 && recorder.sent[0].2==100 && recorder.sent[0].3==60,"Text keeps native schedule/repeat and has no audio TTL")
}
for mutation in 0..<6 {
 let recorder=NativeRecorder();var selected=true;var scope=true
 recorder.interfaceInteraction?.nebulaRecordedText={ (selected,"reviewed",{scope}) }
 let action=recorder.nebulaRecordingSendAction()!;action(nil,nil,nil,false,nil)
 switch mutation {
 case 0:recorder.presentationInterfaceState.interfaceState.mediaDraftState=11
 case 1:recorder.context.account.peerId=3
 case 2:recorder.chatLocation.peerId=4
 case 3:selected=false
 case 4:scope=false
 default:_=recorder.nebulaRecordingSendAction()
 }
 recorder.callbacks[0](false);verify(recorder.sent.isEmpty,"Trim/account/peer/format/service/new attempt invalidates the pending send")
}
do {
 let recorder=NativeRecorder();var value="reviewed at picker opening"
 recorder.interfaceInteraction?.nebulaRecordedText={ (true,value,{true}) }
 let action=recorder.nebulaRecordingSendAction()!;value="later edit";action(nil,200,nil,false,nil);recorder.callbacks[0](false)
 verify(recorder.sent[0].0=="reviewed at picker opening","Scheduler captures text before opening the picker")
}
print("OK: \(cases) actual iOS recording-send payment, scheduling and ownership cases")
'''.replace('__METHOD__',method)
    file=tree/'recorded-send-check.swift';file.write_text(program,encoding='utf-8')
    executable=tree/'recorded-send-check'
    subprocess.run(['swiftc','-swift-version','5','-warnings-as-errors',str(file),'-o',str(executable)],check=True)
    subprocess.run([str(executable)],check=True)
