"""Exercise the production bounded sfnt reader with malformed and valid tables."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
source = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFontFile.java'
program = r'''
import app.nebulagram.ui.NebulaFontFile;
import java.io.*;import java.nio.*;
class FontImportCheck {
 static byte[] font(int magic){
  ByteBuffer b=ByteBuffer.allocate(102).order(ByteOrder.BIG_ENDIAN);
  b.putInt(magic).putShort((short)2);b.position(12);
  b.putInt(0x636d6170).putInt(0).putInt(44).putInt(4);
  b.putInt(0x68656164).putInt(0).putInt(48).putInt(54);return b.array();
 }
 static void rejected(byte[] data)throws Exception{
  try{NebulaFontFile.read(new ByteArrayInputStream(data));throw new AssertionError("Accepted malformed font");}
  catch(IOException expected){}
 }
 public static void main(String[] args)throws Exception{
  for(int magic:new int[]{0x00010000,0x4f54544f,0x74727565}) {
   byte[] good=font(magic);if(NebulaFontFile.read(new ByteArrayInputStream(good)).length!=102)throw new AssertionError();
   for(int size=0;size<good.length;size++)rejected(java.util.Arrays.copyOf(good,size));
   for(int bad:new int[]{0,1,43,102,-1,Integer.MAX_VALUE}) {
    byte[] data=good.clone();ByteBuffer.wrap(data).putInt(20,bad);rejected(data);
   }
  }
  rejected(font(0));rejected(new byte[NebulaFontFile.LIMIT+1]);
  byte[] missing=font(0x00010000);ByteBuffer.wrap(missing).putInt(12,0);rejected(missing);
  System.out.println("Font signatures, all truncations, table boundaries, unsigned offsets and size limit passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-font-') as directory:
    work=Path(directory); test=work/'FontImportCheck.java';test.write_text(program,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(source),str(test)],check=True)
    subprocess.run(['java','-cp',str(work),'FontImportCheck'],check=True)
