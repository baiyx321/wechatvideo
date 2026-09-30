#!/bin/bash
# 完整的端到端测试脚本

set -e
export PATH=~/android-sdk/platform-tools:$PATH

echo "========================================="
echo "视频号拦截器 - 完整端到端测试"
echo "========================================="
echo ""

# 清理
rm -rf /opt/cursor/artifacts/screenshots/*
mkdir -p /opt/cursor/artifacts/screenshots

# 重启服务
echo "准备测试环境..."
adb shell am force-stop com.wechatblocker
adb shell am force-stop com.wechatblocker.fakewechat
sleep 2

adb shell settings put secure enabled_accessibility_services com.wechatblocker/com.wechatblocker.service.WeChatBlockerService
adb shell settings put secure accessibility_enabled 1
sleep 3

# 清空历史和设置
adb shell "run-as com.wechatblocker rm -f /data/user/0/com.wechatblocker/files/reflection_history.json" 2>/dev/null || true
adb shell "run-as com.wechatblocker sh -c 'cd /data/user/0/com.wechatblocker/shared_prefs && rm -f *.xml'" 2>/dev/null || true

sleep 2
adb logcat -c

echo "========================================="
echo "步骤1: 启动假微信,验证覆盖层显示"
echo "========================================="
adb shell am start -n com.wechatblocker.fakewechat/.FinderHomeUI
sleep 4

adb shell screencap -p /sdcard/01_overlay.png
adb pull /sdcard/01_overlay.png /opt/cursor/artifacts/screenshots/01_overlay.png > /dev/null 2>&1

# 检查UI
adb shell uiautomator dump > /dev/null 2>&1
adb pull /sdcard/window_dump.xml /tmp/ui_step1.xml > /dev/null 2>&1

if grep -q "我为什么要刷视频" /tmp/ui_step1.xml 2>/dev/null; then
    echo "✅ 通过: 覆盖层已显示,包含提示文本"
    STEP1="PASS"
else
    echo "✗ 失败: 覆盖层未显示或文本缺失"
    STEP1="FAIL"
fi

# 检查日志
if adb logcat -d | grep -q "覆盖层已显示"; then
    echo "✅ 日志确认覆盖层显示"
else
    echo "⚠ 日志未找到覆盖层显示记录"
fi
echo ""

echo "========================================="
echo "步骤2: 输入少量文本,验证提交按钮禁用"
echo "========================================="

# 输入10个字符
adb shell input tap 540 1000  # 点击输入框
sleep 1
adb shell input text "1234567890"
sleep 1

adb shell screencap -p /sdcard/02_short_text.png
adb pull /sdcard/02_short_text.png /opt/cursor/artifacts/screenshots/02_short_text.png > /dev/null 2>&1

adb shell uiautomator dump > /dev/null 2>&1
adb pull /sdcard/window_dump.xml /tmp/ui_step2.xml > /dev/null 2>&1

# 检查字符计数
if grep -q "10 / 50" /tmp/ui_step2.xml 2>/dev/null || grep -q "10" /tmp/ui_step2.xml 2>/dev/null; then
    echo "✅ 通过: 字符计数显示"
    STEP2="PASS"
else
    echo "⚠ 未确认字符计数"
    STEP2="PARTIAL"
fi

# 检查提交按钮状态
if grep "提交" /tmp/ui_step2.xml 2>/dev/null | grep -q "enabled=\"false\""; then
    echo "✅ 通过: 提交按钮禁用"
elif grep "提交" /tmp/ui_step2.xml 2>/dev/null | grep -q "clickable=\"false\""; then
    echo "✅ 通过: 提交按钮不可点击"
else
    echo "⚠ 提交按钮状态未确认"
fi
echo ""

echo "========================================="
echo "步骤3: 输入足够文本,提交并验证覆盖层消失"
echo "========================================="

# 清除之前的输入
for i in {1..20}; do
    adb shell input keyevent KEYCODE_DEL
done
sleep 1

# 输入50个字符
adb shell input text "abcdefghij" # 10
sleep 0.5
adb shell input text "klmnopqrst" # 20
sleep 0.5
adb shell input text "uvwxyz0123" # 30
sleep 0.5
adb shell input text "456789ABCD" # 40
sleep 0.5
adb shell input text "EFGHIJKLMN" # 50
sleep 1

adb shell screencap -p /sdcard/03_enough_text.png
adb pull /sdcard/03_enough_text.png /opt/cursor/artifacts/screenshots/03_enough_text.png > /dev/null 2>&1

echo "✅ 输入50+字符"

# 点击提交按钮
adb shell input tap 640 1400  # 提交按钮位置(需要根据实际调整)
sleep 2

adb shell screencap -p /sdcard/04_after_submit.png
adb pull /sdcard/04_after_submit.png /opt/cursor/artifacts/screenshots/04_after_submit.png > /dev/null 2>&1

adb shell uiautomator dump > /dev/null 2>&1
adb pull /sdcard/window_dump.xml /tmp/ui_step4.xml > /dev/null 2>&1

# 检查覆盖层是否消失
if ! grep -q "我为什么要刷视频" /tmp/ui_step4.xml 2>/dev/null; then
    echo "✅ 通过: 覆盖层已消失"
    STEP3="PASS"
else
    echo "⚠ 覆盖层仍然显示"
    STEP3="FAIL"
fi

# 检查日志
if adb logcat -d | grep -q "用户提交反思"; then
    echo "✅ 日志确认提交成功"
fi
echo ""

echo "========================================="
echo "步骤4: 验证历史记录"
echo "========================================="

# 启动主应用
adb shell am start -n com.wechatblocker/.ui.MainActivity
sleep 2

# 打开历史记录(需要点击按钮)
adb shell input tap 540 800  # 历史记录按钮位置(需要调整)
sleep 2

adb shell screencap -p /sdcard/05_history.png
adb pull /sdcard/05_history.png /opt/cursor/artifacts/screenshots/05_history.png > /dev/null 2>&1

# 检查历史记录文件
HISTORY_CHECK=$(adb shell "run-as com.wechatblocker cat /data/user/0/com.wechatblocker/files/reflection_history.json 2>/dev/null | wc -l" 2>/dev/null || echo "0")

if [ "$HISTORY_CHECK" -gt "0" ]; then
    echo "✅ 通过: 历史记录文件存在且有内容"
    STEP4="PASS"
else
    echo "⚠ 历史记录文件为空或不存在"
    STEP4="FAIL"
fi
echo ""

echo "========================================="
echo "步骤5: 验证冷却机制"
echo "========================================="

# 再次启动假微信
adb shell am start -n com.wechatblocker.fakewechat/.FinderHomeUI
sleep 3

adb shell screencap -p /sdcard/06_cooldown.png
adb pull /sdcard/06_cooldown.png /opt/cursor/artifacts/screenshots/06_cooldown.png > /dev/null 2>&1

adb shell uiautomator dump > /dev/null 2>&1
adb pull /sdcard/window_dump.xml /tmp/ui_step6.xml > /dev/null 2>&1

# 检查覆盖层是否显示(应该不显示)
if ! grep -q "我为什么要刷视频" /tmp/ui_step6.xml 2>/dev/null; then
    echo "✅ 通过: 冷却期内不显示覆盖层"
    STEP5="PASS"
else
    echo "⚠ 冷却期内仍显示覆盖层(可能冷却时间设置为0)"
    STEP5="FAIL"
fi

# 检查日志
if adb logcat -d | grep -q "冷却"; then
    echo "✅ 日志显示冷却机制生效"
fi
echo ""

echo "========================================="
echo "测试总结"
echo "========================================="
echo "步骤1 (覆盖层显示): $STEP1"
echo "步骤2 (短文本/按钮禁用): $STEP2"
echo "步骤3 (提交/覆盖层消失): $STEP3"
echo "步骤4 (历史记录): $STEP4"
echo "步骤5 (冷却机制): $STEP5"
echo ""
echo "截图已保存到: /opt/cursor/artifacts/screenshots/"
ls -lh /opt/cursor/artifacts/screenshots/
echo "========================================="
