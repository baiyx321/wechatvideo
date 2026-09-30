# 视频号拦截器

一个帮助您控制微信视频号使用的Android应用。当您打开微信视频号时,应用会要求您进行反思,帮助您更有意识地使用短视频。

## 功能特点

- 📱 **智能检测**: 自动检测微信视频号、抖音、抖音极速版、小红书
- ✍️ **文本库抄写**: 通过抄写经典古文段落(论语、大学、中庸、孟子、荀子、管子)来提醒自己
- 🎯 **双触发策略**: 
  - 离开超过N分钟后再打开时触发(默认5分钟)
  - 每晚23:00后首次使用时触发夜间提醒
- 📊 **字符匹配**: 实时统计正确字符数,错误字符红色高亮
- 🔄 **换一段**: 随机抽取新段落,保持新鲜感
- 🎯 **独立配置**: 每个应用独立的触发设置和开关
- 📚 **自定义文本**: 支持导入自己的txt文本库
- 🔧 **灵活设置**: 可自定义离开时间阈值、夜间提醒时间、启用的书籍

## 快速开始

### 📦 直接下载安装

**🚀 稳定下载链接(推荐)**

```
https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk
```

**在手机上的安装步骤:**

1. 打开手机浏览器(微信内置浏览器也可以)
2. 复制上面的链接到地址栏访问
3. 下载`wechatvideo.apk`(文件名固定,不会被改名为`.apk.1`)
4. 下载完成后点击安装
5. 如提示"不允许安装未知来源应用",点击"设置"→允许该浏览器安装应用
6. 安装完成后打开应用

**或者从GitHub Releases页面下载:**

