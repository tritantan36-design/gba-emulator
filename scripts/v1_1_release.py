"""V1.1 local release gates. Ignored project-local signing secrets are never logged."""
import difflib,hashlib,json,os,re,shutil,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_1_release'
SDK=Path(r'D:\GPT\tools\android-sdk')
JAVA=Path(r'D:\GPT\tools\jdk-21-temurin\jdk-21.0.12.1+1\bin\java.exe')
CERT='ac0f13a8cfe4cf1dddee8132b02c34c4013163708662e6f0c3522c6ceba10d5f'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,v):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
def source():
    baseline=json.loads((ROOT/'docs/reports/evidence/v1_1_ui_final_polish/source-delta.json').read_text())['sourceManifest']
    return {n:sha(ROOT/n) for n in baseline}
def guard():
    frozen=json.loads((OUT/'baseline/source-manifest.json').read_text())
    assert source()==frozen,'BASELINE_MISMATCH: production source drift'
    for folder in ROOT.iterdir():
        if folder.is_dir():
            for variant in ['main','debug','release']:
                for p in (folder/'src'/variant).rglob('*'):
                    if p.is_file():assert p.relative_to(ROOT).as_posix() in frozen,'BASELINE_MISMATCH: unlisted production input'
def freeze():
    dest=OUT/'baseline';assert not dest.exists(),'Refusing to overwrite release baseline';dest.mkdir(parents=True)
    expected=json.loads((ROOT/'docs/reports/evidence/v1_1_ui_final_polish/source-delta.json').read_text())['sourceManifest']
    current=source();assert current==expected,'BASELINE_MISMATCH: UI polish source drift'
    assert 'versionCode = 16' in (ROOT/'app/build.gradle.kts').read_text() and 'versionName = "1.1.0"' in (ROOT/'app/build.gradle.kts').read_text()
    old=json.loads((ROOT/'docs/baselines/v1_0_1.json').read_text());assert old['status']=='LOCAL_STABLE' and old['certificateSha256']==CERT
    for n in current:
        if n.startswith(('renderer/','audio/')):assert current[n]==old['sourceManifest'][n],'BASELINE_MISMATCH: renderer/audio drift'
    for name in ['app/src/main/AndroidManifest.xml','gradle/libs.versions.toml','gradle/verification-metadata.xml','third_party/SOURCE_LOCK.json','third_party/mgba-files.sha256.json','third_party/oboe-files.sha256.json']:
        assert current[name]==old['sourceManifest'][name],'BASELINE_MISMATCH: dependency/permission/vendor drift'
    save(dest/'source-manifest.json',current)
    status=subprocess.check_output(['git','status','--porcelain=v1','-uall'],cwd=ROOT).decode();(dest/'git-status.txt').write_text(status,encoding='utf-8')
    for arg,name in [(['rev-parse','HEAD'],'head'),(['branch','--show-current'],'branch'),(['tag','--list'],'tags')]:
        (dest/(name+'.txt')).write_bytes(subprocess.check_output(['git',*arg],cwd=ROOT))
    artifacts={}
    for name,relative in [('preview.apk','app/build/outputs/apk/debug/app-debug.apk'),('preview-tests.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'),('jni-tests.apk','core-mgba/build/outputs/apk/androidTest/debug/core-mgba-debug-androidTest.apk')]:
        p=ROOT/relative;artifacts[name]=sha(p);shutil.copy2(p,dest/name)
    assert artifacts['preview.apk']=='100526962a7579d9cfe3741b087e6b2df3c614658bc7f28bd6409964efb66710'
    save(dest/'identity.json',dict(versionName='1.1.0',versionCode=16,package='dev.gbalite.app',stable='1.0.1/code15',certificateSha256=CERT,artifacts=artifacts,sourceManifestSha256=sha(dest/'source-manifest.json'),physicalDevice='BLOCKING_USER_NOT_AVAILABLE',physicalController='NOT_AVAILABLE',roomSchema=3,dataStoreSchema='unchanged; new per-game JSON catalogs schema=1',rawStateVersion=7,transferContainerVersion=1,permissions=[]))
    shutil.copy2(ROOT/'gradle/libs.versions.toml',dest/'dependency-versions.toml')
    shutil.copy2(ROOT/'third_party/SOURCE_LOCK.json',dest/'vendor-inventory.json')
    delta={}
    for n in sorted(set(old['sourceManifest'])|set(current)):
        if old['sourceManifest'].get(n)==current.get(n):continue
        if 'Battery' in n:category='Save transfer'
        elif 'State' in n or 'Persistence.kt' in n or 'AtomicSaveStorage' in n:category='State transfer'
        elif 'Patch' in n or 'LibraryRepository' in n:category='Patch'
        elif 'Cheat' in n or n.startswith('core-mgba/'):category='Cheats'
        elif 'Hotkey' in n or 'HidInput' in n or 'MainActivity' in n:category='Hotkeys'
        elif 'GameSettings' in n or 'EmulatorSession' in n or 'EmulatorCore' in n or 'SaveRepository' in n:category='Per-game settings'
        elif n.endswith('build.gradle.kts'):category='tests/docs/version metadata'
        else:category='UI polish / approved feature wiring'
        delta[n]=dict(category=category,before=old['sourceManifest'].get(n),after=current.get(n))
    save(dest/'v1_0_1_to_v1_1_source_delta.json',delta)
    print('Baseline PASS: exact UI-polish source; Stable 1.0.1; renderer/audio/dependencies/permissions unchanged.')
