"""Consolidation on one disposable, explicitly named AVD. Never touches physical devices."""
import hashlib,json,re,subprocess
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_1_preview'
ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
def adb(*args,timeout=1200):
    return subprocess.check_output([ADB,'-s','emulator-5580',*map(str,args)],stderr=subprocess.STDOUT,timeout=timeout)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert adb('emu','avd','name').decode().splitlines()[0]=='GBA_Lite_V1_0_1_API36'
    assert adb('shell','getprop','sys.boot_completed').strip()==b'1'
    for file in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','core-mgba/build/outputs/apk/androidTest/debug/core-mgba-debug-androidTest.apk']:
        assert b'Success' in adb('install','-r',ROOT/file)
    installed=adb('shell','pm','path','dev.gbalite.app').decode().strip().removeprefix('package:')
    assert sha(ROOT/'app/build/outputs/apk/debug/app-debug.apk') in adb('shell','sha256sum',installed).decode()
    # All prior preview captures have already been exported. Clear only this dedicated AVD's test app.
    assert b'Success' in adb('shell','pm','clear','dev.gbalite.app')
    instrumentation=adb('shell','pm','list','instrumentation').decode();(OUT/'instrumentation.txt').write_text(instrumentation)
    native=re.search(r'instrumentation:(\S+/dev\.gbalite\.mgba\.ForegroundJniRunner)',instrumentation).group(1)
    base='dev.gbalite.app.'
    regression=[base+'PlayerExperienceTest',base+'PersistenceActivityTest',
        base+'LibraryExperienceTest#explicitSchemaTwoToThreeKeepsIdentitySessionAndMetadata',
        base+'TouchUiPolishTest#landscapeEdgeControlsOnlyReserveCutoutAndRemainDraggable',
        base+'TouchUiPolishTest#immersiveFullscreenAndBarsRestore',
        base+'TouchUiPolishTest#outsideResumeConsumesGestureSecondaryStaysPausedAndLandscapeCompact',
        base+'TouchUiPolishTest#fourPortraitShellsKeepLandscapeBlack']
    features=[base+n for n in ['BatteryTransferActivityTest','StateTransferActivityTest','PatchActivityTest','CheatActivityTest','GameSettingsActivityTest','ControllerHotkeyActivityTest','V11ConsolidationActivityTest']]
    groups=[('app-regression',regression,'dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner',14),
        ('v11-features',features,'dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner',18),
        ('native-jni',['dev.gbalite.mgba.'+n for n in ['JniTest','PersistenceJniTest','PlayerJniTest','PeripheralJniTest','FastForwardAudioJniTest']],native,16)]
    rows=[]
    for name,classes,runner,expected in groups:
        log=OUT/(name+'.log')
        with log.open('wb') as out:
            p=subprocess.run([ADB,'-s','emulator-5580','shell','am','instrument','-w','-r','-e','class',','.join(classes),runner],stdout=out,stderr=subprocess.STDOUT,timeout=1200)
        text=log.read_text(encoding='utf-8',errors='replace');match=re.search(r'OK \((\d+) tests?\)',text)
        passed=p.returncode==0 and match is not None and int(match.group(1))==expected and 'FAILURES' not in text
        rows.append({'group':name,'expected':expected,'passed':passed,'log':log.name})
        (OUT/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(name+': '+('PASS' if passed else 'FAIL'),flush=True)
        if not passed:raise RuntimeError('Consolidation failed: '+name)
    for source,name in [('v1_1-battery-page.png','battery-page.png'),('v1_1-state-page.png','state-page.png'),('v1_1-patch-page.png','patch-page.png'),('v1_1-cheats-page.png','cheats-page.png'),('v1_1-game-settings-page.png','game-settings-page.png'),('v1_1-hotkeys-page.png','hotkeys-page.png')]:
        (OUT/name).write_bytes(adb('exec-out','run-as','dev.gbalite.app','cat','files/'+source))
    (OUT/'app-logcat-crash.txt').write_bytes(adb('logcat','-d','-b','crash','-v','brief'))
    (OUT/'packages.txt').write_bytes(adb('shell','dumpsys','package','dev.gbalite.app'))
    print('All preview validation groups PASS',flush=True)
if __name__=='__main__':main()
