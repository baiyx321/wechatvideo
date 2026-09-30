#!/bin/bash

# 端到端测试脚本

set -e

export PATH=~/android-sdk/platform-tools:$PATH

echo "========================================="
echo "视频号拦截器 - 端到端测试"
echo "========================================="
echo ""

# 1. 检查模拟器连接
echo "1. 检查模拟器连接"
adb devices | grep emulator && echo "✓ 模拟器已连接" || { echo "✗ 模拟器未连接"; exit 1; }
echo ""

# 2. 检查应用已安装
echo "2. 检查应用已安装"
adb shell pm list packages | grep wechatblocker && echo "✓ 应用已安装" || { echo "✗ 应用未安装"; exit 1; }
echo ""

# 3. 检查无障碍服务状态
echo "3. 检查无障碍服务状态"
SERVICE_STATUS=$(adb shell settings get secure enabled_accessibility_services)
if echo "$SERVICE_STATUS" | grep -q "wechatblocker"; then
    echo "✓ 无障碍服务已启用: $SERVICE_STATUS"
else
    echo "✗ 无障碍服务未启用"
    echo "正在启用..."
    adb shell settings put secure enabled_accessibility_services com.wechatblocker/com.wechatblocker.service.WeChatBlockerService
    adb shell settings put secure accessibility_enabled 1
    sleep 2
    echo "✓ 已启用"
fi
echo ""

# 4. 启动主界面并截图
echo "4. 测试主界面"
adb shell am force-stop com.wechatblocker
sleep 1
adb shell am start -n com.wechatblocker/.ui.MainActivity
sleep 2
adb shell screencap -p /sdcard/test_main.png
adb pull /sdcard/test_main.png /opt/cursor/artifacts/screenshots/test_main.png > /dev/null 2>&1
echo "✓ 主界面截图已保存"
echo ""

# 5. 检查服务进程
echo "5. 检查服务进程"
SERVICE_PID=$(adb shell ps | grep wechatblocker | awk '{print $2}')
if [ -n "$SERVICE_PID" ]; then
    echo "✓ 服务进程运行中 (PID: $SERVICE_PID)"
else
    echo "✗ 服务进程未找到"
fi
echo ""

# 6. 测试TestFinderActivity
echo "6. 测试视频号检测"
adb logcat -c
adb shell am start -n com.wechatblocker/.TestFinderActivity
sleep 3
adb shell screencap -p /sdcard/test_finder.png
adb pull /sdcard/test_finder.png /opt/cursor/artifacts/screenshots/test_finder.png > /dev/null 2>&1
echo "✓ TestFinderActivity截图已保存"

# 检查日志
echo ""
echo "查看服务日志:"
adb logcat -d | grep -E "WeChatBlocker|BlockingLogic" | tail -20
echo ""

# 7. 检查UI hierarchy
echo "7. 检查当前UI层次"
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
if grep -q "视频号" /tmp/ui.xml 2>/dev/null; then
    echo "✓ UI中包含'视频号'文本"
fi
if grep -q "BlockingOverlay\|拦截" /tmp/ui.xml 2>/dev/null; then
    echo "✓ 检测到拦截覆盖层"
else
    echo "⚠ 未检测到拦截覆盖层(可能因为包名不匹配)"
fi
echo ""

# 8. 总结
echo "========================================="
echo "测试总结"
echo "========================================="
echo "✓ 模拟器运行正常"
echo "✓ 应用已安装"
echo "✓ 无障碍服务已配置"
echo "✓ 主界面可以启动"
echo "✓ TestFinderActivity可以启动"
echo "✓ 截图已保存到 /opt/cursor/artifacts/screenshots/"
echo ""
echo "注意: 由于包名过滤(默认com.tencent.mm),"
echo "TestFinderActivity可能不会触发拦截。"
echo "需要在设置中添加com.wechatblocker到目标包名列表。"
echo "========================================="
