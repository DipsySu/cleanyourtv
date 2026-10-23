#!/usr/bin/env python3
"""Rasterise the soft vector banners into the soft-glow theme pack's opaque 384x216 lossy WebP banners.

Usage: python3 -I export_pack.py [out_dir]

Reads soft-glow/banner_soft_<id>.xml: the 16 Android VectorDrawables the launcher bundled until the soft look moved
into a theme pack. They are an earlier, hand-tuned set (a pastel field, a soft top-left glow and a glyph raised on
three offset shadow layers), not the 40-icon studio set in svg/ and android/, so they are the source that matches what
the TV showed. Each vector is turned into the equivalent SVG (paths; groups that translate and scale; linear and
radial gradients in viewport units; #AARRGGBB colours as opacity), drawn by headless Chrome at 768x432, twice the
banner, so edges are supersampled, and encoded with cwebp to banners/<id>.webp, by default in theme-packs/soft-glow.
The banners must stay opaque: the launcher draws an opaque banner full-bleed, while one with alpha is shrunk onto a
framed card. So the WebP is checked to be the simple lossy "VP8 " format and decoded again with dwebp to check every
pixel's alpha. All 16 are rendered and checked in a temporary directory first; only then is each staged as a dot
file and renamed into place. Writes validation/pack-report.json.

Anything in a vector this converter does not know (another element, attribute or gradient form) stops the export,
so the raster can never silently differ from the vector. Needs Google Chrome (CHROME_PATH overrides the macOS
default) and cwebp/dwebp (libwebp); /opt/homebrew/bin is preferred for those, then PATH. Nothing is downloaded and
the pages Chrome draws are local files with no external references.
"""
from pathlib import Path
from xml.sax.saxutils import quoteattr
import hashlib,json,os,shutil,struct,subprocess,sys,tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
SOURCE=ROOT/'soft-glow'
PACK=ROOT.parents[1]/'theme-packs/soft-glow'
A='{http://schemas.android.com/apk/res/android}'
AA='{http://schemas.android.com/aapt}'
VIEWPORT,MASTER,SIZE=(320,180),(768,432),(384,216)
FLAGS=['-resize',*map(str,SIZE),'-q','90','-sharp_yuv','-m','6','-noalpha']
CHROME_FLAGS=['--headless','--disable-gpu','--hide-scrollbars','--force-color-profile=srgb','--force-device-scale-factor=1',
    '--default-background-color=000000ff','--no-first-run','--no-default-browser-check','--disable-extensions',
    '--disable-background-networking','--disable-component-update','--disable-sync','--metrics-recording-only']

def tool(name):
    """Homebrew's build first, then whatever PATH has."""
    for path in (f'/opt/homebrew/bin/{name}',shutil.which(name)):
        if path and os.access(path,os.X_OK):return path
    raise SystemExit(f'{name} not found (brew install webp)')

def chrome():
    path=os.environ.get('CHROME_PATH') or '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
    if os.access(path,os.X_OK):return path
    for name in ('google-chrome','chromium','chromium-browser'):
        if shutil.which(name):return shutil.which(name)
    raise SystemExit('Google Chrome not found; set CHROME_PATH')

CWEBP,DWEBP,CHROME=tool('cwebp'),tool('dwebp'),chrome()

def color(value):
    """An Android colour (#RGB, #ARGB, #RRGGBB or #AARRGGBB) as an SVG colour and opacity."""
    assert value.startswith('#') and len(value) in (4,5,7,9),value
    digits=value[1:]
    if len(digits)<=4:digits=''.join(c*2 for c in digits)
    if len(digits)==6:digits='FF'+digits
    int(digits,16)
    return '#'+digits[2:].upper(),round(int(digits[:2],16)/255,5)

def attrs(node,allowed):
    """The node's android: attributes by local name; any other attribute stops the export."""
    out={}
    for key,value in node.attrib.items():
        assert key.startswith(A) and key[len(A):] in allowed,(node.tag,key)
        out[key[len(A):]]=value
    return out

