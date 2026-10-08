"""Original Apache-2.0 fixtures using project-authored bringup ROM; test APK only."""
from pathlib import Path
import hashlib,json,zipfile,io
root=Path(__file__).resolve().parents[1]
original=root/'app/src/androidTest/assets/bringup.gba'
rom=original.read_bytes()+b'PHASE6_LIBRARY_TEST_ONLY'
other=original.read_bytes()+b'PHASE6_LIBRARY_OTHER_TEST_ONLY'
crud=original.read_bytes()+b'PHASE6_LIBRARY_CRUD_STORAGE_ONLY'
def archive(entries):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for name,data in entries:
            info=zipfile.ZipInfo(name,(2026,10,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            z.writestr(info,data)
    return out.getvalue()
files={'phase6-library.gba':rom,'phase6-other.gba':other,'phase6-crud.gba':crud,
       'phase6-slow.gba':rom+b'\0'*(262144-len(rom)),
       'phase6-crud.zip':archive([('Storage_Test.gba',crud)]),
       'phase6-single.zip':archive([('Original_Test.gba',rom),('README.txt',b'GBA Lite original Apache-2.0 test fixture.')]),
       'phase6-multiple.zip':archive([('a.gba',rom),('b.gba',other)])}
manifest={'license':'Apache-2.0','source':'test-rom/bringup.s','originalSha256':hashlib.sha256(original.read_bytes()).hexdigest(),'fixtures':{}}
for name,data in files.items():
    (root/'app/src/androidTest/assets'/name).write_bytes(data)
    manifest['fixtures'][name]={'sha256':hashlib.sha256(data).hexdigest(),'size':len(data)}
(root/'test-rom/manifests/library-tests.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
