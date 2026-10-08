#!/usr/bin/env python3
"""Drive the blocker app + fake apps over adb. No Instrumentation/UiAutomation."""
from __future__ import annotations

import base64
import os
import re
import subprocess
import sys
import time
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ART = Path(os.environ.get("EMU_UI_ART", "emulator-ui-artifacts"))
SHOT = ART / "screenshots"
DUMP_PATH = "/sdcard/window_dump.xml"
PKG = "com.wechatblocker"
DY = "com.ss.android.ugc.aweme"
XHS = "com.xingin.xhs"
SERVICE = f"{PKG}/com.wechatblocker.service.WeChatBlockerService"
CLASSICS = ("论语", "大学", "中庸", "孟子", "荀子", "管子")


def run(args: list[str], check: bool = True, timeout: int = 60) -> str:
    print("+", " ".join(args), flush=True)
    proc = subprocess.run(args, capture_output=True, text=True, timeout=timeout)
    out = (proc.stdout or "") + (proc.stderr or "")
    if out.strip():
        print(out.strip()[:2000], flush=True)
    if check and proc.returncode != 0:
        raise RuntimeError(f"cmd failed {proc.returncode}: {args}\n{out}")
    return out


def adb(*args: str, check: bool = True, timeout: int = 60) -> str:
    return run(["adb", *args], check=check, timeout=timeout)


def adb_shell(cmd: str, check: bool = True) -> str:
    return adb("shell", cmd, check=check)


