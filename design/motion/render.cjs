// Deterministic local graphics renderer. Does not attach to a user's browser profile.
const {chromium}=require(process.env.NEBULA_NODE_MODULES+'/playwright');
const fs=require('fs'),path=require('path'),{spawn}=require('child_process'),{once}=require('events');
const root=path.resolve(__dirname,'../..'),out=path.join(root,'build/motion');
const frames=process.argv.includes('--stills'),preview=process.argv.includes('--preview');
const landscape=process.argv.includes('--landscape');
const width=preview?(landscape?960:540):(landscape?1920:1080),height=width*(landscape?9/16:16/9),fps=preview?30:60,duration=39.375;
const server=require('http').createServer((req,res)=>{
 const file=path.resolve(__dirname,'.'+decodeURIComponent(new URL(req.url,'http://localhost').pathname));
 if(!file.startsWith(__dirname+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404);res.end();return}
 res.setHeader('Content-Type',({'.html':'text/html; charset=utf-8','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png'})[path.extname(file)]||'application/octet-stream');
 fs.createReadStream(file).pipe(res);
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));server.unref();
 fs.mkdirSync(out,{recursive:true});
 const browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--disable-background-timer-throttling','--disable-renderer-backgrounding','--hide-scrollbars','--force-color-profile=srgb']});
 const page=await browser.newPage({viewport:{width,height},deviceScaleFactor:1});
 page.on('pageerror',e=>{console.error(e);process.exitCode=1});
 await page.goto(`http://127.0.0.1:${server.address().port}/film.html`+(landscape?'?landscape':''));await page.evaluate(()=>window.ready);
 if(frames){for(let t of [1.9,3.5,4.5,6.7,7.5,8.4,10.8,13.5,14.5,15.1,16.5,18.8,20.2,22.5,23.3,24.6,25.9,27.5,28.6,32.5,37]){await page.evaluate(t=>window.render(t),t);await page.screenshot({path:path.join(out,`${landscape?'wide-':''}still-${t}.png`)});}await browser.close();return}
 const exportName=process.argv.find(a=>a.startsWith('--name='))?.slice(7);
 const file=path.join(out,exportName||(preview?'NebulaGram-preview.mp4':'NebulaGram-motion-1080x1920.mp4'));
 const log=fs.openSync(path.join(out,path.basename(file,'.mp4')+'.log'),'w');
 const ff=spawn(process.env.NEBULA_FFMPEG,['-y','-hide_banner','-f','image2pipe','-vcodec','mjpeg','-framerate',String(fps),'-i','pipe:0','-i',path.join(out,'music-edit.wav'),'-map','0:v:0','-map','1:a:0','-c:v','libx264','-preset','fast','-crf',preview?'21':'18','-vf','scale=in_range=full:out_range=tv:out_color_matrix=bt709,format=yuv420p','-pix_fmt','yuv420p','-color_range','tv','-colorspace','bt709','-color_primaries','bt709','-color_trc','bt709','-r',String(fps),'-c:a','aac','-b:a','256k','-movflags','+faststart','-t',String(duration),'-metadata','title=NebulaGram — In your element',file],{stdio:['pipe','ignore',log]});
 ff.stdin.on('error',e=>console.error(e.message));
 for(let i=0;i<Math.ceil(duration*fps);i++){
  await page.evaluate(t=>window.render(t),i/fps);
  const b=await page.screenshot({type:'jpeg',quality:94,animations:'allow'});
  if(!ff.stdin.write(b))await once(ff.stdin,'drain');
  if(i%(fps*2)===0)console.log(`${(i/fps).toFixed(1)} / ${duration}s`);
 }
 ff.stdin.end();const [code]=await once(ff,'exit');await browser.close();fs.closeSync(log);if(code)throw Error('ffmpeg exit '+code);console.log(file);
})().catch(e=>{console.error(e);process.exit(1)});
