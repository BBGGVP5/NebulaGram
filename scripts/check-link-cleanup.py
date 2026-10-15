"""Run identical link-cleanup fixtures through the production Java or Swift model."""
from pathlib import Path
import base64,json,os,shutil,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
cases=json.loads((root/'scripts/fixtures/link-cleanup.json').read_text(encoding='utf-8'))
cases += [{'input':'https://x.test/?utm_source=x'+')'*10000,'expected':'https://x.test/'+')'*10000},
 {'input':'x'*50001,'expected':'x'*50001}, {'input':'https://x.test/'+('a'*8200)+'?utm_source=x','expected':'https://x.test/'+('a'*8200)+'?utm_source=x'}]
with tempfile.TemporaryDirectory(prefix='nebula-link-cleanup-') as work:
 work=Path(work);fixture=work/'cases.txt';fixture.write_text('\n'.join(base64.b64encode(c['input'].encode()).decode()+'\t'+base64.b64encode(c['expected'].encode()).decode() for c in cases)+'\n',encoding='utf-8')
 if '--swift' in sys.argv:
  test=work/'check.swift';test.write_text('''import Foundation
@main struct Check {static func main() throws {
 let lines=try String(contentsOfFile:CommandLine.arguments[1],encoding:.utf8).split(separator:"\\n")
 for (index,line) in lines.enumerated(){let parts=line.split(separator:"\\t",omittingEmptySubsequences:false);let input=String(data:Data(base64Encoded:String(parts[0]))!,encoding:.utf8)!;let expected=String(data:Data(base64Encoded:String(parts[1]))!,encoding:.utf8)!;let result=NebulaLinkCleanup.clean(input);precondition(result==expected,"URL fixture \\(index)");precondition(NebulaLinkCleanup.clean(result)==result,"idempotence \\(index)")}
 print("Swift link cleanup: \\(lines.count) shared fixtures and idempotence passed")
}}''',encoding='utf-8')
  binary=work/'check';subprocess.run(['swiftc',str(root/'platform/ios/NebulaSettingsContract/Sources/NebulaSettingsContract/NebulaLinkCleanup.swift'),str(test),'-o',str(binary)],check=True);subprocess.run([str(binary),str(fixture)],check=True,timeout=30)
 else:
  test=work/'Check.java';test.write_text('''import app.nebulagram.ui.NebulaLinkCleanup;import java.nio.file.*;import java.nio.charset.*;import java.util.*;
public class Check{public static void main(String[]args)throws Exception{List<String>lines=Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8);int index=0;for(String line:lines){String[]parts=line.split("\\t",-1);String input=new String(Base64.getDecoder().decode(parts[0]),StandardCharsets.UTF_8);String expected=new String(Base64.getDecoder().decode(parts[1]),StandardCharsets.UTF_8);String result=NebulaLinkCleanup.clean(input);if(!result.equals(expected))throw new AssertionError("URL fixture "+index+" expected "+expected+" got "+result);if(!NebulaLinkCleanup.clean(result).equals(result))throw new AssertionError("Idempotence "+index);index++;}System.out.println("Java link cleanup: "+index+" shared fixtures and idempotence passed");}}
''',encoding='utf-8')
  java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'));subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'--release','8','-encoding','UTF-8','-d',str(work),str(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaLinkCleanup.java'),str(test)],check=True);subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'Check',str(fixture)],check=True,timeout=30)
