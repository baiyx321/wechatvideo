# 微信视频号拦截器 - 测试报告

## 测试环境

- **开发环境**: Ubuntu 24.04 (Cloud Agent VM)
- **Android SDK**: API 34 (Android 14)
- **构建工具**: Gradle 8.2, Kotlin 1.9.20
- **Java版本**: OpenJDK 21

## 测试结果总览

| 测试项目 | 状态 | 备注 |
|---------|------|------|
| 单元测试 | ✅ 通过 | 9个测试全部通过 |
| APK构建 | ✅ 成功 | 6.0 MB debug APK |
| 代码结构 | ✅ 完整 | 所有模块实现 |
| 文档 | ✅ 齐全 | 详细README + CI配置 |
| 真机测试 | ⚠️ 待验证 | 需要真实设备 |

## 1. 单元测试 (✅ 通过)

### 测试覆盖

文件: `app/src/test/java/com/wechatblocker/logic/BlockingLogicTest.kt`

共9个测试用例,覆盖核心业务逻辑:

1. ✅ `test shouldBlock returns false when disabled` - 禁用时不拦截
2. ✅ `test shouldBlock returns false for non-target package` - 非目标应用不拦截
3. ✅ `test shouldBlock returns false during cooldown` - 冷却期内不拦截
4. ✅ `test shouldBlock returns true after cooldown expires` - 冷却期过后拦截
5. ✅ `test isFinderPage detects by class name` - 类名检测
6. ✅ `test isFinderPage detects by visible texts` - 文本检测
7. ✅ `test isValidSubmission checks minimum length` - 最小字符数验证
8. ✅ `test getRemainingChars calculates correctly` - 剩余字符数计算
9. ✅ `test case insensitive class name matching` - 大小写不敏感匹配

### 测试执行

```bash
./gradlew test --rerun-tasks
```

结果:
```
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
> Task :app:test

BUILD SUCCESSFUL in 8s
```

## 2. APK构建 (✅ 成功)

### 构建命令

```bash
./gradlew assembleDebug
```

### 构建输出

- **路径**: `app/build/outputs/apk/debug/app-debug.apk`
- **大小**: 6.0 MB
- **包名**: `com.wechatblocker`
- **版本**: 1.0 (versionCode: 1)
- **目标SDK**: Android 14 (API 34)
- **最低SDK**: Android 8.0 (API 26)

### APK信息

```
package: name='com.wechatblocker' versionCode='1' versionName='1.0'
application-label:'视频号拦截器'
launchable-activity: name='com.wechatblocker.ui.MainActivity'
```

## 3. 代码架构

### 目录结构

```
app/src/main/java/com/wechatblocker/
├── data/                           # 数据层
│   ├── PreferencesManager.kt      # SharedPreferences管理
│   ├── HistoryManager.kt          # JSON历史记录
│   └── ReflectionEntry.kt         # 数据模型
├── logic/                          # 业务逻辑层(纯Kotlin,可测试)
│   └── BlockingLogic.kt           # 拦截逻辑
├── service/                        # Android服务层
│   └── WeChatBlockerService.kt    # AccessibilityService
└── ui/                             # UI层
    ├── MainActivity.kt             # 主界面
    ├── SettingsActivity.kt         # 设置
    ├── HistoryActivity.kt          # 历史记录
    ├── DebugActivity.kt            # 调试工具
    └── BlockingOverlay.kt          # 拦截覆盖层
```

### 资源文件

- 6个布局文件 (XML)
- 80+ 中文字符串资源
- Material Design 3 主题
- 自适应图标

## 4. 功能实现清单

### 核心功能

- [x] **AccessibilityService**: 监控微信应用
- [x] **双重检测机制**:
  - 类名检测: `finder`, `FinderHomeUI`, `FinderUI`
  - 文本检测: 至少2个关键词匹配
- [x] **全屏拦截覆盖层**:
  - 随机提示问题
  - 多行文本输入
  - 实时字符计数
  - 提交按钮(最小字符数限制)
  - 返回按钮
- [x] **冷却机制**: 提交后10分钟内不再拦截
- [x] **配置管理**: SharedPreferences持久化

### UI界面

- [x] **主界面** (MainActivity):
  - 服务状态显示(已启用/未启用)
  - 快速跳转无障碍设置
  - ROM保活指导(小米/华为/OPPO/vivo)
  - 导航按钮(设置/历史/调试)

- [x] **设置界面** (SettingsActivity):
  - 启用/禁用拦截
  - 最小字符数(可调)
  - 冷却时间(可调)
  - 自定义提示语(支持多个)
  - 检测关键词配置
  - 目标应用包名

- [x] **历史记录界面** (HistoryActivity):
  - RecyclerView列表展示
  - 时间戳显示
  - 单条删除
  - 清空全部(带确认对话框)

