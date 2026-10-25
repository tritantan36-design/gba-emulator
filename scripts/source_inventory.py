"""Create vendor file hashes from the ORIGINAL official archives, not the extracted tree."""
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
source_lock = json.loads((root/'third_party/SOURCE_LOCK.json').read_text(encoding='utf-8'))
for source in ('mgba', 'oboe'):
    entry = next(item for item in source_lock if item['name'] == source)
    archive_path = root/f'{source}-source.zip'
    if not archive_path.exists():
        archive_path = root.parents[1]/'.tmp/gba-lite-upstream-source'/f'{source}-source.zip'
    assert hashlib.sha256(archive_path.read_bytes()).hexdigest() == entry['archive_sha256']
    excluded = entry.get('excluded_paths', [])
    with zipfile.ZipFile(archive_path) as archive:
        files = { '/'.join(info.filename.split('/')[1:]): hashlib.sha256(archive.read(info)).hexdigest()
                  for info in archive.infolist() if not info.is_dir()
                  and not any('/'.join(info.filename.split('/')[1:]).startswith(prefix) for prefix in excluded) }
    (root/'third_party'/f'{source}-files.sha256.json').write_text(json.dumps(files, indent=2, sort_keys=True)+'\n')
