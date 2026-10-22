import {createRequire} from 'node:module';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const root=path.dirname(fileURLToPath(import.meta.url));
const mode=process.argv[2]||'all';
if(!['all','verify-only'].includes(mode))throw Error('Usage: node render_wallpapers.mjs [verify-only]');
// System Chrome by default: no Playwright browser download is needed.
const executablePath=process.env.CHROME_PATH||'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const W=1920,H=1080;
// Where the launcher draws on top (see README): clock and greeting top-left, title, dock and one row below.
const regions={clock_area:[0,0,640,260],bottom_band:[0,594,1920,486],hero_band:[100,350,700,130]};
const limits={clock_area:0.35,bottom_band:0.30,hero_band:0.35,min_bytes:120*1024,max_bytes:320*1024};
const browser=await chromium.launch({headless:true,executablePath});
const validation=path.join(root,'validation');await fs.mkdir(validation,{recursive:true});
let page;
try{
 page=await browser.newPage({viewport:{width:W,height:H},deviceScaleFactor:1});
 const errors=[],network=[];page.on('pageerror',e=>errors.push(e.message));page.on('requestfailed',r=>errors.push('failed '+r.url()));page.on('request',r=>{if(/^https?:/.test(r.url()))network.push(r.url())});
 await page.goto(pathToFileURL(path.join(root,'wallpapers.html')).href,{waitUntil:'load'});
 // SVG <image> has no decode(); decoding each source once makes sure every character is ready to paint.
 await page.evaluate(async()=>{const srcs=[...new Set([...document.querySelectorAll('image')].map(i=>i.href.baseVal))];await Promise.all(srcs.map(s=>{const i=new Image();i.src=s;return i.decode()}));await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});
 const meta=await page.evaluate(()=>window.__wallpapers);
 if(meta.wallpapers.length!==8)throw Error('Expected 8 wallpapers');
 const output=path.resolve(root,meta.output);await fs.mkdir(output,{recursive:true});
 if(mode!=='verify-only'){
  for(const w of meta.wallpapers){
   const section=page.locator(`section[id="${w.id}"]`),box=await section.boundingBox();
   if(Math.round(box.width)!==W||Math.round(box.height)!==H)throw Error(`${w.id} is ${box.width}x${box.height}`);
   await section.screenshot({path:path.join(output,w.file),type:'jpeg',quality:meta.jpeg_quality});
  }
 }
 if(errors.length||network.length)throw Error(JSON.stringify({errors,remoteRequests:network}));
 await page.close();
 page=await browser.newPage({viewport:{width:W,height:H},deviceScaleFactor:1});
 const report=[];
 for(const w of meta.wallpapers){
  const file=path.join(output,w.file),data=await fs.readFile(file);
  if(data[0]!==0xff||data[1]!==0xd8||data[2]!==0xff)throw Error(`${w.file} is not a JPEG`);
  // Luma of the decoded JPEG (sRGB values, Rec. 709 weights), 0 black to 1 white.
  const measured=await page.evaluate(async([b64,regions])=>{const img=new Image();img.src='data:image/jpeg;base64,'+b64;await img.decode();const c=document.createElement('canvas');c.width=img.naturalWidth;c.height=img.naturalHeight;const g=c.getContext('2d',{willReadFrequently:true});g.drawImage(img,0,0);const mean=([x,y,w,h])=>{const d=g.getImageData(x,y,w,h).data;let s=0;for(let i=0;i<d.length;i+=4)s+=.2126*d[i]+.7152*d[i+1]+.0722*d[i+2];return s/(d.length/4)/255};const out={width:img.naturalWidth,height:img.naturalHeight,luminance:{}};for(const [k,r] of Object.entries(regions))out.luminance[k]=+mean(r).toFixed(4);out.luminance.overall=+mean([0,0,c.width,c.height]).toFixed(4);return out},[data.toString('base64'),regions]);
  const checks={size:measured.width===W&&measured.height===H,bytes:data.length>=limits.min_bytes&&data.length<=limits.max_bytes,
   clock_area:measured.luminance.clock_area<limits.clock_area,bottom_band:measured.luminance.bottom_band<limits.bottom_band,hero_band:measured.luminance.hero_band<limits.hero_band};
  report.push({id:w.id,file:path.relative(path.resolve(root,'../..'),file),title:w.title,characters:w.characters.map(ch=>({id:ch.id,name:ch.name.en,box:ch.box,face_y:ch.face_y,native:ch.native,scale:ch.scale})),
   width:measured.width,height:measured.height,bytes:data.length,kib:+(data.length/1024).toFixed(1),sha256:crypto.createHash('sha256').update(data).digest('hex'),luminance:measured.luminance,checks,pass:Object.values(checks).every(Boolean)});
 }
 const summary={generated_by:'render_wallpapers.mjs',chrome:browser.version(),jpeg_quality:meta.jpeg_quality,grain:meta.grain,regions,limits,
  luminance:'mean sRGB luma (0.2126 R + 0.7152 G + 0.0722 B) / 255 of the decoded JPEG',wallpapers:report,pass:report.every(r=>r.pass)};
 await fs.writeFile(path.join(validation,'wallpapers-report.json'),JSON.stringify(summary,null,2)+'\n');
 if(mode!=='verify-only'){
  // Contact sheet for review: the eight JPEGs as they will be pushed, with kingdom and character names.
  const cards=await Promise.all(meta.wallpapers.map(async w=>`<figure><img src="data:image/jpeg;base64,${(await fs.readFile(path.join(output,w.file))).toString('base64')}" alt=""><figcaption><b>${w.id.slice(0,2)} ${w.title.zh}</b> ${w.title.en}<span>${w.characters.map(ch=>ch.name.zh+' · '+ch.name.en).join('，')}</span></figcaption></figure>`));
  await page.setViewportSize({width:1440,height:200});
  await page.setContent(`<!doctype html><meta charset="utf-8"><style>body{margin:0;padding:22px 24px 24px;width:1440px;box-sizing:border-box;background:#060B18;color:#E8ECF5;font:14px/1.35 -apple-system,BlinkMacSystemFont,"PingFang SC",sans-serif}h1{margin:0 0 16px;font-size:20px;font-weight:650;letter-spacing:.02em}h1 small{margin-left:10px;font-size:13px;font-weight:400;color:#9AA6BF}main{display:grid;grid-template-columns:repeat(4,1fr);gap:18px 16px}figure{margin:0}img{display:block;width:100%;aspect-ratio:16/9;border-radius:10px;box-shadow:0 0 0 1px #FFFFFF1F}figcaption{padding:7px 2px 0;color:#C6CEDD}b{color:#F2F5FA;font-weight:650}span{display:block;margin-top:1px;font-size:12px;color:#8E9AB3}</style><h1>超级马力欧 奥德赛 · 王国壁纸<small>1920×1080 JPEG · 小米电视桌面背景</small></h1><main>${cards.join('')}</main>`);
  await page.evaluate(()=>Promise.all([...document.images].map(i=>i.decode())));
  await page.screenshot({path:path.join(root,'contact-sheet.jpg'),type:'jpeg',quality:82,fullPage:true});
 }
 for(const r of report)console.log(`${r.pass?'ok  ':'FAIL'} ${r.id.padEnd(12)} ${String(r.kib).padStart(6)} KiB  clock ${r.luminance.clock_area.toFixed(3)}  bottom ${r.luminance.bottom_band.toFixed(3)}  hero ${r.luminance.hero_band.toFixed(3)}  overall ${r.luminance.overall.toFixed(3)}`);
 if(!summary.pass)throw Error('Wallpaper validation failed; see validation/wallpapers-report.json');
 console.log(JSON.stringify({wallpapers:report.length,rendered:mode==='verify-only'?0:report.length,output:path.relative(root,output),pass:true}));
}finally{await browser.close()}
