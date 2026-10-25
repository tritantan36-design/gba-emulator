"""Index untouched emulator screenshots and record the narrow source delta."""
import hashlib,json,shutil,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_1_ui_final_polish'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
attempts=json.loads((OUT/'results.json').read_text())
latest={row['group']:row for row in attempts}
assert set(latest)=={'v11-features','ui-regression','after-narrow','after-narrow-font120'}
assert all(row['passed'] for row in latest.values())
(OUT/'attempts.json').write_text(json.dumps(attempts,indent=2)+'\n')
(OUT/'results.json').write_text(json.dumps(list(latest.values()),indent=2)+'\n')
identity=json.loads((OUT/'identity.json').read_text())
identity['testApkSha256']=sha(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
(OUT/'identity.json').write_text(json.dumps(identity,indent=2)+'\n')
baseline=json.loads((ROOT/'docs/reports/evidence/v1_1_preview/identity.json').read_text())['sourceManifest']
manifest={name:sha(ROOT/name) for name in baseline}
changes={name:dict(before=old,after=manifest[name]) for name,old in baseline.items() if old!=manifest[name]}
assert set(changes)=={'app/src/main/java/dev/gbalite/app/'+n+'.kt' for n in ['BatterySavePage','StateFilePage','PatchPage','CheatsPage','GameSettingsPage','HotkeysPage','PlayerViewModel']},changes.keys()
(OUT/'source-delta.json').write_text(json.dumps(dict(changes=changes,sourceManifest=manifest),indent=2)+'\n')
warnings=[]
for p in ROOT.glob('*/build/reports/lint-results-debug.xml'):
    root=ET.parse(p).getroot();issues=[dict(id=n.get('id'),severity=n.get('severity')) for n in root.findall('issue')]
    warnings.append(dict(module=p.parts[-4],issues=issues))
    assert not any(n['severity'] in ['Error','Fatal'] for n in issues)
(OUT/'lint-summary.json').write_text(json.dumps(warnings,indent=2)+'\n')
shutil.copyfile(ROOT/'app/build/reports/lint-results-debug.txt',OUT/'lint.log')
pages=['battery-save','state-transfer','patch','cheats','per-game-settings','controller-hotkeys']
folders=['before','after','after-narrow','after-narrow-font120']
titles=['Baseline 1.0x','Regular 1.0x','320dp 1.0x','320dp 1.2x']
images=[]
for folder in folders:
    for page in pages:
        p=OUT/folder/(page+'.png');assert p.is_file(),p
        images.append(dict(file=p.relative_to(OUT).as_posix(),sha256=sha(p)))
(OUT/'screenshots.json').write_text(json.dumps(images,indent=2)+'\n')
html='<!doctype html><meta charset="utf-8"><title>V1.1 UI final polish</title><style>body{font-family:system-ui;background:#f6f5f2;color:#292a32;margin:24px}section{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:40px}img{width:100%;border:1px solid #ccc}a{color:#65509a}h2{grid-column:1/-1}figure{margin:0}figcaption{padding:8px}</style><h1>V1.1 UI final polish · 1.1.0 / code16</h1><p>Original emulator captures. Production Release not built.</p>'
for page in pages:
    html+='<section><h2>'+page+'</h2>'
    for folder,title in zip(folders,titles):
        url=folder+'/'+page+'.png';html+=f'<figure><a href="{url}"><img loading="lazy" src="{url}"></a><figcaption>{title}</figcaption></figure>'
    html+='</section>'
(OUT/'preview-gallery.html').write_text(html,encoding='utf-8')
(OUT/'screenshot-index.md').write_text('# Screenshot index\n\nOriginal full-window captures; no image editing. See [gallery](preview-gallery.html).\n\n'+ '\n'.join('- ['+n['file']+']('+n['file']+') · SHA256 `'+n['sha256']+'`' for n in images)+'\n',encoding='utf-8')
print('Source delta: 7 presentation files. Screenshot index: 24 captures. Lint: no errors.')
