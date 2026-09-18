// Deterministic refraction of the wallpaper below the glass, independent of camera motion.
const wavePattern = new Image();
wavePattern.src = 'assets/pattern-v3.svg';
window.waveReady = wavePattern.decode();
let waveSurface;
function drawGlassWaves(t) {
  if (!waveSurface) {
    const host = document.querySelector('.fly-identity');
    const canvas = document.createElement('canvas');
    canvas.width = 720; canvas.height = 248;
    canvas.style.cssText = 'position:absolute;inset:0;width:100%;height:100%;z-index:0;pointer-events:none';
    host.prepend(canvas);
    const texture = document.createElement('canvas');
    texture.width = 900; texture.height = 430;
    const ctx = texture.getContext('2d');
    const fill = ctx.createLinearGradient(0,0,900,430);
    fill.addColorStop(0, '#3c6478'); fill.addColorStop(1, '#243e53');
    ctx.fillStyle = fill; ctx.fillRect(0,0,900,430);
    ctx.globalAlpha = .38;
    // Same wallpaper and scale as the surface underneath, with padding for displacement.
    const wall=document.querySelector('.fly-wall');
    const tile=1240, originX=90-((host.offsetLeft-wall.offsetLeft)*2)%tile,
      originY=90-((host.offsetTop-wall.offsetTop)*2)%tile;
    for(let y=originY-tile;y<430;y+=tile) for(let x=originX-tile;x<900;x+=tile) ctx.drawImage(wavePattern,x,y,tile,tile);
    waveSurface = {ctx:canvas.getContext('2d'),pixels:ctx.getImageData(0,0,900,430).data};
  }
  const {ctx,pixels} = waveSurface, frame = ctx.createImageData(720,248);
  for(let y=0;y<248;y++) for(let x=0;x<720;x++) {
    const dx=x-24,dy=(y-146)*1.15,r=Math.hypot(dx,dy),nx=dx/Math.max(r,1),ny=dy/Math.max(r,1);
    let slope=0,light=0;
    for(const start of [-.5,.65,1.8]) {
      const age=t-start;
      if(age<0)continue;
      const q=(r-age*340)/29, envelope=Math.exp(-q*q*.5)*Math.exp(-age*.22);
      slope+=Math.sin(q*1.8)*envelope;
      light+=Math.cos(q*1.8)*envelope;
    }
    const sx=Math.max(0,Math.min(899,Math.round(x+90+nx*slope*23)));
    const sy=Math.max(0,Math.min(429,Math.round(y+90+ny*slope*23)));
    const src=(sy*900+sx)*4,dst=(y*720+x)*4;
    for(let c=0;c<3;c++)frame.data[dst+c]=Math.max(0,Math.min(255,pixels[src+c]+light*31));
    frame.data[dst+3]=210;
  }
  ctx.putImageData(frame,0,0);
}
