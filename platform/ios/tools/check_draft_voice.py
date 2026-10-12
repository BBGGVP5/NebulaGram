"""Execute the selected-clip CAF/AAC adapter with the real AVFoundation encoder."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[3]


def check(preview: str, work: Path, execute: bool) -> None:
    assert 'presentNebulaDraftVoice(' in preview and 'forControlEvents: .touchUpInside' in preview
    assert 'self.nebulaDraftGeneration == token' in preview and 'self.view.window != nil' in preview
    assert 'self.interfaceInteraction?.deleteRecordedMedia()' in preview
    assert preview.index('deleteRecordedMedia()', preview.index('}, apply:')) < preview.index('updateTextInputStateAndMode', preview.index('}, apply:'))
    assert 'cached == nil && self.nebulaDraftTranscript != nil' in preview
    assert 'self.nebulaDraftFetch.dispose()' in preview
    if not execute:
        print('OK: iOS Aa preview resource/owner/trim guards, explicit text choice and private file bridge', flush=True)
        return
    work = work / 'draft-voice-audio'
    work.mkdir()
    contract = ROOT / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract'
    library = work / 'libNebulaSettingsContract.dylib'
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', '-emit-library', '-emit-module',
                    '-module-name', 'NebulaSettingsContract', str(contract / 'NebulaLocalAudioPolicy.swift'),
                    str(contract / 'NebulaDraftVoicePolicy.swift'), '-emit-module-path', str(work / 'NebulaSettingsContract.swiftmodule'), '-o', str(library)], check=True)
    program = '''import Foundation
import AVFoundation
import AudioToolbox
import NebulaSettingsContract
enum NebulaRecordingPCM {
 static func decode(source: URL, destination: URL, expectedDuration: Double?) async throws {
  try Task.checkCancellation();try FileManager.default.copyItem(at: source, to: destination)
 }
}
@main struct DraftVoiceAudioCheck {
 static func main() async throws {
  let base=URL(fileURLWithPath:CommandLine.arguments[1])
  let source=base.appendingPathComponent("original.caf")
  let format=AVAudioFormat(commonFormat:.pcmFormatInt16,sampleRate:48000,channels:1,interleaved:true)!
  do {
   let output=try AVAudioFile(forWriting:source,settings:format.settings,commonFormat:.pcmFormatInt16,interleaved:true)
   let buffer=AVAudioPCMBuffer(pcmFormat:format,frameCapacity:48000)!;buffer.frameLength=48000
   for i in 0..<48000 { buffer.int16ChannelData![0][i]=Int16((i % 997)-500) }
   try output.write(from:buffer)
  }
  let original=try Data(contentsOf:source)
  for cloud in [false,true] {
   let directory=base.appendingPathComponent(cloud ? "cloud" : "local",isDirectory:true)
   try FileManager.default.createDirectory(at:directory,withIntermediateDirectories:true)
   let clip=try await NebulaDraftVoiceAudio.prepare(source:source,directory:directory,duration:1,start:0.25,end:0.75,cloud:cloud)
   let reader=try AVAudioFile(forReading:clip)
   let seconds=Double(reader.length)/reader.processingFormat.sampleRate
   precondition(abs(seconds-0.5)<0.08,"selected clip duration is wrong")
   let after=try Data(contentsOf:source);precondition(after==original,"original recording changed")
   if !cloud {
    precondition(reader.length==24000,"CAF selection must be exact")
    let samples=try AVAudioFile(forReading:clip,commonFormat:.pcmFormatInt16,interleaved:true)
    let first=AVAudioPCMBuffer(pcmFormat:samples.processingFormat,frameCapacity:1)!
    try samples.read(into:first,frameCount:1)
    precondition(first.int16ChannelData![0][0]==Int16((12000 % 997)-500),"selected CAF starts at wrong sample")
   } else {precondition(reader.fileFormat.streamDescription.pointee.mFormatID==kAudioFormatMPEG4AAC,"cloud clip is not AAC")}
  }
  print("OK: actual AVFoundation CAF/AAC exports select the half-second fragment and preserve original bytes")
 }
}
'''
    main = work / 'DraftVoiceAudioCheck.swift'; main.write_text(program, encoding='utf-8')
    executable = work / 'draft-voice-audio-check'
    adapter = ROOT / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaDraftVoiceAudio.swift'
    subprocess.run(['swiftc', '-swift-version', '5', '-warnings-as-errors', '-parse-as-library', '-I', str(work), '-L', str(work),
                    '-lNebulaSettingsContract', '-Xlinker', '-rpath', '-Xlinker', str(work), str(adapter), str(main), '-o', str(executable)], check=True)
    subprocess.run([str(executable), str(work)], check=True, timeout=30)
