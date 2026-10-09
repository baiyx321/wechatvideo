package com.wechatblocker.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEventPolicyTest {

    private val own = "com.wechatblocker"
    private val ownUi = listOf("MainActivity", "OnboardingActivity", "SettingsActivity")

    @Test
    fun overlayFrameLayoutFromOwnPackageIsNoise() {
        assertTrue(
            OverlayEventPolicy.isNoise(
                own,
                "android.widget.FrameLayout",
                own,
                ownUi
            )
        )
    }

    @Test
    fun ownSettingsActivityIsNotNoise() {
        assertFalse(
            OverlayEventPolicy.isNoise(
                own,
                "com.wechatblocker.ui.SettingsActivity",
                own,
                ownUi
            )
        )
    }

    @Test
    fun androidPackageAndWidgetWindowsAreTransient() {
        assertTrue(OverlayEventPolicy.isTransientWindow("android", "android.widget.FrameLayout"))
        assertTrue(OverlayEventPolicy.isTransientWindow("com.ss.android.ugc.aweme", "android.widget.FrameLayout"))
        assertFalse(
            OverlayEventPolicy.isTransientWindow(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.MainActivity"
            )
        )
    }

    @Test
    fun launcherActivityIsImmediateLeaveButWidgetIsNot() {
        assertTrue(
            OverlayEventPolicy.isLauncherOrRecents(
                "com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity"
            )
        )
        assertFalse(
            OverlayEventPolicy.isLauncherOrRecents(
                "com.google.android.apps.nexuslauncher",
                "android.widget.FrameLayout"
            )
        )
        assertTrue(
            OverlayEventPolicy.isLauncherPackage("com.google.android.apps.nexuslauncher")
        )
        assertTrue(
            OverlayEventPolicy.isLauncherPackage("com.android.launcher3")
        )
        assertFalse(
            OverlayEventPolicy.isLauncherPackage("com.ss.android.ugc.aweme")
        )
    }

    @Test
    fun ignoreLeaveFromTransientEventRightAfterShow() {
        assertTrue(
            OverlayEventPolicy.shouldIgnoreLeaveWhileOverlayShowing(
                "android",
                "android.widget.FrameLayout",
                "com.ss.android.ugc.aweme",
                true
            )
        )
        assertTrue(
            OverlayEventPolicy.shouldIgnoreLeaveWhileOverlayShowing(
                "com.google.android.googlequicksearchbox",
                "android.widget.FrameLayout",
                "com.ss.android.ugc.aweme",
                true
            )
        )
        assertFalse(
            OverlayEventPolicy.shouldIgnoreLeaveWhileOverlayShowing(
                "com.google.android.apps.nexuslauncher",
                "com.google.android.apps.nexuslauncher.NexusLauncherActivity",
                "com.ss.android.ugc.aweme",
                true
            )
        )
    }
}
