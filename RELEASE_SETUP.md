# Release 设置文档

本文档说明了为"视频号拦截器"项目配置的自动化release流程。

## 📦 直接下载链接

**稳定的APK下载地址:**

```
https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk
```

这个链接具有以下特性:
- ✅ **永久有效**: 始终指向最新版本
- ✅ **文件名固定**: 始终为`wechatvideo.apk`,不会被微信改名为`.apk.1`
- ✅ **直接下载**: 可在手机浏览器中直接访问下载
- ✅ **自动更新**: main分支更新后自动发布新版本

## 🔑 Keystore信息

为了确保APK签名一致(允许覆盖安装更新),项目使用了固定的release keystore。

**文件位置**: `app/wechatvideo-release.keystore`

**签名信息**:
- 别名(Alias): `wechatvideo`
- Store密码: `wechatvideo2024`
- Key密码: `wechatvideo2024`
- 算法: RSA 2048位
- 有效期: 10000天
- 证书DN: `CN=WeChatVideoBlocker, OU=Personal, O=Personal, L=Beijing, ST=Beijing, C=CN`
- SHA-256指纹: `b70cc6f8edf5ae015392f2cfd902a2279064727355205778195477211b78da70`

**⚠️ 安全说明**:
- 这是个人使用的应用,不会发布到Google Play
- Keystore已提交到仓库,方便GitHub Actions自动构建
- 如果项目公开分发,建议使用GitHub Secrets存储keystore

## 🚀 GitHub Actions工作流

### 1. Android CI (`android-ci.yml`)

**触发条件**:
- Push到`main`或`cursor/*`分支
- Pull Request到`main`

**执行任务**:
- 设置Java 17和Android SDK
- 运行单元测试 (`./gradlew test`)
- 构建debug APK (`./gradlew assembleDebug`)
- 上传APK和测试报告为artifact

**修复内容**:
- 移除已废弃的`tools`包
- 只安装`platform-tools`
- ✅ CI现已成功通过

### 2. Build and Release (`release.yml`)

**触发条件**:
- Push到`main`分支
- 推送tag (v*)
- 手动触发 (workflow_dispatch)

**执行任务**:
1. 设置构建环境(Java 17 + Android SDK)
2. 构建release APK (`./gradlew assembleRelease`)
3. 重命名APK为`wechatvideo.apk`
4. 创建或更新名为`latest`的rolling release
5. 上传APK到release
6. 生成release说明

**Release特性**:
- **Rolling Release**: 使用固定的`latest` tag
- **自动覆盖**: 每次push到main都会更新同一个release
- **稳定链接**: 下载URL始终不变
- **详细说明**: 自动生成的release说明包含功能介绍和安装步骤

## 📋 如何触发Release

### 方法1: 合并PR到main (推荐)

```bash
# 在GitHub UI上点击"Merge pull request"按钮
# 或使用gh CLI:
gh pr merge 1 --squash
```

合并后GitHub Actions会自动:
1. 运行CI测试
2. 构建release APK
3. 创建/更新latest release
4. 约2-3分钟后,新APK即可通过稳定链接下载

### 方法2: 直接push到main

```bash
git checkout main
git pull
git merge cursor/wechat-video-blocker-782f
git push origin main
```

### 方法3: 打版本tag

```bash
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
```

这会创建一个独立的版本release(除了latest外)

### 方法4: 手动触发

在GitHub Actions页面:
1. 选择"Build and Release APK"工作流
2. 点击"Run workflow"按钮
3. 选择分支(通常是main)
4. 点击"Run workflow"

## 🔍 验证Release

### 检查GitHub Actions状态

```bash
# 查看最近的workflow运行
gh run list --workflow=release.yml --limit 5

# 查看特定运行的详情
gh run view <run-id>

# 查看日志
gh run view <run-id> --log
```

### 检查Release

```bash
# 列出所有releases
gh release list

# 查看latest release详情
gh release view latest

# 下载APK验证
curl -L -o wechatvideo.apk \
  https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk

# 验证APK签名
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs wechatvideo.apk
```

### 测试下载链接

在手机浏览器中访问:
```
https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk
```

应该能直接下载,文件名为`wechatvideo.apk`

## 🛠 本地构建Release

如需手动构建release APK:

```bash
# 清理之前的构建
./gradlew clean

# 构建release APK
./gradlew assembleRelease

# 输出位置
ls -lh app/build/outputs/apk/release/app-release.apk

# 验证签名
$ANDROID_HOME/build-tools/*/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

## 📝 版本管理

当前版本信息在`app/build.gradle.kts`中:

```kotlin
versionCode = 1
versionName = "1.0"
```

更新版本时:
1. 增加`versionCode`(必须,用于判断是否可升级)
2. 更新`versionName`(可选,用于展示)
3. Commit并push到main
4. 自动触发新的release构建

## ⚠️ 注意事项

1. **Keystore安全**: 
   - 当前keystore已公开在仓库中
   - 仅适用于个人/测试应用
   - 商业应用应使用GitHub Secrets

2. **签名一致性**:
   - 必须使用相同的keystore签名
   - 否则无法覆盖安装旧版本
   - 用户需卸载后重新安装

3. **构建时间**:
   - 完整构建约2-3分钟
   - 包括编译、测试、打包、上传

4. **GitHub Releases限制**:
   - 单个文件最大2GB
   - 当前APK约5MB,完全足够

5. **下载链接缓存**:
   - GitHub CDN可能有短暂缓存
   - 新版本发布后可能需要等待几分钟

## 🔗 相关链接

- **Releases页面**: https://github.com/baiyx321/wechatvideo/releases
- **Actions页面**: https://github.com/baiyx321/wechatvideo/actions
- **PR #1**: https://github.com/baiyx321/wechatvideo/pull/1
- **直接下载**: https://github.com/baiyx321/wechatvideo/releases/latest/download/wechatvideo.apk

## ✅ 当前状态

- [x] CI工作流已修复并通过
- [x] Release工作流已配置
- [x] Release keystore已生成并配置
- [x] 稳定下载链接已设置
- [ ] **等待用户合并PR #1到main分支以触发首次release**

合并PR后,约2-3分钟即可在稳定链接下载到签名的release APK。
