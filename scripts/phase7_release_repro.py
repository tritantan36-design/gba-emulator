"""Two clean, unsigned Release builds. Ordinary QA; no ELF/security tests."""
from pathlib import Path
import argparse, datetime, hashlib, json, os, re, subprocess, zipfile

root=Path(__file__).resolve().parents[1]
evidence=root/'docs/reports/evidence/phase7'
parser=argparse.ArgumentParser()
parser.add_argument('--label',default='7a')
label=parser.parse_args().label
if not re.fullmatch(r'7a(?:-[a-z0-9]+)*',label):
    raise SystemExit('Expected a bounded Phase 7A evidence label')
destination=Path('D:/GPT/artifacts/gba-emulator/phase7')/('repro-'+label)
destination.mkdir(parents=True,exist_ok=True)
if any(destination.iterdir()):
    raise SystemExit('Refusing to overwrite existing reproducibility evidence')
env=os.environ.copy()
env['JAVA_HOME']='D:/GPT/tools/jdk-21-temurin/jdk-21.0.12.1+1'
builds=[]
for number in (1,2):
    log=evidence/f'{label}-clean-release-{number}.log'
    with log.open('w',encoding='utf-8') as output:
        run=subprocess.run(['cmd','/c','gradlew.bat','clean',':app:assembleRelease',':app:exportRuntimeDependencies',
                            '--offline','--console=plain'],cwd=root,env=env,stdout=output,stderr=subprocess.STDOUT)
    if run.returncode:
        raise SystemExit(f'Clean build {number} failed; retained {log}')
    apk=root/'app/build/outputs/apk/release/app-release-unsigned.apk'
    snapshot=destination/f'app-release-unsigned-{number}.apk'
    snapshot.write_bytes(apk.read_bytes())
    dependencies=(root/'app/build/reports/runtime-dependencies.txt').read_bytes()
    (destination/f'dependencies-{number}.txt').write_bytes(dependencies)
    with zipfile.ZipFile(snapshot) as archive:
        entries={name:hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist() if not name.endswith('/')}
    builds.append({'number':number,'apkSha256':hashlib.sha256(snapshot.read_bytes()).hexdigest(),
                   'dependenciesSha256':hashlib.sha256(dependencies).hexdigest(),'entries':entries})
left,right=builds
names=set(left['entries'])|set(right['entries'])
different=sorted(name for name in names if left['entries'].get(name)!=right['entries'].get(name))
payload={'capturedAt':datetime.datetime.now().astimezone().isoformat(),'builds':builds,
         'bitIdentical':left['apkSha256']==right['apkSha256'],
         'dependenciesEqual':left['dependenciesSha256']==right['dependenciesSha256'],
         'differentEntries':different,
         'requiredContentEqual':not any(n=='AndroidManifest.xml' or n=='resources.arsc' or n.startswith(('lib/','res/')) for n in different)}
(evidence/f'{label}-release-reproducibility.json').write_text(json.dumps(payload,indent=2),encoding='utf-8')
print(json.dumps({key:value for key,value in payload.items() if key!='builds'},indent=2))
if not payload['dependenciesEqual'] or not payload['requiredContentEqual']:
    raise SystemExit(1)
