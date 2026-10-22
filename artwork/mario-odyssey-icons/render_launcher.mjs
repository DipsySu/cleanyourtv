// Launcher masters: the preview's banner cards, full-bleed and opaque, at 768x432 for the 384x216 WebP export.
// The launcher clips and frames its own tiles, so the card border, inner frame, radius and shadow are dropped and
// the character is drawn ~15% larger to read at TV size. It grows up from the bottom edge, so half-length art
// meets the edge with no strip of background under it and full figures keep a little headroom.
// preview.html itself is not modified.
import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const root=path.dirname(fileURLToPath(import.meta.url)),output=path.join(root,'launcher');
// Same 320x180 card box as the 640x360 export, rendered at 2.4x: gradient, shadow and placement keep their proportions.
const box=[320,180],scale=2.4,size=box.map(v=>v*scale),characterScale=1.15;
const css=`html,body{background:#000!important;margin:0}header,.controls,section[aria-label="固定栏精选"],.section-title,.meta{display:none!important}.shell{width:${box[0]}px;margin:0}.grid{grid-template-columns:${box[0]}px;gap:0}.card{display:block;line-height:0}.card .art{width:${box[0]}px;height:${box[1]}px;aspect-ratio:auto;border:0;border-radius:0;box-shadow:none}.card .art:after{display:none}.card .art img{bottom:0;transform:scale(${characterScale});transform-origin:50% 100%}`;
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
try{
 const page=await browser.newPage({viewport:{width:640,height:400},deviceScaleFactor:scale});
 const errors=[],network=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))network.push(r.url())});
 await page.goto(pathToFileURL(path.join(root,'preview.html')).href);await page.addStyleTag({content:css});
 const broken=await page.evaluate(async()=>{const imgs=[...document.querySelectorAll('#grid .card img')];for(const i of imgs)i.loading='eager';await Promise.allSettled(imgs.map(i=>i.decode()));return imgs.filter(i=>!i.naturalWidth).map(i=>i.closest('.card').dataset.appId)});
 const ids=await page.evaluate(()=>window.__iconCollection.map(x=>x.id));
 if(ids.length!==40||broken.length)throw Error('Character images missing: '+broken.join(', '));
 await fs.mkdir(output,{recursive:true});
 for(const id of ids){
  const file=path.join(output,id+'.png');await page.locator(`#grid [data-app-id="${id}"] .art`).screenshot({path:file,animations:'disabled'});
  const png=await fs.readFile(file),dims=[png.readUInt32BE(16),png.readUInt32BE(20)];
  if(dims[0]!==size[0]||dims[1]!==size[1])throw Error(`${id}: ${dims.join('x')}, expected ${size.join('x')}`);
 }
 if(errors.length||network.length)throw Error(JSON.stringify({errors,network}));
 console.log(JSON.stringify({launcherMasters:ids.length,size,characterScale}));
}finally{await browser.close()}
