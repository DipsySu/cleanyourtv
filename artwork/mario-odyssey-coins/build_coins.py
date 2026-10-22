#!/usr/bin/env python3
"""Fit the Super Mario Odyssey regional coin models into the launcher's 128x128 alpha WebP coins.

Usage: python3 -I build_coins.py [out_dir]

Reads originals/<id>.png, checked against sources.json (PNG signature, size and SHA-256), trims fully transparent
margins, scales the coin with premultiplied alpha so the grey under the transparent background cannot bleed into
its edge, and centres it in a transparent 128x128 square with a 2 px margin (aspect kept). Fully transparent pixels
then take the colour of the nearest visible ones: lossy WebP keeps colour at half resolution, so without that the
edge of the coin would be averaged towards black. Writes coin_odyssey_<id>.webp to out_dir, by default the
launcher's res/drawable-nodpi. All 8 are encoded and decoded again with dwebp in a temporary directory first (size,
lossless alpha, centring), then each is staged as a dot file (which the resource merger ignores) and renamed into
place. Writes validation/coins-report.json.
Needs ffmpeg/ffprobe and cwebp/dwebp (libwebp); /opt/homebrew/bin is preferred, then PATH.
"""
from pathlib import Path
import hashlib,json,os,shutil,struct,subprocess,sys,tempfile,zlib
ROOT=Path(__file__).resolve().parent
RES=ROOT.parents[1]/'launcher/app/src/main/res/drawable-nodpi'
IDS=['cap','metro','sand','seaside','wooded','lost','luncheon','moon']
SIZE,MARGIN=128,2
SCALE='lanczos+accurate_rnd+full_chroma_int'
FLAGS=['-q','90','-alpha_q','100','-alpha_filter','best','-m','6','-sharp_yuv','-exact']

def tool(name):
 """Homebrew's build first, as the README documents, then whatever PATH has."""
 for path in (f'/opt/homebrew/bin/{name}',shutil.which(name)):
  if path and os.access(path,os.X_OK):return path
 raise SystemExit(f'{name} not found (brew install ffmpeg webp)')

FFMPEG,FFPROBE,CWEBP,DWEBP=map(tool,['ffmpeg','ffprobe','cwebp','dwebp'])

def run(*args):return subprocess.run(args,check=True,capture_output=True).stdout

def probe(path):
 w,h=run(FFPROBE,'-v','error','-select_streams','v:0','-show_entries','stream=width,height','-of','csv=p=0',str(path)).decode().split(',')
 return int(w),int(h)

def rgba(path,vf=None):
 """First frame as straight 8-bit RGBA, through ffmpeg (which also honours a PNG tRNS colour key)."""
 return run(FFMPEG,'-v','error','-i',str(path),*(['-vf',vf] if vf else []),'-frames:v','1','-f','rawvideo','-pix_fmt','rgba','-')

def bbox(alpha,w,h):
 """Smallest box (x0, y0, x1, y1; x1 and y1 exclusive) around every pixel with alpha above 0, or None."""
 rows=[y for y in range(h) if any(alpha[y*w:(y+1)*w])]
 cols=[x for x in range(w) if any(alpha[x::w])]
 return (cols[0],rows[0],cols[-1]+1,rows[-1]+1) if rows else None

def fit(w,h):
 """The coin's size inside the square: the long side fills SIZE-2*MARGIN."""
 box=SIZE-2*MARGIN;s=min(box/w,box/h)
 return max(1,round(w*s)),max(1,round(h*s))

def bleed(px):
 """Copy colour into fully transparent pixels, one ring at a time, from the average of their already coloured
 4-neighbours. Alpha is untouched, so nothing becomes visible; it only keeps the encoder's chroma at the edge."""
 n=SIZE;known=[px[i*4+3]>0 for i in range(n*n)];todo=[i for i in range(n*n) if not known[i]]
 while todo:
  fill={}
  for i in todo:
   y,x=divmod(i,n)
   near=[j for j,ok in ((i-n,y>0),(i+n,y<n-1),(i-1,x>0),(i+1,x<n-1)) if ok and known[j]]
   if near:fill[i]=bytes(sum(px[j*4+c] for j in near)//len(near) for c in range(3))
  if not fill:break
  for i,rgb in fill.items():px[i*4:i*4+3]=rgb;known[i]=True
  todo=[i for i in todo if i not in fill]
 return px

def write_png(path,px,w,h):
 """Minimal 8-bit RGBA PNG (filter 0 on every row) for cwebp to read."""
 chunk=lambda tag,data:struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data))
 raw=b''.join(b'\0'+bytes(px[y*w*4:(y+1)*w*4]) for y in range(h))
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))

def webp_size(data):
 """Canvas size of an extended (VP8X) lossy WebP with an ALPH chunk; anything else is rejected."""
 assert data[:4]==b'RIFF' and data[8:12]==b'WEBP' and struct.unpack('<I',data[4:8])[0]+8==len(data),'not a WebP'
 assert data[12:16]==b'VP8X' and data[20]&0x10,'expected a VP8X header with the alpha flag'
 tags,i=[],12
 while i<len(data):n=struct.unpack('<I',data[i+4:i+8])[0];tags.append(data[i:i+4]);i+=8+n+(n&1)
 assert i==len(data) and tags==[b'VP8X',b'ALPH',b'VP8 '],tags
 return int.from_bytes(data[24:27],'little')+1,int.from_bytes(data[27:30],'little')+1

