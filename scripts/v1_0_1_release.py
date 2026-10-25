"""Local hotfix signing/provenance; credentials are external and never logged."""
import hashlib,json,os,shutil,subprocess,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_0_1_release'
SDK=Path(r'D:\GPT\tools\android-sdk')
JAVA=Path(r'D:\GPT\tools\jdk-21-temurin\jdk-21.0.12.1+1\bin\java.exe')
CERT='ac0f13a8cfe4cf1dddee8132b02c34c4013163708662e6f0c3522c6ceba10d5f'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    frozen=json.loads((ROOT/'docs/reports/evidence/v1_0_1_source_identity.json').read_text(encoding='utf-8'))
    for name,h in frozen['sourceManifest'].items():assert sha(ROOT/name)==h,'SOURCE_DRIFT: '+name
    unsigned=OUT/'GBA-Lite-1.0.1-code15-unsigned.apk'
    shutil.copy2(ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk',unsigned)
    apk=OUT/'GBA-Lite-1.0.1-code15-arm64-release.apk'
    assert not apk.exists(),'Refusing to overwrite an existing signed artifact'
    config={}
    for line in Path(r'D:\GPT\projects\gba-emulator\.local\private\signing\signing.properties').read_text(encoding='utf-8').splitlines():
        if '=' in line:k,v=line.split('=',1);config[k]=v
    env=os.environ.copy();env['GBA_STORE_SECRET']=config['storePassword'];env['GBA_KEY_SECRET']=config['keyPassword']
    signer=[str(JAVA),'-jar',str(SDK/'build-tools/36.0.0/lib/apksigner.jar')]
    proc=subprocess.run(signer+['sign','--ks',config['storeFile'],'--ks-key-alias',config['keyAlias'],
        '--ks-pass','env:GBA_STORE_SECRET','--key-pass','env:GBA_KEY_SECRET','--v4-signing-enabled','false','--out',str(apk),str(unsigned)],env=env,capture_output=True)
    assert proc.returncode==0,'Signing failed; credential output suppressed'
    verify=subprocess.run(signer+['verify','--verbose','--print-certs',str(apk)],capture_output=True)
    (OUT/'signature.txt').write_bytes(verify.stdout+verify.stderr)
    assert verify.returncode==0 and CERT.encode() in verify.stdout,'SIGNING_IDENTITY_MISMATCH'
    aapt=str(SDK/'build-tools/35.0.0/aapt2.exe')
    badging=subprocess.check_output([aapt,'dump','badging',str(apk)])
    permissions=subprocess.check_output([aapt,'dump','permissions',str(apk)])
    (OUT/'badging.txt').write_bytes(badging);(OUT/'permissions.txt').write_bytes(permissions)
    assert b"versionCode='15'" in badging and b"versionName='1.0.1'" in badging and b"native-code: 'arm64-v8a'" in badging
    assert b'application-debuggable' not in badging and b'uses-permission:' not in permissions
    native={};entries={}
    nm=SDK/'ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-nm.exe'
    with zipfile.ZipFile(apk) as z:
        names=z.namelist()
        assert not any(n.endswith(('.gba','.zip','wrap.sh')) or 'clang_rt' in n for n in names)
        dex=b'\n'.join(z.read(n) for n in names if n.endswith('.dex'))
        assert not any(s in dex for s in [b'Ldev/gbalite/app/TestRomProvider;',b'Ldev/gbalite/app/ActivityTest;',b'nonzeroSamplesForTest',b'framesForTest',b'probeReadForTest'])
        for n in names:
            if n.startswith('lib/'):
                assert n.startswith('lib/arm64-v8a/') and n.endswith('.so'),n
                p=OUT/'native'/Path(n).name;p.parent.mkdir(exist_ok=True);p.write_bytes(z.read(n))
                symbols=subprocess.check_output([str(nm),'-D','--defined-only',str(p)])
                assert not any(s in symbols for s in [b'ForTest',b'__asan',b'__ubsan']),n
                (p.with_suffix('.symbols.txt')).write_bytes(symbols);native[n]=sha(p)
            if not n.endswith('/'):entries[n]=hashlib.sha256(z.read(n)).hexdigest()
        for src,dst in [('LICENSE','APP-LICENSE.txt'),('NOTICE','NOTICE.txt'),('third_party/mgba/LICENSE','mGBA-LICENSE.txt'),('third_party/oboe/LICENSE','Oboe-LICENSE.txt')]:
            assert z.read('assets/licenses/'+dst)==(ROOT/src).read_bytes(),dst
    assert native,'No native libraries'
    shutil.copy2(ROOT/'app/build/reports/runtime-dependencies.txt',OUT/'runtime-dependencies.txt')
    (OUT/'apk-entries.json').write_text(json.dumps(entries,indent=2)+'\n',encoding='utf-8')
    identity={'versionName':'1.0.1','versionCode':15,'package':'dev.gbalite.app','certificateSha256':CERT,
        'apkSha256':sha(apk),'unsignedSha256':sha(unsigned),'apk':str(apk),'permissions':[],
        'abi':['arm64-v8a'],'nativeLibraries':native,'sourceManifest':frozen['sourceManifest'],
        'releaseSmoke':'PENDING','productionUpgrade':'PENDING','visualApproval':'USER_ACCEPTED_2026-10-15'}
    (OUT/'identity.json').write_text(json.dumps(identity,indent=2)+'\n',encoding='utf-8')
    print('Signed identity and package audit PASS; APK SHA256 '+identity['apkSha256'])

if __name__=='__main__':main()
