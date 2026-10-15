"""Parse actual patched native Java and overlay files using javac, without Android stubs."""
from pathlib import Path
import os,re,shutil,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'vendor/telegram-android'
paths=set()
for patch in (root/'patches/android').glob('*.patch'):
 for path in re.findall(r'^\+\+\+ b/(.+\.java)$',patch.read_text(encoding='utf-8'),re.M):
  candidate=tree/path
  if candidate.is_file():paths.add(candidate.resolve())
paths.update(p.resolve() for p in (root/'platform/android/overlay').rglob('*.java'))
assert paths
source=r'''
import java.io.*;import java.nio.file.*;import java.nio.charset.*;import java.util.*;import javax.tools.*;import com.sun.source.util.JavacTask;
public class NativeSyntaxCheck {
 public static void main(String[] args)throws Exception {
  JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
  DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
  try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,StandardCharsets.UTF_8)){
   List<File> files=new ArrayList<>();for(String line:Files.readAllLines(Paths.get(args[0]),StandardCharsets.UTF_8))files.add(new File(line));
   JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,Arrays.asList("-proc:none","-encoding","UTF-8","--release","17"),null,manager.getJavaFileObjectsFromFiles(files));
   int units=0;for(Object unit:task.parse())units++;
   int errors=0;for(Diagnostic<? extends JavaFileObject> diagnostic:diagnostics.getDiagnostics())if(diagnostic.getKind()==Diagnostic.Kind.ERROR){System.err.println(diagnostic);errors++;}
   if(errors>0)throw new AssertionError(errors+" Java syntax errors");
   System.out.println("javac parsed "+units+" actual native/overlay Java units; dependency typecheck remains part of the APK build");
  }
 }
}'''
java_home=Path(os.environ.get('JAVA_HOME','C:/Program Files/Java/jdk-26.0.2'))
with tempfile.TemporaryDirectory(prefix='nebula-native-java-') as work:
 work=Path(work);test=work/'NativeSyntaxCheck.java';test.write_text(source,encoding='utf-8');manifest=work/'sources.txt';manifest.write_text('\n'.join(map(str,sorted(paths))),encoding='utf-8')
 subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-d',str(work),str(test)],check=True)
 subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-Xmx768m','-cp',str(work),'NativeSyntaxCheck',str(manifest)],check=True)
