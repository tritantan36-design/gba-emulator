"""Audit the exact preview package; this is not a production-release certification."""
from pathlib import Path
import hashlib,json,subprocess,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_1_preview'
def main():
    OUT.mkdir(parents=True,exist_ok=True);apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
    aapt=r'D:\GPT\tools\android-sdk\build-tools\35.0.0\aapt2.exe'
    def dump(*args):return subprocess.check_output([aapt,'dump',*args]).decode('utf-8')
    permissions=dump('permissions',str(apk));manifest=dump('xmltree',str(apk),'--file','AndroidManifest.xml');badging=dump('badging',str(apk))
    for name,value in [('debug-permissions.txt',permissions),('debug-manifest.txt',manifest),('debug-badging.txt',badging)]: (OUT/name).write_text(value,encoding='utf-8')
    assert 'uses-permission' not in permissions
    assert 'TestProvider' not in manifest and 'test.rom' not in manifest and 'test.state' not in manifest
    assert "versionCode='16'" in badging and "versionName='1.1.0'" in badging
    with zipfile.ZipFile(apk) as z:
        names=z.namelist();assert not any(n.lower().endswith(('.gba','.sav','.gbalstate','.ips','.ups','.bps','.keystore','.jks')) for n in names)
        assert all('assets/licenses/'+n in names for n in ['APP-LICENSE.txt','NOTICE.txt','mGBA-LICENSE.txt','Oboe-LICENSE.txt'])
        libs=[n for n in names if n.endswith('.so')];assert libs and all('/arm64-v8a/' in n for n in libs)
    identity={'scope':'Debug preview only; production signing and upgrade pending visual approval','versionName':'1.1.0','versionCode':16,'apkSha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'permissions':[],'testProviderInMainApk':False,'romSavePatchOrKeyAssets':False,'licenses':True,'libraries':libs}
    (OUT/'debug-package-audit.json').write_text(json.dumps(identity,indent=2)+'\n',encoding='utf-8');print(identity)
if __name__=='__main__':main()
