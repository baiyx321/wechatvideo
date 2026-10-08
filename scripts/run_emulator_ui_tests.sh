#!/usr/bin/env bash
# Drive the real app + fake apps on a KVM Android emulator.
# Must not fall back to software emulation.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ART="$ROOT/emulator-ui-artifacts"
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
adb shell getprop sys.boot_completed || true
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
adb install -r -g "$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
adb install -r -g "$ROOT/fakedouyin/build/outputs/apk/debug/fakedouyin-debug.apk"
adb install -r -g "$ROOT/fakewechat/build/outputs/apk/debug/fakewechat-debug.apk"
adb install -r -g "$ROOT/fakexiaohongshu/build/outputs/apk/debug/fakexiaohongshu-debug.apk"

echo "=== optional ADBKeyBoard for Chinese input ==="
ADBKB_APK="/tmp/ADBKeyboard.apk"
if curl -fsSL -o "$ADBKB_APK" "https://github.com/senzhk/ADBKeyBoard/raw/master/ADBKeyboard.apk"; then
  adb install -r -g "$ADBKB_APK" || echo "ADBKeyBoard install failed, continuing"
  adb shell ime enable com.android.adbkeyboard/.AdbIME || true
  adb shell ime set com.android.adbkeyboard/.AdbIME || true
else
  echo "ADBKeyBoard download failed, tests will use ACTION_SET_TEXT"
fi

echo "=== enable accessibility ==="
adb shell settings put secure enabled_accessibility_services \
  com.wechatblocker/com.wechatblocker.service.WeChatBlockerService
adb shell settings put secure accessibility_enabled 1
sleep 2
echo "enabled_accessibility_services=$(adb shell settings get secure enabled_accessibility_services)"
echo "accessibility_enabled=$(adb shell settings get secure accessibility_enabled)"

adb shell mkdir -p /sdcard/Download/blocker-ui
adb logcat -c || true

echo "=== run OverlayUiTest ==="
set +e
adb shell am instrument -w -r \
  -e debug false \
  -e timeout_msec 180000 \
  -e class com.wechatblocker.OverlayUiTest \
  com.wechatblocker.test/androidx.test.runner.AndroidJUnitRunner \
  | tee "$ART/instrument.txt"
INSTR_STATUS=${PIPESTATUS[0]}
set -e

echo "=== collect screenshots and logcat ==="
adb pull /sdcard/Download/blocker-ui "$ART/screenshots" || true
adb logcat -d -v time > "$ART/logcat.txt" || true
adb shell dumpsys accessibility > "$ART/dumpsys-accessibility.txt" || true

echo "instrument exit=$INSTR_STATUS"
ls -la "$ART/screenshots" || true
# flatten if adb pull created a nested dir
if [ -d "$ART/screenshots/blocker-ui" ]; then
  mv "$ART/screenshots/blocker-ui/"* "$ART/screenshots/" 2>/dev/null || true
fi

if [ "$INSTR_STATUS" -ne 0 ]; then
  echo "UI tests failed"
  exit "$INSTR_STATUS"
fi

if ! grep -q "OK (" "$ART/instrument.txt"; then
  echo "instrument output missing OK"
  exit 1
fi

echo "UI tests passed"
