"""Execute the shader's scalar displacement envelope; GPU rendering still needs a device."""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

tree = Path(sys.argv[1])
shader = (tree/'TMessagesProj/src/main/res/raw/liquid_glass_shader.agsl').read_text(encoding='utf-8')
start = shader.index('float nebulaEdgeLimit(')
end = shader.index('\n}', start) + 2
helper = re.sub(r'(\d+\.\d+)', r'\1f', shader[start:end])
# Ensure the tested bound is actually consumed, and skip lens work in the center.
assert 'limit / max(length(displacement), 0.001)' in shader
assert 'sd > -thickness && refract_intensity > 0.0' in shader
source = '''class GlassEdgeCheck {
 static float clamp(float x,float lo,float hi){return Math.max(lo,Math.min(hi,x));}
 static float max(float a,float b){return Math.max(a,b);}
 static float smoothstep(float a,float b,float x){float t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t);}
 static HELPER
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  int cases=0;
  for(float thickness:new float[]{1,5,11,22,33})for(int setting=0;setting<=100;setting++){
   float intensity=setting*.005f,previous=0;
   check(nebulaEdgeLimit(0,thickness,intensity)==0,"discontinuity at outer edge");
   check(nebulaEdgeLimit(-thickness,thickness,intensity)==0,"discontinuity at inner edge");
   for(int step=0;step<=1000;step++){
    float value=nebulaEdgeLimit(-thickness*step/1000,thickness,intensity);
    check(Float.isFinite(value)&&value>=0&&value<=thickness*.35f+.0001f,"unbounded displacement");
    check(Math.abs(value-previous)<thickness*.004f,"edge jump");
    if(setting==0)check(value==0,"zero still distorts");
    previous=value;cases++;
   }
  }
  System.out.println(cases+" glass edge samples: finite, bounded, smooth boundaries and zero bypass passed (scalar math, not GPU rendering)");
 }
}'''.replace('HELPER', helper)
with tempfile.TemporaryDirectory(prefix='nebula-glass-edge-') as temp:
    p = Path(temp)
    (p/'GlassEdgeCheck.java').write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-d', str(p), str(p/'GlassEdgeCheck.java')], check=True)
    subprocess.run(['java', '-cp', str(p), 'GlassEdgeCheck'], check=True)
