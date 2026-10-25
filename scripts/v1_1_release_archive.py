"""Archive frozen production source plus reviewed public source/tests/docs; no private evidence."""
import hashlib,json,re,shutil,zipfile
from pathlib import Path
from v1_1_release import ROOT,OUT,CERT,guard,sha,save
guard()
DEST=Path(r'D:\GPT\projects\gba-emulator\artifacts\v1.1.0');DEST.mkdir(parents=True,exist_ok=True)
archive=DEST/'GBA-Lite-1.1.0-source.zip';assert not archive.exists(),'Refusing to overwrite candidate source archive'
modules=['app','audio','core-api','core-mgba','data','emulator-session','feature-player','input','renderer','storage','gradle','.github','scripts','test-rom','docs','third_party']
rootfiles=['.gitattributes','.gitignore','ARCHITECTURE.md','GBA_TEST_ROM_CORPUS.md','LICENSE','NOTICE','PROJECT_SPEC.md','README.md','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','settings-gradle.lockfile','settings.gradle.kts']
paths={ROOT/n for n in rootfiles}
for name in modules:
    for p in (ROOT/name).rglob('*'):
        if not p.is_file():continue
        relative=p.relative_to(ROOT);parts=relative.parts
        if any(x in {'.git','build','.gradle','.kotlin','.cxx','__pycache__','.local','artifacts','archives'} for x in parts):continue
        if parts[:3]==('docs','reports','evidence'):continue
        if p.name in {'local.properties','signing.properties'} or p.name.startswith('.env') or p.suffix.lower() in {'.jks','.keystore','.apk','.aab','.hprof','.dmp','.pyc','.pem','.key'}:continue
        paths.add(p)
config={}
for line in Path(r'D:\GPT\projects\gba-emulator\.local\private\signing\signing.properties').read_text(encoding='utf-8').splitlines():
    if '=' in line:k,v=line.split('=',1);config[k]=v
secrets=[config[k].encode() for k in ['storePassword','keyPassword']]
files={}
for p in sorted(paths):
    assert p.resolve().is_relative_to(ROOT.resolve()),'ARCHIVE_PATH_BOUNDARY'
    name=p.relative_to(ROOT).as_posix();data=p.read_bytes()
    assert not any(s in data for s in secrets),'ACTIVE_SECRET_IN_SOURCE: '+name
    assert not re.search(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',data),'PRIVATE_KEY_IN_SOURCE: '+name
    files[name]=hashlib.sha256(data).hexdigest()
frozen=json.loads((OUT/'baseline/source-manifest.json').read_text())
assert all(files[n]==h for n,h in frozen.items()),'ARCHIVE_SOURCE_IDENTITY_MISMATCH'
for library in ['mgba','oboe']:
    vendor=json.loads((ROOT/f'third_party/{library}-files.sha256.json').read_text())
    assert all(files['third_party/'+library+'/'+n]==h for n,h in vendor.items()),'ARCHIVE_VENDOR_MISSING'
with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for name in files:
        entry=zipfile.ZipInfo(name,(2026,10,15,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o644<<16
        z.writestr(entry,(ROOT/name).read_bytes())
with zipfile.ZipFile(archive) as z:
    assert not z.testzip();assert set(z.namelist())==set(files)
    assert all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in files.items())
save(OUT/'source-archive-manifest.json',dict(archiveSha256=sha(archive),files=files,secretScan='PASS: active production credential byte patterns and private-key markers absent; secret filenames/evidence/build excluded',licensedTestFixtures='Source archive only; never packaged in production APK'))
apk=DEST/'GBA-Lite-1.1.0-code16-arm64-release.apk';assert not apk.exists()
shutil.copy2(OUT/'build-A/app-signed.apk',apk)
assert sha(apk)==sha(OUT/'build-B/app-signed.apk')
shutil.copy2(ROOT/'docs/RELEASE_NOTES_V1.1.0.md',DEST/'RELEASE_NOTES_V1.1.0.md')
shutil.copy2(ROOT/'docs/V1_1_KNOWN_LIMITATIONS.md',DEST/'V1_1_KNOWN_LIMITATIONS.md')
shutil.copy2(ROOT/'docs/reports/V1_1_PRODUCTION_RELEASE_AND_STABLE_PROMOTION_REPORT.md',DEST/'V1_1_PRODUCTION_RELEASE_AND_STABLE_PROMOTION_REPORT.md')
shutil.copy2(ROOT/'docs/reports/V1_1_MANUAL_ACCEPTANCE.md',DEST/'V1_1_MANUAL_ACCEPTANCE.md')
shutil.copy2(OUT/'baseline/source-manifest.json',DEST/'production-source-manifest.json')
identity=dict(status='NOT_RELEASED_PENDING_MANUAL_ACCEPTANCE',versionName='1.1.0',versionCode=16,apk=apk.name,apkSha256=sha(apk),certificateSha256=CERT,sourceManifestSha256=sha(DEST/'production-source-manifest.json'),sourceArchive=archive.name,sourceArchiveSha256=sha(archive),stable='1.0.1/code15',commit=None,tag=None)
save(DEST/'artifact-identity.json',identity);save(OUT/'artifact-identity.json',identity)
print(json.dumps(identity,indent=2));print('Archive files: '+str(len(files)))