def gradient(node,ident):
    """An aapt:attr <gradient> as an SVG gradient in the user space of the path that uses it, as Android draws it."""
    g=attrs(node,{'type','startX','startY','endX','endY','centerX','centerY','gradientRadius','startColor','endColor'})
    assert not list(node),'gradient <item> stops are not supported'
    stops=''.join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a}"/>'
        for o,(c,a) in ((0,color(g['startColor'])),(1,color(g['endColor']))))
    if g.get('type','linear')=='linear':
        assert set(g)<={'type','startX','startY','endX','endY','startColor','endColor'},g
        coords=' '.join(f'{k}="{float(g.get(v,0))}"' for k,v in (('x1','startX'),('y1','startY'),('x2','endX'),('y2','endY')))
        return f'<linearGradient id="{ident}" gradientUnits="userSpaceOnUse" {coords}>{stops}</linearGradient>'
    assert g['type']=='radial' and set(g)<={'type','centerX','centerY','gradientRadius','startColor','endColor'},g
    coords=f'cx="{float(g.get("centerX",0))}" cy="{float(g.get("centerY",0))}" r="{float(g["gradientRadius"])}"'
    return f'<radialGradient id="{ident}" gradientUnits="userSpaceOnUse" {coords}>{stops}</radialGradient>'

def svg(path):
    """The VectorDrawable at path as an SVG document drawn at MASTER size."""
    root=ET.parse(path).getroot();assert root.tag=='vector',root.tag
    v=attrs(root,{'width','height','viewportWidth','viewportHeight'})
    assert (float(v['viewportWidth']),float(v['viewportHeight']))==VIEWPORT,(path.name,v)
    defs,body=[],[]
    def paint(node,name,default):
        """fill or stroke paint: an aapt:attr gradient child wins over the attribute, as in Android."""
        for child in node:
            assert child.tag==AA+'attr' and child.get('name') in ('android:fillColor','android:strokeColor'),(path.name,child.tag)
            if child.get('name')=='android:'+name:
                kids=list(child);assert len(kids)==1 and kids[0].tag=='gradient',path.name
                ident=f'g{len(defs)}';defs.append(gradient(kids[0],ident));return f'url(#{ident})',1
        value=node.get(A+name)
        if value is None:return default
        c,a=color(value);return ('none',0) if a==0 else (c,a)
    def walk(node):
        for child in node:
            if child.tag=='group':
                g=attrs(child,{'translateX','translateY','scaleX','scaleY'})
                t=f'translate({float(g.get("translateX",0))} {float(g.get("translateY",0))}) scale({float(g.get("scaleX",1))} {float(g.get("scaleY",1))})'
                body.append(f'<g transform="{t}">');walk(child);body.append('</g>')
            elif child.tag=='path':
                p=attrs(child,{'pathData','fillColor','strokeColor','strokeWidth','strokeLineCap','strokeLineJoin'})
                fill,fill_a=paint(child,'fillColor',('none',0));stroke,stroke_a=paint(child,'strokeColor',('none',0))
                out=f'<path d={quoteattr(p["pathData"])} fill="{fill}"'+(f' fill-opacity="{fill_a}"' if fill!='none' else '')
                if stroke!='none':
                    out+=(f' stroke="{stroke}" stroke-opacity="{stroke_a}" stroke-width="{float(p.get("strokeWidth",0))}"'
                        f' stroke-linecap="{p.get("strokeLineCap","butt")}" stroke-linejoin="{p.get("strokeLineJoin","miter")}"')
                body.append(out+'/>')
            else:raise AssertionError((path.name,child.tag))
    walk(root)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{MASTER[0]}" height="{MASTER[1]}" viewBox="0 0 {VIEWPORT[0]} {VIEWPORT[1]}">'
        f'<defs>{"".join(defs)}</defs>{"".join(body)}</svg>')

def render(markup,png,profile):
    """One headless Chrome screenshot of the SVG on a black page, exactly MASTER pixels."""
    page=png.with_suffix('.html')
    page.write_text('<!doctype html><meta charset="utf-8"><style>html,body{margin:0;background:#000;overflow:hidden}'
        'svg{display:block}</style>'+markup,encoding='utf-8')
    subprocess.run([CHROME,*CHROME_FLAGS,f'--user-data-dir={profile}',f'--window-size={MASTER[0]},{MASTER[1]}',
        f'--screenshot={png}',page.as_uri()],check=True,capture_output=True,timeout=120)
    data=png.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',png.name
    assert struct.unpack('>II',data[16:24])==MASTER,(png.name,struct.unpack('>II',data[16:24]))

