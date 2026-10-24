// Export opaque native launcher banners. This script never rewrites source/ images.
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const artwork = __dirname;
const root = path.resolve(artwork, '../..');
const theme = path.join(root, 'theme-packs/black-myth-wukong');
const design = path.join(artwork, 'banner-design.html');
const entries = JSON.parse(fs.readFileSync(path.join(artwork, 'icon-map.json'), 'utf8')).entries;
const sha256 = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');

function jpegInfo(file) {
  const bytes = fs.readFileSync(file);
  if (bytes[0] !== 0xff || bytes[1] !== 0xd8) throw new Error(`${file} is not JPEG`);
  for (let i = 2; i < bytes.length;) {
    if (bytes[i] !== 0xff) throw new Error(`invalid JPEG marker in ${file}`);
    while (bytes[i] === 0xff) i++;
    const marker = bytes[i++];
    if (marker === 0xd9 || marker === 0xda) break;
    const length = bytes.readUInt16BE(i);
    if (length < 2 || i + length > bytes.length) throw new Error(`invalid JPEG segment in ${file}`);
    if ((marker >= 0xc0 && marker <= 0xc3) || (marker >= 0xc5 && marker <= 0xc7) || (marker >= 0xc9 && marker <= 0xcb) || (marker >= 0xcd && marker <= 0xcf)) {
      return { format: 'jpeg', width: bytes.readUInt16BE(i + 5), height: bytes.readUInt16BE(i + 3), opaque: true };
    }
    i += length;
  }
  throw new Error(`missing JPEG frame in ${file}`);
}

async function main() {
  if (entries.length !== 16) throw new Error(`expected 16 entries, got ${entries.length}`);
  const { chromium } = require('playwright');
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const outputs = [];
  try {
    const page = await browser.newPage({ viewport: { width: 512, height: 288 }, deviceScaleFactor: 1 });
    const httpRequests = [];
    const pageErrors = [];
    page.on('request', request => {
      if (/^https?:/i.test(request.url())) httpRequests.push(request.url());
    });
    page.on('pageerror', error => pageErrors.push(error.message));
    await page.goto(pathToFileURL(design).href);
    for (const entry of entries) {
      const source = path.join(artwork, entry.source_file);
      const output = path.join(theme, entry.banner);
      if (!fs.existsSync(source)) throw new Error(`missing source: ${entry.source_file}`);
      const sourceChecksum = sha256(source);
      if (sourceChecksum !== entry.source_sha256) throw new Error(`source checksum mismatch: ${entry.source_file}`);
      const sourceInfo = jpegInfo(source);
      if (sourceInfo.width !== 256 || sourceInfo.height !== 256) throw new Error(`source must be 256x256: ${entry.source_file}`);
      const decoded = await page.evaluate(async sourceUrl => {
        const image = document.querySelector('#source');
        image.src = sourceUrl;
        await image.decode();
        return { width: image.naturalWidth, height: image.naturalHeight };
      }, pathToFileURL(source).href);
      if (decoded.width !== 256 || decoded.height !== 256) throw new Error(`browser decode mismatch: ${entry.source_file}`);
      await page.screenshot({ path: output, type: 'jpeg', quality: 96, clip: { x: 0, y: 0, width: 512, height: 288 } });
      if (sha256(source) !== sourceChecksum) throw new Error(`source changed during export: ${entry.source_file}`);
      const outputInfo = jpegInfo(output);
      if (outputInfo.width !== 512 || outputInfo.height !== 288 || !outputInfo.opaque) throw new Error(`invalid banner export: ${entry.slug}`);
      outputs.push({
        slug: entry.slug,
        source_file: entry.source_file,
        source_sha256: sourceChecksum,
        source: sourceInfo,
        banner: entry.banner,
        output_sha256: sha256(output),
        output: outputInfo
      });
    }
    if (httpRequests.length) throw new Error(`unexpected remote requests: ${httpRequests.join(', ')}`);
    if (pageErrors.length) throw new Error(`page errors: ${pageErrors.join('; ')}`);
  } finally {
    await browser.close();
  }
  fs.writeFileSync(path.join(artwork, 'banner-export-report.json'), `${JSON.stringify({ design: { width: 512, height: 288, background: '#D7D7D7', warm_wash: { background: '#B79D77', opacity: 0.16 } }, entries: outputs }, null, 2)}\n`);
  console.log(JSON.stringify({ exported: outputs.length, report: path.join(artwork, 'banner-export-report.json') }));
}

main().catch(error => { console.error(error); process.exitCode = 1; });
