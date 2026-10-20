"""Capture real app screens from the project's isolated Android emulator.

No image editing or simulated UI. --apply-wallpaper exercises the app's own
explicit wallpaper button on this test VM only; physical devices are rejected.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ADB = ROOT / ".tools/android-sdk/platform-tools/adb.exe"
SERIAL = "emulator-5580"
PNG_HEADER = b"\x89PNG\r\n\x1a\n"


def adb(*args: str, timeout: int = 30, binary: bool = False):
    result = subprocess.run(
        [str(ADB), "-s", SERIAL, *args],
        capture_output=True,
        timeout=timeout,
        check=True,
    )
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace").strip()


def hierarchy() -> ET.Element:
    # A failed UIAutomator invocation can leave an old dump behind, even across VM boots.
    # Use a fresh path and demand explicit success rather than inspecting stale UI state.
    path = f"/sdcard/SidGlassOS-{uuid.uuid4().hex}.xml"
    try:
        for attempt in range(3):
            result = adb("shell", "uiautomator", "dump", path)
            if "dumped to:" in result:
                return ET.fromstring(adb("shell", "cat", path))
            if attempt < 2:
                time.sleep(1)  # Let a preceding automation connection detach after a transition.
        raise RuntimeError("Android did not produce a fresh UI hierarchy; cached dumps were not used")
    finally:
        adb("shell", "rm", "-f", path)


def node_with_text(tree: ET.Element, value: str):
    return next((node for node in tree.iter("node") if node.get("text") == value), None)


def wait_for_text(value: str, timeout: int = 90) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        tree = hierarchy()
        if node_with_text(tree, value) is not None:
            return
        if any("isn't responding" in node.get("text", "") for node in tree.iter("node")):
            raise RuntimeError("The emulator is showing an unresponsive-app dialog; recover it before capture")
        time.sleep(1)
    raise RuntimeError(f"The expected app screen never became ready: {value}")


def tap_node(node: ET.Element) -> None:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        raise RuntimeError("UI node has no usable screen bounds")
    left, top, right, bottom = map(int, match.groups())
    if right <= left or bottom <= top:
        raise RuntimeError("UI node has empty screen bounds")
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def visible_button(node: ET.Element | None, width: int, height: int) -> bool:
    if node is None or node.get("enabled") == "false":
        return False
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return False
    left, top, right, bottom = map(int, match.groups())
    return 0 <= left < right <= width and 0 <= top < bottom <= height


def show(route: str) -> None:
    adb("shell", "am", "start", "-W", "-S", "-n", "com.sidglassos/.DashboardActivity", "--es", "route", route)
    wait_for_text({
        "home": "A quieter kind of Android.",
        "icons": "Made of light",
        "controlcenter": "Control Center",
        "wallpapers": "Moonlit forest",
    }[route])


def save_screen(destination: Path) -> None:
    contents = adb("exec-out", "screencap", "-p", binary=True)
    if not contents.startswith(PNG_HEADER):
        raise RuntimeError("Android did not return a valid PNG screenshot")
    destination.write_bytes(contents)
    print(f"Captured {destination.relative_to(ROOT)}", flush=True)


def apply_home_wallpaper() -> None:
    show("wallpapers")
    raw_size = adb("shell", "wm", "size")
    dimensions = re.findall(r"(\d+)x(\d+)", raw_size)
    if not dimensions:
        raise RuntimeError("Cannot read test display size")
    width, height = map(int, dimensions[-1])
    for _ in range(6):
        tree = hierarchy()
        node = node_with_text(tree, "Set home wallpaper")
        if visible_button(node, width, height):
            tap_node(node)
            break
        adb("shell", "input", "swipe", str(width // 2), str(int(height * .78)), str(width // 2), str(int(height * .36)), "350")
        time.sleep(1)
    else:
        raise RuntimeError("The app's Set home wallpaper button was not found")
    for _ in range(12):
        time.sleep(1)
        tree = hierarchy()
        if node_with_text(tree, "Forest wallpaper applied to your home screen.") is not None:
            print("App confirmed static home wallpaper was applied to the test VM.", flush=True)
            return
        if any("Wallpaper could not be applied" in node.get("text", "") for node in tree.iter("node")):
            raise RuntimeError("The app reported a wallpaper application failure")
    raise RuntimeError("No success was observed after tapping Set home wallpaper")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply-wallpaper", action="store_true")
    arguments = parser.parse_args()
    identity = [line.strip() for line in adb("emu", "avd", "name").splitlines() if line.strip() and line.strip() != "OK"]
    if identity != ["SidGlassOS_API36"]:
        raise RuntimeError("Only the project's SidGlassOS_API36 emulator may be captured")
    output = ROOT / "docs/previews"
    output.mkdir(parents=True, exist_ok=True)
    if arguments.apply_wallpaper:
        apply_home_wallpaper()
    for route, name in (("home", "dashboard"), ("icons", "icons"), ("controlcenter", "control-center")):
        show(route)
        save_screen(output / f"{name}.png")
    adb("shell", "am", "start", "-W", "-S", "-n", "com.sidglassos/.features.launcher.LauncherActivity")
    wait_for_text("All apps")
    save_screen(output / "launcher.png")
    facts = {
        "captured_at_utc": datetime.now(timezone.utc).isoformat(),
        "avd": "SidGlassOS_API36",
        "serial": SERIAL,
        "android": adb("shell", "getprop", "ro.build.version.release"),
        "api": adb("shell", "getprop", "ro.build.version.sdk"),
        "build": adb("shell", "getprop", "ro.build.display.id"),
        "wallpaper_apply_observed": arguments.apply_wallpaper,
        "screenshots": "Unedited PNGs returned by Android screencap",
        "workspace_debug_apk_sha256": hashlib.sha256(
            (ROOT / "app/build/outputs/apk/debug/app-debug.apk").read_bytes()
        ).hexdigest().upper(),
    }
    (output / "capture.json").write_text(json.dumps(facts, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
