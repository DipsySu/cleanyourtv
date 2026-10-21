import {createRequire} from 'node:module';
import fs from 'node:fs/promises';
import path from 'node:path';
import {pathToFileURL,fileURLToPath} from 'node:url';
const require=createRequire(import.meta.url),{chromium}=require('playwright');
const root=path.dirname(fileURLToPath(import.meta.url));
const mode=process.argv[2]||'all';
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
const output=path.join(root,'validation');await fs.mkdir(output,{recursive:true});
let page;
try{
 page=await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
 const errors=[],network=[];page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url()))network.push(r.url())});
 await page.goto(pathToFileURL(path.join(root,'preview.html')).href);
 async function loaded(){await page.evaluate(async()=>{const imgs=[...document.querySelectorAll('.card img,.quick-card img')];for(const i of imgs)i.loading='eager';await Promise.all(imgs.map(i=>i.decode()));});}
 await loaded();const cards=page.locator('#grid .card');if(await cards.count()!==40)throw Error('Expected 40 icons');
 if(mode!=='export-only'){
  await page.screenshot({path:path.join(output,'desktop.png'),fullPage:true});
  await page.locator('#search').fill('Cappy');if(await cards.count()!==1)throw Error('Character search failed');
  await page.locator('#search').fill('no-such-character-zzz');if(await cards.count()!==0)throw Error('Empty state failed');
  await page.locator('#search').fill('');await page.locator('button[data-category="媒体播放器"]').click();if(await cards.count()!==8)throw Error('Category failed');
  await page.locator('button[data-category="全部"]').click();await loaded();
  await page.locator('#grid [data-app-id="emby"]').click();if(!await page.locator('#detail').evaluate(e=>e.open))throw Error('Detail failed');
  if(!(await page.locator('#download').getAttribute('href')).endsWith('emby.png'))throw Error('Download mismatch');await page.keyboard.press('Escape');
  await cards.first().focus();await page.keyboard.press('ArrowRight');if(await page.evaluate(()=>document.activeElement.dataset.appId)!=='bili')throw Error('Keyboard navigation failed');
  await page.locator('#format').click();let box=await page.locator('#grid .art').first().boundingBox();if(Math.abs(box.width-box.height)>1)throw Error('Square aspect failed');
  await loaded();await page.screenshot({path:path.join(output,'square.png'),fullPage:true});
  await page.locator('#size').click();box=await page.locator('#grid .art').first().boundingBox();if(box.width!==160||box.height!==160)throw Error('Small square failed');
  await page.locator('#format').click();box=await page.locator('#grid .art').first().boundingBox();if(box.width!==160||box.height!==90)throw Error('TV banner failed');
  await page.locator('#theme').click();await loaded();await page.screenshot({path:path.join(output,'small-dark.png'),fullPage:true});
  await page.setViewportSize({width:390,height:844});if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth))throw Error('Mobile overflow');await loaded();await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  const r={icons:40,imagesLoaded:44,search:true,categories:true,emptyState:true,dialogAndDownload:true,keyboard:true,square:true,smallSquare:[160,160],smallBanner:[160,90],mobileWidth:390,errors,remoteRequests:network};if(errors.length||network.length)throw Error(JSON.stringify(r));await fs.writeFile(path.join(output,'interaction-report.json'),JSON.stringify(r,null,2)+'\n');
 }
 await page.close();
 page=await browser.newPage({viewport:{width:1920,height:1080},deviceScaleFactor:1});await page.goto(pathToFileURL(path.join(root,'preview.html')).href);await loaded();
 await page.addStyleTag({content:'.shell{width:1808px;max-width:none}.top{padding:32px 0 24px}.heading h1{font-size:34px}.heading p{font-size:16px;margin-top:8px}.controls,.quick,.section-title{display:none}.grid{grid-template-columns:repeat(8,minmax(0,1fr));gap:18px 20px}.art{border-radius:17px}.meta{padding-top:7px}.meta strong{font-size:16px}.character{font-size:10px}body{background:#fff7e7}'});
 await page.screenshot({path:path.join(root,'contact-sheet.png')});
 if(mode!=='test-only'){
  await page.close();page=await browser.newPage({viewport:{width:900,height:700},deviceScaleFactor:2});await page.goto(pathToFileURL(path.join(root,'preview.html')).href);await loaded();
  await page.addStyleTag({content:'html,body{background:transparent!important}header,.controls,section[aria-label="固定栏精选"],.section-title,.meta{display:none!important}.shell{width:850px;margin:0}.grid{position:absolute;top:0;left:0;grid-template-columns:repeat(2,320px);gap:16px;align-items:start}.card{display:block;line-height:0}.card .art{width:320px;height:180px;aspect-ratio:auto;border-radius:22px;box-shadow:none}.card.square .art{width:256px;height:256px;border-radius:58px}.meta{height:50px}'});
  for(const kind of ['banner','square']){
   const dir=path.join(root,kind==='banner'?'banners':'squares');await fs.mkdir(dir,{recursive:true});
   if(kind==='square'){await page.evaluate(()=>document.querySelector('#format').click());await loaded();}
   const ids=await page.evaluate(()=>window.__iconCollection.map(x=>x.id));
   for(const id of ids)await page.locator(`#grid [data-app-id="${id}"] .art`).screenshot({path:path.join(dir,id+'.png'),omitBackground:true});
  }
 }
 console.log(JSON.stringify({icons:40,uiChecks:mode==='export-only'?'not-run':'passed',exported:mode==='test-only'?0:80}));
}finally{await browser.close()}
