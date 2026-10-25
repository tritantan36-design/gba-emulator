"""Dedicated emulator-only landscape matrix. Preserves raw failures and restores overlays."""
import json, re, subprocess, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/reports/evidence/v1_0_1_layout'
ADB = r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5580'

def adb(*args):
    p = subprocess.run([ADB, '-s', SERIAL, *map(str,args)], capture_output=True, timeout=240)
    if p.returncode:
        raise RuntimeError(p.stderr.decode(errors='replace'))
    return p.stdout

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    assert adb('emu','avd','name').decode().splitlines()[0] == 'GBA_Lite_V1_0_1_API36'
    assert b'versionCode=15' in adb('shell','dumpsys','package','dev.gbalite.app')
    original=adb('shell','cmd','overlay','list').decode()
    (OUT/'original-overlays.txt').write_text(original,encoding='utf-8')
    managed=re.findall(r'\[[ x]\] (com\.android\.internal\.(?:display\.cutout\.emulation\.[\w]+|systemui\.navbar\.(?:threebutton|gestural)|emulation\.pixel_5))',original)
    enabled=set(re.findall(r'\[x\] (\S+)',original))
    rows=[]
    try:
        for label,cutout,nav in [('no-cutout',None,'gestural'),('left-hole','hole','gestural'),('wide-notch','tall','gestural'),('three-button',None,'threebutton')]:
            for overlay in managed:
                if '.display.cutout.' in overlay or overlay.endswith('emulation.pixel_5'):
                    adb('shell','cmd','overlay','disable',overlay)
            if cutout: adb('shell','cmd','overlay','enable','com.android.internal.display.cutout.emulation.'+cutout)
            adb('shell','cmd','overlay','enable-exclusive','--category','com.android.internal.systemui.navbar.'+nav)
            time.sleep(3)
            folder=OUT/label;folder.mkdir(exist_ok=True)
            result=adb('shell','am','instrument','-w','-r','-e','class',
                'dev.gbalite.app.TouchUiPolishTest#landscapeEdgeControlsOnlyReserveCutoutAndRemainDraggable,dev.gbalite.app.TouchUiPolishTest#immersiveFullscreenAndBarsRestore',
                'dev.gbalite.app.test/androidx.test.runner.AndroidJUnitRunner')
            (folder/'instrumentation.log').write_bytes(result)
            passed=b'OK (2 tests)' in result and b'FAILURES' not in result
            for orientation,name in [(0,'landscape'),(8,'reverse-landscape')]:
                # ActivityInfo landscape=0, reverse landscape=8.
                bounds=adb('exec-out','run-as','dev.gbalite.app','cat',f'files/hotfix-bounds-{orientation}.json')
                (folder/(name+'-bounds.json')).write_bytes(bounds)
                screenshot=adb('exec-out','run-as','dev.gbalite.app','cat',f'files/touch-ui-screenshots/{name}-edge-controls-window.png')
                (folder/(name+'.png')).write_bytes(screenshot)
            rows.append({'configuration':label,'navigation':nav,'cutout':cutout,'passed':passed})
            print(label+': '+('PASS' if passed else 'FAIL'),flush=True)
            if not passed: raise RuntimeError('Matrix failed; inspect raw evidence')
    finally:
        for overlay in managed:
            adb('shell','cmd','overlay','enable' if overlay in enabled else 'disable',overlay)
        (OUT/'matrix.json').write_text(json.dumps(rows,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__': main()