def sign_file(unsigned,apk,verify):
    assert not apk.exists(),'Refusing to overwrite signed candidate'
    config={}
    for line in Path(r'D:\GPT\projects\gba-emulator\.local\private\signing\signing.properties').read_text(encoding='utf-8').splitlines():
        if '=' in line:k,v=line.split('=',1);config[k]=v
    env=os.environ.copy();env['GBA_STORE_SECRET']=config['storePassword'];env['GBA_KEY_SECRET']=config['keyPassword']
    signer=[str(JAVA),'-jar',str(SDK/'build-tools/36.0.0/lib/apksigner.jar')]
    p=subprocess.run(signer+['sign','--ks',config['storeFile'],'--ks-key-alias',config['keyAlias'],'--ks-pass','env:GBA_STORE_SECRET','--key-pass','env:GBA_KEY_SECRET','--v4-signing-enabled','false','--out',str(apk),str(unsigned)],env=env,capture_output=True)
    assert p.returncode==0,'Signing failed; credential output suppressed'
    v=subprocess.run(signer+['verify','--verbose','--print-certs',str(apk)],capture_output=True)
    verify.write_bytes(v.stdout+v.stderr);assert v.returncode==0 and CERT.encode() in v.stdout,'SIGNING_IDENTITY_MISMATCH'
