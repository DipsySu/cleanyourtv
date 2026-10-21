#!/usr/bin/env python3
"""Package bounded-size PNG exports as Android drawable-nodpi resources."""
from pathlib import Path
import json,shutil,struct
ROOT=Path(__file__).resolve().parent
items=json.loads((ROOT/'manifest.json').read_text())
assert len(items)==len({x['id'] for x in items})==40
res=ROOT/'android/res';(res/'drawable-nodpi').mkdir(parents=True,exist_ok=True)
for item in items:
 id=item['id']
 for kind,folder,expected in [('banner','banners',(640,360)),('icon','squares',(512,512))]:
  source=ROOT/folder/f'{id}.png';data=source.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n'
  assert struct.unpack('>II',data[16:24])==expected,(id,kind)
  shutil.copy2(source,res/'drawable-nodpi'/f'{kind}_odyssey_{id}.png')
  obsolete=res/'drawable'/f'{kind}_odyssey_{id}.xml'
  if obsolete.exists():obsolete.unlink()
 obsolete=res/'drawable-nodpi'/f'odyssey_char_{id}.png'
 if obsolete.exists():obsolete.unlink()
overlay={'app_banners':[f"{x['package']}=banner_odyssey_{x['id']}" for x in items if x['package']],'always_show_names':True}
(ROOT/'theme-overlay.json').write_text(json.dumps(overlay,ensure_ascii=False,indent=2)+'\n')
print('Packaged 80 bounded-size Android bitmaps; 16 package mappings.')
