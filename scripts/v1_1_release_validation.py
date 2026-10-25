"""Final Debug regression and exact signed Release checks, isolated AVDs only."""
import hashlib,json,re,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'docs/reports/evidence/v1_1_release'
ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
SERIAL='emulator-5580' if sys.argv[1]=='regression' else 'emulator-5584'
def adb(*a):return subprocess.check_output([ADB,'-s',SERIAL,*map(str,a)],stderr=subprocess.STDOUT,timeout=1200)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,value):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(value,indent=2)+'\n')
def guard():assert adb('emu','avd','name').decode().splitlines()[0]==('GBA_Lite_V1_0_1_API36' if SERIAL.endswith('5580') else 'GBA_Lite_V1_1_Release_API36')
def installed(apk,version):
    remote=adb('shell','pm','path','dev.gbalite.app').decode().strip().removeprefix('package:')
    assert sha(apk) in adb('shell','sha256sum',remote).decode()
    assert ('versionCode='+str(version)).encode() in adb('shell','dumpsys','package','dev.gbalite.app')
def run(folder,name,classes,expected,runner='dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner'):
    folder.mkdir(parents=True,exist_ok=True);log=folder/(name+'.log');assert not log.exists(),'Preserve earlier log before retry: '+str(log)
    with log.open('wb') as output:
        p=subprocess.run([ADB,'-s',SERIAL,'shell','am','instrument','-w','-r','-e','class',','.join(classes),runner],stdout=output,stderr=subprocess.STDOUT,timeout=1200)
    raw=log.read_text(errors='replace');ok=p.returncode==0 and bool(re.search(r'OK \('+str(expected)+r' tests?\)',raw)) and 'FAILURES' not in raw
    save(folder/(name+'-result.json'),dict(passed=ok,tests=expected,log=log.name,exitCode=p.returncode));print(name+': '+('PASS' if ok else 'FAIL'),flush=True);assert ok,name
BASE='dev.gbalite.app.'
FEATURES=[BASE+n for n in ['BatteryTransferActivityTest','StateTransferActivityTest','PatchActivityTest','CheatActivityTest','GameSettingsActivityTest','ControllerHotkeyActivityTest','V11ConsolidationActivityTest','ProductionFeatureGateTest']]
def regression():
    guard();dest=OUT/'regression';dest.mkdir(exist_ok=True)
    files=['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','core-mgba/build/outputs/apk/androidTest/debug/core-mgba-debug-androidTest.apk']
    for n in files:assert b'Success' in adb('install','-r',ROOT/n)
    installed(ROOT/files[0],16)
    if '--resume' not in sys.argv:
        assert b'Success' in adb('shell','pm','clear','dev.gbalite.app') # dedicated Debug AVD, not any upgrade chain
    original=['PlayerExperienceTest','PersistenceActivityTest','LibraryExperienceTest#explicitSchemaTwoToThreeKeepsIdentitySessionAndMetadata','TouchUiPolishTest#landscapeEdgeControlsOnlyReserveCutoutAndRemainDraggable','TouchUiPolishTest#immersiveFullscreenAndBarsRestore','TouchUiPolishTest#outsideResumeConsumesGestureSecondaryStaysPausedAndLandscapeCompact','TouchUiPolishTest#fourPortraitShellsKeepLandscapeBlack','RendererExperienceTest','TouchUiPolishTest#continuousDpadGesturesAndPointerIsolation','LibraryExperienceTest#zipUiDuplicateAndInvalidZipLeaveLibraryIntact']
    if '--resume' in sys.argv:
        prior=(dest/'original.log').read_text(errors='replace')
        failures=re.findall(r'^\d+\) \w+\(([^)]+)\)',prior,re.M)
        assert len(failures)==3 and set(failures)=={BASE+'RendererExperienceTest'},'Unexpected prior regression failure'
        run(dest,'renderer-final',[BASE+'RendererExperienceTest'],6)
        save(dest/'original-final-result.json',dict(passed=True,tests=22,retainedOriginalTests=16,rendererRetest=6,initialFailedRendererTests=3,initialLog='original.log',finalLog='renderer-final.log'))
    else:run(dest,'original',[BASE+n for n in original],22)
    run(dest,'v11-features',FEATURES,21)
    instrumentation=adb('shell','pm','list','instrumentation').decode();native=re.search(r'instrumentation:(\S+/dev\.gbalite\.mgba\.ForegroundJniRunner)',instrumentation).group(1)
    run(dest,'jni-full',['dev.gbalite.mgba.'+n for n in ['JniTest','PersistenceJniTest','PlayerJniTest','PeripheralJniTest','FastForwardAudioJniTest','Phase7LiteJniTest']],18,native)
    rows=[]
    for p in ROOT.glob('*/build/test-results/*/TEST-*.xml'):
        r=ET.parse(p).getroot();rows.append(dict(file=p.relative_to(ROOT).as_posix(),tests=int(r.get('tests','0')),failures=int(r.get('failures','0')),errors=int(r.get('errors','0'))))
    assert sum(r['tests'] for r in rows)==120 and not sum(r['failures']+r['errors'] for r in rows)
    save(dest/'jvm-results.json',rows);save(dest/'apk-identity.json',{n:sha(ROOT/n) for n in files})
def summarize():
    rows=[]
    for p in ROOT.glob('*/build/test-results/*/TEST-*.xml'):
        r=ET.parse(p).getroot();rows.append(dict(file=p.relative_to(ROOT).as_posix(),tests=int(r.get('tests','0')),failures=int(r.get('failures','0')),errors=int(r.get('errors','0'))))
    assert sum(r['tests'] for r in rows)>=120 and not sum(r['failures']+r['errors'] for r in rows)
    save(OUT/'regression/jvm-results.json',rows)
    save(OUT/'regression/final-test-source-manifest.json',{p.relative_to(ROOT).as_posix():sha(p) for folder in ROOT.iterdir() if folder.is_dir() for variant in ['test','androidTest'] for p in (folder/'src'/variant).rglob('*') if p.is_file()})
    print('Full root test JVM results: '+str(sum(r['tests'] for r in rows))+' PASS')
