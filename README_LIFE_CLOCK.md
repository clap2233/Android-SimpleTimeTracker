# 生活时间钟

基于 [Simple Time Tracker](https://github.com/Razeeman/Android-SimpleTimeTracker) v1.59 二次开发。
上游提交：`ec82d9afa4c26f03f0a73eb7deac2d3ddf73254b`。保留上游许可证及版权声明，见 [LICENSE.md](LICENSE.md)。

需求与开发计划位于项目上一级目录。Android 优先，本地存储；iPhone 延后。当前是开发中的版本，尚未完成首版交付。

M3 计时整理、M4 详情／评分／照片、M6 完整备份恢复、M5 手机周回顾和 M7 云备份／电脑只读已进入开发预览。当前 UI2（版本 158）在 UI1 基础上采用森林系奶油小猫视觉，保留详情入口和设置分组，沿用数据库 37 与既有发布证书；界面说明见 [UI_REDESIGN.md](UI_REDESIGN.md)。公网 443 与真机验收尚未齐备。实际检查和待完成范围见 [开发记录](DEVELOPMENT.md)，使用入口见 [使用与迁移说明](USER_GUIDE.md)，发布构建见 [发布说明](RELEASE.md)。

## Windows 构建

沿用上游 JDK 21、Gradle 8.6、AGP 8.4.0、Kotlin 1.9.25、Android platform 35、build-tools 34.0.0。

在项目同级目录 `.life-clock-tools` 放置解压后的 JDK（包含 `bin/javac.exe`）和 Android SDK（目录名 `sdk`）。
安装 SDK 的 `platforms;android-35`、`build-tools;34.0.0`、`platform-tools`。工具与下载缓存不进入源码仓库。

```powershell
.\scripts\build-android.ps1
.\scripts\build-android.ps1 -Tasks ':domain:test'
.\scripts\build-android.ps1 -Offline
```

脚本仅为本次进程设置工具路径、Windows 可信证书存储和系统已有代理，结束后恢复原环境变量。中文路径会在系统临时目录建立指向原目录的 ASCII junction，避免 Android 原生工具的路径编码问题；已有别名若指向其他目录，脚本停止并报告，不覆盖。不改动全局 Java 配置。

默认构建不依赖 Google Play 服务的 `baseDebug`，并运行领域层单元测试。APK 输出到 `app/build/outputs/apk/base/debug/`，测试报告位于 `domain/build/reports/tests/test/index.html`。

首次构建需要下载依赖。上游 AGP 8.4.0 对 compileSdk 35 会输出兼容性提示；保留提示，待基线稳定后再单独评估升级。

## 验证范围

编译、单元测试、模拟器检查和真机验收分别记录。照片、整理、评分、周回顾、完整恢复及云备份通过的自动化／模拟器证据见开发记录；公网联调、发布候选迁移及真机后台兼容仍需按 M8 门槛核验。无连接真机时，不将通知、后台运行或实际手机迁移标为已验证。
