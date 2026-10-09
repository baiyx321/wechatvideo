#!/usr/bin/env python3
"""Drive the blocker app + fake apps over adb. No Instrumentation/UiAutomation.

uiautomator dump / UiAutomation unbinds AccessibilityService on API 30 and
destroys TYPE_ACCESSIBILITY_OVERLAY. Overlay state is read via debug broadcast.
"""
from __future__ import annotations

import base64
import json
import os
import re
import subprocess
import sys
import time
import unicodedata
from pathlib import Path

ART = Path(os.environ.get("EMU_UI_ART", "emulator-ui-artifacts"))
SHOT = ART / "screenshots"
PKG = "com.wechatblocker"
DY = "com.ss.android.ugc.aweme"
XHS = "com.xingin.xhs"
SERVICE = f"{PKG}/com.wechatblocker.service.WeChatBlockerService"
RECEIVER = f"{PKG}/com.wechatblocker.service.DebugOverlayReceiver"
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


def adb_shell(cmd: str, check: bool = True, timeout: int = 60) -> str:
    return adb("shell", cmd, check=check, timeout=timeout)


def screenshot(name: str) -> None:
    SHOT.mkdir(parents=True, exist_ok=True)
    remote = f"/sdcard/Download/blocker-ui/{name}.png"
    adb_shell("mkdir -p /sdcard/Download/blocker-ui")
    adb_shell(f"screencap -p {remote}")
    adb("pull", remote, str(SHOT / f"{name}.png"), check=False)


def broadcast(action: str, extra: str = "") -> str:
    cmd = f"am broadcast -a {action} -n {RECEIVER}"
    if extra:
        cmd += " " + extra
    return adb_shell(cmd, check=False)


def _extract_json(raw: str) -> dict | None:
    raw = (raw or "").replace("\r", "").strip()
    if not raw:
        return None
    for line in reversed(raw.splitlines() or [raw]):
        line = line.strip()
        if line.startswith("{") and line.endswith("}"):
            try:
                return json.loads(line)
            except json.JSONDecodeError:
                continue
    start = raw.find("{")
    end = raw.rfind("}")
    if start >= 0 and end > start:
        try:
            return json.loads(raw[start : end + 1])
        except json.JSONDecodeError:
            return None
    return None


