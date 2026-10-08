"""Offline manifest, dependency, source and APK boundary checks."""
from pathlib import Path
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
android = '{http://schemas.android.com/apk/res/android}'
results = []
phase2 = '--phase2' in sys.argv
phase3 = '--phase3' in sys.argv
phase7a = '--phase7a' in sys.argv
phase7 = '--phase7' in sys.argv or phase7a
phase6 = '--phase6' in sys.argv or phase7
phase5 = '--phase5' in sys.argv or phase6
phase4 = '--phase4' in sys.argv or phase5
phase3 = phase3 or phase4
phase2 = phase2 or phase3
def check(ok, message):
    results.append((bool(ok), message))

for variant in ('debug', 'release'):
    matches = list((root/'app/build/intermediates/merged_manifests'/variant).glob('**/AndroidManifest.xml'))
    check(bool(matches), f'{variant} merged manifest exists')
    for manifest in matches:
        tree = ET.parse(manifest)
        permissions = [node.get(android+'name') for node in tree.findall('.//uses-permission')]
        check(not permissions, f'{variant} merged permissions = {permissions}')
        activity = tree.find('.//activity')
        check(activity is not None, f'{variant} launcher Activity exists')

dependency_file = root/'app/build/reports/runtime-dependencies.txt'
check(dependency_file.is_file(), 'release dependency list generated')
if dependency_file.is_file():
    deps = dependency_file.read_text(encoding='utf-8').splitlines()
    forbidden = re.compile(r'firebase|crashlytics|okhttp|retrofit|analytics|com\.google\.android\.gms|com\.facebook|com\.unity|:ads', re.I)
    check(not any(forbidden.search(d) for d in deps), 'no network/Analytics/Ads runtime SDKs')
    check(not any('+' in d or 'SNAPSHOT' in d or ':latest' in d for d in deps), 'runtime dependency versions pinned')

lock = json.loads((root/'third_party/SOURCE_LOCK.json').read_text(encoding='utf-8'))
for source in lock:
    directory = root/'third_party'/source['name']
    check((directory/'LICENSE').is_file(), source['name']+' upstream LICENSE retained')
    file_lock = root/'third_party'/f"{source['name']}-files.sha256.json"
    check(file_lock.exists(), source['name']+' content hash manifest exists')
    if file_lock.exists():
        hashes = json.loads(file_lock.read_text(encoding='utf-8'))
        actual = {p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}
        check(actual == set(hashes), source['name']+' vendor file list matches official archive')
        check(all((directory/p).is_file() and hashlib.sha256((directory/p).read_bytes()).hexdigest() == h
                  for p,h in hashes.items()), source['name']+' upstream source unchanged')

for module in ('feature-player', 'renderer', 'input', 'emulator-session', 'core-api', 'storage', 'data'):
    authored = '\n'.join(p.read_text(encoding='utf-8') for p in (root/module/'src/main').rglob('*.kt'))
    check(not re.search(r'import dev\.gbalite\.mgba|external fun|System\.loadLibrary', authored), module+' has no concrete-core/JNI dependency')
check('Apache License' in (root/'LICENSE').read_text(encoding='utf-8'), 'own-code Apache-2.0 LICENSE')
check('This project uses mGBA' in (root/'NOTICE').read_text(encoding='utf-8'), 'mGBA attribution in NOTICE')
check(bool(list(root.glob('**/gradle.lockfile'))), 'Gradle dependency lockfiles generated')