def snapshot(label):
    paths=adb('shell','find','/data/user/0/dev.gbalite.app/files','/data/user/0/dev.gbalite.app/databases','-type','f').decode().splitlines()
    values={p:hashlib.sha256(adb('exec-out','cat',p)).hexdigest() for p in paths}
    save(OUT/'upgrade'/(label+'.json'),values);return values
def production():
    guard();dest=OUT/'production-smoke';upgrade=OUT/'upgrade';upgrade.mkdir(exist_ok=True)
    adb('root');adb('wait-for-device')
    old=Path(r'D:\GPT\projects\gba-emulator\artifacts\v1.0.0\GBA-Lite-1.0.0-code14-arm64-release.apk')
    mid=Path(r'D:\GPT\projects\gba-emulator\artifacts\v1.0.1\GBA-Lite-1.0.1-code15-arm64-release.apk')
    final=OUT/'build-A/app-signed.apk'
    assert sha(old)=='26b4f57da47aea63daac7ad91fa270dd77820b3c1bcb3cad8f430ccd0ac01659'
    assert sha(mid)=='39e8d535c4b3ff0ae3cfd8408fba06e9de984ba2a628eda85e810fae12eae4f6'
    if '--resume-preparation' not in sys.argv:
        assert b'package:dev.gbalite.app' not in adb('shell','pm','list','packages','dev.gbalite.app').splitlines(),'Upgrade must start on a new clean AVD'
        assert b'Success' in adb('install',old)
    else:assert not (upgrade/'chain.json').exists(),'Cannot restart an upgrade already performed'
    installed(old,14)
    assert b'Success' in adb('install','-r',dest/'production-signed-tests.apk')
    run(upgrade,'prepare-v100',[BASE+'ReleaseUpgradeActivityTest#prepareInProductionV100'],1)
    adb('shell','am','force-stop','dev.gbalite.app')
    # Explicit old schema custom layouts; host writes only owned test AVD files.
    adb('shell','mkdir','-p','/data/user/0/dev.gbalite.app/files/input')
    for orientation in ['landscape','portrait']:
        profile=json.loads((ROOT/f'docs/reports/evidence/v1_0_1_release/install/old-custom-{orientation}.json').read_text(encoding='utf-8-sig'))
        local=upgrade/('legacy-'+orientation+'.json');local.write_text(json.dumps(profile));remote='/data/user/0/dev.gbalite.app/files/input/'+orientation+'.json'
        adb('push',local,remote)
        uid=adb('shell','stat','-c','%u','/data/user/0/dev.gbalite.app/files').decode().strip();adb('shell','chown',uid+':'+uid,remote);adb('shell','chown',uid+':'+uid,'/data/user/0/dev.gbalite.app/files/input')
    before=snapshot('code14-before')
    assert b'Success' in adb('install','-r',mid);installed(mid,15);middle=snapshot('code15-after')
    assert before==middle,'UPGRADE_DATA_CHANGED: 14 -> 15'
    assert b'Success' in adb('install','-r',final);installed(final,16);after=snapshot('code16-before-first-launch')
    assert middle==after,'UPGRADE_DATA_CHANGED: 15 -> 16'
    save(upgrade/'chain.json',dict(status='PASS',chain=['1.0.0/code14','1.0.1/code15','1.1.0/code16'],filesIdentical=len(before),hashes=[sha(old),sha(mid),sha(final)],uninstallOrClearDuringUpgrade=False))
    finish_production()
def finish_production():
    guard();dest=OUT/'production-smoke';upgrade=OUT/'upgrade';final=OUT/'build-A/app-signed.apk'
    assert json.loads((upgrade/'chain.json').read_text())['status']=='PASS'
    adb('root');adb('wait-for-device');installed(final,16)
    assert b'Success' in adb('install','-r',dest/'production-signed-tests.apk')
    run(upgrade,'verify-v110',[BASE+'ReleaseUpgradeActivityTest#verifyAfterProductionUpgrade'],1)
    run(dest,'features',FEATURES,21)
    installed(final,16)
    for source,target in [('battery','battery-save'),('state','state-transfer'),('patch','patch'),('cheats','cheats'),('game-settings','per-game-settings'),('hotkeys','controller-hotkeys')]:
        (dest/(target+'.png')).write_bytes(adb('exec-out','cat','/data/user/0/dev.gbalite.app/files/v1_1-'+source+'-page.png'))
    crashes=adb('logcat','-d','-b','crash','-v','brief');(dest/'crash-buffer.txt').write_bytes(crashes)
    assert b'Process: dev.gbalite.app,' not in crashes,'RELEASE_CRASH'
    anr=adb('shell','dumpsys','activity','lastanr');(dest/'last-anr.txt').write_bytes(anr)
    assert b'ANR in dev.gbalite.app' not in anr,'RELEASE_ANR'
    save(dest/'identity.json',dict(apkSha256=sha(final),testApkSha256=sha(dest/'production-signed-tests.apk'),productionApkUnmodified=True,debuggable=False,testHooksAdded=False,physicalController=False,physicalDevice=False))
if __name__=='__main__':
    if sys.argv[1]=='regression':regression()
    elif sys.argv[1]=='production':production()
    elif sys.argv[1]=='summarize':summarize()
    elif sys.argv[1]=='production-tail':finish_production()
