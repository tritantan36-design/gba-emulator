"""Phase8 local release packaging: external credentials, public evidence only."""
from pathlib import Path
import os,json,subprocess,hashlib,shutil,zipfile,sys
ROOT=Path(__file__).resolve().parents[1]
E=ROOT/'docs/reports/evidence/phase8_v1_release'
JAVA=Path(r'D:\GPT\tools\jdk-21-temurin\jdk-21.0.12.1+1\bin\java.exe')
SDK=Path(r'D:\GPT\tools\android-sdk')
H=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def sign(label):
 assert label in ('A','B')
 p=E/'unsigned'/('GBA-Lite-1.0.0-code14-unsigned-'+label+'.apk')
 shutil.copy2(ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk',p)
 config={}
 for line in Path(r'D:\GPT\projects\gba-emulator\.local\private\signing\signing.properties').read_text().splitlines():
  if '=' in line:k,v=line.split('=',1);config[k]=v
 env=os.environ.copy();env['GBA_STORE_SECRET']=config['storePassword'];env['GBA_KEY_SECRET']=config['keyPassword']
 apk=E/'reproducibility'/('GBA-Lite-1.0.0-code14-signed-'+label+'.apk')
 args=[str(JAVA),'-jar',str(SDK/'build-tools/36.0.0/lib/apksigner.jar'),'sign','--ks',config['storeFile'],'--ks-key-alias',config['keyAlias'],'--ks-pass','env:GBA_STORE_SECRET','--key-pass','env:GBA_KEY_SECRET','--out',str(apk),str(p)]
 proc=subprocess.run(args,env=env,capture_output=True)
 assert proc.returncode==0,'Signing failed (credential output suppressed)'
 v=subprocess.run([str(JAVA),'-jar',str(SDK/'build-tools/36.0.0/lib/apksigner.jar'),'verify','--verbose','--print-certs',str(apk)],capture_output=True)
 (E/'signing'/('verify-'+label+'.txt')).write_bytes(v.stdout+v.stderr);assert v.returncode==0
 cert=json.loads((E/'signing/identity.json').read_text())['certificateSha256'];assert cert.encode() in v.stdout
 a=subprocess.run([str(SDK/'build-tools/35.0.0/aapt2.exe'),'dump','badging',str(apk)],capture_output=True);assert a.returncode==0
 (E/'provenance'/('badging-'+label+'.txt')).write_bytes(a.stdout)
 assert b"versionCode='14'" in a.stdout and b"versionName='1.0.0'" in a.stdout and b"native-code: 'arm64-v8a'" in a.stdout
 assert b'application-debuggable' not in a.stdout
 with zipfile.ZipFile(apk) as z:entries={i.filename:{'sha256':hashlib.sha256(z.read(i)).hexdigest(),'timestamp':i.date_time,'size':i.file_size} for i in z.infolist() if not i.is_dir()}
 (E/'reproducibility'/('build-'+label+'.json')).write_text(json.dumps({'unsignedSha256':H(p),'signedSha256':H(apk),'certificateSha256':cert,'entries':entries},indent=2)+'\n')
 print('Build '+label+' signed and verified; APK SHA256 '+H(apk))
if __name__=='__main__':sign(sys.argv[1])