def decode(path,tmp):
 """Decode with dwebp, independently of the container check, to straight RGBA."""
 pam=tmp/(path.stem+'.pam');subprocess.run([DWEBP,'-quiet',str(path),'-pam','-o',str(pam)],check=True)
 head,pixels=pam.read_bytes().split(b'ENDHDR\n',1);fields=dict(l.split(b' ',1) for l in head.splitlines()[1:])
 w,h=int(fields[b'WIDTH']),int(fields[b'HEIGHT']);assert int(fields[b'DEPTH'])==4 and len(pixels)==w*h*4
 pam.unlink();return (w,h),pixels

def original(src):
 """Check one originals/ file against its sources.json row and return its straight RGBA pixels."""
 path=ROOT/src['file'];data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',src['id']
 size=(src['width'],src['height'])
 assert hashlib.sha256(data).hexdigest()==src['sha256'] and len(data)==src['bytes'],(src['id'],'original changed')
 assert struct.unpack('>II',data[16:24])==size==probe(path),src['id']
 pixels=rgba(path);assert len(pixels)==size[0]*size[1]*4,src['id']
 return path,size,pixels

def build(src,tmp):
 path,(w,h),pixels=original(src);id=src['id']
 box=bbox(pixels[3::4],w,h);assert box,(id,'fully transparent')
 x0,y0,x1,y1=box;cw,ch=x1-x0,y1-y0;tw,th=fit(cw,ch)
 vf=f'crop={cw}:{ch}:{x0}:{y0},format=rgba,premultiply=inplace=1,scale={tw}:{th}:flags={SCALE},unpremultiply=inplace=1'
 coin=rgba(path,vf);assert len(coin)==tw*th*4,id
 px=bytearray(SIZE*SIZE*4);ox,oy=(SIZE-tw)//2,(SIZE-th)//2
 for y in range(th):i=((oy+y)*SIZE+ox)*4;px[i:i+tw*4]=coin[y*tw*4:(y+1)*tw*4]
 master=tmp/f'{id}.png';write_png(master,bleed(px),SIZE,SIZE)
 target=tmp/f'coin_odyssey_{id}.webp';subprocess.run([CWEBP,'-quiet',*FLAGS,str(master),'-o',str(target)],check=True)
 webp=target.read_bytes();size,out=decode(target,tmp);assert webp_size(webp)==size==(SIZE,SIZE),id
 assert out[3::4]==bytes(px[3::4]),(id,'alpha must survive lossless')
 cx0,cy0,cx1,cy1=content=bbox(out[3::4],SIZE,SIZE)
 assert ox<=cx0 and oy<=cy0 and cx1<=ox+tw and cy1<=oy+th,(id,'coin outside its box')
 assert abs(cx0-(SIZE-cx1))<=2 and abs(cy0-(SIZE-cy1))<=2,(id,'not centred',content)
 assert max(cx1-cx0,cy1-cy0)>=SIZE-2*MARGIN-2,(id,'coin too small',content)
 seen=[i for i in range(SIZE*SIZE) if px[i*4+3]>=128]
 error=sum(abs(out[i*4+c]-px[i*4+c]) for i in seen for c in range(3))/(3*len(seen))
 return {'id':id,'kingdom':src['kingdom'],'wallpaper':src['wallpaper'],'resource':target.name,'size':list(size),
  'bytes':len(webp),'sha256':hashlib.sha256(webp).hexdigest(),'source':src['file'],'sourceSize':[w,h],'trim':list(box),
  'coinSize':[tw,th],'content':list(content),'rgbMeanError':round(error,2)}

def main():
 out=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else RES
 if len(sys.argv)>2:raise SystemExit('Usage: python3 -I build_coins.py [out_dir]')
 sources=json.loads((ROOT/'sources.json').read_text());assert [s['id'] for s in sources]==IDS,'sources.json order'
 version=run(CWEBP,'-version').decode().split()[0]
 out.mkdir(parents=True,exist_ok=True);rows=[]
 with tempfile.TemporaryDirectory() as t:
  tmp=Path(t)
  for src in sources:rows.append(build(src,tmp))
  for row in rows:
   part=out/f".{row['resource']}.part";shutil.copyfile(tmp/row['resource'],part)
   (out/row['resource']).with_suffix('.png').unlink(missing_ok=True);os.replace(part,out/row['resource'])
 report={'coins':len(rows),'canvas':[SIZE,SIZE],'margin':MARGIN,'scaler':f'ffmpeg premultiplied {SCALE}',
  'encoder':f'cwebp {version}','flags':FLAGS,'totalBytes':sum(r['bytes'] for r in rows),'items':rows}
 (ROOT/'validation').mkdir(exist_ok=True)
 (ROOT/'validation/coins-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
 print(f"Encoded {len(rows)} {SIZE}x{SIZE} alpha WebP coins, {report['totalBytes']:,} bytes, into {out}.")

if __name__=='__main__':main()