def read_json_file(name: str) -> dict:
    raw = adb_shell(f"run-as {PKG} cat files/{name}", check=False)
    parsed = _extract_json(raw)
    if parsed is not None:
        return parsed
    local = ART / name
    adb(
        "pull",
        f"/sdcard/Android/data/{PKG}/files/{name}",
        str(local),
        check=False,
    )
    if local.exists():
        try:
            return json.loads(local.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            print(f"{name} json parse error", e, flush=True)
    print(f"{name} raw={raw[:500]!r}", flush=True)
    return {}


def onboarding_state() -> dict:
    broadcast("com.wechatblocker.DEBUG_DUMP_ONBOARDING")
    time.sleep(0.4)
    state = read_json_file("onboarding_state.json")
    print(f"onboarding_state={json.dumps(state, ensure_ascii=False)[:500]}", flush=True)
    return state


def wait_onboarding(timeout: float = 10) -> dict:
    t0 = time.time()
    last: dict = {}
    while time.time() - t0 < timeout:
        last = onboarding_state()
        if last.get("visible"):
            center = last.get("openCenter") or {}
            if int(center.get("x") or 0) > 0:
                return last
        time.sleep(0.5)
    adb_shell("dumpsys activity top | grep -E 'Onboarding|MainActivity|openAccessibility' | head -40", check=False)
    return last


def in_accessibility_settings() -> bool:
    focus = current_focus()
    resumed = adb_shell(
        "dumpsys activity activities | grep -E 'mResumedActivity|mFocusedActivity|topResumedActivity' | head",
        check=False,
    )
    blob = (focus + "\n" + resumed).lower()
    print(f"a11y_settings_focus={focus.strip()[:200]}", flush=True)
    if "com.android.settings" not in blob and "settings" not in blob:
        return False
    return any(
        token in blob
        for token in (
            "accessibility",
            "subsettings",
            "accessibilitysettings",
            "toggleaccessibility",
        )
    )


def overlay_state() -> dict:
    broadcast("com.wechatblocker.DEBUG_DUMP_OVERLAY")
    time.sleep(0.4)
    state = read_json_file("overlay_state.json")
    print(f"overlay_state={json.dumps(state, ensure_ascii=False)[:500]}", flush=True)
    return state


def overlay_window_listed() -> bool:
    a11y = adb_shell("dumpsys accessibility", check=False)
    if "TYPE_ACCESSIBILITY_OVERLAY" in a11y:
        return True
    win = adb_shell("dumpsys window windows | grep -E 'ty=2032|type=2032|TYPE_ACCESSIBILITY' | head -20", check=False)
    return "2032" in win or "TYPE_ACCESSIBILITY" in win


def overlay_visible(state: dict | None = None) -> bool:
    st = state if state is not None else overlay_state()
    if st.get("visible") is True:
        return True
    if st.get("visible") is False:
        return False
    return overlay_window_listed()


def wait_overlay(timeout: float = 20) -> dict:
    t0 = time.time()
    last: dict = {}
    while time.time() - t0 < timeout:
        last = overlay_state()
        if last.get("visible"):
            return last
        if overlay_window_listed() and last.get("passage"):
            last["visible"] = True
            return last
        time.sleep(0.8)
    print("wait_overlay timeout; dumpsys accessibility (head):", flush=True)
    adb_shell("dumpsys accessibility | head -40", check=False)
    adb_shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head", check=False)
    return last


def wait_gone(timeout: float = 8) -> bool:
    t0 = time.time()
    while time.time() - t0 < timeout:
        st = overlay_state()
        if not st.get("visible") and not overlay_window_listed():
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


def set_overlay_text(text: str) -> dict:
    encoded = base64.b64encode(text.encode("utf-8")).decode("ascii")
    broadcast("com.wechatblocker.DEBUG_SET_OVERLAY_TEXT", f"--es b64 {encoded}")
    t0 = time.time()
    last: dict = {}
    while time.time() - t0 < 6:
        last = overlay_state()
        typed = last.get("typedView") or last.get("typed") or ""
        if typed == text or text[:12] in typed:
            return last
        time.sleep(0.4)
    return last


def launch(component: str) -> None:
    adb_shell(f"am start -W -n {component}", check=False)
    time.sleep(0.6)


def press_home() -> None:
    adb_shell("input keyevent KEYCODE_HOME")
    time.sleep(1.2)


def current_focus() -> str:
    return adb_shell("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head", check=False)


def service_bound() -> bool:
    dump = adb_shell("dumpsys accessibility", check=False)
    bound_line = next((ln for ln in dump.splitlines() if "Bound services:" in ln), "")
    crashed_line = next((ln for ln in dump.splitlines() if "Crashed services:" in ln), "")
    bound = "视频号拦截器" in bound_line or "WeChatBlocker" in bound_line
    crashed = "WeChatBlocker" in crashed_line
    print(f"bound_line={bound_line.strip()}\ncrashed_line={crashed_line.strip()}", flush=True)
    return bound and not crashed


def disable_a11y() -> None:
    """CI-only: start the onboarding path with the service off. Never used by the app."""
    adb_shell("settings put secure enabled_accessibility_services null", check=False)
    adb_shell("settings put secure accessibility_enabled 0", check=False)
    for _ in range(15):
        if not service_bound():
            return
        time.sleep(0.3)
    print("WARN a11y still bound after disable", flush=True)


def enable_a11y() -> None:
    adb_shell(f"settings put secure enabled_accessibility_services {SERVICE}")
    adb_shell("settings put secure accessibility_enabled 1")
    for _ in range(20):
        if service_bound():
            time.sleep(1.5)
            return
        time.sleep(0.5)
    raise RuntimeError("accessibility service not bound")


def ensure_a11y() -> None:
    if not service_bound():
        print("a11y not bound, re-enabling", flush=True)
        enable_a11y()


def has_anr() -> bool:
    anr = adb_shell("dumpsys activity anr", check=False)
    if "com.wechatblocker" in anr and "ANR in" in anr:
        return True
    log = adb_shell("logcat -d -s ActivityManager:I | grep -E 'ANR in com.wechatblocker' | tail", check=False)
    return "ANR in com.wechatblocker" in log


def correct_count(state: dict) -> int:
    counter = state.get("counter") or ""
    m = re.search(r"正确\s*(\d+)\s*/", counter)
    return int(m.group(1)) if m else -1


def submit_enabled(state: dict) -> bool:
    return bool(state.get("submitEnabled"))


def tap_center(center: dict | None, label: str) -> None:
    if not isinstance(center, dict):
        raise RuntimeError(f"{label} center missing: {center!r}")
    x, y = int(center.get("x") or 0), int(center.get("y") or 0)
    w, h = int(center.get("w") or 0), int(center.get("h") or 0)
    print(f"tap {label} at {x},{y} size={w}x{h}", flush=True)
    if x <= 0 or y <= 0:
        raise RuntimeError(f"{label} has invalid screen position {x},{y}")
    adb_shell(f"input tap {x} {y}")


def collect_debug(tag: str) -> None:
    path = ART / f"debug-{tag}.txt"
    chunks = [
        current_focus(),
        adb_shell("dumpsys accessibility | head -50", check=False),
        json.dumps(read_json_file("overlay_state.json"), ensure_ascii=False, indent=2),
        json.dumps(read_json_file("onboarding_state.json"), ensure_ascii=False, indent=2),
    ]
    path.write_text("\n\n".join(chunks), encoding="utf-8")
    print(f"wrote {path}", flush=True)


results: list[tuple[str, str, str]] = []


def record(name: str, status: str, detail: str) -> None:
    print(f"RESULT {status} {name}: {detail}", flush=True)
    results.append((name, status, detail))


def fail_if(cond: bool, name: str, detail: str) -> None:
    if cond:
        record(name, "FAIL", detail)
        collect_debug(name)
        raise AssertionError(f"{name}: {detail}")


def main() -> int:
    ART.mkdir(parents=True, exist_ok=True)
    SHOT.mkdir(parents=True, exist_ok=True)
    adb_shell("mkdir -p /sdcard/Download/blocker-ui")
    adb_shell("input keyevent KEYCODE_WAKEUP", check=False)
    adb_shell("wm dismiss-keyguard", check=False)
    disable_a11y()
    adb_shell(f"am force-stop {PKG}", check=False)
    launch(f"{PKG}/.ui.MainActivity")
    state = wait_onboarding(12)
    screenshot("01_onboarding")
    title = state.get("title") or ""
    expl = state.get("explanation") or ""
    top = adb_shell(
        "dumpsys activity top | grep -E 'OnboardingActivity|onboardingTitle|无障碍|openAccessibility' | head -40",
        check=False,
    )
    fail_if(not state.get("visible") and "OnboardingActivity" not in top, "01_onboarding_shown", "onboarding not shown")
    fail_if(
        "无障碍" not in title + expl + top,
        "01_onboarding_shown",
        f"missing why-text title={title!r}",
    )
    fail_if(state.get("serviceEnabled") is True, "01_onboarding_shown", "service already enabled")
    record("01_onboarding_shown", "PASS", f"title={title}")

    tap_center(state.get("openCenter"), "openAccessibilityButton")
    opened = False
    for _ in range(10):
        time.sleep(0.5)
        if in_accessibility_settings():
            opened = True
            break
    screenshot("01_onboarding_settings")
    fail_if(not opened, "01_onboarding_opens_settings", f"not in a11y settings: {current_focus()}")
    record("01_onboarding_opens_settings", "PASS", current_focus().strip()[:100])

    state = {}
    for _ in range(4):
        adb_shell("input keyevent KEYCODE_BACK")
        time.sleep(0.9)
        state = onboarding_state()
        if state.get("visible"):
            break
    if not state.get("visible"):
        launch(f"{PKG}/.ui.MainActivity")
        state = wait_onboarding(8)
    screenshot("01_onboarding_retry")
    fail_if(not state.get("visible"), "01_onboarding_retry", "onboarding missing after back")
    fail_if(
        not state.get("retryVisible") and "再去" not in (state.get("buttonText") or ""),
        "01_onboarding_retry",
        f"retry not shown state={state}",
    )
    record("01_onboarding_retry", "PASS", f"button={state.get('buttonText')}")

    enable_a11y()
    reached_main = False
    for _ in range(20):
        focus = current_focus()
        if "MainActivity" in focus and "OnboardingActivity" not in focus:
            reached_main = True
            break
        time.sleep(0.4)
    if not reached_main:
        # Bring the task forward; onboarding should have continued once the service bound.
        adb_shell(
            f"am start -W -n {PKG}/.ui.MainActivity -f 0x10008000",
            check=False,
        )
        time.sleep(1.2)
    screenshot("01_onboarding_enabled")
    focus = current_focus()
    top = adb_shell(
        "dumpsys activity top | grep -E 'MainActivity|OnboardingActivity|已启用|statusText' | head -40",
        check=False,
    )
    fail_if("OnboardingActivity" in focus, "01_onboarding_continues", "still on onboarding after enable")
    fail_if(
        "MainActivity" not in focus and "MainActivity" not in top,
        "01_onboarding_continues",
        f"main not shown focus={focus}",
    )
    fail_if(not service_bound(), "01_onboarding_continues", "service not bound after enable")
    record("01_onboarding_continues", "PASS", "main after enable")

    # 13 settings, no ANR. dumpsys activity top does not use UiAutomation.
    launch(f"{PKG}/.ui.SettingsActivity")
    time.sleep(2)
    top = adb_shell(
        "dumpsys activity top | grep -E 'SettingsActivity|enableDouyinSwitch|拦截抖音' | head -40",
        check=False,
    )
    screenshot("13_settings")
    fail_if(has_anr(), "13_settings", "ANR dialog")
    fail_if(
        "SettingsActivity" not in top and "SettingsActivity" not in current_focus(),
        "13_settings",
        "settings activity not resumed",
    )
    fail_if(
        "enableDouyinSwitch" not in top and "拦截抖音" not in top,
        "13_settings",
        "settings widgets missing",
    )
    record("13_settings", "PASS", "settings page open, no ANR")

    # 08 open douyin overlay
    ensure_a11y()
    adb_shell(f"am force-stop {DY}", check=False)
    adb_shell(f"am force-stop {XHS}", check=False)
    press_home()
    ensure_a11y()
    launch(f"{DY}/.MainActivity")
    state = wait_overlay(25)
    screenshot("08_passage")
    fail_if(not overlay_visible(state), "08_passage", "overlay not shown on fake douyin")
    source = state.get("sourceView") or (f"《{state.get('source')}》" if state.get("source") else "")
    passage = state.get("passageView") or state.get("passage") or ""
    fail_if("《" not in source, "08_passage", f"no source: {source!r}")
    fail_if(not any(b in source for b in CLASSICS), "08_passage", f"source not classic: {source!r}")
    clean = clean_text(passage)
    fail_if(len(clean) < 20, "08_passage", f"passage too short: {passage!r}")
    record("08_passage", "PASS", f"source={source} len={len(clean)}")

    # Overlay must stay up: addView must not bounce hide/show.
    s1 = overlay_state()
    show0 = int(s1.get("showCount") or 0)
    hide0 = int(s1.get("hideCount") or 0)
    fail_if(show0 < 1, "08_overlay_stable", f"showCount={show0}")
    fail_if(hide0 != 0, "08_overlay_stable", f"overlay already hid after first show hideCount={hide0} showCount={show0}")
    fail_if(show0 > 1, "08_overlay_stable", f"overlay recreated showCount={show0}")
    time.sleep(2.2)
    s2 = overlay_state()
    screenshot("08_overlay_stable")
    fail_if(not s2.get("visible"), "08_overlay_stable", "overlay vanished during hold")
    fail_if(
        int(s2.get("hideCount") or 0) != hide0 or int(s2.get("showCount") or 0) != show0,
        "08_overlay_stable",
        f"churn show {show0}->{s2.get('showCount')} hide {hide0}->{s2.get('hideCount')}",
    )
    record("08_overlay_stable", "PASS", f"held 2s show={show0} hide={hide0}")

    # 09 partial
    typed20 = clean[:20]
    state = set_overlay_text(typed20)
    screenshot("09_partial")
    count = correct_count(state)
    fail_if(count < 15 or count >= 50, "09_partial", f"count={count} counter={state.get('counter')!r}")
    fail_if(submit_enabled(state), "09_partial", "submit enabled too early")
    record("09_partial", "PASS", f"count={count} submit disabled")

    # 10 mismatch
    state = set_overlay_text(typed20 + "错错错xyz")
    screenshot("10_mismatch")
    after = correct_count(state)
    hint = state.get("mismatchHint") or ""
    preview = state.get("matchPreview") or ""
    fail_if(after > count, "10_mismatch", f"wrong chars counted {count}->{after}")
    fail_if("错误" not in hint and not preview, "10_mismatch", f"no highlight hint={hint!r}")
    record("10_mismatch", "PASS", f"count stayed {after}, hint={hint}")
    state = set_overlay_text(typed20)

    # 20 home
    press_home()
    gone = wait_gone(5)
    state = overlay_state()
    screenshot("20_home_free")
    focus = current_focus()
    fail_if(not gone or overlay_visible(state), "20_home_free", "overlay still on launcher")
    fail_if(int(state.get("hideCount") or 0) < 1, "20_home_free", f"home did not hide overlay hideCount={state.get('hideCount')}")
    fail_if("aweme" in focus, "20_home_free", f"still on douyin: {focus}")
    record("20_home_free", "PASS", f"launcher, no overlay, hideCount={state.get('hideCount')} focus={focus.strip()[:80]}")

    # 21 other app
    adb_shell("am start -W -a android.settings.SETTINGS", check=False)
    time.sleep(1.5)
    state = overlay_state()
    screenshot("21_other_app")
    fail_if(overlay_visible(state), "21_other_app", "overlay shown over Settings")
    fail_if("aweme" in current_focus(), "21_other_app", "still on douyin")
    record("21_other_app", "PASS", "system settings, no overlay")

    # 22 resume
    ensure_a11y()
    launch(f"{DY}/.MainActivity")
    state = wait_overlay(15)
    screenshot("22_resume")
    fail_if(not overlay_visible(state), "22_resume", "overlay did not return")
    typed = state.get("typedView") or state.get("typed") or ""
    fail_if(typed20[:8] not in typed and typed != typed20, "22_resume", f"text lost typed={typed!r}")
    record("22_resume", "PASS", f"kept text {typed[:20]!r}")

    # 11 complete
    passage = state.get("passageView") or state.get("passage") or passage
    clean = clean_text(passage)
    fail_if(len(clean) < 50, "11_complete", f"passage < 50: {len(clean)}")
    typed50 = clean[:50]
    state = set_overlay_text(typed50)
    screenshot("11_complete")
    count = correct_count(state)
    fail_if(count < 50, "11_complete", f"count={count} counter={state.get('counter')!r}")
    fail_if(not submit_enabled(state), "11_complete", "submit still disabled")
    record("11_complete", "PASS", f"count={count} submit enabled")

    # 12 real tap submit
    tap_center(state.get("submitCenter"), "submitButton")
    gone = wait_gone(8)
    screenshot("12_after_submit")
    fail_if(not gone, "12_after_submit", "overlay still visible after tap")
    record("12_after_submit", "PASS", "real tap dismissed overlay")

    # 15 no retrigger
    press_home()
    time.sleep(1)
    launch(f"{DY}/.MainActivity")
    time.sleep(4)
    state = overlay_state()
    screenshot("15_no_retrigger")
    fail_if(overlay_visible(state), "15_no_retrigger", "retriggered inside away window")
    record("15_no_retrigger", "PASS", "no overlay on quick return")

    # 16 night trigger
    press_home()
    launch(f"{PKG}/.ui.SettingsActivity")
    time.sleep(1)
    broadcast("com.wechatblocker.DEBUG_NIGHT_TRIGGER")
    time.sleep(1)
    launch(f"{DY}/.MainActivity")
    state = wait_overlay(15)
    screenshot("16_night_trigger")
    fail_if(not overlay_visible(state), "16_night_trigger", "night overlay missing")
    record("16_night_trigger", "PASS", "night overlay shown")
    back = state.get("backCenter")
    if isinstance(back, dict) and int(back.get("x") or 0) > 0:
        tap_center(back, "backButton")
        time.sleep(1)
    press_home()

    # 23 xhs
    adb_shell(f"am force-stop {DY}", check=False)
    adb_shell(f"am force-stop {XHS}", check=False)
    time.sleep(0.5)
    ensure_a11y()
    launch(f"{XHS}/.MainActivity")
    state = wait_overlay(20)
    screenshot("23_xhs_open")
    fail_if(not overlay_visible(state), "23_xhs_open", "no overlay on fake xhs")
    source = state.get("sourceView") or (f"《{state.get('source')}》" if state.get("source") else "")
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
        collect_debug("exception")
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
