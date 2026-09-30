# 架构设计文档

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                         用户界面层                            │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │ 主界面   │ │ 设置界面 │ │ 历史记录 │ │ 调试界面 │      │
│  │MainActivity│SettingsActivity│HistoryActivity│DebugActivity│ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
│       │            │            │            │               │
│       └────────────┴────────────┴────────────┘               │
│                      ↓                                        │
└─────────────────────────────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                      业务逻辑层                               │
│  ┌──────────────────────────────────────────────────┐       │
│  │          BlockingLogic (纯Kotlin类)              │       │
│  │  • shouldBlock() - 判断是否拦截                  │       │
│  │  • isFinderPage() - 检测视频号页面               │       │
│  │  • isValidSubmission() - 验证提交                │       │
│  └──────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────┘
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                       数据层                                  │
│  ┌───────────────┐ ┌──────────────┐ ┌─────────────┐        │
│  │PreferencesManager│HistoryManager│ReflectionEntry│        │
│  │ (设置管理)    │  (历史记录)   │  (数据模型)  │        │
│  └───────────────┘ └──────────────┘ └─────────────┘        │
│         ↓                  ↓                                  │
│  SharedPreferences    JSON文件                               │
└─────────────────────────────────────────────────────────────┘
                       ↑
┌─────────────────────────────────────────────────────────────┐
│                    Android系统服务层                          │
│  ┌──────────────────────────────────────────────────┐       │
│  │      WeChatBlockerService                        │       │
│  │      (AccessibilityService)                      │       │
│  │  • 监听窗口变化事件                              │       │
│  │  • 收集可见文本                                  │       │
│  │  • 显示拦截覆盖层                                │       │
│  └──────────────────────────────────────────────────┘       │
│         ↓                    ↓                                │
│  Android系统          BlockingOverlay                        │
│  无障碍事件           (TYPE_ACCESSIBILITY_OVERLAY)           │
└─────────────────────────────────────────────────────────────┘
                       ↓
                  目标应用(微信)
```

## 核心流程

### 1. 拦截检测流程

```
用户打开微信视频号
       ↓
Android系统触发无障碍事件
       ↓
WeChatBlockerService.onAccessibilityEvent()
       ↓
收集窗口信息(包名、类名、可见文本)
       ↓
创建BlockingLogic实例(加载配置)
       ↓
调用shouldBlock()判断
       ↓
检查: 启用状态? → NO → 不拦截
       ↓ YES
检查: 目标应用? → NO → 不拦截
       ↓ YES
检查: 冷却期内? → YES → 不拦截
       ↓ NO
检查: 视频号页面? → NO → 不拦截
       ↓ YES
显示BlockingOverlay拦截界面
```

### 2. 页面检测逻辑

```
isFinderPage(className, visibleTexts)
       ↓
方法1: 类名检测
       ↓
检查className是否包含关键词?
(finder, FinderHomeUI, FinderUI)
       ↓ YES
       └─> 返回 true (检测到)
       ↓ NO
方法2: 文本检测(后备方案)
       ↓
统计visibleTexts中匹配的关键词数量
(视频号, 关注, 朋友, 推荐)
       ↓
匹配数 >= 2?
       ↓ YES
       └─> 返回 true (检测到)
       ↓ NO
返回 false (未检测到)
```

### 3. 用户交互流程

```
拦截界面显示
       ↓
显示随机提示问题
       ↓
用户输入文本
       ↓
实时更新字符计数
       ↓
字符数 >= 最小值?
       ↓ YES
       └─> 启用提交按钮
       ↓ NO
       └─> 禁用提交按钮
       ↓
用户点击提交/返回
       ↓
提交: 保存到HistoryManager
       设置lastPassTime(开始冷却)
       隐藏覆盖层
       执行GLOBAL_ACTION_BACK
       ↓
返回: 隐藏覆盖层
       执行GLOBAL_ACTION_BACK
