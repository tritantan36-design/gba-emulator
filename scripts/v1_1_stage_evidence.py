"""Freeze stage source / exact installed Debug identity and bounded test summaries. Emulator only."""
import argparse, hashlib, json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--stage',type=int,required=True);parser.add_argument('--tests',type=int,required=True);parser.add_argument('--log',required=True);parser.add_argument('--capture',required=True);args=parser.parse_args()
    out=ROOT/f'docs/reports/evidence/v1_1_stage{args.stage}';out.mkdir(parents=True,exist_ok=True)
    raw=(ROOT/args.log).read_text(encoding='utf-8',errors='replace');assert f'OK ({args.tests} tests)' in raw and 'FAILURES' not in raw
    def adb(*a):return subprocess.check_output([ADB,'-s','emulator-5580',*a])
    assert adb('emu','avd','name').decode().splitlines()[0]=='GBA_Lite_V1_0_1_API36'
    assert b'versionCode=16' in adb('shell','dumpsys','package','dev.gbalite.app')
    manifest={}
    baseline=json.loads((ROOT/'docs/reports/evidence/v1_1_stage1/identity.json').read_text())
    paths={ROOT/n for n in baseline['sourceManifest']}
    for folder in ROOT.iterdir():
        if not folder.is_dir():continue
        for variant in ['main','debug','release']:
            paths.update(f for f in (folder/'src'/variant).rglob('*') if f.is_file())
    for f in sorted(paths):manifest[f.relative_to(ROOT).as_posix()]=digest(f)
    rows=[]
    for f in ROOT.glob('*/build/test-results/*/TEST-*.xml'):
        r=ET.parse(f).getroot();rows.append({'file':f.relative_to(ROOT).as_posix(),'tests':int(r.get('tests','0')),'failures':int(r.get('failures','0')),'errors':int(r.get('errors','0'))})
    assert not sum(r['failures']+r['errors'] for r in rows)
    (out/'jvm-results.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8')
    apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';test=ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    installed=adb('shell','pm','path','dev.gbalite.app').decode().strip().removeprefix('package:');assert digest(apk) in adb('shell','sha256sum',installed).decode()
    testinstalled=adb('shell','pm','path','dev.gbalite.app.test').decode().strip().removeprefix('package:');assert digest(test) in adb('shell','sha256sum',testinstalled).decode()
    (out/args.capture).write_bytes(adb('exec-out','run-as','dev.gbalite.app','cat','files/v1_1-'+args.capture))
    identity={'stage':args.stage,'status':'PASS_AUTOMATED_GATE','baseline':'1.0.1/code15','versionName':'1.1.0','versionCode':16,'jvmTests':sum(r['tests'] for r in rows),'androidTests':args.tests,'debugApkSha256':digest(apk),'testApkSha256':digest(test),'sourceManifest':manifest,'releaseBuilt':False}
    (out/'identity.json').write_text(json.dumps(identity,indent=2)+'\n',encoding='utf-8');print({k:v for k,v in identity.items() if k!='sourceManifest'})
if __name__=='__main__':main()