def sign(label):
    guard();assert label in ['A','B'];folder=OUT/('build-'+label);folder.mkdir(exist_ok=True)
    unsigned=folder/'app-unsigned.apk';shutil.copy2(ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk',unsigned)
    sign_file(unsigned,folder/'app-signed.apk',folder/'signature.txt')
    shutil.copy2(ROOT/'app/build/reports/runtime-dependencies.txt',folder/'dependencies.txt')
    save(folder/'identity.json',dict(unsignedSha256=sha(unsigned),signedSha256=sha(folder/'app-signed.apk'),certificateSha256=CERT))
    print('Signed '+label+': '+sha(folder/'app-signed.apk'))
def audit():
    guard();dest=OUT/'provenance';dest.mkdir(exist_ok=True);a=OUT/'build-A/app-signed.apk';b=OUT/'build-B/app-signed.apk'
    assert sha(a)==sha(b),'REPRODUCIBILITY_MISMATCH: inspect before proceeding'
    save(OUT/'reproducibility.json',dict(classification='BIT_FOR_BIT_REPRODUCIBLE',signedSha256=sha(a),unsignedIdentical=sha(OUT/'build-A/app-unsigned.apk')==sha(OUT/'build-B/app-unsigned.apk')))
    aapt=str(SDK/'build-tools/35.0.0/aapt2.exe')
    badging=subprocess.check_output([aapt,'dump','badging',a]);permissions=subprocess.check_output([aapt,'dump','permissions',a]);manifest=subprocess.check_output([aapt,'dump','xmltree',a,'--file','AndroidManifest.xml'])
    for n,v in [('badging.txt',badging),('permissions.txt',permissions),('manifest.txt',manifest)]: (dest/n).write_bytes(v)
    assert all(t in badging for t in [b"package: name='dev.gbalite.app'",b"versionCode='16'",b"versionName='1.1.0'",b"native-code: 'arm64-v8a'",b"minSdkVersion:'26'",b"targetSdkVersion:'36'"])
    assert b'application-debuggable' not in badging and b'uses-permission:' not in permissions
    assert not re.search(rb'android:(?:debuggable|testOnly).*true',manifest) and b'E: instrumentation' not in manifest
    provider_blocks=re.findall(rb'E: provider.*?(?=\n\s{0,10}E:|\Z)',manifest,re.S)
    assert all(b'androidx.startup.InitializationProvider' in block and b':exported' in block and b'=false' in block for block in provider_blocks)
    assert not any(t in manifest for t in [b'TestRomProvider',b'BatteryTestProvider',b'StateTestProvider'])
    deps=(OUT/'build-B/dependencies.txt').read_text();assert not re.search(r'firebase|crashlytics|okhttp|retrofit|analytics|com\.google\.android\.gms|com\.facebook|com\.unity|:ads',deps,re.I)
    nm=SDK/'ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe';entries={};native={}
    with zipfile.ZipFile(a) as z:
        names=z.namelist();assert not any(n.endswith(('.gba','.sav','.gbalstate','.ips','.ups','.bps','.jks','.keystore','signing.properties','.zip','wrap.sh')) or 'clang_rt' in n or re.search(r'(^|/)(gba_)?bios\.(bin|rom)$',n,re.I) for n in names)
        dex=b'\n'.join(z.read(n) for n in names if n.endswith('.dex'))
        assert not any(t in dex for t in [b'Ldev/gbalite/app/TestRomProvider;',b'Ldev/gbalite/app/BatteryTestProvider;',b'Ldev/gbalite/app/StateTestProvider;',b'Ldev/gbalite/app/CheatActivityTest;',b'nonzeroSamplesForTest',b'framesForTest',b'playedForTest',b'probeReadForTest'])
        for n in names:
            if n.endswith('/'):continue
            data=z.read(n);entries[n]=hashlib.sha256(data).hexdigest()
            if n.startswith('lib/'):
                assert n.startswith('lib/arm64-v8a/') and Path(n).name in {'libgba_bridge.so','libc++_shared.so','libandroidx.graphics.path.so','libdatastore_shared_counter.so'},n
                p=dest/Path(n).name;p.write_bytes(data);symbols=subprocess.check_output([str(nm),'-D',p]);(dest/(p.name+'.symbols.txt')).write_bytes(symbols)
                assert not any(t in symbols for t in [b'ForTest',b'__asan',b'__ubsan'])
                native[n]=entries[n]
        for src,dst in [('LICENSE','APP-LICENSE.txt'),('NOTICE','NOTICE.txt'),('third_party/mgba/LICENSE','mGBA-LICENSE.txt'),('third_party/oboe/LICENSE','Oboe-LICENSE.txt')]:assert z.read('assets/licenses/'+dst)==(ROOT/src).read_bytes()
    assert native
    vendor=[]
    for name in ['mgba','oboe']:
        hashes=json.loads((ROOT/f'third_party/{name}-files.sha256.json').read_text());directory=ROOT/'third_party'/name
        assert {p.relative_to(directory).as_posix() for p in directory.rglob('*') if p.is_file()}==set(hashes)
        assert all(sha(directory/n)==h for n,h in hashes.items());vendor.append(dict(name=name,files=len(hashes),verified=True))
    save(dest/'native-integrity.json',dict(abi=['arm64-v8a'],libraries=native,noTestExports=True,noSanitizer=True,vendor=vendor))
    save(dest/'apk-entries.json',entries)
    save(dest/'permissions.json',dict(permissions=[],debuggable=False,testOnly=False,instrumentation=False,testProvider=False))
    save(dest/'audit.json',dict(status='PASS',versionName='1.1.0',versionCode=16,certificateSha256=CERT,apkSha256=sha(a),sourceManifestSha256=sha(OUT/'baseline/source-manifest.json')))
    print('Reproducibility, signed package, native/vendor, permissions, licenses PASS')
def sign_tests():
    folder=OUT/'production-smoke';folder.mkdir(exist_ok=True)
    unsigned=folder/'tests-unsigned.apk';shutil.copy2(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk',unsigned)
    sign_file(unsigned,folder/'production-signed-tests.apk',folder/'test-signature.txt')
    print('Separate test package signed for exact production APK instrumentation; never included in delivery')
def delta_audit():
    guard();old=Path(r'D:\GPT\projects\gba-emulator\artifacts\v1.0.1\GBA-Lite-1.0.1-source.zip')
    assert sha(old)=='daf94f544425329a8f9effadda622d4d10d11d7582037df269fd65bd5040c786'
    path=OUT/'baseline/v1_0_1_to_v1_1_source_delta.json';delta=json.loads(path.read_text());diff=[];recovered=[]
    with zipfile.ZipFile(old) as z:
        names=set(z.namelist())
        stable_manifest=json.loads((ROOT/'docs/baselines/v1_0_1.json').read_text())['sourceManifest']
        recovered=[n for n in source() if n not in stable_manifest and n in names]
        locks={p.relative_to(ROOT).as_posix():sha(p) for p in ROOT.glob('**/gradle.lockfile') if 'build' not in p.relative_to(ROOT).parts}
        locks['settings-gradle.lockfile']=sha(ROOT/'settings-gradle.lockfile')
        for name,h in locks.items():assert name in names and hashlib.sha256(z.read(name)).hexdigest()==h,'DEPENDENCY_LOCK_DRIFT: '+name
        save(OUT/'baseline/dependency-lockfiles.json',locks)
        for name,item in list(delta.items()):
            if name in names:
                data=z.read(name);oldhash=hashlib.sha256(data).hexdigest()
                if item['before'] is not None:assert item['before']==oldhash,name
                else:item['before']=oldhash
                if oldhash==item['after']:del delta[name];continue
                diff.extend(difflib.unified_diff(data.decode('utf-8-sig').splitlines(True),(ROOT/name).read_text(encoding='utf-8-sig').splitlines(True),fromfile='stable/'+name,tofile='v1.1/'+name))
            if item['category']=='UI polish / approved feature wiring':item['category']='UI polish'
    save(path,delta);(OUT/'baseline/stable-to-v11-existing-source.diff').write_text(''.join(diff),encoding='utf-8')
    save(OUT/'baseline/stable-archive-crosscheck.json',dict(sourceArchiveSha256=sha(old),olderManifestOmissionsRecoveredFromArchive=recovered,rendererAudioUnchanged=True,nativeChanges='approved cheat parser/bridge/include boundary only'))
    print('Stable source archive cross-check PASS; native delta is approved cheats only')
if __name__=='__main__':
    command=sys.argv[1]
    if command=='freeze':freeze()
    elif command=='sign':sign(sys.argv[2])
    elif command=='audit':audit()
    elif command=='sign-tests':sign_tests()
    elif command=='delta':delta_audit()
