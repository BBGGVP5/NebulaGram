"""Execute the production message metadata snapshots without network or file access."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
with tempfile.TemporaryDirectory(prefix='nebula-message-details-') as temporary:
    work = Path(temporary)
    if '--swift' in sys.argv:
        source = (root / 'platform/ios/overlay/submodules/SettingsUI/Sources/NebulaMessageDetailsController.swift').read_text(encoding='utf-8')
        start = source.index('        var fields: [Field]')
        end = source.index('        self.fields = fields', start)
        snapshot = source[start:end] + '        return fields\n'
        test = work / 'Check.swift'
        test.write_text('''import Foundation
struct PeerId {let value: Int64; func toInt64() -> Int64 { value }}
struct Peer {let id: PeerId}
struct MessageId {let id: Int32; let peerId: PeerId}
struct MessageForwardInfo {let date: Int32}
final class EditedMessageAttribute {let date: Int32; init(_ date: Int32) {self.date = date}}
struct TelegramMediaVideoFlags: OptionSet {let rawValue: Int32; static let instantRoundVideo = TelegramMediaVideoFlags(rawValue: 1 << 0); static let supportsStreaming = TelegramMediaVideoFlags(rawValue: 1 << 1)}
enum TelegramMediaFileAttribute {case Audio(isVoice: Bool, duration: Int, title: String?, performer: String?, waveform: Data?); case Video(duration: Double, size: Int, flags: TelegramMediaVideoFlags, preloadSize: Int32?, coverTime: Double?, videoCodec: String?); case FileName(fileName: String)}
final class TelegramMediaFile {let fileName: String?; let mimeType: String; let size: Int64?; let attributes: [TelegramMediaFileAttribute]; init(_ name: String?, _ mime: String, _ size: Int64?, _ attributes: [TelegramMediaFileAttribute] = []) {fileName = name; mimeType = mime; self.size = size; self.attributes = attributes}}
struct Message {let id: MessageId; let author: Peer?; let timestamp: Int32; let attributes: [Any]; let forwardInfo: MessageForwardInfo?; let media: [Any]}
struct Field {let title: String; let value: String}
func snapshot(_ message: Message, _ russian: Bool) -> [Field] {
SNAPSHOT
}
@main struct Check {static func main() {
 let plain = Message(id: MessageId(id: 2147483647, peerId: PeerId(value: 9223372036854775806)), author: nil, timestamp: 0, attributes: [], forwardInfo: nil, media: [])
 let fields = snapshot(plain, false); precondition(fields.count == 2 && fields[1].value == "9223372036854775806")
 let full = Message(id: plain.id, author: Peer(id: PeerId(value: 1000000000001)), timestamp: 2147483647, attributes: [EditedMessageAttribute(2147483647)], forwardInfo: MessageForwardInfo(date: 2147483647), media: [TelegramMediaFile("имя 🎵.ogg", "audio/ogg", 5_000_000_000), TelegramMediaFile(nil, "", .max)])
 let all = snapshot(full, true); precondition(all.count == 9); precondition(all.contains {$0.value == "имя 🎵.ogg"}); precondition(all.contains {$0.value.contains("5000000000 байт")}); precondition(all.filter {$0.value.hasPrefix("2038-")}.count == 3)
 let voice = snapshot(Message(id: plain.id, author: nil, timestamp: 0, attributes: [], forwardInfo: nil, media: [TelegramMediaFile(nil, "audio/ogg", 10, [.Audio(isVoice: true, duration: 3, title: nil, performer: nil, waveform: Data(repeating: 1, count: 63))])]), true)
 precondition(voice.last?.title == "Отправлено с" && voice.last!.value.contains("Android и iOS"))
 let round = snapshot(Message(id: plain.id, author: nil, timestamp: 0, attributes: [], forwardInfo: nil, media: [TelegramMediaFile("video.mp4", "video/mp4", 10, [.Video(duration: 1, size: 240, flags: [.instantRoundVideo], preloadSize: nil, coverTime: nil, videoCodec: nil)])]), false)
 precondition(round.contains {$0.title == "Sent from" && $0.value.contains("iOS")})
 let plainVideo = snapshot(Message(id: plain.id, author: nil, timestamp: 0, attributes: [], forwardInfo: nil, media: [TelegramMediaFile(nil, "video/mp4", 10, [.Video(duration: 1, size: 240, flags: [.supportsStreaming], preloadSize: nil, coverTime: nil, videoCodec: nil)])]), false)
 precondition(!plainVideo.contains {$0.title == "Sent from"})
 print("Swift message details: sparse/full metadata, 64-bit identities and sizes, UTF-16 names, 2038 dates and voice/round sender platform passed")
}}
'''.replace('SNAPSHOT', snapshot), encoding='utf-8')
        binary = work / 'check'
        sent_from = root / 'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaSentFrom.swift'
        subprocess.run(['swiftc', '-parse-as-library', str(test), str(sent_from), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
    else:
        files = {
            'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static String formatFileSize(long n) {return "size";}}',
            'org/telegram/messenger/FileLoader.java': 'package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class FileLoader {public static String getDocumentFileName(TLRPC.Document d) {return d.name;}}',
            'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC {public static class Message {public int date,edit_date;public Message fwd_from;} public static class Document {public String name,mime_type;public long size;public java.util.ArrayList<DocumentAttribute> attributes=new java.util.ArrayList<>();} public static class DocumentAttribute {public boolean supports_streaming;public String file_name;public byte[] waveform;} public static class TL_documentAttributeFilename extends DocumentAttribute {} public static class TL_documentAttributeVideo extends DocumentAttribute {} public static class TL_documentAttributeAudio extends DocumentAttribute {}}',
            'org/telegram/messenger/MessageObject.java': 'package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessageObject {public TLRPC.Message messageOwner = new TLRPC.Message();public TLRPC.Document document;public int getId(){return Integer.MAX_VALUE;}public long getDialogId(){return 9223372036854775806L;}public long getFromChatId(){return 1000000000001L;}public TLRPC.Document getDocument(){return document;}public boolean voice,round;public boolean isVoice(){return voice;}public boolean isRoundVideo(){return round;}}',
            'Check.java': '''import app.nebulagram.ui.NebulaMessageDetails;import org.telegram.messenger.MessageObject;import org.telegram.tgnet.TLRPC;import java.util.*;
public class Check {static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[]args){
 check(NebulaMessageDetails.snapshot(null,false).isEmpty());
 MessageObject m=new MessageObject();List<NebulaMessageDetails.Field> sparse=NebulaMessageDetails.snapshot(m,false);check(sparse.size()==3);check(sparse.get(1).value.equals("9223372036854775806"));
 try{sparse.clear();throw new AssertionError("Mutable snapshot");}catch(UnsupportedOperationException expected){}
 m.messageOwner.date=Integer.MAX_VALUE;m.messageOwner.edit_date=Integer.MAX_VALUE;m.messageOwner.fwd_from=new TLRPC.Message();m.messageOwner.fwd_from.date=Integer.MAX_VALUE;
 m.document=new TLRPC.Document();m.document.name="имя 🎵.ogg";m.document.mime_type="audio/ogg";m.document.size=5000000000L;
 List<NebulaMessageDetails.Field> full=NebulaMessageDetails.snapshot(m,true);check(full.size()==9);check(full.stream().anyMatch(f->f.value.equals("имя 🎵.ogg")));check(full.stream().anyMatch(f->f.value.contains("5000000000 байт")));check(full.stream().filter(f->f.value.startsWith("2038-")).count()==3);
 m.voice=true;TLRPC.TL_documentAttributeAudio audio=new TLRPC.TL_documentAttributeAudio();audio.waveform=new byte[63];m.document.name=null;m.document.attributes.add(audio);
 List<NebulaMessageDetails.Field> voice=NebulaMessageDetails.snapshot(m,true);check(voice.size()==9);check(voice.get(8).title.equals("Отправлено с")&&voice.get(8).value.contains("Android и iOS"));
 m.voice=false;m.round=true;m.document.mime_type="video/mp4";m.document.attributes.clear();TLRPC.TL_documentAttributeFilename name=new TLRPC.TL_documentAttributeFilename();name.file_name="video.mp4";m.document.attributes.add(name);m.document.attributes.add(new TLRPC.TL_documentAttributeVideo());
 check(NebulaMessageDetails.snapshot(m,false).stream().anyMatch(f->f.title.equals("Sent from")&&f.value.contains("iOS")));
 m.messageOwner=null;check(NebulaMessageDetails.snapshot(m,false).isEmpty());
 System.out.println("Java message details: null/sparse/full metadata, immutable snapshot, 64-bit identities and sizes, Unicode names, 2038 dates and voice/round sender platform passed");}}
'''
        }
        sources = [str(ui / 'NebulaMessageDetails.java'), str(ui / 'NebulaSentFromRules.java')]
        for name, content in files.items():
            path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content, encoding='utf-8'); sources.append(str(path))
        home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work), *sources], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', str(work), 'Check'], check=True)