访问 [Releases页面](https://github.com/baiyx321/wechatvideo/releases) 选择最新版本下载

### ⚙️ 首次配置

1. 打开"视频号拦截器"应用
2. 点击"启用服务"按钮
3. 在弹出的无障碍设置中找到"视频号拦截器"
4. 开启服务权限
5. 返回应用,确认服务状态显示为"已启用"(绿色)
6. (可选)点击"设置"调整触发条件和启用的应用

### ROM保活设置

为确保后台正常工作:

**小米/红米**: 设置→应用管理→视频号拦截器→自启动(开)、省电策略(无限制)  
**华为**: 设置→应用启动管理→手动管理(全开)  
**OPPO**: 设置→应用权限→自启动(开)、关联启动(开)  
**vivo**: 设置→权限管理→自启动(开)  
**通用**: 最近任务中锁定应用

## 使用说明

### 📖 文本库抄写模式

1. 启用服务后,应用在后台监控配置的应用(微信视频号、抖音、小红书)
2. 当触发条件满足时(离开后再打开或23:00夜间提醒),弹出抄写界面
3. 界面显示一段随机抽取的经典古文(50-80字)和来源标注
4. 在输入框中抄写文本,系统实时统计正确字符数(忽略标点空格)
5. 如有错误,首个错误字符会显示为红色
6. 达到要求字符数后,提交按钮变为绿色可点击
7. 可点击"换一段"按钮重新抽取段落
8. 点击返回按钮会退出当前应用

### ⚙️ 设置选项

- **应用开关**: 独立控制微信、抖音、小红书的拦截
- **离开时间**: 设置离开多少分钟后再打开时触发(默认5分钟)
- **夜间提醒**: 设置几点后触发夜间提醒(默认23:00)
- **文本库设置**: 
  - 选择启用的经典书籍(论语、大学、中庸、孟子、荀子、管子)
  - 导入自定义txt文本(通过文件选择器)
  - 每个文本文件会自动按段落分割,随机抽取50-80字显示

### 🎯 触发逻辑

**离开后再打开触发:**
- 首次打开应用:立即触发
- 离开应用并超过设定时间后再打开:触发
- 未超过设定时间:不触发

**23:00夜间提醒:**
- 每天首次在设定时间后使用应用时触发
- 每天只触发一次
- 与"离开后再打开"独立判断

## 项目架构

```
app/src/main/java/com/wechatblocker/
├── data/                           # 数据层
│   ├── PreferencesManager.kt       # 配置管理
│   ├── AppUsageTracker.kt          # 应用使用跟踪
│   ├── TextLibraryManager.kt       # 文本库管理
│   ├── CopyTypingMatcher.kt        # 字符匹配算法
│   ├── HistoryManager.kt           # 历史记录
│   └── ReflectionEntry.kt          # 数据模型
├── logic/                          # 业务逻辑层
│   └── BlockingLogic.kt            # 拦截逻辑(可单元测试)
├── service/                        # 服务层
│   └── WeChatBlockerService.kt     # AccessibilityService
└── ui/                             # UI层
    ├── MainActivity.kt
    ├── SettingsActivity.kt
    ├── HistoryActivity.kt
    ├── DebugActivity.kt
    └── BlockingOverlay.kt          # 抄写覆盖层

app/src/main/assets/texts/          # 文本资源
├── lunyu.txt                       # 论语
├── daxue.txt                       # 大学
├── zhongyong.txt                   # 中庸
├── mengzi.txt                      # 孟子
├── xunzi.txt                       # 荀子
└── guanzi.txt                      # 管子
```

### 核心技术

- **AccessibilityService**: 监控目标应用
- **多应用检测**: 
  - 微信:类名+文本双重检测视频号界面
  - 抖音/小红书:整个应用前台检测
- **TYPE_ACCESSIBILITY_OVERLAY**: 全屏覆盖层
- **双触发策略**: 离开后再打开 + 夜间提醒
- **字符匹配**: 忽略标点空格,只比较汉字
- **本地存储**: SharedPreferences(配置) + JSON(历史) + Assets(文本库)
- **分层设计**: 业务逻辑与Android API分离,便于测试

### 检测流程

```
用户打开目标应用
  ↓
AccessibilityService检测到窗口变化
  ↓
识别应用类型:
  - 微信: 进一步检测是否为视频号界面
  - 抖音/小红书: 直接判定
  ↓
检查触发条件:
  - 应用是否启用?
  - 离开超过N分钟?
  - 23:00夜间提醒是否已触发?
  ↓
满足条件 → 显示抄写覆盖层
  ↓
显示随机抽取的古文段落(50-80字)
  ↓
用户抄写,实时字符匹配和计数
  ↓
达到要求字符数 → 提交按钮启用
  ↓
提交后记录时间戳,更新触发状态
```

## 开发

### 构建

```bash
# 克隆仓库
git clone https://github.com/baiyx321/wechatvideo.git
cd wechatvideo

# 运行测试
./gradlew test

# 构建debug APK
./gradlew assembleDebug

# 构建release APK(已签名)
./gradlew assembleRelease

# 输出: 
# app/build/outputs/apk/debug/app-debug.apk
# app/build/outputs/apk/release/app-release.apk
```

### CI/CD

**GitHub Actions CI (android-ci.yml)**
- 触发:Push到main或cursor/*分支、Pull Request
- 运行单元测试
- 构建debug APK
- 上传测试报告和APK artifact

**自动化Release (release.yml)**
- 触发:Push到main分支或打tag
- 构建签名的release APK
- 创建/更新rolling "latest" release
- 上传wechatvideo.apk到GitHub Releases
- 提供稳定下载链接

**Release Keystore**
- 文件:`app/wechatvideo-release.keystore`
- 别名:`wechatvideo`
- 密码:`wechatvideo2024`
- 已提交到仓库(个人使用应用)

### 测试

为了测试拦截功能,项目包含三个独立的测试APK:

**fakewechat** - 模拟微信视频号
- 包名: `com.wechatblocker.fakewechat`
- Activity: `FinderHomeUI`
- 包含视频号关键词

**fakedouyin** - 模拟抖音
- 包名: `com.ss.android.ugc.aweme.test`
- 简单的测试应用

**fakexiaohongshu** - 模拟小红书
- 包名: `com.xingin.xhs.test`
- 简单的测试应用

**测试步骤**:
1. 构建并安装主应用和测试应用
2. 启用无障碍服务
3. 打开测试应用
4. 验证覆盖层显示和抄写功能
5. 测试触发时机逻辑(离开后再打开、夜间提醒)

### 技术栈

- Kotlin 1.9.20
- Android SDK 34 (minSdk 26)
- Material Design 3
- Gradle 8.2 + Kotlin DSL
- GitHub Actions CI/CD

### 测试

单元测试覆盖核心逻辑:
- `BlockingLogicTest`: 视频号检测逻辑
- `CopyTypingMatcherTest`: 字符匹配算法  
- `AppUsageTrackerTest`: 触发时机判断
- `TextLibraryManagerTest`: 文本库管理

```bash
./gradlew test
# 查看测试报告:
# app/build/reports/tests/testDebugUnitTest/index.html
```

## 隐私与安全

- ✅ **无网络权限** - 不上传任何数据
- ✅ **本地存储** - 所有记录保存在设备上
- ✅ **最小权限** - 仅使用无障碍服务
- ✅ **开源透明** - 所有代码可审查

无障碍服务仅用于:
- 检测当前应用和窗口类名
- 读取界面文本(仅用于检测视频号特征)
- 显示覆盖层

不会读取聊天内容或其他敏感信息。

## 已知限制

1. **微信版本**: 微信更新可能改变类名,需使用调试工具更新关键词
2. **系统限制**: Android 10+后台限制,需正确配置保活
3. **应用商店**: Google Play可能拒绝无障碍应用,建议通过GitHub发布
4. **仅限Android**: iOS无法实现类似功能

## 常见问题

**Q: 如何下载安装?**  
A: 访问 `https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk` 直接下载,或在 [Releases页面](https://github.com/baiyx321/wechatvideo/releases) 下载最新版本。

**Q: 拦截不生效?**  
A: 
1. 确认无障碍服务已启用(设置→无障碍)
2. 检查对应应用的开关是否打开(设置界面)
3. 检查是否在冷却期内(刚刚触发过)
4. 检查离开时间是否满足设定值
5. 确认应用未被系统清理(配置保活设置)

**Q: 微信视频号检测失败?**  
A: 
1. 微信更新后可能改变界面特征
2. 在设置中调整检测关键词
3. 确认打开的是"视频号"tab,不是普通聊天界面

**Q: 如何关闭拦截?**  
A: 
1. 方法1:在应用设置中关闭对应应用的开关
2. 方法2:在系统设置→无障碍中关闭"视频号拦截器"服务

**Q: 数据会同步到云端吗?**  
A: 不会,所有数据仅保存在本地,应用无网络权限。

**Q: 可以自定义文本内容吗?**  
A: 可以。在设置中点击"导入自定义文本",选择txt文件即可。文本会按段落自动分割。

**Q: 为什么使用古文抄写而不是自由输入?**  
A: 抄写古文可以:
1. 强制放慢节奏,让大脑从短视频的快节奏中抽离
2. 学习传统文化,一举两得
3. 避免敷衍应付(自由输入可能随便打几个字)

## 更新日志

### v1.0.0 (2026-09-30)
- ✅ 支持微信视频号检测和拦截
- ✅ 支持抖音、抖音极速版、小红书全应用拦截
- ✅ 文本库抄写模式:6部经典古文(论语、大学、中庸、孟子、荀子、管子)
- ✅ 双触发策略:离开后再打开 + 夜间提醒
- ✅ 字符匹配计数和错误高亮
- ✅ 独立的per-app配置和追踪
- ✅ 自定义文本库导入
- ✅ 自动化CI/CD和GitHub Releases
- ✅ 稳定下载链接

## 贡献

欢迎提交Issue和Pull Request!

## 许可证

MIT License

## 免责声明

本应用仅供个人学习和自我管理使用。开发者不对使用本应用导致的任何问题负责。