- [x] **调试界面** (DebugActivity):
  - 实时显示当前应用包名
  - 显示当前窗口类名
  - 显示前20个可见文本
  - 自动刷新(2秒)
  - 一键复制功能

### 数据持久化

- [x] **SharedPreferences**: 配置存储
- [x] **JSON文件**: 历史记录(本地,不上传)

## 5. 测试场景设计

### 已测试(单元测试)

1. ✅ 拦截逻辑正确性
2. ✅ 冷却时间计算
3. ✅ 字符数验证
4. ✅ 关键词匹配(大小写不敏感)
5. ✅ 多条件组合判断

### 待真机测试

由于缺少真实微信环境,以下场景需要在真实设备上验证:

#### 场景1: 基本拦截流程
1. 安装APK并启用无障碍服务
2. 打开微信
3. 进入视频号
4. **预期**: 弹出拦截界面
5. 输入少于50字 → **预期**: 提交按钮禁用
6. 输入50字以上 → **预期**: 提交按钮启用
7. 点击提交 → **预期**: 保存到历史,返回桌面

#### 场景2: 冷却机制
1. 完成第一次拦截
2. 10分钟内再次打开视频号
3. **预期**: 不弹出拦截界面
4. 10分钟后再次打开
5. **预期**: 再次弹出拦截界面

#### 场景3: 配置调整
1. 在设置中修改最小字符数为100
2. 修改冷却时间为5分钟
3. 重新测试拦截
4. **预期**: 按新配置工作

#### 场景4: 调试校准
1. 打开调试界面
2. 打开微信视频号
3. 切换回调试界面
4. **预期**: 显示微信包名、FinderUI类名、相关文本
5. 复制类名到设置
6. **预期**: 检测更准确

#### 场景5: 历史记录
1. 完成多次拦截
2. 打开历史记录
3. **预期**: 显示所有记录,时间倒序
4. 删除单条记录
5. **预期**: 记录消失
6. 清空全部
7. **预期**: 列表为空

## 6. 已知限制与建议

### 技术限制

1. **微信版本依赖**: 微信更新可能改变类名,需要通过调试工具更新关键词
2. **系统限制**: Android 10+ 后台限制,需要正确配置保活
3. **无障碍服务**: 可能被系统或用户误关闭
4. **应用商店**: Google Play可能拒绝无障碍应用

### 测试建议

在真实设备上测试时:

1. **测试设备要求**:
   - Android 8.0+ (API 26+)
   - 已安装微信(最新版本)
   - 非开发者模式(模拟真实用户)

2. **测试步骤**:
   - 按照README安装配置
   - 执行上述5个测试场景
   - 记录任何异常行为
   - 验证ROM保活设置

3. **兼容性测试**:
   - 不同品牌手机(小米/华为/OPPO/vivo)
   - 不同Android版本
   - 不同微信版本

4. **性能测试**:
   - 电池消耗
   - 内存占用
   - 对微信性能影响

## 7. 交付物清单

- [x] 源代码(完整Android项目)
- [x] Debug APK (`/opt/cursor/artifacts/wechatvideo-debug.apk`)
- [x] 详细README(中文,242行)
- [x] GitHub Actions CI/CD配置
- [x] 单元测试(9个测试用例)
- [x] Pull Request ([#1](https://github.com/baiyx321/wechatvideo/pull/1))
- [x] 测试报告(本文档)

## 8. 总结

### 已完成

✅ 功能完整的Android应用  
✅ 核心逻辑单元测试通过  
✅ APK成功构建(6.0 MB)  
✅ 代码架构清晰,模块分离  
✅ 详细中文文档  
✅ CI/CD配置  

### 待验证

⚠️ 真实微信环境测试  
⚠️ 拦截覆盖层实际显示  
⚠️ 无障碍服务稳定性  
⚠️ 多设备兼容性  
⚠️ 性能和电量影响  

### 建议

1. 在真实设备上安装APK进行完整测试
2. 测试不同微信版本的兼容性
3. 如果检测失败,使用调试工具更新关键词
4. 根据实际使用情况调整默认配置
5. 考虑添加更多提示语选项

## 9. 快速开始

### 安装测试

```bash
# 1. 下载APK
adb install /opt/cursor/artifacts/wechatvideo-debug.apk

# 2. 启用无障碍服务
# 手动操作: 设置 → 无障碍 → 视频号拦截器

# 3. 打开微信视频号测试
```

### 开发测试

```bash
# 运行单元测试
./gradlew test

# 构建APK
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

---

**测试日期**: 2026-09-30  
**版本**: v1.0.0  
**测试环境**: Cloud Agent VM (Ubuntu 24.04)  
**APK**: `/opt/cursor/artifacts/wechatvideo-debug.apk`  
**PR**: https://github.com/baiyx321/wechatvideo/pull/1