apk = root/'app/build/outputs/apk/debug/app-debug.apk'
check(apk.exists(), 'Debug APK exists')
if apk.exists():
    with zipfile.ZipFile(apk) as z:
        libraries = [n for n in z.namelist() if n.endswith('.so')]
        check(all(n.startswith('lib/arm64-v8a/') for n in libraries), 'APK is arm64-v8a only')
        check('lib/arm64-v8a/libgba_bridge.so' in libraries, 'APK includes source-built bridge')
        known = {'libgba_bridge.so', 'libc++_shared.so', 'libandroidx.graphics.path.so'}
        if phase4:
            known.add('libdatastore_shared_counter.so')
            datastore_native_ok = 'androidx.datastore:datastore-core-android:1.1.7' in deps
            for variant_apk in (apk, root/'app/build/outputs/apk/release/app-release-unsigned.apk'):
                try:
                    with zipfile.ZipFile(variant_apk) as packaged:
                        digest = hashlib.sha256(packaged.read('lib/arm64-v8a/libdatastore_shared_counter.so')).hexdigest()
                    datastore_native_ok &= digest == 'd3e48717c9aa147e0ab21063ba0e8e0211cabf8bf40b222640829519edbf58e1'
                except (OSError, KeyError, zipfile.BadZipFile):
                    datastore_native_ok = False
            check(datastore_native_ok, 'Debug/Release DataStore native matches pinned official 1.1.7 AAR bytes')
        check(all(Path(n).name in known for n in libraries), 'APK native library provenance allowlist: '+str(libraries))
        check(not any(n.endswith('.gba') for n in z.namelist()), 'test ROM not bundled in app')

if phase2:
    for module in ('storage','data'):
        check((root/module/'gradle.lockfile').is_file(),module+' dependency lock exists')
    check((root/'data/schemas/dev.gbalite.data.SaveDatabase/1.json').is_file(), 'Room schema 1 retained')
    check((root/'data/schemas/dev.gbalite.data.SaveDatabase/2.json').is_file(), 'Room schema 2 exported')
    check('fallbackToDestructiveMigration' not in (root/'data/src/main/java/dev/gbalite/data/SaveRepository.kt').read_text(encoding='utf-8'), 'no destructive Room migration')
    check((root/'test-rom/test-save-v1.sav').stat().st_size == 32768, 'fixed legal v1 battery regression asset retained')
    check('ATOMIC_MOVE' in (root/'storage/src/main/kotlin/dev/gbalite/storage/AtomicSaveStorage.kt').read_text(encoding='utf-8'), 'storage fails closed without atomic replace')
if phase3:
    check((root/'docs/adr/ADR-009-player-experience.md').is_file(), 'player ownership decisions recorded before implementation')
    check((root/'core-mgba/src/main/cpp/rewind_ring.h').is_file(), 'bounded memory-only rewind ring exists')
    check(hashlib.sha256((root/'test-rom/test-save-v1.sav').read_bytes()).hexdigest() == '91fe8bc63da1c542130e95137a4e28eeb1ec7df6239ca92fbe08e050b3979c9f', 'fixed v1 sav unchanged')
    for name in ('PROJECT_SPEC.md','ARCHITECTURE.md'):
        original=Path('C:/Users/minc/Downloads')/name
        if original.exists(): check((root/name).read_bytes()==original.read_bytes(),name+' unchanged from provided specification')
if phase4:
    check((root/'docs/adr/ADR-012-renderer-visual-experience.md').is_file(), 'renderer decisions recorded before implementation')
    shaders = root/'renderer/src/main/assets/shaders'
    check({p.name for p in shaders.iterdir()} == {'common.vert','original.frag','sharp.frag','gba_color.frag','lcd.frag'}, 'only four built-in shader modes')
    renderer=(root/'renderer/src/main/java/dev/gbalite/renderer/OriginalSurface.kt').read_text(encoding='utf-8-sig')
    check('ByteBuffer.allocateDirect' in renderer and 'Bitmap' not in renderer, 'reusable upload buffers; no per-frame Bitmap')
    check('GL_FRAMEBUFFER' not in renderer, 'single pass without FBO chain')
    check('failed+=mode' in renderer and 'mode=DisplayMode.ORIGINAL' in renderer, 'shader failure fallback exists')
    check(any('androidx.datastore:datastore-core:1.1.7' == d for d in deps), 'official pinned DataStore prescribed by highest specification')
    manifest=json.loads((root/'test-rom/manifests/video-tests.json').read_text())
    for item in manifest['tests']:
        for extension,key in [('gba','romSha256'),('rgba','rawSha256')]:
            asset=root/'core-mgba/src/androidTest/assets'/f"{item['id']}.{extension}"
            check(hashlib.sha256(asset.read_bytes()).hexdigest()==item[key],f"{item['id']} {extension} pinned hash")