@dataclass
class Node:
    attrib: dict

    @property
    def rid(self) -> str:
        return self.attrib.get("resource-id", "")

    @property
    def text(self) -> str:
        return self.attrib.get("text", "")

    @property
    def enabled(self) -> bool:
        return self.attrib.get("enabled", "true") == "true"

    def center(self) -> tuple[int, int]:
        m = re.search(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", self.attrib.get("bounds", ""))
        if not m:
            raise RuntimeError(f"no bounds: {self.attrib}")
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2


def dump_ui() -> list[Node]:
    adb_shell(f"uiautomator dump {DUMP_PATH}", check=False)
    local = ART / "window_dump.xml"
    adb("pull", DUMP_PATH, str(local), check=False)
    if not local.exists():
        return []
    try:
        root = ET.parse(local).getroot()
    except ET.ParseError as e:
        print("xml parse error", e, flush=True)
        return []
    return [Node(n.attrib) for n in root.iter("node")]


def find_id(nodes: list[Node], suffix: str) -> Node | None:
    want = suffix if ":" in suffix else f"{PKG}:id/{suffix}"
    for n in nodes:
        if n.rid == want:
            return n
    return None


def find_text(nodes: list[Node], needle: str) -> Node | None:
    for n in nodes:
        if needle in (n.text or ""):
            return n
    return None


def screenshot(name: str) -> None:
    SHOT.mkdir(parents=True, exist_ok=True)
    remote = f"/sdcard/Download/blocker-ui/{name}.png"
    adb_shell("mkdir -p /sdcard/Download/blocker-ui")
    adb_shell(f"screencap -p {remote}")
    adb("pull", remote, str(SHOT / f"{name}.png"), check=False)


def tap_node(node: Node) -> None:
    x, y = node.center()
    print(f"tap {node.rid or node.text} at {x},{y}", flush=True)
    adb_shell(f"input tap {x} {y}")


def overlay_visible(nodes: list[Node] | None = None) -> bool:
    nodes = nodes if nodes is not None else dump_ui()
    return find_id(nodes, "promptText") is not None or find_id(nodes, "submitButton") is not None


def wait_overlay(timeout: float = 20) -> list[Node]:
    t0 = time.time()
    last: list[Node] = []
    while time.time() - t0 < timeout:
        last = dump_ui()
        if overlay_visible(last):
            return last
        time.sleep(0.8)
    adb_shell("dumpsys accessibility | head -20", check=False)
    return last


def wait_gone(timeout: float = 8) -> bool:
    t0 = time.time()
    while time.time() - t0 < timeout:
        if not overlay_visible():
            return True
        time.sleep(0.5)
    return False


def clean_text(text: str) -> str:
    out = []
    for ch in text:
        if ch.isspace():
            continue
        if unicodedata.category(ch).startswith("P"):
            continue
        if ch in "·—…《》〈〉「」『』【】（）()[]{}\"'“”‘’":
            continue
        out.append(ch)
    return "".join(out)


def set_overlay_text(text: str) -> None:
    encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
    adb_shell(
        f"am broadcast -a com.wechatblocker.DEBUG_SET_OVERLAY_TEXT --es b64 {encoded}"
    )
    time.sleep(0.8)


def launch(component: str) -> None:
    adb_shell(f"am start -W -n {component}", check=False)
    time.sleep(0.6)


def press_home() -> None:
    adb_shell("input keyevent KEYCODE_HOME")
    time.sleep(1.2)


def current_pkg() -> str:
    out = adb_shell("dumpsys window | grep mCurrentFocus", check=False)
    return out


def service_bound() -> bool:
    dump = adb_shell("dumpsys accessibility", check=False)
    bound_line = next((ln for ln in dump.splitlines() if "Bound services:" in ln), "")
    crashed_line = next((ln for ln in dump.splitlines() if "Crashed services:" in ln), "")
    bound = "视频号拦截器" in bound_line or "WeChatBlocker" in bound_line
    crashed = "WeChatBlocker" in crashed_line
    print(f"bound_line={bound_line.strip()}\ncrashed_line={crashed_line.strip()}", flush=True)
    return bound and not crashed


def enable_a11y() -> None:
    adb_shell(f"settings put secure enabled_accessibility_services {SERVICE}")
    adb_shell("settings put secure accessibility_enabled 1")
    for _ in range(20):
        if service_bound():
            time.sleep(1.5)
            return
        time.sleep(0.5)
    raise RuntimeError("accessibility service not bound")


def has_anr(nodes: list[Node]) -> bool:
    blob = " ".join(n.text for n in nodes)
    return any(s in blob for s in ("isn't responding", "无响应", "没有响应", "ANR"))


def correct_count(nodes: list[Node]) -> int:
    n = find_id(nodes, "charCounter")
    if not n:
        return -1
    m = re.search(r"正确\s*(\d+)\s*/", n.text)
    return int(m.group(1)) if m else -1


def submit_enabled(nodes: list[Node]) -> bool:
    n = find_id(nodes, "submitButton")
    return bool(n and n.enabled)


results: list[tuple[str, str, str]] = []


def record(name: str, status: str, detail: str) -> None:
    print(f"RESULT {status} {name}: {detail}", flush=True)
    results.append((name, status, detail))


def fail_if(cond: bool, name: str, detail: str) -> None:
    if cond:
        record(name, "FAIL", detail)
        raise AssertionError(f"{name}: {detail}")


def main() -> int:
    ART.mkdir(parents=True, exist_ok=True)
    SHOT.mkdir(parents=True, exist_ok=True)
    adb_shell("mkdir -p /sdcard/Download/blocker-ui")
    adb_shell("input keyevent KEYCODE_WAKEUP", check=False)
    adb_shell("wm dismiss-keyguard", check=False)
    enable_a11y()

    # 13 settings, no ANR
    launch(f"{PKG}/.ui.SettingsActivity")
    time.sleep(2)
    nodes = dump_ui()
    screenshot("13_settings")
    fail_if(has_anr(nodes), "13_settings", "ANR dialog")
    fail_if(
        find_id(nodes, "enableDouyinSwitch") is None and find_text(nodes, "拦截抖音") is None,
        "13_settings",
        "settings widgets missing",
    )
    record("13_settings", "PASS", "settings page open, no ANR")

    # 08 open douyin overlay
    adb_shell(f"am force-stop {DY}", check=False)
    adb_shell(f"am force-stop {XHS}", check=False)
    press_home()
    launch(f"{DY}/.MainActivity")
    nodes = wait_overlay(25)
    screenshot("08_passage")
    fail_if(not overlay_visible(nodes), "08_passage", "overlay not shown on fake douyin")
    source = find_id(nodes, "sourceText").text if find_id(nodes, "sourceText") else ""
    passage = find_id(nodes, "promptText").text if find_id(nodes, "promptText") else ""
    fail_if("《" not in source, "08_passage", f"no source: {source!r}")
    fail_if(not any(b in source for b in CLASSICS), "08_passage", f"source not classic: {source!r}")
    clean = clean_text(passage)
    fail_if(len(clean) < 20, "08_passage", f"passage too short: {passage!r}")
    record("08_passage", "PASS", f"source={source} len={len(clean)}")

    # 09 partial
    typed20 = clean[:20]
    set_overlay_text(typed20)
    nodes = dump_ui()
    screenshot("09_partial")
    count = correct_count(nodes)
    fail_if(count < 15 or count >= 50, "09_partial", f"count={count}")
    fail_if(submit_enabled(nodes), "09_partial", "submit enabled too early")
    record("09_partial", "PASS", f"count={count} submit disabled")

    # 10 mismatch
    set_overlay_text(typed20 + "错错错xyz")
    nodes = dump_ui()
    screenshot("10_mismatch")
    after = correct_count(nodes)
    hint = find_id(nodes, "mismatchHint").text if find_id(nodes, "mismatchHint") else ""
    preview = find_id(nodes, "matchPreview").text if find_id(nodes, "matchPreview") else ""
    fail_if(after > count, "10_mismatch", f"wrong chars counted {count}->{after}")
    fail_if("错误" not in hint and not preview, "10_mismatch", f"no highlight hint={hint!r}")
    record("10_mismatch", "PASS", f"count stayed {after}, hint={hint}")
    set_overlay_text(typed20)

    # 20 home
    press_home()
    nodes = dump_ui()
    screenshot("20_home_free")
    focus = current_pkg()
    fail_if(overlay_visible(nodes), "20_home_free", "overlay still on launcher")
    fail_if("aweme" in focus, "20_home_free", f"still on douyin: {focus}")
    record("20_home_free", "PASS", f"launcher, no overlay, focus={focus.strip()[:80]}")

    # 21 other app
    adb_shell("am start -W -a android.settings.SETTINGS", check=False)
    time.sleep(1.5)
    nodes = dump_ui()
    screenshot("21_other_app")
    fail_if(overlay_visible(nodes), "21_other_app", "overlay shown over Settings")
    record("21_other_app", "PASS", "system settings, no overlay")

    # 22 resume
    launch(f"{DY}/.MainActivity")
    nodes = wait_overlay(15)
    screenshot("22_resume")
    fail_if(not overlay_visible(nodes), "22_resume", "overlay did not return")
    typed = find_id(nodes, "inputText").text if find_id(nodes, "inputText") else ""
    fail_if(typed20[:8] not in typed and typed != typed20, "22_resume", f"text lost typed={typed!r}")
    record("22_resume", "PASS", f"kept text {typed[:20]!r}")

    # 11 complete
    passage = find_id(nodes, "promptText").text if find_id(nodes, "promptText") else passage
    clean = clean_text(passage)
    fail_if(len(clean) < 50, "11_complete", f"passage < 50: {len(clean)}")
    typed50 = clean[:50]
    set_overlay_text(typed50)
    nodes = dump_ui()
    screenshot("11_complete")
    count = correct_count(nodes)
    fail_if(count < 50, "11_complete", f"count={count}")
    fail_if(not submit_enabled(nodes), "11_complete", "submit still disabled")
    record("11_complete", "PASS", f"count={count} submit enabled")

    # 12 real tap submit
    btn = find_id(nodes, "submitButton")
    fail_if(btn is None, "12_after_submit", "no submit button")
    tap_node(btn)
    gone = wait_gone(8)
    screenshot("12_after_submit")
    fail_if(not gone, "12_after_submit", "overlay still visible after tap")
    record("12_after_submit", "PASS", "real tap dismissed overlay")

    # 15 no retrigger
    press_home()
    time.sleep(1)
    launch(f"{DY}/.MainActivity")
    time.sleep(4)
    nodes = dump_ui()
    screenshot("15_no_retrigger")
    fail_if(overlay_visible(nodes), "15_no_retrigger", "retriggered inside away window")
    record("15_no_retrigger", "PASS", "no overlay on quick return")

    # 16 night trigger
    press_home()
    launch(f"{PKG}/.ui.SettingsActivity")
    time.sleep(1)
    for _ in range(4):
        adb_shell("input swipe 540 1500 540 400 300", check=False)
        time.sleep(0.3)
    nodes = dump_ui()
    night = find_id(nodes, "triggerNightButton") or find_text(nodes, "夜间检查")
    if night:
        tap_node(night)
    else:
        adb_shell("am broadcast -a com.wechatblocker.DEBUG_NIGHT_TRIGGER", check=False)
    time.sleep(1)
    launch(f"{DY}/.MainActivity")
    nodes = wait_overlay(15)
    screenshot("16_night_trigger")
    fail_if(not overlay_visible(nodes), "16_night_trigger", "night overlay missing")
    record("16_night_trigger", "PASS", "night overlay shown")
    back = find_id(nodes, "backButton")
    if back:
        tap_node(back)
        time.sleep(1)
    press_home()

    # 23 xhs
    adb_shell(f"am force-stop {DY}", check=False)
    adb_shell(f"am force-stop {XHS}", check=False)
    time.sleep(0.5)
    launch(f"{XHS}/.MainActivity")
    nodes = wait_overlay(20)
    screenshot("23_xhs_open")
    fail_if(not overlay_visible(nodes), "23_xhs_open", "no overlay on fake xhs")
    source = find_id(nodes, "sourceText").text if find_id(nodes, "sourceText") else ""
    fail_if("《" not in source, "23_xhs_open", f"xhs source missing: {source!r}")
    record("23_xhs_open", "PASS", f"xhs overlay source={source}")

    return 0


if __name__ == "__main__":
    code = 1
    try:
        code = main()
    except Exception as e:
        print("TEST EXCEPTION", e, flush=True)
        screenshot("zz_failure")
        if not any(r[1] == "FAIL" for r in results):
            record("exception", "FAIL", str(e))
        code = 1
    result_path = ART / "results.txt"
    with result_path.open("w", encoding="utf-8") as f:
        for name, status, detail in results:
            f.write(f"{status}\t{name}\t{detail}\n")
    print("==== RESULTS ====", flush=True)
    print(result_path.read_text(encoding="utf-8"), flush=True)
    if any(s == "FAIL" for _, s, _ in results):
        sys.exit(1)
    sys.exit(code)
