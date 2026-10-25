"""Production artifact UI/upgrade checks on one isolated, explicitly owned emulator."""
import hashlib,json,re,subprocess,sys,time,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'docs/reports/evidence/v1_0_1_release/install'
ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe'
SERIAL='emulator-5582'
PACKAGE='dev.gbalite.app'
def adb(*args):
    p=subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=60)
    assert p.returncode==0,p.stderr.decode(errors='replace')
    return p.stdout
def owned():assert adb('emu','avd','name').decode().splitlines()[0]=='GBA_Lite_V1_0_1_Release_API36'
def ui(label='latest'):
    adb('shell','uiautomator','dump','/sdcard/hotfix-ui.xml')
    data=adb('shell','cat','/sdcard/hotfix-ui.xml');(OUT/(label+'.xml')).write_bytes(data)
    return list(ET.fromstring(data).iter('node'))
def tap(text):
    matches=[n for n in ui() if n.get('text')==text or n.get('content-desc')==text]
    assert matches,'No live UI match: '+text
    n=next(n for n in matches if n.get('enabled')=='true')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
    x=(x1+x2)//2;y=(y1+y2)//2
    adb('shell','input','swipe',x,y,x,y,120)
    time.sleep(.8)
def snapshot(label):
    owned();adb('root');time.sleep(1)
    paths=adb('shell','find',f'/data/user/0/{PACKAGE}/files',f'/data/user/0/{PACKAGE}/databases','-type','f').decode().splitlines()
    result={}
    for path in paths:
        result[path]=hashlib.sha256(adb('exec-out','cat',path)).hexdigest()
    (OUT/(label+'.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');return result
def main():
    OUT.mkdir(parents=True,exist_ok=True);owned();cmd=sys.argv[1]
    if cmd=='ui':print(json.dumps([(n.get('text') or n.get('content-desc'),n.get('bounds')) for n in ui(sys.argv[2] if len(sys.argv)>2 else 'latest') if n.get('text') or n.get('content-desc')],ensure_ascii=True))
    elif cmd=='tap':tap(sys.argv[2])
    elif cmd=='shot':(OUT/(sys.argv[2]+'.png')).write_bytes(adb('exec-out','screencap','-p'))
    elif cmd=='shell':print(adb('shell',*sys.argv[2:]).decode(errors='replace'))
    elif cmd=='snapshot':print('Snapshot files: '+str(len(snapshot(sys.argv[2]))))
    elif cmd=='init':
        old=ROOT/'docs/reports/evidence/phase8_v1_release/final/GBA-Lite-1.0.0-code14-arm64-release.apk'
        assert hashlib.sha256(old.read_bytes()).hexdigest()=='26b4f57da47aea63daac7ad91fa270dd77820b3c1bcb3cad8f430ccd0ac01659'
        assert b'Success' in adb('install',str(old))
        adb('push',ROOT/'app/src/androidTest/assets/persistence.gba','/sdcard/Download/hotfix-persistence.gba')
        adb('shell','am','start','-W','-n',PACKAGE+'/.MainActivity')
        print('Production V1.0.0 installed on clean test AVD')
    elif cmd=='upgrade':
        adb('shell','am','force-stop',PACKAGE);before=snapshot('before-upgrade')
        apk=OUT.parent/'GBA-Lite-1.0.1-code15-arm64-release.apk'
        result=adb('install','-r',str(apk));assert b'Success' in result
        after=snapshot('after-upgrade');assert before==after,'UPGRADE_DATA_CHANGED'
        remote=adb('shell','pm','path',PACKAGE).decode().strip().split('package:')[1]
        assert adb('shell','sha256sum',remote).decode().split()[0]==hashlib.sha256(apk.read_bytes()).hexdigest()
        assert b'versionCode=15' in adb('shell','dumpsys','package',PACKAGE)
        (OUT/'upgrade.json').write_text(json.dumps({'status':'PASS','from':'1.0.0/code14','to':'1.0.1/code15','filesIdentical':len(before),'installedApkSha256':hashlib.sha256(apk.read_bytes()).hexdigest()},indent=2)+'\n',encoding='utf-8')
        print('Production code14 -> code15 upgrade PASS; '+str(len(before))+' private files unchanged')
if __name__=='__main__':main()