```

## 数据模型

### ReflectionEntry
```kotlin
data class ReflectionEntry(
    val id: Long,           // 时间戳作为ID
    val content: String,    // 用户输入的反思内容
    val timestamp: Long     // 提交时间
)
```

### PreferencesManager存储的配置
```
minChars: Int = 50              # 最小字符数
cooldownMinutes: Int = 10       # 冷却分钟数
enabled: Boolean = true         # 是否启用
prompts: String                 # 提示语(支持多个,用---分隔)
classKeywords: String           # 类名关键词(逗号分隔)
textKeywords: String            # 文本关键词(逗号分隔)
targetPackages: String          # 目标包名(逗号分隔)
lastPassTime: Long              # 上次通过时间戳
```

### HistoryManager存储格式(JSON)
```json
[
    {
        "id": 1727688000000,
        "content": "我为什么要刷视频...",
        "timestamp": 1727688000000
    },
    {
        "id": 1727687000000,
        "content": "我现在本该...",
        "timestamp": 1727687000000
    }
]
```

## 关键技术点

### 1. AccessibilityService配置
```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:packageNames="com.tencent.mm" />
```

- **事件类型**: 窗口状态/内容变化、滚动事件
- **权限**: 可读取窗口内容
- **作用域**: 仅监控com.tencent.mm(微信)

### 2. TYPE_ACCESSIBILITY_OVERLAY
```kotlin
WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    TYPE_ACCESSIBILITY_OVERLAY,
    FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
    PixelFormat.TRANSLUCENT
)
```

- **类型**: 无障碍覆盖层(系统级最高层)
- **特点**: 可以覆盖任何应用,包括系统UI
- **权限**: 需要无障碍服务权限

### 3. 文本收集策略
```kotlin
fun collectTexts(event: AccessibilityEvent): List<String> {
    val texts = mutableListOf<String>()
    // 1. 从事件本身收集
    event.text?.forEach { texts.add(it) }
    // 2. 遍历整个窗口树
    rootInActiveWindow?.let { root ->
        collectFromNode(root, texts)
    }
    return texts.distinct().take(50)
}
```

### 4. 测试友好设计
```kotlin
// 业务逻辑与Android API分离
class BlockingLogic(
    // 所有参数通过构造函数传入
    private val minChars: Int,
    private val cooldownMinutes: Int,
    // ...
) {
    // 纯函数,可直接单元测试
    fun shouldBlock(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
        lastPassTime: Long,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        // 没有任何Android依赖
        // 可以在JVM上直接测试
    }
}
```

## 安全与隐私

### 权限使用
- **SYSTEM_ALERT_WINDOW**: 显示覆盖层(可选,用于兼容性)
- **BIND_ACCESSIBILITY_SERVICE**: 无障碍服务(核心权限)

### 隐私保护
- ✅ 不申请网络权限
- ✅ 不申请存储权限
- ✅ 历史记录仅本地存储
- ✅ 不收集任何个人信息
- ✅ 无障碍服务仅读取必要信息(类名、文本)
- ✅ 不记录微信聊天内容或敏感信息

## 性能优化

### 1. 事件过滤
- 仅监听必要的事件类型
- 仅监听目标应用(com.tencent.mm)
- 防抖处理,避免重复拦截

### 2. 文本收集优化
- 限制最多收集50个文本
- 去重处理
- 异常捕获,防止崩溃

### 3. 内存管理
- AccessibilityNodeInfo及时recycle
- 使用轻量级数据结构
- JSON文件延迟加载

## 扩展性

### 支持其他应用
修改配置即可适配其他短视频应用:

```kotlin
targetPackages = "com.ss.android.ugc.aweme" // 抖音
classKeywords = "MainFragment,FeedFragment"
textKeywords = "推荐,关注,附近"
```

### 支持多语言
- 字符串资源已统一管理
- 可添加其他语言的values文件夹
- 如: `values-en/strings.xml`

### 支持自定义检测规则
- 正则表达式支持(未来版本)
- 自定义脚本支持(未来版本)
- 机器学习模型(未来版本)
