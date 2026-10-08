"""Summarize existing Phase7 JSONL evidence; never starts a device or a test."""
import argparse
import json
from pathlib import Path
import re


def summarize(source):
    rows = [json.loads(line) for line in source.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    if not rows:
        raise ValueError("No metric samples")
    result = {"source": str(source), "sampleCount": len(rows), "firstEvent": rows[0].get("event"),
              "lastEvent": rows[-1].get("event"), "elapsedMs": max(r["elapsedMs"] for r in rows),
              "completed": any(r.get("event") == "complete" for r in rows),
              "cycleSamples": sum(str(r.get("event", "")).startswith("cycle-") for r in rows),
              "pids": sorted({r.get("pid") for r in rows if r.get("pid") is not None}),
              "measurements": {},
              "limitations": ["Sampled peak, not continuous maximum", "Emulator/ARM translation, not physical thermal/battery evidence",
                              "Counters reset with ROM/core changes; frame counts are not cumulative gameplay duration",
                              "Zero native-heap readings treated as unavailable, not zero allocation", "Finite observations do not prove absence of leaks"]}
    extractors = {
        "rssKiB": lambda r: int(re.search(r"VmRSS:\s*(\d+)", r.get("procStatus", "")).group(1)),
        "osThreads": lambda r: int(re.search(r"Threads:\s*(\d+)", r.get("procStatus", "")).group(1)),
        "fdCount": lambda r: r.get("fdCount"), "jvmThreads": lambda r: r.get("jvmThreads"),
        "javaUsedBytes": lambda r: r.get("javaUsedBytes"), "nativeHeapBytes": lambda r: r.get("nativeHeapBytes"),
        "totalPssKiB": lambda r: r.get("totalPssKiB"),
    }
    for field, extract in extractors.items():
        readings = []
        for row in rows:
            try:
                value = extract(row)
                readings.append(value if value is not None and value > 0 else None)
            except (AttributeError, ValueError, TypeError):
                readings.append(None)
        valid = [v for v in readings if v is not None]
        result["measurements"][field] = {"start": readings[0], "sampledPeak": max(valid) if valid else None,
                                          "end": readings[-1], "lastValid": valid[-1] if valid else None,
                                          "unavailableReadings": readings.count(None)}
    resources = {}
    for field in ("textures", "programs", "buffers", "activeSurfaces"):
        values = [int(m.group(1)) for r in rows if (m := re.search(r"\b" + field + r"=(\d+)", r.get("renderer", "")))]
        resources[field] = {"sampledPeak": max(values) if values else None, "last": values[-1] if values else None}
    result["rendererResources"] = resources
    # These are per-core counters. ROM changes reset them: never sum their
    # sampled values or interpret a final zero after session close as no events.
    counters = {}
    for field in ("frames", "underruns", "rewindBytes"):
        values = [r[field] for r in rows if isinstance(r.get(field), (int, float)) and r[field] >= 0]
        counters[field] = {"firstObserved": values[0] if values else None,
                           "sampledPeak": max(values) if values else None,
                           "lastObserved": values[-1] if values else None}
    result["perCoreCounters"] = counters
    result["limitations"].append("Per-core audio underrun counters reset on ROM changes; sampled values are not a whole-run total or audible-quality verdict")
    return result


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source", type=Path)
    p.add_argument("--output", required=True, type=Path)
    a = p.parse_args()
    a.output.write_text(json.dumps(summarize(a.source), indent=2) + "\n", encoding="utf-8")
    print(a.output)
