"""Collect actual Lite regression evidence; no test execution or verdict fabrication."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/reports/evidence/phase7/7b-lite"


def digest(path):
    return hashlib.file_digest(path.open("rb"), "sha256").hexdigest()


def write(name, value):
    (OUT / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    suites = []
    for module in ("core-api", "storage", "emulator-session", "input", "renderer", "core-mgba", "data", "app"):
        for source in sorted((ROOT / module / "build/test-results").glob("*/TEST-*.xml")):
            dest = OUT / "jvm-xml" / module / source.parent.name / source.name
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, dest)
            tree = ET.parse(source).getroot()
            suites.append({"file": str(dest.relative_to(ROOT)), "name": tree.get("name"),
                           **{k: int(tree.get(k, 0)) for k in ("tests", "failures", "errors", "skipped")},
                           "cases": [{"name": c.get("name"), "status": "FAIL" if c.find("failure") is not None or c.find("error") is not None else "SKIP" if c.find("skipped") is not None else "PASS"} for c in tree.findall("testcase")]})
    write("jvm-results.json", {"totals": {k: sum(s[k] for s in suites) for k in ("tests", "failures", "errors", "skipped")}, "suites": suites})
    for name, selection in {
        "malformed-rom-results.json": ("Phase7ImportTest", "Phase7LiteProtectionTest"),
        "zip-regression-results.json": ("RomImportTest",),
        "path-safety-results.json": ("RomImportTest", "Phase7LiteProtectionTest"),
        "save-state-results.json": ("StorageTest", "Phase7IoFailureTest", "Phase7LiteProtectionTest", "PersistenceSessionTest"),
        "metadata-fallback-results.json": ("DisplaySettingsTest", "SensorMapperTest", "StorageTest", "Phase7LiteProtectionTest"),
    }.items():
        write(name, {"scope": "JVM cases; Android results are in device-results.json", "suites": [s for s in suites if s["name"].split(".")[-1] in selection]})
    devices = []
    for path in sorted(OUT.glob("*.log")):
        if not path.name.startswith(("jni-", "activity-", "ubsan-jni", "ubsan-activity")):
            continue
        if any(label in path.name for label in ("install", "logcat", "progress")):
            continue
        text = path.read_text(encoding="utf-8-sig", errors="replace")
        ok = re.search(r"OK \((\d+) tests?\)", text)
        failed = re.search(r"Tests run: (\d+),\s+Failures: (\d+)", text)
        devices.append({"file": path.name, "status": "FAIL" if failed or "Process crashed" in text else "PASS" if ok else "INCOMPLETE",
                        "tests": int(ok[1]) if ok else int(failed[1]) if failed else None,
                        "failures": int(failed[2]) if failed else 0 if ok else None,
                        "seconds": next(iter(re.findall(r"Time: ([\d.]+)", text)), None)})
    write("device-results.json", devices)
    write("jni-results.json", [item for item in devices if item["file"].startswith("jni-")])
    write("ubsan-results.json", {"scope": "Existing Android gbaUbsan bridge/audio/Oboe path; pinned mGBA compile options omit UBSan. Historical full-dependency hash.c finding remains open.",
                                "runs": [item for item in devices if item["file"].startswith("ubsan-")]})
    ninja = OUT / "ubsan-build.ninja"
    if ninja.exists():
        objects = []
        current = ""
        for line in ninja.read_text(encoding="utf-8").splitlines():
            if line.startswith("build "):
                current = line.split(": ", 1)[0][6:]
            if line.startswith("  FLAGS =") and current.endswith(".o"):
                objects.append({"object": current, "ubsan": "-fsanitize=undefined" in line})
        write("ubsan-instrumentation.json", objects)
    refs = ["7a-software-api34-longrun-final-summary.json", "7a-software-api34-longrun-final-metrics.jsonl",
            "7a-software-api34-sixty-minute.log", "7a-api31-hundred-io-completed-metrics.jsonl",
            "7a-hundred-io-resource-summary.json", "7a-transactions-idle-device.log",
            "7a-final-gate-software-transactions.jsonl", "7a-home-key-fifty-and-shader-hundred.log"]
    write("rom-switch-results.json", {"execution": "Reused Phase7A stress evidence; see report for assertions and limitations",
          "evidence": [{"path": "../" + name, "sha256": digest(OUT.parent / name)} for name in refs]})
    write("artifact-hashes.json", [{"path": str(p.relative_to(ROOT)), "sha256": digest(p)} for directory in ("delivery-apks", "ubsan-apks", "normal-restored-apks") for p in sorted((OUT / directory).glob("*.apk"))])
    write("source-hashes.json", [{"path": str(p.relative_to(ROOT)), "sha256": digest(p)} for p in [
        ROOT / "storage/src/main/kotlin/dev/gbalite/storage/RomImport.kt",
        ROOT / "core-mgba/src/main/cpp/mgba_bridge.cpp",
        ROOT / "core-mgba/src/main/java/dev/gbalite/mgba/MgbaCoreAdapter.kt",
        ROOT / "core-mgba/src/main/cpp/CMakeLists.txt"]])
    by_name = {item["file"]: item for item in devices}
    checks = {"jvm": bool(suites) and all(s["failures"] == 0 and s["errors"] == 0 for s in suites)}
    for name, count in {"jni-final.log": 17, "activity-latest-targeted.log": 11,
                        "activity-normal-restored-smoke.log": 8, "ubsan-jni.log": 17,
                        "ubsan-activity.log": 15}.items():
        item = by_name.get(name, {})
        checks[name] = item.get("status") == "PASS" and item.get("tests") == count
    host = (OUT / "host-native-2.log").read_text(encoding="utf-8-sig", errors="replace")
    checks["host-native"] = host.count("100% tests passed, 0 tests failed") == 2
    audit_path = OUT / "apk-source-audit.log"
    audit = audit_path.read_text(encoding="utf-8-sig", errors="replace") if audit_path.exists() else ""
    checks["apk-source-audit"] = "PASS:" in audit and not re.search(r"^FAIL:", audit, re.M)
    checks["normal-artifacts-restored"] = all((OUT / "normal-restored-apks" / p.name).exists() and
          digest(p) == digest(OUT / "normal-restored-apks" / p.name) for p in (OUT / "delivery-apks").glob("*.apk"))
    normal_ninja = OUT / "normal-build.ninja"
    checks["normal-ubsan-off"] = normal_ninja.exists() and "-fsanitize=undefined" not in normal_ninja.read_text(encoding="utf-8")
    installed = (OUT / "normal-final-install.log").read_text(encoding="utf-8-sig", errors="replace") if (OUT / "normal-final-install.log").exists() else ""
    checks["installed-normal-sha"] = digest(OUT / "delivery-apks/app-debug.apk") in installed
    write("final-validation.json", {"checks": checks, "phase7bLite": "PASS" if all(checks.values()) else "NOT READY",
          "phase7a": "NOT READY — existing gates unchanged", "phase7": "NOT READY", "advancedHardening": "DEFERRED — PRE_PUBLIC_RELEASE_HARDENING"})
    print(json.dumps({"jvmExecutions": sum(s["tests"] for s in suites), "jvmFailures": sum(s["failures"] + s["errors"] for s in suites), "deviceLogs": devices}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
