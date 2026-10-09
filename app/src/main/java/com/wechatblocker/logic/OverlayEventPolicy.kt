package com.wechatblocker.logic

/**
 * 决定一条无障碍窗口事件该忽略、立即隐藏 overlay，还是可能只是 addView 带来的噪声。
 */
object OverlayEventPolicy {

    fun isOwnUi(packageName: String, className: String?, ownPackage: String, ownUiClasses: Collection<String>): Boolean {
        if (packageName != ownPackage) return false
        return ownUiClasses.any { className?.contains(it) == true }
    }

    fun isNoise(packageName: String, className: String?, ownPackage: String, ownUiClasses: Collection<String>): Boolean {
        if (packageName == "com.android.systemui") {
            return !isRecentsUi(className)
        }
        if (packageName == "android") return true
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        if (packageName.contains("keyboard", ignoreCase = true)) return true
        if (packageName == "com.android.adbkeyboard") return true
        if (packageName.endsWith(".permissioncontroller")) return true
        if (packageName == "com.android.packageinstaller") return true
        if (packageName.endsWith(".packageinstaller")) return true
        if (packageName == ownPackage) {
            return !isOwnUi(packageName, className, ownPackage, ownUiClasses)
        }
        return false
    }

    fun isLauncherPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg.contains("launcher") || pkg.contains("trebuchet")
    }

    fun isLauncherOrRecents(packageName: String, className: String?): Boolean {
        if (isRecentsUi(className)) return true
        val cls = className ?: return false
        if (cls.contains("Launcher")) return true
        return isLauncherPackage(packageName) && cls.contains("Activity")
    }

    fun isTransientWindow(packageName: String, className: String?): Boolean {
        if (packageName == "android") return true
        val cls = className ?: return true
        if (cls.contains("PopupWindow") || cls.contains("Toast") || cls.contains("Tooltip")) return true
        if (cls.startsWith("android.widget.") || cls.startsWith("android.view.View")) return true
        if (cls == "android.widget.FrameLayout" || cls.endsWith(".FrameLayout")) return true
        return false
    }

    /**
     * addView(TYPE_ACCESSIBILITY_OVERLAY) 常会同步/紧接着抛出非目标 WINDOW_STATE_CHANGED。
     * 这些不能当成用户离开，否则会 hide 再被目标应用事件 restore，看起来像闪烁。
     */
    fun shouldIgnoreLeaveWhileOverlayShowing(
        eventPackage: String,
        className: String?,
        targetPackage: String?,
        overlayJustShown: Boolean
    ): Boolean {
        if (targetPackage != null && eventPackage == targetPackage) return true
        if (isTransientWindow(eventPackage, className)) return true
        if (overlayJustShown && !isLauncherOrRecents(eventPackage, className)) return true
        return false
    }

    private fun isRecentsUi(className: String?): Boolean {
        val cls = className ?: return false
        return cls.contains("Recents") || cls.contains("Overview")
    }
}
