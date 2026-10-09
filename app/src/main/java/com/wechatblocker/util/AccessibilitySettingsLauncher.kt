package com.wechatblocker.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.wechatblocker.service.WeChatBlockerService

/**
 * 打开系统无障碍设置。Android 禁止应用自己写入 enabled_accessibility_services，
 * 只能把用户带到设置页，由用户打开开关。
 */
object AccessibilitySettingsLauncher {
    private const val TAG = "A11ySettingsLauncher"
    private const val FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    const val ACTION_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    const val DETAILS_SDK = 31

    data class SettingsTarget(
        val action: String,
        val highlightKey: String?,
        val useComponentExtra: Boolean
    )

    fun serviceComponent(context: Context): ComponentName {
        return ComponentName(context, WeChatBlockerService::class.java)
    }

    fun flattenComponent(context: Context): String {
        return serviceComponent(context).flattenToString()
    }

    fun isServiceListed(
        enabledServices: String?,
        packageName: String,
        serviceClass: String
    ): Boolean {
        if (enabledServices.isNullOrBlank() || enabledServices == "null") return false
        val shortClass = if (serviceClass.startsWith("$packageName.")) {
            "." + serviceClass.removePrefix("$packageName.")
        } else {
            serviceClass
        }
        val simpleName = serviceClass.substringAfterLast('.')
        val aliases = setOf(
            "$packageName/$serviceClass",
            "$packageName/$shortClass",
            "$packageName/$simpleName"
        )
        return enabledServices.split(':').any { entry ->
            val item = entry.trim()
            if (item.isEmpty()) return@any false
            item in aliases ||
                item.contains(serviceClass) ||
                item.endsWith(shortClass) ||
                item.endsWith("/$simpleName")
        }
    }

    fun isServiceEnabled(context: Context): Boolean {
        if (isEnabledViaManager(context)) return true
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val listed = isServiceListed(
            enabled,
            context.packageName,
            WeChatBlockerService::class.java.name
        )
        Log.d(TAG, "isServiceEnabled manager=false listed=$listed raw=${enabled?.take(120)}")
        return listed
    }

    private fun isEnabledViaManager(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val want = WeChatBlockerService::class.java.name
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                ?: emptyList()
            list.any { info ->
                val si = info.resolveInfo?.serviceInfo ?: return@any false
                si.packageName == context.packageName &&
                    (si.name == want || si.name.endsWith(".WeChatBlockerService"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccessibilityManager 查询失败", e)
            false
        }
    }

    fun resolveTarget(sdkInt: Int, flatten: String): SettingsTarget {
        if (sdkInt >= DETAILS_SDK) {
            return SettingsTarget(
                action = ACTION_DETAILS,
                highlightKey = null,
                useComponentExtra = true
            )
        }
        return SettingsTarget(
            action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
            highlightKey = flatten,
            useComponentExtra = false
        )
    }

    fun buildIntent(sdkInt: Int, component: ComponentName): Intent {
        val target = resolveTarget(sdkInt, component.flattenToString())
        val intent = Intent(target.action)
        if (target.useComponentExtra) {
            intent.putExtra(Intent.EXTRA_COMPONENT_NAME, component)
        }
        if (target.highlightKey != null) {
            val args = Bundle()
            args.putString(FRAGMENT_ARG_KEY, target.highlightKey)
            intent.putExtra(FRAGMENT_ARG_KEY, target.highlightKey)
            intent.putExtra(SHOW_FRAGMENT_ARGS, args)
        }
        return intent
    }

    fun open(activity: Activity) {
        val component = serviceComponent(activity)
        val candidates = linkedSetOf<Intent>()
        if (Build.VERSION.SDK_INT >= DETAILS_SDK) {
            candidates += buildIntent(DETAILS_SDK, component)
        }
        candidates += buildIntent(30, component)
        candidates += Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        for (intent in candidates) {
            try {
                if (intent.resolveActivity(activity.packageManager) != null) {
                    Log.d(TAG, "打开无障碍设置 action=${intent.action}")
                    activity.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "无法打开 ${intent.action}", e)
            }
        }
        Log.w(TAG, "回退到通用无障碍设置列表")
        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
