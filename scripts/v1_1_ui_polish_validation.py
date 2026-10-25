"""UI-only regression and captures on the disposable V1.1 preview AVD."""
import hashlib,json,re,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_1_ui_final_polish'
ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
def adb(*args):
    return subprocess.check_output([ADB,'-s','emulator-5580',*map(str,args)],stderr=subprocess.STDOUT,timeout=1200)
PAGES=[('BatteryTransferActivityTest','cancelSafImportAndExportDoNotChangeBatteryAndCapturePreview','battery-page','battery-save'),
       ('StateTransferActivityTest','cancelFilePickersLeavesSlotsUntouchedAndCapturePage','state-page','state-transfer'),
       ('PatchActivityTest','cancelPatchPickerAndCaptureCleanPage','patch-page','patch'),
       ('CheatActivityTest','uiCreateEditToggleDeleteAndCaptureList','cheats-page','cheats'),
       ('GameSettingsActivityTest','gameUiChooseUseGlobalAndResetCapture','game-settings-page','per-game-settings'),
       ('ControllerHotkeyActivityTest','configurationUiConflictPhysicalCaptureAndPreview','hotkeys-page','controller-hotkeys')]
results=json.loads((OUT/'results.json').read_text()) if '--resume' in sys.argv else []
def run(name,classes,count):
    if any(row['group']==name and row['passed'] for row in results):
        print(name+': retained PASS',flush=True);return False
    with (OUT/(name+'.log')).open('wb') as log:
        p=subprocess.run([ADB,'-s','emulator-5580','shell','am','instrument','-w','-r','-e','class',','.join('dev.gbalite.app.'+c for c in classes),'dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT,timeout=1200)
    raw=(OUT/(name+'.log')).read_bytes()
    ok=bool(re.search(r'OK \('+str(count)+r' tests?\)',raw.decode(errors='replace'))) and b'FAILURES' not in raw
    results.append(dict(group=name,tests=count,passed=ok))
    (OUT/'results.json').write_text(json.dumps(results,indent=2))
    print(name+': '+('PASS' if ok else 'FAIL'),flush=True)
    assert ok,(name,p.returncode,raw[-2000:]);return True
def capture(folder):
    dest=OUT/folder;dest.mkdir(parents=True,exist_ok=True)
    for _,_,source,target in PAGES:
        (dest/(target+'.png')).write_bytes(adb('exec-out','run-as','dev.gbalite.app','cat','files/v1_1-'+source+'.png'))
def main():
    assert adb('emu','avd','name').decode().splitlines()[0]=='GBA_Lite_V1_0_1_API36'
    before={k:adb('shell','wm',k).decode() for k in ['size','density']}
    font=adb('shell','settings','get','system','font_scale').decode().strip()
    (OUT/'display-before.json').write_text(json.dumps(dict(before,font_scale=font),indent=2))
    for path in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        assert b'Success' in adb('install','-r',ROOT/path)
    installed=adb('shell','pm','path','dev.gbalite.app').decode().strip().removeprefix('package:')
    sha=hashlib.sha256((ROOT/'app/build/outputs/apk/debug/app-debug.apk').read_bytes()).hexdigest()
    assert sha in adb('shell','sha256sum',installed).decode()
    try:
        adb('shell','settings','put','system','font_scale','1.0')
        if run('v11-features',[p[0] for p in PAGES]+['V11ConsolidationActivityTest'],18):capture('after')
        run('ui-regression',['PlayerExperienceTest','PersistenceActivityTest','TouchUiPolishTest#outsideResumeConsumesGestureSecondaryStaysPausedAndLandscapeCompact'],10)
        adb('shell','wm','size','960x1920');adb('shell','wm','density','480')
        for scale,folder in [('1.0','after-narrow'),('1.2','after-narrow-font120')]:
            adb('shell','settings','put','system','font_scale',scale)
            if run(folder,[p[0]+'#'+p[1] for p in PAGES],6):capture(folder)
    finally:
        for key,value in before.items():
            override=re.search(r'Override \w+: (\S+)',value)
            adb('shell','wm',key,override.group(1) if override else 'reset')
        if font=='null':adb('shell','settings','delete','system','font_scale')
        else:adb('shell','settings','put','system','font_scale',font)
    rows=[]
    for p in ROOT.glob('*/build/test-results/*/TEST-*.xml'):
        r=ET.parse(p).getroot();rows.append(dict(file=p.relative_to(ROOT).as_posix(),tests=int(r.get('tests','0')),failures=int(r.get('failures','0')),errors=int(r.get('errors','0'))))
    (OUT/'jvm-results.json').write_text(json.dumps(rows,indent=2))
    (OUT/'identity.json').write_text(json.dumps(dict(versionName='1.1.0',versionCode=16,debugApkSha256=sha,releaseBuilt=False),indent=2))
    (OUT/'app-logcat-crash.txt').write_bytes(adb('logcat','-d','-b','crash','-v','brief'))
if __name__=='__main__':main()
