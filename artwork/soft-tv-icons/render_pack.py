"""Render reviewed icon design data to SVG and Android vectors (standard library only)."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parent
A='{http://schemas.android.com/apk/res/android}'
AA='{http://schemas.android.com/aapt}'
S='http://www.w3.org/2000/svg'
ET.register_namespace('android', A[1:-1])
ET.register_namespace('aapt', AA[1:-1])
ET.register_namespace('',S)
def number(x): return f'{float(x):.5f}'.rstrip('0').rstrip('.') or '0'
def rgb(hexcolor): return [int(hexcolor[i:i+2],16) for i in (1,3,5)]
def darken(value,amount): return '#'+''.join(f'{round(c*amount):02X}' for c in rgb(value))
def argb(color,opacity=1): return '#'+f'{round(opacity*255):02X}'+color[1:]
def canonical_shapes(icon):
    if icon.get('brand_slug'):
        doc=ET.parse(ROOT/'reference/brand'/f'{icon["brand_slug"]}.svg').getroot()
        return [dict(d=p.get('d'),fill='pearl',stroke='none',stroke_width=0,fill_rule=p.get('fill-rule','nonzero')) for p in doc.iter() if p.tag.split('}')[-1]=='path']
    return icon['shapes']
def svg_tag(parent,name,attr): return ET.SubElement(parent,'{'+S+'}'+name,attr)
def bg_path(w,h,r):
    if not r:return f'M0,0H{w}V{h}H0Z'
    return f'M{r},0H{w-r}Q{w},0 {w},{r}V{h-r}Q{w},{h} {w-r},{h}H{r}Q0,{h} 0,{h-r}V{r}Q0,0 {r},0Z'
def svg_gradient(defs,ident,kind,coords,colors):
    attrs={'id':ident,'gradientUnits':'userSpaceOnUse',**coords}
    grad=svg_tag(defs,kind+'Gradient',attrs)
    for offset,col,opacity in colors:
        svg_tag(grad,'stop',{'offset':str(offset),'stop-color':col,'stop-opacity':number(round(opacity*255)/255)})
    return 'url(#'+ident+')'
def native_gradient(path,which,kind,coords,colors):
    node=ET.SubElement(path,AA+'attr',{'name':'android:'+which+'Color'})
    node=ET.SubElement(node,'gradient',{A+'type':kind,**{A+k:v for k,v in coords.items()}})
    for offset,col,opacity in colors: ET.SubElement(node,'item',{A+'offset':str(offset),A+'color':argb(col,opacity)})
def render(icon,shapes,bbox,square=False,native=False):
    w,h=(256,256) if square else (320,180)
    bx,by,bw,bh=bbox['x'],bbox['y'],bbox['width'],bbox['height']
    tw,th=(min(196,icon['width']*1.65),152) if square else (icon['width'],icon['height'])
    scale=min(tw/bw,th/bh)
    x,y=w/2-(bx+bw/2)*scale,h/2-(by+bh/2)*scale
    ident=icon['id']+('_square' if square else '_banner')
    fg=icon.get('foreground','#F8FBFF')
    tail=darken(fg,.92 if sum(rgb(fg))>400 else .55)
    bg=bg_path(w,h,58 if square else 0)
    bg_coords=dict(x1=number(w*.125),y1='0',x2=number(w*.9125),y2=str(h))
    glow_coords=dict(cx=number(w*.225),cy=number(-h*.089),r=number(max(w,h)*.775))
    fg_coords=dict(x1=number(bx),y1=number(by),x2=number(bx+bw),y2=number(by+bh))
    bg_colors=[(0,icon['top_color'],1),(1,icon['bottom_color'],1)]
    glow_colors=[(0,'#FFFFFF',.2),(1,'#FFFFFF',0)]
    fg_colors=[(0,fg,1),(1,tail,1)]
    def native_coords(kind,coords):
        mapping={'x1':'startX','y1':'startY','x2':'endX','y2':'endY','cx':'centerX','cy':'centerY','r':'gradientRadius'}
        return {mapping[k]:v for k,v in coords.items()}
    if native:
        root=ET.Element('vector',{A+'width':'180dp',A+'height':'101.25dp',A+'viewportWidth':'320',A+'viewportHeight':'180'})
        for kind,coords,colors in [('linear',bg_coords,bg_colors),('radial',glow_coords,glow_colors)]:
            node=ET.SubElement(root,'path',{A+'pathData':bg})
            native_gradient(node,'fill',kind,native_coords(kind,coords),colors)
    else:
        root=ET.Element('{'+S+'}svg',{'width':str(w),'height':str(h),'viewBox':f'0 0 {w} {h}'})
        defs=svg_tag(root,'defs',{})
        for suffix,kind,coords,colors in [('background','linear',bg_coords,bg_colors),('glow','radial',glow_coords,glow_colors),('foreground','linear',fg_coords,fg_colors)]:
            fill=svg_gradient(defs,ident+'_'+suffix,kind,coords,colors)
            if suffix!='foreground':svg_tag(root,'path',{'d':bg,'fill':fill})
    for dx,dy,alpha,shadow in [(th*.036,th*.056,.14,True),(th*.016,th*.026,.24,True),(0,0,1,False)]:
        if native:group=ET.SubElement(root,'group',{A+'translateX':number(x+dx),A+'translateY':number(y+dy),A+'scaleX':number(scale),A+'scaleY':number(scale)})
        else:group=svg_tag(root,'g',{'transform':f'translate({number(x+dx)} {number(y+dy)}) scale({number(scale)})'})
        for shape in shapes:
            if native: node=ET.SubElement(group,'path',{A+'pathData':shape['d']})
            else: node=svg_tag(group,'path',{'d':shape['d']})
            if shape.get('fill_rule')=='evenodd':node.set(A+'fillType' if native else 'fill-rule','evenOdd' if native else 'evenodd')
            for paint in ['fill','stroke']:
                value=shape.get(paint,'none')
                if value in ('none',None,''):
                    node.set(A+paint+'Color' if native else paint,'#00000000' if native else 'none')
                    continue
                if shadow:
                    if native:node.set(A+paint+'Color',argb('#102432',alpha))
                    else:
                        node.set(paint,'#102432');node.set(paint+'-opacity',number(round(alpha*255)/255))
                elif value=='pearl':
                    if native:native_gradient(node,paint,'linear',native_coords('linear',fg_coords),fg_colors)
                    else:node.set(paint,'url(#'+ident+'_foreground)')
                else:node.set(A+paint+'Color' if native else paint,value)
            if shape.get('stroke','none')!='none':
                if native:
                    for k,v in [('strokeWidth',number(shape.get('stroke_width',1.6))),('strokeLineCap','round'),('strokeLineJoin','round')]:node.set(A+k,v)
                else:
                    for k,v in [('stroke-width',number(shape.get('stroke_width',1.6))),('stroke-linecap','round'),('stroke-linejoin','round')]:node.set(k,v)
    ET.indent(root,space='  ')
    return '<?xml version="1.0" encoding="UTF-8"?>\n'+ET.tostring(root,encoding='unicode')+'\n'

def main():
    spec=json.loads((ROOT/'design-data.json').read_text())
    bounds=json.loads((ROOT/'geometry-bounds.json').read_text())
    mappings={item.split('=')[1].removeprefix('banner_soft_'):item.split('=')[0] for item in json.loads((ROOT/'reference/current-mappings.json').read_text())}
    for folder in ['svg','square','android']:(ROOT/folder).mkdir(exist_ok=True)
    manifest=[]
    for icon in spec['icons']:
        name=icon['id'];shapes=canonical_shapes(icon);bbox=bounds[name]
        for folder,square,native,filename in [('svg',False,False,name+'.svg'),('square',True,False,name+'.svg'),('android',False,True,'banner_studio_'+name+'.xml')]:
            output=render(icon,shapes,bbox,square,native);ET.fromstring(output);(ROOT/folder/filename).write_text(output)
        aliases={'bili':['B站','Bilibili','哔哩哔哩'],'tencent':['云视听极光','Tencent Video'],'iqiyi':['爱奇艺','奇异果','iQIYI'],'youku':['优酷','酷喵','Youku'],'115':['115网盘'],'netease':['网易云','NetEase Cloud Music'],'qqmusic':['QQ Music'],'aliyundrive':['阿里云盘','Alipan'],'baidunetdisk':['Baidu Netdisk'],'quark':['Quark']}
        manifest.append({**{k:icon[k] for k in ['id','label','category','top_color','bottom_color','foreground']},'aliases':aliases.get(name,[]),'brand_reference':('reference/brand/'+icon['brand_slug']+'.svg') if icon.get('brand_slug') else '', 'stylized':not bool(icon.get('brand_slug')),'package':mappings.get(name,''),'svg':'svg/'+name+'.svg','square_svg':'square/'+name+'.svg','android':'android/banner_studio_'+name+'.xml'})
    (ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    print(f'Rendered {len(manifest)} icons in three vector formats.')
if __name__=='__main__':main()
