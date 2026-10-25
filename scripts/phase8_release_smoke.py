from pathlib import Path
import subprocess,xml.etree.ElementTree as ET,re,json,sys,time
R=Path(__file__).resolve().parents[1];E=R/'docs/reports/evidence/phase8_v1_release/install';ADB=r'D:\GPT\tools\android-sdk\platform-tools\adb.exe';SERIAL='emulator-5576'
def adb(*args):
 p=subprocess.run([ADB,'-s',SERIAL,*map(str,args)],capture_output=True,timeout=45)
 assert p.returncode==0,p.stderr.decode(errors='replace')
 return p.stdout
def ui(label='latest'):
 adb('shell','uiautomator','dump','/sdcard/phase8-ui.xml');b=adb('shell','cat','/sdcard/phase8-ui.xml');(E/(label+'.xml')).write_bytes(b)
 root=ET.fromstring(b);nodes=list(root.iter('node'));return nodes
def texts(nodes):return [(n.get('text') or n.get('content-desc'),n.get('bounds')) for n in nodes if n.get('text') or n.get('content-desc')]
def tap(text):
 ns=ui();n=next(n for n in ns if n.get('text')==text or n.get('content-desc')==text);a=list(map(int,re.findall(r'\d+',n.get('bounds'))));adb('shell','input','tap',(a[0]+a[2])//2,(a[1]+a[3])//2)
def shot(label): (E/(label+'.png')).write_bytes(adb('exec-out','screencap','-p'))
if __name__=='__main__':
 cmd=sys.argv[1]
 if cmd=='tap':tap(sys.argv[2]);print('Tapped '+sys.argv[2])
 elif cmd=='ui':print(json.dumps(texts(ui(sys.argv[2] if len(sys.argv)>2 else 'latest')),ensure_ascii=False))
 elif cmd=='shot':shot(sys.argv[2]);print('Screenshot saved')
 elif cmd=='shell':print(adb('shell',*sys.argv[2:]).decode(errors='replace'))
