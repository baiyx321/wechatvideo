package com.wechatblocker.util

import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySettingsLauncherTest {

    private val pkg = "com.wechatblocker"
    private val cls = "com.wechatblocker.service.WeChatBlockerService"
    private val flatten = "$pkg/$cls"

    @Test
    fun listedWhenExactComponentPresent() {
        assertTrue(AccessibilitySettingsLauncher.isServiceListed(flatten, pkg, cls))
    }

    @Test
    fun listedInColonSeparatedList() {
        val enabled = "com.other/.Other:$flatten:com.foo/.Bar"
        assertTrue(AccessibilitySettingsLauncher.isServiceListed(enabled, pkg, cls))
    }

    @Test
    fun listedWhenShortComponentName() {
        assertTrue(
            AccessibilitySettingsLauncher.isServiceListed(
                "com.wechatblocker/.service.WeChatBlockerService",
                pkg,
                cls
            )
        )
    }

    @Test
    fun notListedWhenEmptyOrUnrelated() {
        assertFalse(AccessibilitySettingsLauncher.isServiceListed(null, pkg, cls))
        assertFalse(AccessibilitySettingsLauncher.isServiceListed("", pkg, cls))
        assertFalse(AccessibilitySettingsLauncher.isServiceListed("null", pkg, cls))
        assertFalse(AccessibilitySettingsLauncher.isServiceListed("com.other/.Svc", pkg, cls))
    }

    @Test
    fun api31OpensOfficialDetailsScreen() {
        val target = AccessibilitySettingsLauncher.resolveTarget(31, flatten)
        assertEquals(AccessibilitySettingsLauncher.ACTION_DETAILS, target.action)
        assertTrue(target.useComponentExtra)
        assertNull(target.highlightKey)
    }

    @Test
    fun api30HighlightsOurServiceInAccessibilityList() {
        val target = AccessibilitySettingsLauncher.resolveTarget(30, flatten)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, target.action)
        assertEquals(flatten, target.highlightKey)
        assertFalse(target.useComponentExtra)
    }
}