if phase5:
    check((root/'docs/adr/ADR-014-gba-peripherals.md').is_file(), 'peripheral ownership ADR recorded')
    decision=(root/'docs/adr/ADR-015-rumble-permission.md').read_text(encoding='utf-8')
    check('retain zero permissions' in decision and '保持零权限' in decision, 'user zero-permission choice retained; no real rumble claim')
    check((root/'docs/GBA_PERIPHERALS.md').is_file(), 'pinned peripheral APIs documented')
    peripheral_manifest=json.loads((root/'test-rom/manifests/peripheral-tests.json').read_text())
    check(hashlib.sha256((root/peripheral_manifest['source']).read_bytes()).hexdigest()==peripheral_manifest['sourceSha256'], 'original peripheral probe source hash')
    for name,item in peripheral_manifest['roms'].items():
        check(all(hashlib.sha256((root/module/'src/androidTest/assets'/f'{name}.gba').read_bytes()).hexdigest()==item['sha256'] for module in ('app','core-mgba')), name+' pinned test APK ROM hashes')
    sensor=(root/'app/src/main/java/dev/gbalite/app/SensorAdapter.kt').read_text(encoding='utf-8')
    check('unregisterListener(this)' in sensor and 'SENSOR_DELAY_GAME' in sensor, 'bounded sensor sampling and listener cleanup')
    check('native handle' not in sensor.replace('native handles',''), 'sensor adapter owns no native handle')
if phase6:
    check((root/'docs/adr/ADR-016-library-import-playtime.md').is_file(), 'library/import/playtime decisions recorded')
    check((root/'data/schemas/dev.gbalite.data.SaveDatabase/3.json').is_file(), 'Room schema 3 exported without removing old schemas')
    repo=(root/'data/src/main/java/dev/gbalite/data/SaveRepository.kt').read_text(encoding='utf-8')
    check('Migration(2,3)' in repo and 'Migration(1,2)' in repo, 'explicit Room 1-to-2-to-3 migrations')
    importer=(root/'storage/src/main/kotlin/dev/gbalite/storage/RomImport.kt').read_text(encoding='utf-8')
    check(all(name in importer for name in ('INPUT_MAX','TOTAL_MAX','ROM_MAX','ENTRY_MAX','RATIO_MAX','PATH_MAX','DEPTH_MAX','TIMEOUT_MS')), 'ZIP limits explicitly bounded')
    check('ATOMIC_MOVE' in importer and 'CRC32' in importer, 'ROM commit atomic; ZIP CRC checked')
    check('extractAll' not in importer, 'ZIP never bulk extracts untrusted paths')
    fixtures=json.loads((root/'test-rom/manifests/library-tests.json').read_text(encoding='utf-8'))
    for name,item in fixtures['fixtures'].items():
        check(hashlib.sha256((root/'app/src/androidTest/assets'/name).read_bytes()).hexdigest()==item['sha256'], name+' original library fixture hash')
    for src,dst in [('LICENSE','APP-LICENSE.txt'),('NOTICE','NOTICE.txt'),('third_party/mgba/LICENSE','mGBA-LICENSE.txt'),('third_party/oboe/LICENSE','Oboe-LICENSE.txt')]:
        check((root/src).read_bytes()==(root/'app/src/main/assets/licenses'/dst).read_bytes(), dst+' packaged attribution matches source')
