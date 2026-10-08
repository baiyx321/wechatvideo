#!/usr/bin/env bash
# Drive the real app + fake apps on a KVM Android emulator via adb.
# Must not use Instrumentation/UiAutomation (it unbinds the AccessibilityService).
# Must not fall back to software emulation.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export EMU_UI_ART="$ROOT/emulator-ui-artifacts"
ART="$EMU_UI_ART"
mkdir -p "$ART/screenshots"

echo "=== host arch ==="
uname -m
if [ "$(uname -m)" != "x86_64" ]; then
  echo "Need x64 runner for KVM Android emulator"
  exit 1
fi

echo "=== KVM hard check ==="
ls -l /dev/kvm || true
if [ ! -w /dev/kvm ]; then
  echo "KVM is not writable; refusing software emulation"
  exit 1
fi

export PATH="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}/platform-tools:$PATH"

echo "=== devices ==="
adb devices -l
adb wait-for-device
for i in $(seq 1 60); do
  if [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; then
    echo "boot_completed=1"
    break
  fi
  sleep 2
done

adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
adb shell settings put global stay_on_while_plugged_in 3 || true
adb shell svc power stayon true || true

echo "=== install APKs ==="
adb install -r -g "$ROOT/app/build/outputs/apk/debug/app-debug.apk"
adb install -r -g "$ROOT/fakedouyin/build/outputs/apk/debug/fakedouyin-debug.apk"
adb install -r -g "$ROOT/fakewechat/build/outputs/apk/debug/fakewechat-debug.apk"
adb install -r -g "$ROOT/fakexiaohongshu/build/outputs/apk/debug/fakexiaohongshu-debug.apk"

echo "=== optional ADBKeyBoard ==="
ADBKB_APK="/tmp/ADBKeyboard.apk"
if curl -fsSL -L -o "$ADBKB_APK" "https://github.com/senzhk/ADBKeyBoard/raw/master/ADBKeyboard.apk"; then
  adb install -r -g "$ADBKB_APK" || echo "ADBKeyBoard install failed, continuing"
  adb shell ime list -a || true
  adb shell ime enable com.android.adbkeyboard/.AdbIME || true
  adb shell ime set com.android.adbkeyboard/.AdbIME || true
else
  echo "ADBKeyBoard download failed; tests use debug broadcast for Chinese"
fi

echo "=== enable accessibility ==="
adb shell settings put secure enabled_accessibility_services \
  com.wechatblocker/com.wechatblocker.service.WeChatBlockerService
adb shell settings put secure accessibility_enabled 1
sleep 2
adb shell dumpsys accessibility | head -20 || true

adb shell mkdir -p /sdcard/Download/blocker-ui
adb logcat -c || true

echo "=== run adb UI scenarios (no Instrumentation) ==="
set +e
python3 "$ROOT/scripts/emulator_ui_test.py"
PY_STATUS=$?
set -e

echo "=== collect screenshots and logcat ==="
adb pull /sdcard/Download/blocker-ui "$ART/screenshots" || true
adb logcat -d -v time > "$ART/logcat.txt" || true
adb shell dumpsys accessibility > "$ART/dumpsys-accessibility.txt" || true
adb shell dumpsys window windows > "$ART/dumpsys-window.txt" || true
adb shell run-as com.wechatblocker cat files/overlay_state.json > "$ART/overlay_state.json" || true
adb pull /sdcard/Android/data/com.wechatblocker/files/overlay_state.json "$ART/overlay_state.json" || true

if [ -d "$ART/screenshots/blocker-ui" ]; then
  mv "$ART/screenshots/blocker-ui/"* "$ART/screenshots/" 2>/dev/null || true
  rmdir "$ART/screenshots/blocker-ui" 2>/dev/null || true
fi

ls -la "$ART/screenshots" || true
echo "python exit=$PY_STATUS"
if [ "$PY_STATUS" -ne 0 ]; then
  echo "UI tests failed"
  exit "$PY_STATUS"
fi
if [ ! -f "$ART/results.txt" ] || grep -q '^FAIL' "$ART/results.txt"; then
  echo "results missing or contain FAIL"
  cat "$ART/results.txt" || true
  exit 1
fi
echo "UI tests passed"
cat "$ART/results.txt"
