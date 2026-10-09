#!/bin/bash
# 详细的手动测试脚本

set -e
export PATH=~/android-sdk/platform-tools:$PATH

echo "========================================="
echo "手动测试 - 每步确认"
echo "========================================="

# 重新构建并安装
echo "1. 重新构建APK..."
cd /workspace
export ANDROID_HOME=~/android-sdk
./gradlew :app:assembleDebug > /dev/null 2>&1
echo "✓ 构建完成"

echo "2. 安装APK..."
adb install -r /workspace/app/build/outputs/apk/debug/app-debug.apk > /dev/null 2>&1
echo "✓ 安装完成"

# 清理环境
echo "3. 清理环境..."
rm -rf /opt/cursor/artifacts/screenshots/*
mkdir -p /opt/cursor/artifacts/screenshots
adb shell am force-stop com.wechatblocker
adb shell am force-stop com.wechatblocker.fakewechat
sleep 2

# 清空历史
adb shell "run-as com.wechatblocker rm -f /data/user/0/com.wechatblocker/files/reflection_history.json" 2>/dev/null || true

# 启用服务
echo "4. 启用无障碍服务..."
adb shell settings put secure enabled_accessibility_services com.wechatblocker/com.wechatblocker.service.WeChatBlockerService
adb shell settings put secure accessibility_enabled 1
sleep 3
echo "✓ 服务已启用"

adb logcat -c

echo ""
echo "========================================="
echo "步骤1: 启动假微信,查看覆盖层"
echo "========================================="
read -p "按Enter继续..."

adb shell am start -n com.wechatblocker.fakewechat/.FinderHomeUI
sleep 4

adb shell screencap -p /sdcard/01.png
adb pull /sdcard/01.png /opt/cursor/artifacts/screenshots/01_overlay.png > /dev/null 2>&1

echo "截图保存: 01_overlay.png ($(ls -lh /opt/cursor/artifacts/screenshots/01_overlay.png | awk '{print $5}'))"
echo "应该看到: 黑色半透明覆盖层,白色卡片,提示文本,输入框,字符计数,返回和提交按钮"

echo ""
echo "========================================="
echo "步骤2: 输入10个字符"
echo "========================================="
read -p "按Enter继续..."

# 点击输入框中心 (假设1080x2340屏幕,覆盖层在中心)
adb shell input tap 540 1170
sleep 1

# 输入10个字符
adb shell input text "1234567890"
sleep 2

adb shell screencap -p /sdcard/02.png
adb pull /sdcard/02.png /opt/cursor/artifacts/screenshots/02_short_text.png > /dev/null 2>&1

echo "截图保存: 02_short_text.png ($(ls -lh /opt/cursor/artifacts/screenshots/02_short_text.png | awk '{print $5}'))"
echo "应该看到: 输入框中有'1234567890',字符计数'10 / 50',提交按钮灰色disabled"

echo ""
echo "========================================="
echo "步骤3: 输入足够字符(50+)"
echo "========================================="
read -p "按Enter继续..."

# 清除之前的输入
for i in {1..15}; do
    adb shell input keyevent KEYCODE_DEL
done
sleep 1

# 输入52个字符
adb shell input text "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMN"
sleep 2

adb shell screencap -p /sdcard/03.png
adb pull /sdcard/03.png /opt/cursor/artifacts/screenshots/03_enough_text.png > /dev/null 2>&1

echo "截图保存: 03_enough_text.png ($(ls -lh /opt/cursor/artifacts/screenshots/03_enough_text.png | awk '{print $5}'))"
echo "应该看到: 输入框中有52个字符,字符计数'52 / 50',提交按钮绿色enabled"

echo ""
echo "========================================="
echo "步骤4: 点击提交按钮"
echo "========================================="
read -p "按Enter继续..."

# 提交按钮位置 (覆盖层下方,右侧)
# 假设覆盖层高度约900px,按钮在底部,右侧按钮中心约810
adb shell input tap 810 1470
sleep 3

adb shell screencap -p /sdcard/04.png
adb pull /sdcard/04.png /opt/cursor/artifacts/screenshots/04_after_submit.png > /dev/null 2>&1

echo "截图保存: 04_after_submit.png ($(ls -lh /opt/cursor/artifacts/screenshots/04_after_submit.png | awk '{print $5}'))"
echo "应该看到: 假微信页面(视频号/关注/朋友/推荐),覆盖层已消失"

# 检查日志
echo ""
echo "=== 检查日志 ==="
if adb logcat -d | grep -q "用户提交反思"; then
    echo "✓ 日志确认: 找到'用户提交反思'"
else
    echo "✗ 日志: 未找到提交记录"
fi

# 检查历史文件
echo ""
echo "=== 检查历史文件 ==="
if adb shell "run-as com.wechatblocker test -f /data/user/0/com.wechatblocker/files/reflection_history.json && echo exists" 2>/dev/null | grep -q exists; then
    echo "✓ 历史文件已创建"
    CONTENT=$(adb shell "run-as com.wechatblocker cat /data/user/0/com.wechatblocker/files/reflection_history.json" 2>/dev/null | head -3)
    echo "内容预览: $CONTENT"
else
    echo "✗ 历史文件不存在"
fi

echo ""
echo "========================================="
echo "步骤5: 打开历史记录页面"
echo "========================================="
read -p "按Enter继续..."

adb shell am start -n com.wechatblocker/.ui.HistoryActivity
sleep 2

adb shell screencap -p /sdcard/05.png
adb pull /sdcard/05.png /opt/cursor/artifacts/screenshots/05_history.png > /dev/null 2>&1

echo "截图保存: 05_history.png ($(ls -lh /opt/cursor/artifacts/screenshots/05_history.png | awk '{print $5}'))"
echo "应该看到: 历史记录列表,包含刚才提交的内容和时间戳"

echo ""
echo "========================================="
echo "步骤6: 再次启动假微信验证冷却"
echo "========================================="
read -p "按Enter继续..."

adb shell am start -n com.wechatblocker.fakewechat/.FinderHomeUI
sleep 3

adb shell screencap -p /sdcard/06.png
adb pull /sdcard/06.png /opt/cursor/artifacts/screenshots/06_cooldown.png > /dev/null 2>&1

echo "截图保存: 06_cooldown.png ($(ls -lh /opt/cursor/artifacts/screenshots/06_cooldown.png | awk '{print $5}'))"
echo "应该看到: 假微信页面,无覆盖层(冷却期内)"

# 检查日志
echo ""
echo "=== 检查冷却日志 ==="
if adb logcat -d | tail -50 | grep -q "冷却"; then
    echo "✓ 日志确认: 找到冷却相关信息"
else
    echo "⚠ 日志: 未找到明确的冷却记录(可能判断逻辑不输出日志)"
fi

echo ""
echo "========================================="
echo "测试完成!"
echo "========================================="
echo "所有截图:"
ls -lh /opt/cursor/artifacts/screenshots/
echo ""
echo "复制APK到artifacts:"
cp /workspace/app/build/outputs/apk/debug/app-debug.apk /opt/cursor/artifacts/wechatvideo-debug.apk
echo "✓ $(ls -lh /opt/cursor/artifacts/wechatvideo-debug.apk | awk '{print $9 " (" $5 ")"}')"
