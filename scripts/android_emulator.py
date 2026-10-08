"""Explicit-serial, project-owned AVD utilities. Python standard library only.

ANDROID_HOME/ANDROID_SDK_ROOT take precedence over project local.properties.
No operation selects a physical device; destructive commands require an owned AVD.
"""
import argparse
import os
from pathlib import Path
import subprocess
import sys
import time
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.gbalite.app"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def sdk_root():
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    properties = ROOT / "local.properties"
    if properties.exists():
        for line in properties.read_text().splitlines():
            if line.startswith("sdk.dir="):
                return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    raise RuntimeError("Set ANDROID_HOME or ANDROID_SDK_ROOT")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["start", "detect", "wait", "install", "launch", "stop", "relaunch", "home", "back", "ui", "tap", "scroll", "rotate", "logcat", "screenshot", "clear", "uninstall", "shutdown"])
    parser.add_argument("--serial", help="Required for every device operation: emulator-NNNN")
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--rotation", type=int, choices=[0, 1, 2, 3], default=0)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--filtered", action="store_true")
    parser.add_argument("--text", help="Exact live UI text/content-description for tap")
    parser.add_argument("--avd", help="Existing project-owned AVD name for start")
    parser.add_argument("--graphics", choices=["auto", "host", "software"], default="auto")
    parser.add_argument("--direction", choices=["up", "down"], default="down", help="Content scroll direction")
    args = parser.parse_args()
    adb = sdk_root() / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")

    def run(*command, check=True):
        return subprocess.run([str(adb), *command], capture_output=True, check=check)

    if args.action == "detect":
        print(run("devices", "-l").stdout.decode(errors="replace"))
        return
    if not args.serial or not args.serial.startswith("emulator-") or not args.serial[9:].isdigit():
        parser.error("An explicit emulator-NNNN serial is required; physical devices are refused")

    if args.action == "start":
        if not args.avd or not args.avd.startswith(("GBA_Lite_", "GbaLite_QA_")) or not args.output:
            parser.error("start requires an owned --avd and --output boot log")
        port = int(args.serial[9:])
        if port < 5554 or port > 5682 or port % 2:
            parser.error("Emulator console port must be even and in5554..5682")
        if args.serial in run("devices").stdout.decode().split():
            raise RuntimeError("Requested serial is already in use")
        emulator = sdk_root() / "emulator" / ("emulator.exe" if os.name == "nt" else "emulator")
        avds = subprocess.run([str(emulator), "-list-avds"], capture_output=True, check=True).stdout.decode().splitlines()
        if args.avd not in avds:
            raise RuntimeError("AVD not found; set ANDROID_AVD_HOME if needed")
        args.output.parent.mkdir(parents=True, exist_ok=True)
        if args.output.exists():
            raise FileExistsError("Choose a new boot-log path; existing evidence is preserved")
        with args.output.open("wb") as log:
            child = subprocess.Popen([str(emulator), "-avd", args.avd, "-port", str(port),
                                      "-no-window", "-no-boot-anim", "-no-snapshot", "-gpu", args.graphics],
                                     stdout=log, stderr=subprocess.STDOUT,
                                     creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
                                     start_new_session=os.name != "nt")
        print(f"started pid={child.pid}; serial={args.serial}; avd={args.avd}; graphics={args.graphics}")
        return

    def device(*command, check=True):
        return run("-s", args.serial, *command, check=check)

    if args.action == "wait":
        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            state = device("shell", "getprop", "sys.boot_completed", check=False)
            if state.returncode == 0 and state.stdout.strip() == b"1":
                print("boot_completed=1")
                break
            time.sleep(2)
        else:
            raise TimeoutError("Emulator did not boot before deadline")

    avd = device("emu", "avd", "name").stdout.decode(errors="replace").splitlines()[0].strip()
    if not avd.startswith(("GBA_Lite_", "GbaLite_QA_")):
        raise RuntimeError(f"Refusing non-project AVD: {avd}")
    if args.action in ("clear", "uninstall") and "historical" in avd.lower():
        raise RuntimeError("Historical evidence AVD data must be preserved")
    print(f"serial={args.serial}; avd={avd}; action={args.action}")
    if args.action == "wait":
        return
    if args.action == "install":
        apk = args.apk or ROOT / "app/build/outputs/apk/debug/app-debug.apk"
        if not apk.is_file():
            raise FileNotFoundError(apk)
        result = device("install", "-r", str(apk))
    elif args.action in ("launch", "relaunch"):
        if args.action == "relaunch":
            device("shell", "am", "force-stop", PACKAGE)
        result = device("shell", "am", "start", "-W", "-n", ACTIVITY)
    elif args.action == "stop":
        result = device("shell", "am", "force-stop", PACKAGE)
    elif args.action == "home":
        result = device("shell", "input", "keyevent", "KEYCODE_HOME")
    elif args.action == "back":
        result = device("shell", "input", "keyevent", "KEYCODE_BACK")
    elif args.action in ("ui", "tap", "scroll"):
        def dump():
            device("shell", "uiautomator", "dump", "/sdcard/gbalite-infra-ui.xml")
            return device("exec-out", "cat", "/sdcard/gbalite-infra-ui.xml")
        result = dump()
        if args.action == "scroll":
            nodes = [n for n in ET.fromstring(result.stdout).iter("node") if n.get("scrollable") == "true"]
            if len(nodes) != 1:
                raise RuntimeError(f"Expected one visible scroll container, found {len(nodes)}")
            x1, y1, x2, y2 = map(int, re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", nodes[0].get("bounds")).groups())
            if x2 <= x1 or y2 <= y1:
                raise RuntimeError("Scroll container has no visible area")
            start, end = y1+(y2-y1)*3//4, y1+(y2-y1)//4
            if args.direction == "up":
                start, end = end, start
            result = device("shell", "input", "swipe", str((x1+x2)//2), str(start), str((x1+x2)//2), str(end), "450")
        if args.action == "tap":
            if not args.text:
                parser.error("tap requires --text")
            deadline = time.monotonic() + 30
            while True:
                nodes = [n for n in ET.fromstring(result.stdout).iter("node")
                         if args.text in (n.get("text"), n.get("content-desc"))]
                if nodes or time.monotonic() >= deadline:
                    break
                time.sleep(.5)
                result = dump()
            if len(nodes) != 1:
                raise RuntimeError(f"Expected one visible node for {args.text!r}, found {len(nodes)}")
            x1, y1, x2, y2 = map(int, re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", nodes[0].get("bounds")).groups())
            if x2 <= x1 or y2 <= y1:
                raise RuntimeError("UI node has no visible area")
            result = device("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    elif args.action == "rotate":
        device("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        result = device("shell", "settings", "put", "system", "user_rotation", str(args.rotation))
    elif args.action == "clear":
        result = device("shell", "pm", "clear", PACKAGE)
    elif args.action == "uninstall":
        result = device("uninstall", PACKAGE)
    elif args.action == "shutdown":
        result = device("emu", "kill")
    elif args.action == "logcat":
        command = ["logcat", "-d", "-v", "threadtime"]
        if args.filtered:
            pid = device("shell", "pidof", PACKAGE).stdout.decode().strip()
            if not pid.isdigit():
                raise RuntimeError("App PID unavailable for filtered logcat")
            command += ["--pid", pid]
        result = device(*command)
    elif args.action == "screenshot":
        result = device("exec-out", "screencap", "-p")
        if not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Invalid PNG output")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(result.stdout)
        print(f"saved={args.output}; bytes={len(result.stdout)}")
    else:
        if args.action == "screenshot":
            parser.error("screenshot requires --output")
        print(result.stdout.decode(errors="replace"))
    if result.stderr:
        print(result.stderr.decode(errors="replace"), file=sys.stderr)


if __name__ == "__main__":
    main()
