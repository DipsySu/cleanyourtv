from pathlib import Path
import json,re,sys
p=Path(sys.argv[1]);spec=json.loads(p.read_text())
ids=set()
for icon in spec['icons']:
    name=icon['id'];assert re.fullmatch(r'[a-z0-9_]+',name) and name not in ids;ids.add(name)
    assert isinstance(icon['label'],str) and 0<len(icon['label'])<50
    for key in ['top_color','bottom_color','foreground']:assert re.fullmatch(r'#[0-9A-Fa-f]{6}',icon[key]),(name,key)
    assert 40<=icon['width']<=252 and 24<=icon['height']<=110,(name,'dimensions')
    slug=icon['brand_slug'];assert not slug or re.fullmatch(r'[a-z0-9]+',slug)
    assert bool(slug)!=bool(icon['shapes']),(name,'geometry source')
    if slug:assert (p.parent/'reference/brand'/f'{slug}.svg').exists(),slug
    for shape in icon['shapes']:
        assert re.fullmatch(r'[MmZzLlHhVvCcSsQqTtAa0-9eE,.\s+\-]+',shape['d']),(name,'path')
        for paint in ['fill','stroke']:assert shape[paint] in ['none','pearl'] or re.fullmatch(r'#[0-9A-Fa-f]{6}',shape[paint]),(name,paint)
        assert 0<=shape['stroke_width']<=5
        assert shape.get('fill_rule','nonzero') in ['nonzero','evenodd']
assert len(ids)==40, 'Expected the complete 40-icon collection'
print('Validated',len(ids),'design records.')
