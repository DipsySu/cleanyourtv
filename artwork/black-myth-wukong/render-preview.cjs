// Render a local design preview. This is not an Android device screenshot.
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const root = path.resolve(__dirname, '../..');
const theme = path.join(root, 'theme-packs/black-myth-wukong');
const entries = JSON.parse(fs.readFileSync(path.join(__dirname, 'icon-map.json'))).entries;
const dataURL = (file, type) => `data:${type};base64,${fs.readFileSync(file).toString('base64')}`;
const wallpaper = dataURL(path.join(theme, 'wallpaper.jpg'), 'image/jpeg');
const font = dataURL(path.join(root, 'launcher/app/src/main/res/font/outfit.ttf'), 'font/ttf');
const escape = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
const card = (item, detail = false) => `<article class="item"><div class="card"><img src="${dataURL(path.join(theme, item.banner.split('#')[0]), 'image/jpeg')}" alt="${escape(item.achievement_name)}"></div><div class="label">${escape(item.label)}</div>${detail ? `<p>第${item.achievement_number}难 · ${escape(item.achievement_name)}</p>` : ''}</article>`;
const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>黑神话 · 八十一难 — 主题预览</title><style>
@font-face{font-family:Outfit;src:url('${font}');font-weight:100 900}
*{box-sizing:border-box}body{margin:0;background:#181713;color:#eee8db;font-family:Outfit,"PingFang SC",sans-serif}header,section,footer{max-width:1760px;margin:0 auto;padding:40px 0}h1{font-size:42px;font-weight:500;margin:10px 0}p{color:#b6afa1;line-height:1.7}.eyebrow{color:#c88a64;letter-spacing:4px;font-size:13px}.screen-wrap{width:100%;overflow:auto}.screen{position:relative;width:1920px;height:1080px;background:url('${wallpaper}') center/cover}.top{position:absolute;top:54px;left:112px;right:112px;display:flex;justify-content:space-between;align-items:center}.greeting{font-size:20px;color:#ffffff8c;letter-spacing:4px;margin-left:6px}.clock-line{display:flex;align-items:center;gap:32px}.clock{font-family:Outfit,sans-serif;font-size:104px;font-weight:300;letter-spacing:-4px;line-height:1.1;color:white}.date{font-size:25px;letter-spacing:3px;color:#ffffffa6;border-left:2px solid #ffffff33;padding-left:32px}.buttons{display:flex;gap:20px}.buttons span{border-radius:35px;background:#1719179e;padding:20px 30px;color:#eee;font-size:24px;border:1px solid #ffffff18}.dock{position:absolute;left:98px;right:98px;bottom:58px;display:grid;grid-template-columns:repeat(6,1fr);gap:26px;padding:26px 26px 20px;border-radius:30px;background:linear-gradient(rgba(75,66,55,.42),rgba(34,30,25,.56));border:2px solid #cdcac74d}.item{text-align:center;min-width:0}.card{position:relative;aspect-ratio:180/101;background:#d2cec8;border-radius:14px;overflow:hidden}.card img{display:block;width:100%;height:100%;object-fit:cover}.label{font-size:25px;color:white;line-height:1.4;margin-top:16px}.gallery{display:grid;grid-template-columns:repeat(4,1fr);gap:44px 30px}.gallery .card{max-width:370px;margin:auto}.gallery p{font-size:16px;margin-top:8px}h2{font-weight:500;font-size:30px}footer{border-top:1px solid #ffffff20;font-size:16px;color:#b6afa1}a{color:#dfaa78}header p{max-width:950px}.screen-note{max-width:1760px;margin:18px auto;color:#b6afa1}
</style><header><div class="eyebrow">BLACK MYTH: WUKONG</div><h1>黑神话 · 八十一难</h1><p>朱砂成就印章 × 水墨山景。16 张朱砂印章配暖灰宣纸卡片，呼应壁纸云雾；配合电视桌面的六列布局。</p><p><a href="delivery/black-myth-wukong-v3.zip">下载主题 ZIP</a> · <a href="README.md">来源与导入说明</a></p></header><div class="screen-wrap"><div class="screen" id="screen"><div class="top"><div><div class="greeting">晚上好</div><div class="clock-line"><span class="clock">20:26</span><span class="date">10月24日 · 星期六</span></div></div><div class="buttons"><span>应用库</span><span>设置</span></div></div><div class="dock">${entries.slice(0,6).map(e=>card(e)).join('')}</div></div></div><div class="screen-note">布局示意，非实机截图。卡片使用主题包的满版横幅；实际应用顺序、日期、字体和焦点以电视为准。</div><section><h2>16 张应用卡片</h2><p>原始 256 × 256 成就图完整居中，导出为 512 × 288 暖灰宣纸横幅；取消青绿底板与硬金角。</p><div class="gallery">${entries.map(e=>card(e,true)).join('')}</div></section><footer>个人同人主题，与原作方无官方关联。图标来自用户提供的 Steam 成就素材；壁纸为生成绘画。</footer></html>`;
fs.writeFileSync(path.join(__dirname, 'preview.html'), html);

async function main() {
  // NODE_PATH may point at the Codex bundled dependencies or a local Playwright install.
  const { chromium } = require('playwright');
  const browser = await chromium.launch({headless: true,channel: 'chrome'});
  try {
    const page = await browser.newPage({viewport: {width:1920,height:1400},deviceScaleFactor:1});
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    await page.goto(pathToFileURL(path.join(__dirname, 'preview.html')).href);
    await page.evaluate(async () => {await document.fonts.ready;await Promise.all([...document.images].map(i=>i.decode()));});
    await page.evaluate(() => window.scrollTo(0, 0));
    const screen = await page.locator('#screen').boundingBox();
    await page.screenshot({path:path.join(theme,'preview.jpg'),type:'jpeg',quality:94,
      clip:{x:Math.round(screen.x),y:Math.round(screen.y),width:1920,height:1080}});
    const missing = await page.evaluate(()=>[...document.images].filter(i=>!i.complete||!i.naturalWidth).length);
    if (errors.length || missing) throw new Error(JSON.stringify({errors,missing}));
    fs.mkdirSync(path.join(__dirname,'delivery'),{recursive:true});
    fs.copyFileSync(path.join(theme,'preview.jpg'),path.join(__dirname,'delivery/preview.jpg'));
    console.log(JSON.stringify({preview:path.join(theme,'preview.jpg'),images:22,missing,errors}));
  } finally {await browser.close();}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
