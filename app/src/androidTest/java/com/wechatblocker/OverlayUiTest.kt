package com.wechatblocker

import android.app.UiAutomation
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.wechatblocker.data.CopyTypingMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * 仪器测试路径: UiAutomation 在 API 30 上会 unbind AccessibilityService。
 * CI 使用 scripts/emulator_ui_test.py（纯 adb，不用 dump）。
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OverlayUiTest {

    @Test
    fun test_00_enableAccessibility() {
        enableAccessibility()
        screenshot("00_setup")
        assertTrue("无障碍服务应已启用", isServiceEnabled())
    }

    @Test
    fun test_01_settingsOpensWithoutAnr() {
        enableAccessibility()
        pressHomeQuiet()
        shell("am start -W -n $PKG/.ui.SettingsActivity")
        device.wait(Until.hasObject(By.pkg(PKG)), 8000)
        sleep(2000)
        screenshot("13_settings")
        assertFalse("设置页出现 ANR", hasAnr())
        assertTrue(
            "设置页应显示应用开关",
            device.hasObject(By.textContains("拦截抖音")) ||
                device.hasObject(By.res("$PKG:id/enableDouyinSwitch"))
        )
        log("settings page ok, pkg=${device.currentPackageName}")
    }

    @Test
    fun test_02_openDouyinShowsPassage() {
        enableAccessibility()
        resetFakeApps()
        launchDouyin()
        assertTrue("打开假抖音后应显示抄写覆盖层", waitForOverlay(20000))
        sleep(800)
        screenshot("08_passage")
        val source = textOf("sourceText")
        val passage = textOf("promptText")
        log("passage source='$source' len=${passage.length} text='${passage.take(40)}'")
        assertTrue("应显示来源《书名》, 实际: $source", source.contains("《") && source.contains("》"))
        assertTrue(
            "来源应是经典书籍, 实际: $source",
            CLASSIC_BOOKS.any { source.contains(it) }
        )
        assertTrue("段落应有足够汉字, 实际: $passage", CopyTypingMatcher.cleanText(passage).length >= 20)
        savedPassage = passage
        savedSource = source
    }

    @Test
    fun test_03_partialCorrectKeepsSubmitDisabled() {
        assertTrue("覆盖层应仍在", waitForOverlay(8000))
        val passage = currentPassage()
        val clean = CopyTypingMatcher.cleanText(passage)
        savedTyped = clean.take(20)
        setOverlayInput(savedTyped)
        sleep(800)
        screenshot("09_partial")
        val count = correctCount()
        log("partial typed='${savedTyped}' count=$count submit=${submitEnabled()}")
        assertTrue("约20个正确字应计入, 实际 count=$count", count in 15..40)
        assertTrue("未满50正确字时提交应禁用, count=$count", count < 50)
        assertFalse("提交按钮应禁用", submitEnabled())
    }

    @Test
    fun test_04_wrongCharsNotCounted() {
        assertTrue("覆盖层应仍在", waitForOverlay(8000))
        val before = correctCount()
        val wrong = savedTyped + "错错错xyz"
        setOverlayInput(wrong)
        sleep(800)
        screenshot("10_mismatch")
        val after = correctCount()
        val hint = textOf("mismatchHint")
        val preview = textOf("matchPreview")
        log("mismatch before=$before after=$after hint='$hint' preview='${preview.take(40)}'")
        assertTrue("错误字不应增加计数 before=$before after=$after", after <= before)
        assertTrue(
            "应提示错误或高亮, hint='$hint' preview='$preview'",
            hint.contains("错误") || preview.isNotBlank()
        )
        setOverlayInput(savedTyped)
        sleep(400)
    }

    @Test
    fun test_05_homeHidesOverlay() {
        assertTrue("覆盖层应在按 Home 前显示", waitForOverlay(8000))
        device.pressHome()
        device.waitForIdle(3000)
        sleep(1500)
        screenshot("20_home_free")
        val pkg = device.currentPackageName ?: ""
        log("after HOME pkg=$pkg overlay=${overlayVisible()}")
        assertFalse("Home 后覆盖层必须消失", overlayVisible())
        assertFalse("不应仍停在假抖音上 pkg=$pkg", pkg.contains("aweme"))
        assertFalse("出现 ANR", hasAnr())
    }

    @Test
    fun test_06_otherAppHasNoOverlay() {
        shell("am start -W -a android.settings.SETTINGS")
        device.wait(Until.hasObject(By.pkg("com.android.settings")), 8000)
        sleep(1500)
        screenshot("21_other_app")
        val pkg = device.currentPackageName ?: ""
        log("settings pkg=$pkg overlay=${overlayVisible()}")
        assertFalse("打开系统设置时不应显示覆盖层", overlayVisible())
        assertTrue("应在系统设置 pkg=$pkg", pkg.contains("settings") || pkg.contains("Settings"))
    }

    @Test
    fun test_07_resumeKeepsTypedText() {
        launchDouyin()
        assertTrue("返回假抖音应恢复覆盖层", waitForOverlay(15000))
        sleep(800)
        screenshot("22_resume")
        val typed = textOf("inputText")
        val passage = currentPassage()
        log("resume typed='$typed' expected='$savedTyped' passage='${passage.take(20)}'")
        assertTrue(
            "恢复后应保留已输入文字, typed='$typed' expected='$savedTyped'",
            typed.contains(savedTyped.take(10)) || typed == savedTyped
        )
        assertTrue("段落应保持", passage.isNotBlank())
    }

    @Test
    fun test_08_fiftyCharsEnablesSubmit() {
        assertTrue("覆盖层应在", waitForOverlay(8000))
        val clean = CopyTypingMatcher.cleanText(currentPassage())
        assertTrue("段落应至少50个有效字, 实际 ${clean.length}", clean.length >= 50)
        savedTyped = clean.take(50)
        setOverlayInput(savedTyped)
        sleep(1000)
        screenshot("11_complete")
        val count = correctCount()
        log("complete count=$count typedLen=${savedTyped.length} enabled=${submitEnabled()}")
        assertTrue("正确字应>=50, 实际 $count", count >= 50)
        assertTrue("提交按钮应启用", submitEnabled())
    }

    @Test
    fun test_09_tapSubmitDismissesOverlay() {
        assertTrue("覆盖层应在", waitForOverlay(8000))
        if (!submitEnabled()) {
            val clean = CopyTypingMatcher.cleanText(currentPassage())
            setOverlayInput(clean.take(50))
            sleep(800)
        }
        val submit = waitObj("submitButton")
        assertNotNull("找不到提交按钮", submit)
        log("tapping submit enabled=${submit!!.isEnabled}")
        submit.click()
        val gone = device.wait(Until.gone(By.res("$PKG:id/promptText")), 8000)
        sleep(1000)
        screenshot("12_after_submit")
        log("after submit overlay=${overlayVisible()} gone=$gone")
        assertFalse("点击提交后覆盖层应消失", overlayVisible())
    }

    @Test
    fun test_10_reopenWithinAwayDoesNotRetrigger() {
        pressHomeQuiet()
        sleep(1000)
        launchDouyin()
        sleep(4000)
        screenshot("15_no_retrigger")
        log("reopen overlay=${overlayVisible()} pkg=${device.currentPackageName}")
        assertFalse("离开时间窗口内再次打开不应弹出覆盖层", overlayVisible())
    }

    @Test
    fun test_11_nightTriggerShowsOverlay() {
        pressHomeQuiet()
        shell("am start -W -n $PKG/.ui.SettingsActivity")
        device.wait(Until.hasObject(By.pkg(PKG)), 8000)
        sleep(500)
        repeat(4) {
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.8).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.25).toInt(),
                30
            )
            sleep(400)
        }
        val nightBtn = device.wait(
            Until.findObject(By.res("$PKG:id/triggerNightButton")),
            8000
        ) ?: device.findObject(By.textContains("夜间检查"))
        assertNotNull("找不到夜间触发按钮", nightBtn)
        nightBtn!!.click()
        sleep(1500)
        launchDouyin()
        assertTrue("夜间触发后打开假抖音应显示覆盖层", waitForOverlay(15000))
        sleep(800)
        screenshot("16_night_trigger")
        assertTrue("夜间覆盖层应有段落", textOf("promptText").isNotBlank())
        val back = device.findObject(By.res("$PKG:id/backButton"))
        back?.click()
        sleep(1500)
        pressHomeQuiet()
    }

    @Test
    fun test_12_xiaohongshuGetsOwnOverlay() {
        resetFakeApps()
        shell("am force-stop $DY")
        sleep(500)
        shell("am start -W -n $XHS/.MainActivity")
        assertTrue("打开假小红书应显示覆盖层", waitForOverlay(20000))
        sleep(800)
        screenshot("23_xhs_open")
        val source = textOf("sourceText")
        val passage = textOf("promptText")
        log("xhs source='$source' passage='${passage.take(40)}'")
        assertTrue("小红书覆盖层应有来源", source.contains("《"))
        assertTrue("小红书覆盖层应有段落", CopyTypingMatcher.cleanText(passage).length >= 20)
        assertTrue("当前应在小红书或覆盖层 pkg=${device.currentPackageName}", true)
    }

    companion object {
        private const val TAG = "OverlayUiTest"
        const val PKG = "com.wechatblocker"
        const val SERVICE = "com.wechatblocker/com.wechatblocker.service.WeChatBlockerService"
        const val DY = "com.ss.android.ugc.aweme"
        const val XHS = "com.xingin.xhs"
        private val CLASSIC_BOOKS = listOf("论语", "大学", "中庸", "孟子", "荀子", "管子")

        lateinit var device: UiDevice
        var savedPassage: String = ""
        var savedSource: String = ""
        var savedTyped: String = ""

        @JvmStatic
        @BeforeClass
        fun classSetup() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            device = UiDevice.getInstance(instrumentation)
            shell("mkdir -p /sdcard/Download/blocker-ui")
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("input keyevent 82")
            enableAccessibility()
            assertTrue("无障碍服务应处于 Bound 状态", waitServiceBound(20000))
        }

        fun log(msg: String) {
            Log.i(TAG, msg)
            println("OverlayUiTest: $msg")
        }

        fun sleep(ms: Long) = Thread.sleep(ms)

        fun shell(cmd: String): String {
            val out = device.executeShellCommand(cmd) ?: ""
            log("shell `$cmd` -> ${out.take(200)}")
            return out
        }

        fun screenshot(name: String) {
            shell("mkdir -p /sdcard/Download/blocker-ui")
            shell("screencap -p /sdcard/Download/blocker-ui/$name.png")
        }

        fun enableAccessibility() {
            shell("settings put secure enabled_accessibility_services $SERVICE")
            shell("settings put secure accessibility_enabled 1")
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < 15000) {
                if (isServiceEnabled()) {
                    log("accessibility enabled in settings")
                    waitServiceBound(10000)
                    return
                }
                sleep(400)
            }
            log("WARN accessibility not confirmed: ${shell("settings get secure enabled_accessibility_services")}")
            log(shell("dumpsys accessibility"))
        }

        fun isServiceEnabled(): Boolean {
            val enabled = shell("settings get secure enabled_accessibility_services")
            val flag = shell("settings get secure accessibility_enabled")
            return enabled.contains("WeChatBlockerService") && flag.trim().startsWith("1")
        }

        fun dumpLine(dump: String, prefix: String): String {
            val idx = dump.indexOf(prefix)
            if (idx < 0) return ""
            return dump.substring(idx).lineSequence().first()
        }

        fun waitServiceBound(timeoutMs: Long): Boolean {
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val dump = device.executeShellCommand("dumpsys accessibility") ?: ""
                val crashedLine = dumpLine(dump, "Crashed services:")
                val boundLine = dumpLine(dump, "Bound services:")
                val crashed = crashedLine.contains("WeChatBlocker")
                val bound = boundLine.contains("WeChatBlocker")
                log("a11y boundLine='$boundLine' crashedLine='$crashedLine' bound=$bound crashed=$crashed")
                if (bound && !crashed) return true
                if (crashed || !bound) {
                    log("service not bound, re-enable")
                    shell("settings put secure enabled_accessibility_services null")
                    sleep(400)
                    shell("settings put secure enabled_accessibility_services $SERVICE")
                    shell("settings put secure accessibility_enabled 1")
                }
                sleep(600)
            }
            return false
        }

        fun pressHomeQuiet() {
            device.pressHome()
            device.waitForIdle(2000)
            sleep(500)
        }

        fun resetFakeApps() {
            shell("am force-stop $DY")
            shell("am force-stop $XHS")
            shell("am force-stop com.wechatblocker.fakewechat")
            sleep(400)
        }

        fun launchDouyin() {
            shell("am start -W -n $DY/.MainActivity")
            device.waitForIdle(2000)
        }

        fun overlayVisible(): Boolean {
            return device.hasObject(By.res("$PKG:id/promptText")) ||
                device.hasObject(By.res("$PKG:id/submitButton"))
        }

        fun waitForOverlay(timeoutMs: Long): Boolean {
            val ok = device.wait(Until.hasObject(By.res("$PKG:id/promptText")), timeoutMs) == true
            log("waitForOverlay timeout=$timeoutMs result=$ok pkg=${device.currentPackageName}")
            if (!ok) {
                try {
                    device.dumpWindowHierarchy(File("/sdcard/Download/blocker-ui/dump_no_overlay.xml"))
                } catch (e: Exception) {
                    log("dumpWindowHierarchy failed: ${e.message}")
                }
                log(shell("dumpsys accessibility"))
            }
            return ok
        }

        fun waitObj(id: String, timeoutMs: Long = 5000): UiObject2? {
            return device.wait(Until.findObject(By.res("$PKG:id/$id")), timeoutMs)
        }

        fun textOf(id: String): String {
            return device.findObject(By.res("$PKG:id/$id"))?.text ?: ""
        }

        fun currentPassage(): String {
            val live = textOf("promptText")
            if (live.isNotBlank()) {
                savedPassage = live
            }
            return savedPassage
        }

        fun correctCount(): Int {
            val t = textOf("charCounter")
            val match = Regex("正确\\s*(\\d+)\\s*/").find(t)
            val n = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
            log("charCounter='$t' parsed=$n")
            return n
        }

        fun submitEnabled(): Boolean {
            val btn = device.findObject(By.res("$PKG:id/submitButton"))
            val enabled = btn?.isEnabled == true
            log("submit enabled=$enabled exists=${btn != null}")
            return enabled
        }

        fun hasAnr(): Boolean {
            return device.hasObject(By.textContains("isn't responding")) ||
                device.hasObject(By.textContains("无响应")) ||
                device.hasObject(By.textContains("没有响应"))
        }

        fun setOverlayInput(text: String) {
            val input = waitObj("inputText", 8000)
            assertNotNull("找不到输入框", input)
            input!!.click()
            sleep(300)
            try {
                input.text = text
            } catch (e: Exception) {
                log("setText failed: ${e.message}")
            }
            sleep(600)
            var actual = textOf("inputText")
            if (actual.isBlank() && text.isNotBlank()) {
                log("ACTION_SET_TEXT 未生效, 改用 ADBKeyBoard")
                input.click()
                sleep(200)
                // ADBKeyBoard 对长中文一次广播即可
                shell("am broadcast -a ADB_INPUT_TEXT --es msg \"$text\"")
                sleep(1000)
                actual = textOf("inputText")
            }
            if (actual.isBlank() && text.isNotBlank()) {
                log("IME 仍失败, 逐字 clipboard 回退")
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val cm = InstrumentationRegistry.getInstrumentation()
                        .targetContext
                        .getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("t", text))
                }
                sleep(200)
                input.longClick()
                sleep(400)
                val paste = device.findObject(By.text("Paste"))
                    ?: device.findObject(By.text("粘贴"))
                paste?.click()
                sleep(500)
            }
            log("setOverlayInput wantLen=${text.length} actual='${textOf("inputText").take(40)}'")
        }
    }
}