def webp_size(data):
    """Width and height of a simple lossy WebP; anything else (VP8X, alpha, lossless) is rejected."""
    assert data[:4]==b'RIFF' and data[8:12]==b'WEBP' and struct.unpack('<I',data[4:8])[0]+8==len(data),'not a WebP'
    assert data[12:16]==b'VP8 ','expected a plain lossy VP8 stream without alpha'
    assert data[23:26]==b'\x9d\x01\x2a','bad VP8 key frame'
    w,h=struct.unpack('<HH',data[26:30]);return w&0x3fff,h&0x3fff

def opaque(path,tmp):
    """Decode with dwebp and check every pixel's alpha, independently of the container check."""
    pam=tmp/(path.stem+'.pam');subprocess.run([DWEBP,'-quiet',str(path),'-pam','-o',str(pam)],check=True)
    head,pixels=pam.read_bytes().split(b'ENDHDR\n',1);fields=dict(l.split(b' ',1) for l in head.splitlines()[1:])
    w,h=int(fields[b'WIDTH']),int(fields[b'HEIGHT']);assert int(fields[b'DEPTH'])==4 and len(pixels)==w*h*4
    pam.unlink();return (w,h),pixels[3::4].count(255)==w*h

def main():
    if len(sys.argv)>2:raise SystemExit('Usage: python3 -I export_pack.py [out_dir]')
    out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else PACK/'banners'
    sources=sorted(SOURCE.glob('banner_soft_*.xml'));ids=[p.stem.removeprefix('banner_soft_') for p in sources]
    assert len(ids)==16,ids
    if out==PACK/'banners':
        # Into the pack itself: its config.json must name exactly these banners, or the pack would ship dead files
        # or point at missing ones.
        named=set(json.loads((PACK/'config.json').read_text(encoding='utf-8'))['app_banners'].values())
        assert named=={f'banners/{id}.webp' for id in ids},sorted(named^{f'banners/{id}.webp' for id in ids})
    versions={'chrome':subprocess.run([CHROME,'--version'],check=True,capture_output=True,text=True).stdout.strip(),
        'cwebp':subprocess.run([CWEBP,'-version'],check=True,capture_output=True,text=True).stdout.split()[0]}
    out.mkdir(parents=True,exist_ok=True);rows=[]
    with tempfile.TemporaryDirectory() as t:
        tmp=Path(t)
        for source,id in zip(sources,ids):
            markup=svg(source);png=tmp/f'{id}.png';render(markup,png,tmp/'chrome-profile')
            target=tmp/f'{id}.webp';subprocess.run([CWEBP,'-quiet',*FLAGS,str(png),'-o',str(target)],check=True)
            webp=target.read_bytes();size,solid=opaque(target,tmp);assert webp_size(webp)==size==SIZE and solid,id
            rows.append({'id':id,'source':f'soft-glow/{source.name}','svgSha256':hashlib.sha256(markup.encode()).hexdigest(),
                'file':target.name,'size':list(size),'chunk':'VP8 ','bytes':len(webp),'sha256':hashlib.sha256(webp).hexdigest(),'opaque':solid})
        for row in rows:
            part=out/f".{row['file']}.part";shutil.copyfile(tmp/row['file'],part);os.replace(part,out/row['file'])
    report={'banners':len(rows),'source':'soft-glow/*.xml (Android VectorDrawable, viewport 320x180)','masterSize':list(MASTER),
        'renderer':versions['chrome'],'encoder':f"cwebp {versions['cwebp']}",'flags':FLAGS,
        'totalBytes':sum(r['bytes'] for r in rows),'items':rows}
    (ROOT/'validation').mkdir(exist_ok=True)
    (ROOT/'validation/pack-report.json').write_text(json.dumps(report,indent=2)+'\n')
    print(f"Encoded {len(rows)} opaque {SIZE[0]}x{SIZE[1]} WebP banners, {report['totalBytes']:,} bytes, into {out}.")

if __name__=='__main__':main()
