"""Compare production geometry with numeric samples of FlClash's physical curves."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
if len(sys.argv) > 1:
    ui = Path(sys.argv[1])
fixture = json.loads((root / 'scripts/fixtures/menu-flclash-motion.json').read_text(encoding='utf-8'))

def rows(samples):
    return ',\n'.join('{' + ','.join(f'{v:.10f}f' for v in row) + '}' for row in samples)

source = '''import app.nebulagram.ui.NebulaMenuBubble;
public class Check {static void near(float value,float expected,String why){
 if(Math.abs(value-expected)>.00004f)throw new AssertionError(why+": "+value+" expected "+expected);
}public static void main(String[] args){
 float[][] open={OPEN},close={CLOSE};int cases=0;
 NebulaMenuBubble.Frame frame=new NebulaMenuBubble.Frame(),from=new NebulaMenuBubble.Frame();
 for(float w:new float[]{180,240,600})for(float h:new float[]{96,400,900})for(int corner=0;corner<4;corner++){
  float x=(corner&1)==0?0:w,y=(corner&2)==0?0:h;
  for(float[] row:open){NebulaMenuBubble.opening(frame,row[0]/750,w,h,x,y,64,24);
   near((frame.width-64)/(w-64),row[1],"reference growth");near((frame.height-64)/(h-64),row[1],"reference height");
   near((frame.x-x)/(w/2-x),row[2],"reference X");near((frame.y-y)/(h/2-y),row[3],"reference Y");
   near(frame.content,row[4],"reference content focus");cases++;
  }
  NebulaMenuBubble.opening(from,1,w,h,x,y,64,24);
  for(float[] row:close){NebulaMenuBubble.closing(frame,from,row[0]/400,w,h,x,y,64);
   near((from.width-frame.width)/(w-64),row[1],"reference shrink");
   near((frame.x-from.x)/(x-from.x),row[2],"reference return X");near((frame.y-from.y)/(y-from.y),row[3],"reference return Y");
   near(frame.content,row[4],"reference closing focus");cases++;
  }
 }
 System.out.println(cases+" physical FlClash reference frames match growth, travel and content focus");
}}
'''.replace('OPEN', rows(fixture['open'])).replace('CLOSE', rows(fixture['close']))
with tempfile.TemporaryDirectory(prefix='nebula-menu-reference-') as folder:
    target = Path(folder) / 'Check.java'
    target.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(target), str(ui / 'NebulaMenuBubble.java')], check=True)
    subprocess.run(['java', '-cp', folder, 'Check'], check=True)