if phase7:
    # Portable public export of the original capture; raw device/local evidence stays local.
    baseline_path=root/'docs/baselines/phase6.json'
    baseline=json.loads(baseline_path.read_text(encoding='utf-8')) if baseline_path.is_file() else {}
    check(baseline.get('appVersion')=='0.6.0' and baseline.get('versionCode')==6
          and baseline.get('sourcePhase')=='6 READY'
          and baseline.get('rawStateVersion')==7
          and len(baseline.get('originalCaptureSha256',''))==64,
          'Phase 6 baseline frozen before Phase 7 changes (redacted original capture)')
    check((root/'docs/adr/ADR-017-phase7-hardening-validation.md').is_file(), 'Phase 7 decisions recorded')
    selected=json.loads((root/'test-rom/manifests/suite-selected.json').read_text(encoding='utf-8'))
    check(selected['revision']=='e6942030d25ffe3ba76c72b73a86da073ec857cc', 'selected mGBA suite revision pinned')
    for module in ('app','core-mgba'):
        check(hashlib.sha256((root/module/'src/androidTest/assets/mgba-suite-shifter.gba').read_bytes()).hexdigest()==selected['romSha256'], module+' selected mGBA suite ROM hash')
    for name,item in selected['sourceSha256'].items():
        check(hashlib.sha256((root/'test-rom/licenses/mgba-suite'/Path(name).name).read_bytes()).hexdigest()==item,'selected suite upstream '+name+' unchanged')
    for name,item in json.loads((root/'test-rom/manifests/phase7-stress.json').read_text(encoding='utf-8')).items():
        check(hashlib.sha256((root/'app/src/androidTest/assets'/name).read_bytes()).hexdigest()==item['sha256'],name+' isolated stress fixture hash')
    upgrade=json.loads((root/'test-rom/manifests/phase7-upgrade.json').read_text(encoding='utf-8'))
    check(hashlib.sha256((root/'app/src/androidTest/assets/phase7-upgrade.gba').read_bytes()).hexdigest()==upgrade['sha256'], 'isolated actual-upgrade fixture hash')
    for variant in ('debug','release'):
        p=root/('app/build/outputs/apk/debug/app-debug.apk' if variant=='debug' else 'app/build/outputs/apk/release/app-release-unsigned.apk')
        with zipfile.ZipFile(p) as z:
            names=z.namelist()
            check(not any(n.endswith(('.gba','.zip')) or re.search(r'(^|/)(gba_)?bios\.(bin|rom)$',n,re.I) for n in names), variant+' production has no ROM/ZIP/BIOS assets')
            check(not any(n.endswith('wrap.sh') or 'clang_rt' in n for n in names), variant+' delivery has no sanitizer runtime or wrap script')
            dex=b'\n'.join(z.read(n) for n in names if n.endswith('.dex'))
            test_types=(b'Ldev/gbalite/app/Phase7',b'Ldev/gbalite/app/TestRomProvider;',
                b'Ldev/gbalite/app/ActivityTest;',b'Ldev/gbalite/app/PersistenceActivityTest;',
                b'Ldev/gbalite/mgba/PlayerJniTest;',b'Ldev/gbalite/mgba/JniHarnessActivity;')
            check(not any(name in dex for name in test_types),variant+' delivery DEX has no own instrumentation/test-provider classes')
            probes=(b'nonzeroSamplesForTest',b'framesForTest',b'playedForTest',b'activeHandlesForTest',b'probeReadForTest')
            check(all(name in dex for name in probes) if variant=='debug' else not any(name in dex for name in probes),
                variant+' DEX test-probe declaration boundary (Debug only)')
    check((root/'test-rom/licenses/THIRD_PARTY_ROM_LICENSES.md').is_file(),'test corpus license inventory retained')
out = root/'docs/reports'/('PHASE_7A_APK_PROVENANCE_AUDIT.md' if phase7a else 'PHASE_7_AUDIT_RESULTS.md' if phase7 else 'PHASE_6_AUDIT_RESULTS.md' if phase6 else 'PHASE_5_AUDIT_RESULTS.md' if phase5 else 'PHASE_4_AUDIT_RESULTS.md' if phase4 else 'PHASE_3_AUDIT_RESULTS.md' if phase3 else 'PHASE_2_AUDIT_RESULTS.md' if phase2 else 'AUDIT_RESULTS.md')
# Allow a new validation phase to retain its own audit without overwriting
# historical Phase 6/7A evidence. The default report location stays unchanged.
if '--output' in sys.argv:
    out = root / sys.argv[sys.argv.index('--output') + 1]
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(('# Phase 7A APK / provenance audit (no 7B validation)\n\n' if phase7a else '# Phase 7 audit\n\n' if phase7 else '# Phase 6 audit\n\n' if phase6 else '# Phase 5 audit\n\n' if phase5 else '# Phase 4 audit\n\n' if phase4 else '# Phase 3 audit\n\n' if phase3 else '# Phase 2 audit\n\n' if phase2 else '# Phase 0/1 audit\n\n')+ '\n'.join(f'- {"PASS" if ok else "FAIL"}: {message}' for ok,message in results)+'\n', encoding='utf-8')
for ok, message in results:
    print(('PASS: ' if ok else 'FAIL: ')+message)
sys.exit(0 if all(ok for ok,_ in results) else 1)
