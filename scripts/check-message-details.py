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
final class TelegramMediaFile {let fileName: String?; let mimeType: String; let size: Int64?; init(_ name: String?, _ mime: String, _ size: Int64?) {fileName = name; mimeType = mime; self.size = size}}
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
 print("Swift message details: sparse/full metadata, 64-bit identities and sizes, UTF-16 names and 2038 dates passed")
}}
'''.replace('SNAPSHOT', snapshot), encoding='utf-8')
        binary = work / 'check'
        subprocess.run(['swiftc', '-parse-as-library', str(test), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True)
    else:
        files = {
            'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static String formatFileSize(long n) {return "size";}}',
            'org/telegram/messenger/FileLoader.java': 'package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class FileLoader {public static String getDocumentFileName(TLRPC.Document d) {return d.name;}}',
            'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC {public static class Message {public int date,edit_date;public Message fwd_from;} public static class Document {public String name,mime_type;public long size;}}',
            'org/telegram/messenger/MessageObject.java': 'package org.telegram.messenger; import org.telegram.tgnet.TLRPC; public class MessageObject {public TLRPC.Message messageOwner = new TLRPC.Message();public TLRPC.Document document;public int getId(){return Integer.MAX_VALUE;}public long getDialogId(){return 9223372036854775806L;}public long getFromChatId(){return 1000000000001L;}public TLRPC.Document getDocument(){return document;}}',
            'Check.java': '''import app.nebulagram.ui.NebulaMessageDetails;import org.telegram.messenger.MessageObject;import org.telegram.tgnet.TLRPC;import java.util.*;
public class Check {static void check(boolean b){if(!b)throw new AssertionError();}public static void main(String[]args){
 check(NebulaMessageDetails.snapshot(null,false).isEmpty());
 MessageObject m=new MessageObject();List<NebulaMessageDetails.Field> sparse=NebulaMessageDetails.snapshot(m,false);check(sparse.size()==3);check(sparse.get(1).value.equals("9223372036854775806"));
 try{sparse.clear();throw new AssertionError("Mutable snapshot");}catch(UnsupportedOperationException expected){}
 m.messageOwner.date=Integer.MAX_VALUE;m.messageOwner.edit_date=Integer.MAX_VALUE;m.messageOwner.fwd_from=new TLRPC.Message();m.messageOwner.fwd_from.date=Integer.MAX_VALUE;
 m.document=new TLRPC.Document();m.document.name="имя 🎵.ogg";m.document.mime_type="audio/ogg";m.document.size=5000000000L;
 List<NebulaMessageDetails.Field> full=NebulaMessageDetails.snapshot(m,true);check(full.size()==9);check(full.stream().anyMatch(f->f.value.equals("имя 🎵.ogg")));check(full.stream().anyMatch(f->f.value.contains("5000000000 байт")));check(full.stream().filter(f->f.value.startsWith("2038-")).count()==3);
 m.messageOwner=null;check(NebulaMessageDetails.snapshot(m,false).isEmpty());
 System.out.println("Java message details: null/sparse/full metadata, immutable snapshot, 64-bit identities and sizes, Unicode names and 2038 dates passed");}}
'''
        }
        sources = [str(ui / 'NebulaMessageDetails.java')]
        for name, content in files.items():
            path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content, encoding='utf-8'); sources.append(str(path))
        home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work), *sources], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', str(work), 'Check'], check=True)
