#!/usr/bin/env node
/**
 * Archived version 1 inventory art for the Zelda Wilds theme.
 * Outputs transparent 512 x 288 PNG banners and their inspectable SVG sources.
 */
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = fileURLToPath(new URL('../..', import.meta.url));
const svgDir = join(root, 'artwork/theme-packs/zelda-svg');
const pngDir = join(root, 'backup/zelda-vector-v1/banners');
const W = 512, H = 288;
const colors = { ink: '#10202a', deep: '#17395a', blue: '#2d6f9d', jade: '#51b99b', mint: '#bde6c9', gold: '#d5ad55', pale: '#f3e4b5', silver: '#bacbd1', bronze: '#a76f3c', stone: '#73868a', glow: '#d8f3ce' };
const defs = `
  <defs>
    <linearGradient id="jade" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#d0f1ce"/><stop offset=".38" stop-color="#54b89a"/><stop offset="1" stop-color="#1d6f75"/></linearGradient>
    <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#fff3bd"/><stop offset=".38" stop-color="#d6ad55"/><stop offset="1" stop-color="#8b532c"/></linearGradient>
    <linearGradient id="steel" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#f4fbf2"/><stop offset=".45" stop-color="#b7ccd1"/><stop offset="1" stop-color="#466373"/></linearGradient>
    <linearGradient id="bronze" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#e7bc6a"/><stop offset=".42" stop-color="#ad7440"/><stop offset="1" stop-color="#623a2a"/></linearGradient>
    <linearGradient id="stone" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#d3ddd0"/><stop offset=".48" stop-color="#819296"/><stop offset="1" stop-color="#45575e"/></linearGradient>
    <linearGradient id="deep" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#407faf"/><stop offset=".45" stop-color="#204d7a"/><stop offset="1" stop-color="#112d52"/></linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur in="SourceAlpha" stdDeviation="5"/><feOffset dy="6"/><feComponentTransfer><feFuncA type="linear" slope=".28"/></feComponentTransfer><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter>
  </defs>`;
