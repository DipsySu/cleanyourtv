#!/usr/bin/env python3
"""Build an offline, file://-friendly preview for the Odyssey TV icon pack.

Usage: python3 make_preview.py [pack_dir]

The pack directory normally contains manifest.json and characters/<id>.png.
During art production the sibling mapping.json is accepted as a source so the
layout can be reviewed before the final manifest is assembled.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


HERE = Path(__file__).resolve().parent
DEFAULT_PACK = HERE


def load_items(pack_dir: Path) -> list[dict]:
    """Read the final manifest, falling back to the work mapping when needed."""
    manifest = pack_dir / "manifest.json"
    source = manifest if manifest.exists() else pack_dir / "mapping.json"
    if not source.exists():
        raise FileNotFoundError(f"Missing manifest.json or mapping.json in {pack_dir}")
    value = json.loads(source.read_text(encoding="utf-8"))
    items = value.get("icons", value) if isinstance(value, dict) else value
    if not isinstance(items, list):
        raise ValueError(f"{source} must contain a list of icon records")
    required = {"id", "label", "category", "package", "aliases", "character", "character_zh"}
    for item in items:
        if not isinstance(item, dict) or required - item.keys():
            raise ValueError(f"Invalid icon record in {source}: {item!r}")
    return items


def script_json(value: object) -> str:
    """Keep embedded JSON safe inside a script element without changing data."""
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            .replace("</", "<\\/")
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029"))


HTML = r'''<!doctype html>
<html lang="zh-CN"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>超级马力欧 奥德赛 · 电视图标旅行册</title>
<style>
:root{color-scheme:light;--cream:#fff8e9;--paper:#fffdf8;--ink:#132b4b;--muted:#667589;--navy:#082b54;--red:#df2635;--red-soft:#ffe0d9;--line:#eadbc3;--shadow:0 12px 28px #13315218;--focus:#e43b48}*{box-sizing:border-box}html{background:var(--cream)}body{margin:0;min-width:0;background:radial-gradient(circle at 7% 0,#fff 0 9%,transparent 30%),linear-gradient(135deg,#fff7e8,#f4ead6);color:var(--ink);font:15px/1.45 -apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif}.shell{width:min(1360px,calc(100% - 32px));margin:auto}.top{padding:25px 0 14px;display:flex;align-items:flex-start;gap:18px}.stamp{flex:0 0 auto;width:47px;height:47px;border-radius:50%;display:grid;place-items:center;background:var(--red);color:#fff;font-weight:850;font-size:21px;transform:rotate(-8deg);box-shadow:0 5px 0 #a5182b}.heading{min-width:0;flex:1}.heading h1{font-size:clamp(21px,4vw,33px);line-height:1.1;margin:0;letter-spacing:.025em}.heading p{margin:5px 0 0;color:var(--muted);font-size:13px}.controls{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin:0 0 16px;padding:11px 13px;background:#ffffffbd;border:1px solid var(--line);border-radius:16px;box-shadow:0 6px 18px #17365a0a}.button-row,.filters{display:flex;align-items:center;gap:8px;flex-wrap:wrap}button,input{font:inherit;color:inherit;background:var(--paper);border:1px solid var(--line);border-radius:10px;min-height:38px;padding:7px 10px}button{cursor:pointer;font-weight:650}button[aria-pressed="true"],.filter.active{background:var(--navy);border-color:var(--navy);color:#fff}.filter{font-size:12px;min-height:32px}.search{width:min(250px,100%)}button:focus-visible,input:focus-visible,.card:focus-visible,.quick-card:focus-visible{outline:3px solid var(--focus);outline-offset:3px}.quick{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:11px;margin:0 0 20px}.quick-card{position:relative;display:block;min-height:91px;overflow:hidden;border:1px solid var(--line);border-radius:14px;background:linear-gradient(130deg,var(--c1,#dce9fd),var(--c2,#fff));box-shadow:0 6px 15px #17365a12;cursor:pointer;text-align:left;padding:11px;isolation:isolate}.quick-card img{position:absolute;right:3%;bottom:0;width:49%;height:90%;object-fit:contain;object-position:right bottom;z-index:-1}.quick-card strong{display:block;max-width:61%;font-size:13px;line-height:1.2}.quick-card span{display:block;color:#44586d;font-size:10px;margin-top:5px}.section-title{display:flex;align-items:baseline;justify-content:space-between;gap:12px;margin:10px 1px}.section-title h2{font-size:17px;margin:0}.count{color:var(--muted);font-size:12px}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(212px,1fr));gap:18px}.card{border:0;background:transparent;padding:0;text-align:left;min-width:0;cursor:pointer}.art{position:relative;aspect-ratio:16/9;overflow:hidden;border-radius:17px;background:linear-gradient(135deg,var(--c1),var(--c2));box-shadow:var(--shadow);border:1px solid #fff8}.art:after{content:"";position:absolute;inset:8px;border:1px solid #fff8;border-radius:11px;pointer-events:none}.art img{width:90%;height:86%;position:absolute;bottom:7%;left:5%;object-fit:contain;object-position:center center;filter:drop-shadow(0 7px 5px #14263b33)}.missing{display:none;position:absolute;inset:0;place-items:center;padding:12px;text-align:center;color:#5f5a52;background:#fffbf1cc;font-size:12px;z-index:2}.missing.show{display:grid}.meta{padding:8px 3px 0;display:block}.meta strong{display:block;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.character{display:block;margin-top:2px;color:var(--muted);font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.card.square .art{aspect-ratio:1;border-radius:27%}.card.small .art{width:160px;max-width:100%;height:auto;aspect-ratio:16/9;border-radius:10px}.card.small.square .art{aspect-ratio:1}.card.small .meta{width:160px}.card.small .character{display:none}.grid.small-grid{grid-template-columns:repeat(auto-fill,160px);gap:20px}.empty{margin:22px 0;color:var(--muted)}dialog{width:min(520px,calc(100% - 28px));border:0;border-radius:20px;padding:0;color:var(--ink);background:var(--paper);box-shadow:0 24px 80px #06182db0}dialog::backdrop{background:#061a35a8}.modal-art{height:210px;position:relative;overflow:hidden;background:linear-gradient(135deg,var(--c1),var(--c2))}.modal-art img{height:90%;width:100%;object-fit:contain;object-position:center bottom}.modal-body{padding:18px}.modal-body h2{margin:0;font-size:23px}.modal-body p{margin:6px 0;color:var(--muted)}.modal-actions{display:flex;justify-content:flex-end;gap:9px;margin-top:18px}.download{background:var(--red);color:#fff;border-color:var(--red);text-decoration:none;display:inline-grid;place-items:center;padding:7px 11px;border-radius:10px;font-weight:650}.dark{color-scheme:dark;--cream:#071a31;--paper:#0c2848;--ink:#f2f5f4;--muted:#bcc8d4;--navy:#f1c754;--red:#ff5866;--red-soft:#51303c;--line:#2b4968;--shadow:0 12px 28px #0006}.dark body{background:radial-gradient(circle at 7% 0,#164b73 0 9%,transparent 30%),linear-gradient(135deg,#071a31,#0a2543)}.dark .controls{background:#0b2948d9}.dark .quick-card span{color:#d0dae5}.dark .art:after{border-color:#fff4}@media(max-width:650px){.shell{width:min(100% - 22px,1360px)}.top{padding-top:18px}.controls{align-items:stretch}.filters{overflow:auto;flex-wrap:nowrap;padding-bottom:2px}.filter{white-space:nowrap}.search{width:100%}.quick{grid-template-columns:repeat(2,minmax(0,1fr));gap:9px}.quick-card{min-height:82px}.grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:15px 11px}.meta strong{font-size:13px}.character{font-size:10px}.button-row{width:100%;justify-content:space-between}.button-row button{flex:1;padding-inline:5px}.card.small .art{width:160px;max-width:100%}.card.small .meta{width:100%}.grid.small-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:15px 11px}}
.modal-actions{flex-wrap:wrap}.download:focus-visible{outline:3px solid var(--focus);outline-offset:3px}</style></head><body><div class="shell">
<header class="top"><div class="stamp" aria-hidden="true">M</div><div class="heading"><h1>奥德赛电视图标旅行册</h1><p>40 个常用入口，一场角色同行的旅行</p></div></header>
<section class="controls" aria-label="预览控制"><div class="filters" id="filters"></div><input class="search" id="search" type="search" aria-label="搜索应用或角色" placeholder="搜索应用、角色或别名"><div class="button-row"><button id="format" aria-pressed="false">横幅 16:9</button><button id="size" aria-pressed="false">电视小尺寸</button><button id="theme" aria-pressed="false">深海蓝</button></div></section>
<section aria-label="固定栏精选"><div class="quick" id="quick"></div></section>
<div class="section-title"><h2>旅行收藏</h2><span class="count" id="count" aria-live="polite"></span></div><main class="grid" id="grid" aria-label="图标网格"></main>
</div><dialog id="detail"><div class="modal-art" id="modalArt"><img id="modalImage" alt=""></div><div class="modal-body"><h2 id="modalTitle"></h2><p id="modalText"></p><p id="modalPackage"></p><div class="modal-actions"><button id="close">关闭</button><a id="download" class="download" download>下载横幅</a><a id="downloadSquare" class="download" download>下载方形</a></div></div></dialog>
<script>
window.__iconCollection=__ICON_COLLECTION__;
const data=window.__iconCollection, grid=document.querySelector('#grid'), detail=document.querySelector('#detail');
const palette=[['#d8eff6','#fff5cf'],['#fae1bc','#f5b4ae'],['#d6e9c7','#fff2ca'],['#d5dcf5','#f9d0c7'],['#ead8ed','#cce6ed']];
let category='全部', square=false, small=false;
const relPath=id=>'characters/'+encodeURIComponent(id)+'.png';
function searchable(x){return [x.id,x.label,x.category,x.package,x.character,x.character_zh,...(x.aliases||[])].join(' ').toLocaleLowerCase()}
function imageFor(x){const img=document.createElement('img');img.src=relPath(x.id);img.alt=x.label+' · '+x.character_zh;img.loading='lazy';return img}
function setMissing(img, holder){img.addEventListener('error',()=>holder.querySelector('.missing').classList.add('show'),{once:true})}
function cardFor(x, quick=false){const n=data.indexOf(x),p=palette[n%palette.length],card=document.createElement('button');card.type='button';card.className=quick?'quick-card':'card'+(square?' square':'')+(small?' small':'');card.dataset.appId=x.id;card.style.setProperty('--c1',x.background?.[0]||p[0]);card.style.setProperty('--c2',x.background?.[1]||p[1]);card.setAttribute('aria-label','查看 '+x.label+'，角色 '+x.character_zh);if(quick){const rgb=(x.background?.[1]||p[1]).slice(1).match(/../g).map(v=>parseInt(v,16));const dark=rgb[0]*.2126+rgb[1]*.7152+rgb[2]*.0722<145;card.style.color=dark?'#fff':'#132b4b';card.innerHTML='<strong></strong><span></span>';card.querySelector('strong').textContent=x.label;card.querySelector('span').textContent=x.character_zh;card.querySelector('span').style.color=dark?'#e5efff':'#44586d';card.append(imageFor(x));}else{const art=document.createElement('div');art.className='art';const missing=document.createElement('div');missing.className='missing';missing.textContent='角色图片加载失败';const img=imageFor(x);art.append(img,missing);setMissing(img,art);const meta=document.createElement('div');meta.className='meta';const label=document.createElement('strong');label.textContent=x.label;const character=document.createElement('span');character.className='character';character.textContent=x.character_zh+' · '+x.character;meta.append(label,character);card.append(art,meta)}card.addEventListener('click',()=>openDetail(x));return card}
function renderFilters(){const categories=['全部',...new Set(data.map(x=>x.category))];const box=document.querySelector('#filters');box.replaceChildren(...categories.map(c=>{const b=document.createElement('button');b.className='filter'+(c===category?' active':'');b.textContent=c;b.dataset.category=c;b.setAttribute('aria-pressed',String(c===category));b.onclick=()=>{category=c;renderFilters();render()};return b}))}
function render(){const q=document.querySelector('#search').value.trim().toLocaleLowerCase();const items=data.filter(x=>(category==='全部'||x.category===category)&&searchable(x).includes(q));grid.classList.toggle('small-grid',small);grid.replaceChildren(...items.map(x=>cardFor(x)));if(!items.length){const p=document.createElement('p');p.className='empty';p.textContent='没有匹配的旅行收藏。';grid.append(p)}document.querySelector('#count').textContent=items.length+' / '+data.length+' 枚';}
function openDetail(x){const n=data.indexOf(x),p=palette[n%palette.length],a=document.querySelector('#modalArt'),img=document.querySelector('#modalImage');a.style.setProperty('--c1',x.background?.[0]||p[0]);a.style.setProperty('--c2',x.background?.[1]||p[1]);img.src=relPath(x.id);img.alt=x.label+' · '+x.character_zh;document.querySelector('#modalTitle').textContent=x.label;document.querySelector('#modalText').textContent='同行角色：'+x.character_zh+'（'+x.character+'） · '+x.category;document.querySelector('#modalPackage').textContent=x.pairing_note||'';const d=document.querySelector('#download');d.href='banners/'+encodeURIComponent(x.id)+'.png';d.textContent='下载横幅';document.querySelector('#downloadSquare').href='squares/'+encodeURIComponent(x.id)+'.png';detail.showModal()}
document.querySelector('#search').addEventListener('input',render);document.querySelector('#format').onclick=e=>{square=!square;e.currentTarget.textContent=square?'方形 1:1':'横幅 16:9';e.currentTarget.setAttribute('aria-pressed',String(square));render()};document.querySelector('#size').onclick=e=>{small=!small;e.currentTarget.textContent=small?'正常尺寸':'电视小尺寸';e.currentTarget.setAttribute('aria-pressed',String(small));render()};document.querySelector('#theme').onclick=e=>{const dark=document.documentElement.classList.toggle('dark');e.currentTarget.textContent=dark?'奶油浅色':'深海蓝';e.currentTarget.setAttribute('aria-pressed',String(dark))};document.querySelector('#close').onclick=()=>detail.close();grid.addEventListener('keydown',e=>{if(!e.key.startsWith('Arrow'))return;const cards=[...grid.querySelectorAll('.card')],i=cards.indexOf(document.activeElement);if(i<0)return;e.preventDefault();const cols=getComputedStyle(grid).gridTemplateColumns.split(' ').length,step={ArrowRight:1,ArrowLeft:-1,ArrowDown:cols,ArrowUp:-cols}[e.key];cards[Math.max(0,Math.min(cards.length-1,i+step))]?.focus()});const featured=['cast','bili','live','emby'].map(id=>data.find(x=>x.id===id)).filter(Boolean);document.querySelector('#quick').replaceChildren(...featured.map(x=>cardFor(x,true)));renderFilters();render();
</script></body></html>'''


def main() -> None:
    pack_dir = Path(sys.argv[1]).expanduser().resolve() if len(sys.argv) > 1 else DEFAULT_PACK
    if len(sys.argv) > 2:
        raise SystemExit("Usage: python3 make_preview.py [pack_dir]")
    items = load_items(pack_dir)
    output = pack_dir / "preview.html"
    output.write_text(HTML.replace("__ICON_COLLECTION__", script_json(items)), encoding="utf-8")
    print(f"Built {output} for {len(items)} icons.")


if __name__ == "__main__":
    main()
