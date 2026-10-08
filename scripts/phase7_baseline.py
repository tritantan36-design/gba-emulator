"""Read-only Phase 6 freeze before Phase 7 changes; never overwrite evidence."""
from pathlib import Path
import datetime, hashlib, json, subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'docs/reports/evidence/phase7/baseline.json'
if out.exists():
    raise SystemExit('Baseline already frozen; refusing overwrite')
def git(*args):
    r = subprocess.run(['git', *args], cwd=root, capture_output=True, text=True)
    return {'exit': r.returncode, 'stdout': r.stdout, 'stderr': r.stderr}
paths = ['PROJECT_SPEC.md', 'ARCHITECTURE.md', 'app/build.gradle.kts',
         'gradle/libs.versions.toml', 'gradle/wrapper/gradle-wrapper.properties',
         'third_party/SOURCE_LOCK.json', 'test-rom/test-save-v1.sav']
paths += [p.relative_to(root).as_posix() for p in (root/'data/schemas').rglob('*.json')]
paths += [p.relative_to(root).as_posix() for p in root.glob('**/gradle.lockfile')]
paths += ['gradle/verification-metadata.xml', 'settings-gradle.lockfile']
paths += [p.relative_to(root).as_posix() for p in (root/'data/src/main').rglob('*Settings*') if p.is_file()]
artifacts = Path('D:/GPT/artifacts/gba-emulator/phase6/delivery-final')
files = [root/p for p in paths] + list(artifacts.glob('*.apk'))
payload = {
    'capturedAt': datetime.datetime.now().astimezone().isoformat(),
    'sourcePhase': '6 READY', 'appVersion': '0.6.0', 'versionCode': 6,
    'git': {key: git(*args) for key,args in {
        'status': ['status','--porcelain=v1'], 'head': ['rev-parse','HEAD'],
        'remotes': ['remote','-v'], 'tracked': ['ls-files']}.items()},
    'files': {str(p): {'size': p.stat().st_size,
                       'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files},
    'tools': {'AGP':'8.13.0','Gradle':'8.14.3','Kotlin':'2.2.20','ComposeBOM':'2025.09.01',
              'NDK':'27.2.12479018','CMake':'3.22.1','JDK':'21.0.12.1+1'},
    'debugCertificateSha256': '3660dfb189306e49d973a7f91c9cbc1cb35cc93921f0292e8db28b3fa10b4094',
    'releaseSigned': False, 'roomSchema': 3, 'stateJsonSchema': 1, 'rawStateVersion': 7,
    'displayEncoding': 2, 'peripheralEncoding': 1,
    'note': 'No Git HEAD or tracked files; no remote. Certificate value from Phase 6 evidence; verify again for Phase 7 delivery.'
}
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(payload, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')
print(out)
