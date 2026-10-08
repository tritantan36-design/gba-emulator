"""Install an already downloaded, checksum-verified official Google SDK image.

Fallback for SDKManager transport failure, not a mirror or ABI/image patch.
Arguments identify the exact official repository XML and archive; existing images
are never replaced. SDK license must already be accepted in this SDK installation.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile
import zlib


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--sdk", type=Path, required=True)
    p.add_argument("--metadata", type=Path, required=True)
    p.add_argument("--archive", type=Path, required=True)
    p.add_argument("--package", required=True)
    p.add_argument("--evidence", type=Path, required=True)
    p.add_argument("--complete-extracted", action="store_true", help="Verify every extracted file CRC before metadata-only recovery")
    a = p.parse_args()
    if not re.fullmatch(r"system-images;android-\d+;google_apis;x86_64", a.package):
        raise ValueError("Only explicit Google APIs x86_64 image packages supported")
    if not (a.sdk / "licenses/android-sdk-license").is_file():
        raise RuntimeError("Existing accepted SDK license required; use SDKManager licenses")
    xml = a.metadata.read_text(encoding="utf-8")
    root = ET.fromstring(xml)
    package = next(x for x in root.findall("remotePackage") if x.get("path") == a.package)
    archive = package.find("archives/archive/complete")
    url = archive.findtext("url")
    if a.archive.name != url:
        raise ValueError("Archive filename differs from official metadata")
    if a.archive.stat().st_size != int(archive.findtext("size")):
        raise ValueError("Archive size mismatch")
    checksum = archive.find("checksum")
    algorithm = checksum.get("type", "sha1")
    digest = hashlib.file_digest(a.archive.open("rb"), algorithm).hexdigest()
    if digest != checksum.text.strip():
        raise ValueError("Official archive checksum mismatch")
    target = a.sdk.joinpath(*a.package.split(";"))
    if not a.complete_extracted and target.exists() and any(x.name != ".installer" for x in target.iterdir()):
        raise FileExistsError(f"Refusing to replace installed image: {target}")
    parent = target.parent.resolve()
    with zipfile.ZipFile(a.archive) as z:
        for item in z.infolist():
            candidate = (parent / item.filename).resolve()
            if not candidate.is_relative_to(target.resolve()):
                raise ValueError(f"Unexpected archive member: {item.filename}")
            if a.complete_extracted and not item.is_dir():
                crc = 0
                with candidate.open("rb") as existing:
                    while chunk := existing.read(1024*1024):
                        crc = zlib.crc32(chunk, crc)
                if candidate.stat().st_size != item.file_size or crc != item.CRC:
                    raise ValueError(f"Extracted file does not match verified archive: {candidate}")
        if not a.complete_extracted:
            z.extractall(parent)
    # Preserve original namespace declarations and official package details.
    header = re.search(r"<sys-img:sdk-sys-img\b[^>]*>", xml).group()
    header = header.replace('<sys-img:sdk-sys-img', '<ns2:repository xmlns:ns2="http://schemas.android.com/repository/android/common/02"', 1)
    closing = "</ns2:repository>"
    licenses = "\n".join(re.findall(r"<license\b.*?</license>", xml, re.S))
    remote = re.search(r'<remotePackage\b[^>]*path="' + re.escape(a.package) + r'"[^>]*>.*?</remotePackage>', xml, re.S).group()
    local = remote.replace("<remotePackage", "<localPackage", 1).replace("</remotePackage>", "</localPackage>")
    local = re.sub(r"<archives>.*?</archives>", "", local, flags=re.S)
    local = re.sub(r"<channelRef[^>]*/>", "", local)
    (target / "package.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n' + header + licenses + local + closing, encoding="utf-8")
    proof = {"package": a.package, "officialUrl": "https://dl.google.com/android/repository/sys-img/google_apis/" + url,
             "checksumAlgorithm": algorithm, "officialChecksum": digest,
             "sha256": hashlib.file_digest(a.archive.open("rb"), "sha256").hexdigest(),
             "revision": package.findtext("revision/major"), "target": str(target),
             "sourceProperties": (target / "source.properties").read_text()}
    a.evidence.write_text(json.dumps(proof, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(proof, indent=2))


if __name__ == "__main__":
    main()
