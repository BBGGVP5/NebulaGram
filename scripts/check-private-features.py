"""Exercise the sync document and local password store on the JVM."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
work = root / 'build/private-features-check'
work.mkdir(parents=True, exist_ok=True)
jar = next((Path.home() / '.gradle/caches/modules-2/files-2.1/org.json/json/20240303').glob('*/json-20240303.jar'), root / 'build/ai-protocol-check/json-20240303.jar')
source = work / 'android/util/AtomicFile.java'
source.parent.mkdir(parents=True, exist_ok=True)
source.write_text('''package android.util;
public final class AtomicFile {
 private final java.io.File file;
 public AtomicFile(java.io.File f){file=f;}
 public java.io.File getBaseFile(){return file;}
 public byte[] readFully() throws Exception{return java.nio.file.Files.readAllBytes(file.toPath());}
 public java.io.FileOutputStream startWrite() throws Exception{return new java.io.FileOutputStream(file);}
 public void finishWrite(java.io.FileOutputStream s) throws Exception{s.close();}
 public void failWrite(java.io.FileOutputStream s) throws Exception{s.close();}
}''', encoding='utf-8')
base = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sources = [source, base / 'NebulaSettingsSchema.java', base / 'NebulaSyncDocument.java',
           base / 'NebulaChatLockStore.java', root / 'tests/android/PrivateFeaturesCheck.java']
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), *map(str, sources)], check=True)
subprocess.run(['java', '-cp', os.pathsep.join([str(work), str(jar)]), 'PrivateFeaturesCheck'], check=True)