const frame = (body) => `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">${defs}<g filter="url(#shadow)" stroke="${colors.ink}" stroke-width="7" stroke-linejoin="round" stroke-linecap="round">${body}</g></svg>`;
const gem = (x,y,r=13) => `<path d="M${x} ${y-r} l${r*.7} ${r*.35} l${r*.3} ${r*.75} l-${r*.3} ${r*.75} l-${r*.7} ${r*.35} l-${r*.7} -${r*.35} l-${r*.3} -${r*.75} l${r*.3} -${r*.75}z" fill="url(#jade)"/><path d="M${x} ${y-r}v${r*2}M${x-r} ${y}h${r*2}" fill="none" stroke="#d7f5d7" stroke-width="2"/>`;
const icons = {
  cast: () => `<path d="M256 56C171 56 116 111 91 144c25 33 80 88 165 88s140-55 165-88C396 111 341 56 256 56z" fill="url(#deep)"/><path d="M256 84c-39 0-70 27-70 60s31 60 70 60 70-27 70-60-31-60-70-60z" fill="url(#jade)"/><path d="M256 105c-17 19-28 29-28 46 0 16 12 29 28 29s28-13 28-29c0-17-11-27-28-46z" fill="url(#gold)"/><path d="M117 144c33-31 70-40 111-40m56 0c41 0 78 9 111 40M117 144c33 31 70 40 111 40m56 0c41 0 78-9 111-40" fill="none" stroke="#b9e7cc" stroke-width="4"/>${gem(166,113)}${gem(346,113)}`,
  bili: () => `<path d="M257 48c-65 34-111 83-111 144 0 32 28 52 110 52s110-20 110-52c0-61-46-110-109-144z" fill="url(#jade)"/><path d="M256 65c-42 43-69 85-69 125 0 22 23 32 69 32s69-10 69-32c0-40-27-82-69-125z" fill="#77c99b"/><path d="M215 166c0-22 18-39 41-39s41 17 41 39" fill="none" stroke="url(#deep)" stroke-width="14"/><path d="M218 191c22 16 54 16 76 0" fill="none" stroke="#17395a" stroke-width="9"/><path d="M256 65v49m-45-16l21 32m69-32l-21 32" fill="none" stroke="#d6f1c7" stroke-width="5"/>${gem(185,185,10)}${gem(327,185,10)}`,
  live: () => `<path d="M104 173c30-78 84-105 152-105s122 27 152 105c-41-19-86-27-152-27s-111 8-152 27z" fill="url(#jade)"/><path d="M105 173c42-6 83 1 119 20l32-47 32 47c36-19 77-26 119-20" fill="url(#deep)"/><path d="M256 146v86m0-1l-45 18m45-18l45 18" fill="none" stroke="url(#gold)" stroke-width="12"/><path d="M154 122l39 50m165-50l-39 50" fill="none" stroke="#d6f1c7" stroke-width="5"/><circle cx="256" cy="233" r="10" fill="url(#gold)"/>`,
  emby: () => `<g transform="translate(30.72 17.28) scale(.88)"><path d="M256 37l22 47-12 106 62 43-25 20-47-25-47 25-25-20 62-43-12-106z" fill="url(#steel)"/><path d="M256 37v191m-22-144l22 16 22-16m-34 106l12 38 12-38" fill="none" stroke="#d9f2e6" stroke-width="4"/><path d="M183 231l73-30 73 30-17 37-56-18-56 18z" fill="url(#jade)"/><path d="M256 201v67" fill="none" stroke="url(#gold)" stroke-width="10"/><path d="M224 267l32-17 32 17-16 17h-32z" fill="url(#gold)"/></g>`,
  tencent: () => `<path d="M256 43l104 34v73c0 61-43 89-104 106-61-17-104-45-104-106V77z" fill="url(#steel)"/><path d="M256 62l83 28v58c0 48-31 70-83 87-52-17-83-39-83-87V90z" fill="url(#deep)"/><path d="M256 91L282 136H230ZM228 140L254 185H202ZM284 140L310 185H258Z" fill="url(#gold)"/><path d="M256 91L282 136H230ZM228 140L254 185H202ZM284 140L310 185H258Z" fill="none" stroke="#fff0bb" stroke-width="4"/>`,
  player: () => `<rect x="163" y="45" width="186" height="198" rx="24" fill="url(#deep)"/><rect x="182" y="67" width="148" height="112" rx="10" fill="#73c2b5"/><path d="M256 86c-27 0-48 21-48 47s21 47 48 47 48-21 48-47-21-47-48-47z" fill="none" stroke="#d6f5d2" stroke-width="7"/><path d="M256 98l17 25-17 10-17-10zM220 149l36-16 36 16M211 205h38m14 0h38" fill="none" stroke="url(#gold)" stroke-width="9"/><circle cx="295" cy="205" r="9" fill="url(#jade)"/>`,
  disk: () => `<g transform="translate(30.72 17.28) scale(.88)"><path d="M125 125l54-61h154l54 61v104l-45 34H170l-45-34z" fill="url(#bronze)"/><path d="M125 125h262l-45 45H170z" fill="url(#gold)"/><path d="M170 170h172v93H170z" fill="#8b542d"/><path d="M231 170v35h50v-35" fill="url(#jade)"/><path d="M184 91h144m-94 79v93m86-93v93" fill="none" stroke="#f0d98a" stroke-width="4"/>${gem(256,118,12)}</g>`,
  weather: () => `<path d="M230 61l13 29 32-4 4 32 29 13-17 28 17 28-29 13-4 32-32-4-13 29-28-17-28 17-13-29-32 4-4-32-29-13 17-28-17-28 29-13 4-32 32 4 13-29 28 17z" fill="url(#gold)"/><path d="M235 100c-28 0-51 23-51 51s23 51 51 51 51-23 51-51-23-51-51-51z" fill="#f7e5a2"/><path d="M254 211c13-26 39-38 65-28 24 9 32 37 22 56h-120c-10-21 3-48 33-48z" fill="url(#steel)"/><path d="M228 239h113" fill="none" stroke="#edf7ec" stroke-width="4"/>`,
  alarm: () => `<path d="M172 58h168l-25 38v93l25 39H172l25-39V96z" fill="url(#gold)"/><path d="M196 87h120l-16 28v56l16 28H196l16-28v-56z" fill="#e9d296"/><path d="M206 99l100 88m0-88l-100 88" stroke="#a66d3c" stroke-width="5"/><path d="M205 73h102m-102 140h102" fill="none" stroke="#fff1bd" stroke-width="5"/>${gem(256,144,12)}`,
  voice: () => `<path d="M111 173c55-19 130-25 252-6l39 40c-60 20-149 31-254 7l-37-41z" fill="url(#jade)"/><path d="M132 180c67 10 157 1 239-4l18 23c-75 20-153 23-230 7z" fill="#317d83"/><circle cx="181" cy="186" r="14" fill="url(#gold)"/><circle cx="238" cy="184" r="14" fill="url(#gold)"/><circle cx="295" cy="181" r="14" fill="url(#gold)"/><path d="M158 146l42-38 28 28 32-46 37 36 39-23" fill="none" stroke="url(#gold)" stroke-width="12"/><path d="M158 146l42-38 28 28 32-46 37 36 39-23" fill="none" stroke="#e8f4ca" stroke-width="3"/>`,
  light: () => `<path d="M256 38c-45 54-76 96-76 145 0 43 34 70 76 70s76-27 76-70c0-49-31-91-76-145z" fill="url(#jade)"/><path d="M256 76c-23 36-42 70-42 103 0 23 18 39 42 39s42-16 42-39c0-33-19-67-42-103z" fill="#d6f2bc"/><path d="M256 101c-10 23-17 42-17 59 0 11 7 19 17 19s17-8 17-19c0-17-7-36-17-59z" fill="url(#gold)"/><path d="M184 230l22-13m-7 31l21-18m92 0l21 18m-7-31l22 13" fill="none" stroke="#b6e8cf" stroke-width="7"/>`,
  calendar: () => `<path d="M151 50h210l20 31v145l-36 30H167l-36-30V81z" fill="url(#stone)"/><path d="M171 83h170v142H171z" fill="#b8c5b3"/><path d="M151 81h230M204 42v46m104-46v46" fill="none" stroke="url(#gold)" stroke-width="12"/><path d="M203 125l25-17 28 17-15 29h-30zm61 0l25-17 28 17-15 29h-30zm-61 58l25-17 28 17-15 29h-30zm61 0l25-17 28 17-15 29h-30z" fill="url(#jade)"/>`,
  gallery: () => `<path d="M101 232l84-117 53 62 48-82 125 137z" fill="url(#deep)"/><path d="M101 232l84-117 53 62 48-82 125 137z" fill="none" stroke="url(#gold)"/><path d="M147 232l39-55 31 36 69-118 80 137" fill="none" stroke="#a8dfc2" stroke-width="6"/><path d="M204 65c39-27 81-26 112 8 20 22 23 56 3 78-35 38-94 27-119-13-16-26-12-55 4-73z" fill="none" stroke="url(#jade)" stroke-width="15"/><circle cx="305" cy="108" r="17" fill="url(#gold)"/>`,
  manager: () => `<g transform="translate(30.72 17.28) scale(.88)"><path d="M256 51l21 32 37-6 3 39 34 18-19 34 19 34-34 18-3 39-37-6-21 32-21-32-37 6-3-39-34-18 19-34-19-34 34-18 3-39 37 6z" fill="url(#gold)"/><path d="M256 102c-36 0-65 29-65 65s29 65 65 65 65-29 65-65-29-65-65-65z" fill="url(#deep)"/><path d="M256 130c-20 0-37 17-37 37s17 37 37 37 37-17 37-37-17-37-37-37z" fill="url(#jade)"/><circle cx="256" cy="167" r="13" fill="url(#gold)"/></g>`,
  settings: () => `<g transform="translate(51.2 28.8) scale(.8)"><path d="M256 48c74 0 133 53 133 119s-59 119-133 119S123 233 123 167 182 48 256 48z" fill="url(#jade)"/><path d="M256 76c56 0 101 40 101 91s-45 91-101 91-101-40-101-91 45-91 101-91z" fill="#287a7d"/><path d="M256 102c38 0 68 28 68 64s-30 64-68 64-68-28-68-64 30-64 68-64z" fill="none" stroke="url(#gold)" stroke-width="16"/><path d="M256 102c-25 22-27 48-8 64 17 15 15 35-10 64m86-105c-32-8-55 6-58 31-3 22-22 31-59 20" fill="none" stroke="#d5f0c9" stroke-width="7"/><circle cx="256" cy="166" r="12" fill="url(#gold)"/></g>`,
  video: () => `<path d="M256 244c-81-46-133-91-133-143 0-35 26-61 61-61 28 0 51 15 72 40 21-25 44-40 72-40 35 0 61 26 61 61 0 52-52 97-135 143z" fill="url(#gold)"/><path d="M256 216c-57-35-98-70-98-110 0-17 12-31 29-31 28 0 48 29 69 52 21-23 41-52 69-52 17 0 29 14 29 31 0 40-41 75-98 110z" fill="#c74245"/><path d="M256 102v72m-36-36h72" stroke="#ffeab2" stroke-width="8" fill="none"/><path d="M162 89c12-18 28-24 46-23m142 23c-12-18-28-24-46-23" fill="none" stroke="#f4dc8b" stroke-width="7"/>`
};

async function main() {
  await Promise.all([mkdir(svgDir, { recursive: true }), mkdir(pngDir, { recursive: true })]);
  for (const [name, make] of Object.entries(icons)) {
    const svg = frame(make());
    await writeFile(join(svgDir, `${name}.svg`), svg + '\n');
    await sharp(Buffer.from(svg)).png({ compressionLevel: 9, palette: true }).toFile(join(pngDir, `${name}.png`));
  }
  console.log(`Rendered ${Object.keys(icons).length} Zelda vector cards.`);
}
main().catch((error) => { console.error(error); process.exitCode = 1; });
