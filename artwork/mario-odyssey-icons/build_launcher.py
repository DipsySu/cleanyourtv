#!/usr/bin/env python3
"""Encode the launcher masters as the APK's opaque 384x216 lossy WebP banners.

Usage: python3 build_launcher.py [out_dir]

Reads launcher/<id>.png (768x432, from render_launcher.mjs) and writes banner_odyssey_<id>.webp to out_dir,
by default the launcher's res/drawable-nodpi. All 40 are encoded and checked in a temporary directory first,
then each is staged as a dot file (which the resource merger ignores) and renamed over its predecessor; a
same-named PNG goes first so aapt2 never sees both.
Writes validation/launcher-report.json. Needs cwebp and dwebp (libwebp) on PATH.
"""
from pathlib import Path
import hashlib,json,os,shutil,struct,subprocess,sys,tempfile
ROOT=Path(__file__).resolve().parent
RES=ROOT.parents[1]/'launcher/app/src/main/res/drawable-nodpi'
MASTER,SIZE=(768,432),(384,216)
FLAGS=['-resize',*map(str,SIZE),'-q','88','-sharp_yuv','-m','6','-noalpha']

def webp_size(data):
 """Width and height of a simple lossy WebP; anything else (VP8X, alpha, lossless) is rejected."""
 assert data[:4]==b'RIFF' and data[8:12]==b'WEBP' and struct.unpack('<I',data[4:8])[0]+8==len(data),'not a WebP'
 assert data[12:16]==b'VP8 ','expected a plain lossy VP8 stream without alpha'
 assert data[23:26]==b'\x9d\x01\x2a','bad VP8 key frame'
 w,h=struct.unpack('<HH',data[26:30]);return w&0x3fff,h&0x3fff

def opaque(path,tmp):
 """Decode with dwebp and check every pixel's alpha, independently of the container check."""
 pam=tmp/(path.stem+'.pam');subprocess.run(['dwebp','-quiet',str(path),'-pam','-o',str(pam)],check=True)
 data=pam.read_bytes();head,pixels=data.split(b'ENDHDR\n',1);fields=dict(l.split(b' ',1) for l in head.splitlines()[1:])
 w,h=int(fields[b'WIDTH']),int(fields[b'HEIGHT']);assert int(fields[b'DEPTH'])==4 and len(pixels)==w*h*4
 pam.unlink();return (w,h),pixels[3::4].count(255)==w*h

def main():
 out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else RES
 if len(sys.argv)>2:raise SystemExit('Usage: python3 build_launcher.py [out_dir]')
 ids=[x['id'] for x in json.loads((ROOT/'manifest.json').read_text())];assert len(ids)==len(set(ids))==40
 version=subprocess.run(['cwebp','-version'],check=True,capture_output=True,text=True).stdout.split()[0]
 out.mkdir(parents=True,exist_ok=True);rows=[]
 with tempfile.TemporaryDirectory() as t:
  tmp=Path(t)
  for id in ids:
   source=ROOT/'launcher'/f'{id}.png';data=source.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',id
   assert struct.unpack('>II',data[16:24])==MASTER,(id,'run render_launcher.mjs first')
   target=tmp/f'banner_odyssey_{id}.webp';subprocess.run(['cwebp','-quiet',*FLAGS,str(source),'-o',str(target)],check=True)
   webp=target.read_bytes();size,solid=opaque(target,tmp);assert webp_size(webp)==size==SIZE and solid,id
   rows.append({'id':id,'resource':target.name,'size':list(size),'bytes':len(webp),'sha256':hashlib.sha256(webp).hexdigest(),'opaque':solid})
  for row in rows:
   part=out/f".{row['resource']}.part";shutil.copyfile(tmp/row['resource'],part)
   (out/row['resource']).with_suffix('.png').unlink(missing_ok=True);os.replace(part,out/row['resource'])
 report={'icons':len(rows),'source':'render_launcher.mjs','masterSize':list(MASTER),
  'encoder':f'cwebp {version}','flags':FLAGS,'totalBytes':sum(r['bytes'] for r in rows),'banners':rows}
 (ROOT/'validation/launcher-report.json').write_text(json.dumps(report,indent=2)+'\n')
 print(f"Encoded {len(rows)} opaque {SIZE[0]}x{SIZE[1]} WebP banners, {report['totalBytes']:,} bytes, into {out}.")

if __name__=='__main__':main()
