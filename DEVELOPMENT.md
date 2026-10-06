# 开发记录

更新：2026-10-02。

## 已固定的基础

- 上游 Simple Time Tracker v1.59，提交 `ec82d9afa4c26f03f0a73eb7deac2d3ddf73254b`。
- 本地分支 `life-clock/android`；源码和许可证保留，尚未创建远程 fork 或发布。
- JDK 21、Gradle 8.6、Android SDK 35 和 build-tools 34.0.0 已准备；构建入口见 `README_LIFE_CLOCK.md`。
- Windows 中文路径导致 Gradle 测试启动类与 AAPT2 SDK 路径读取失败，构建脚本使用临时目录 junction 提供 ASCII 路径，文件仍在原工作区。
- JVM 使用 Windows 可信证书存储和系统已有代理；没有关闭 TLS 校验或修改全局 Java 配置。

## M1 验证状态

原版 `:app:assembleBaseDebug :domain:test` 已通过（本次恢复构建耗时 6 分 8 秒）。领域测试报告包含 155 个测试，失败 0、错误 0。原版 APK 留存于工作区 `.life-clock-tools/artifacts/upstream-v1.59-base-debug.apk`，SHA-256 为 `8BF82C8D537A4359715461B4A224ED3638701BDCFF7F4B551096E6C892171F6A`。

首轮改动已写入代码并通过构建与以下自动检查：

- 独立包名 `app.lifetimeclock.android`（调试包追加 `.debug`）、显示名「生活时间钟」和临时图标；同步修正主版本及调试版静态快捷入口，并隔离与原版的外部自动化广播名称。
- 十类中文预设及确认的色值，类别备注保存用途说明。
- 新安装默认关闭并行计时，默认每 15 分钟循环提醒；保留用户主动关闭提醒的能力。
- 提醒时间相对开始时间计算，编辑后不改变节奏，迟到后跳过错过的节点；无活动或提醒关闭时撤销旧调度。免打扰结束后回到开始时间对应的下一节点。
- 新增 8 个提醒时间边界单元测试，并更新原有分类选择 UI 测试的数据。

最终验证结果：

| 检查 | 结果 |
| --- | --- |
| `:app:assembleBaseDebug` | 通过；最终资源打包的离线增量构建耗时 40 秒 |
| `:domain:test` | 163 个测试，失败 0、错误 0、跳过 0 |
| `ktlintCheck` | domain、core、data_local、feature_notification、feature_dialogs、app 六个模块通过 |
| 包内标识 | applicationId、显示名「生活时间钟·开发版」、三个桌面快捷入口及外部广播名称核对通过 |
| APK 签名 | apksigner 验证通过，包含 v1／v2 签名；当前是调试签名，不是最终发布签名 |
| 差异检查 | `git diff --check` 通过；本次修改文件已消除混合换行导致的整文件差异 |

开发预览 APK：`../.life-clock-tools/artifacts/life-time-clock-m1-preview.apk`，22,776,345 字节。
SHA-256：`8B0833813C9DA10135C9BB07A67170C2B81EDC53A4DAFD5909D0A921147CADA8`。

日志位于工作区 `.life-clock-tools`：`baseline-resume-build.log`、`m1-development-build.log`、`m1-final-build.log`、`m1-package-build.log` 和 `m1-signature-check.log`。可用下列命令复现主要检查：

```powershell
.\scripts\build-android.ps1 -Offline -Tasks ':app:assembleBaseDebug',':domain:test',':domain:ktlintCheck',':core:ktlintCheck',':data_local:ktlintCheck',':feature_notification:ktlintCheck',':feature_dialogs:ktlintCheck',':app:ktlintCheck'
```

`adb devices` 当前没有连接设备，因此没有执行仪器化 UI 测试或真机验收。安装并存、锁屏重开、通知权限、后台计时和备份恢复需要后续设备验证。

当前仍保留上游设置和规则入口；默认关闭并行计时不等于已完成 M3 的强制单主活动约束。M1 包尚不包含后续数据与界面功能，不用于首版全链路验收。

## M2 首批数据基础

已实现数据库版本 34 → 35 的迁移，以及对应领域模型、存储映射和必要编辑路径的保留逻辑：

- 已结束记录增加待整理／已确认状态、摘要、可空时区和模板引用；运行记录增加摘要、时区和模板引用。
- 标签增加普通／人物／环境用途；运行记录与已结束记录的数值标签关联增加可空填写时间。旧标签用途保持普通，不根据名称猜测人物、环境或评分身份；旧值（包括空值及范围外的既有数值）保持原样。
- 增加照片关联表，保存已结束或运行记录中的一个归属、相对路径、SHA-256 与展示顺序。照片归属使用外键；路径与哈希在模型入口校验。
- 迁移仅将正时长、15 分钟整数倍、结束不晚于迁移时刻，且与其他记录及运行记录无重叠的旧区间标为已确认。相邻边界不算重叠；未分类、跨夜本身不阻止确认。其他旧区间保持真实起止并标为待整理。
- 旧数据没有时区与评分填写时间时保存为空，不编造历史数据。新建记录保存创建时的设备时区；时间边界保留毫秒，不再在存储或运行缓存中截掉精度。
- 编辑保留原摘要、时区、模板引用和标签用途；修改起止后回到待整理。运行记录结束时转移摘要、时区、模板引用及原标签值和填写时间。
- 记录写入改用 Room Upsert，更新同一身份不会先删除记录，从而避免外键连带删除照片；继续保留原插入接口返回的记录 ID。

本地验证使用 SQLite JDBC 执行生产迁移 SQL，分别从版本 34 及版本 6 的导出结构开始，再与 Room 生成的版本 35 结构比对。检查列、主键、明确声明的默认值、索引及外键，并逐项核对历史起止、身份、备注、标签数值和空值。

| 检查 | 结果 |
| --- | --- |
| `:domain:test` | 164 个测试，失败 0、错误 0、跳过 0 |
| `:data_local:testDebugUnitTest` | 9 个测试，失败 0、错误 0、跳过 0；覆盖迁移、结构、照片外键、字段往返及附件路径边界 |
| 相关模块格式检查 | domain、data_local、feature_change_record、feature_change_running_record、feature_change_record_tag、app 六个模块通过 |
| 应用与设备测试包 | `:app:assembleBaseDebug` 与 `:app:assembleBaseDebugAndroidTest` 通过；最终离线增量构建耗时 2 分 37 秒 |
| APK 标识与签名 | 包名、显示名、最低／目标系统版本核对通过；v1／v2 签名通过，与 M1 开发包使用同一证书 |
| Android 仪器化测试 | 新增迁移数据保留及同一身份更新不丢照片的测试，测试包编译通过；未连接设备，尚未运行 |
| 差异检查 | `git diff --check` 通过 |

开发预览 APK：`../.life-clock-tools/artifacts/life-time-clock-m2-data-preview.apk`，19,917,638 字节。
SHA-256：`C712DA057F14433A970C8C3DB4E4418EC2B8C7EED1DB914617B40D8A8793F4B0`。
设备测试 APK：`../.life-clock-tools/artifacts/life-time-clock-m2-data-tests.apk`。运行前需要安装对应开发预览包。

验证日志：`.life-clock-tools/m2-data-check-2.log`、`m2-final-build.log`、`m2-package-build.log`、`m2-verified-build.log`、`m2-signature-check.log`。前几份日志包含已修复的失败：综合样例中的跨夜记录误放在持续计时之后、首次离线构建设备测试包缺少缓存依赖，以及新增测试误用数据库关闭接口；最终测试报告与构建日志为验收依据。没有更换上游工具链或向应用运行依赖加入 SQLite JDBC。

依赖已缓存后可复现本轮检查：

```powershell
.\scripts\build-android.ps1 -Offline -Tasks ':data_local:testDebugUnitTest',':domain:test',':data_local:ktlintCheck',':domain:ktlintCheck',':feature_change_record:ktlintCheck',':feature_change_running_record:ktlintCheck',':feature_change_record_tag:ktlintCheck',':app:ktlintCheck',':app:assembleBaseDebug',':app:assembleBaseDebugAndroidTest'
```

未完成范围：模板和周回顾持久化、内置快乐／意义标签身份、版本化完整快照与共用验收样例。照片表和转移查询只是数据基础；选图／拍照、停止或切换时的附件事务、双评分选择器及整理确认界面尚未接入。原有文本备份没有扩展到新增字段和照片，不能作为本版本的完整恢复方案。

M2 仍为进行中；M3–M8 尚未完成，开发包不等于首版交付。

## 启动弹窗修复

2026-10-02，用户在安卓启动时看到「违反了自我强制执行的严格模式（StrictMode）政策」弹窗，并提供了原文截图。代码核查确认上游 `TimeTrackerApp.initStrictMode()` 在 debug 构建中开启了 `penaltyDialog()`；该 API 会在检测到违规时显示开发调试弹窗，见 [Android 官方说明](https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder#penaltyDialog())。

本次只移除弹窗处罚，保留线程及 VM 检测与日志。不修改数据、数据库版本、包名或签名配置。具体触发的启动调用尚未定位，需要设备上的 StrictMode 堆栈；去掉弹窗不等于该调用已完成性能修复。

修复包：`../.life-clock-tools/artifacts/life-time-clock-m2-startup-fix.apk`，20,195,961 字节。后续安装优先使用此包，直接覆盖现有开发版以保留记录。
SHA-256：`54D02BAD5031E2F0E16984B3822CC0B03F28DC1A74774981A4F80FD92F5845A4`。

验证：`:app:ktlintCheck` 与 `:app:assembleBaseDebug` 通过，离线构建耗时 2 分 42 秒；核查编译后的 Application 字节码，确认不再调用 `penaltyDialog()`，线程及 VM 检测与日志仍在。包名、显示名、系统版本及 v1／v2 签名验证通过，签名证书与已发 M2 包一致。日志为工作区 `.life-clock-tools/m2-strictmode-fix-build.log` 与 `m2-strictmode-fix-signature.log`。本次未改领域和数据层，未重复运行上一轮 173 个测试。

当前 `adb devices` 没有设备，尚未复现用户手机上的启动过程；不能据编译和签名检查宣称真机启动已经通过。

## 新增回顾范围

方案与开发计划已加入保留／调整选择、重要活动置顶和组合活动记录。它们不增加开始记录时的必填项。未来计划不算实际经历，标签及多个目的不重复累计时长。新增回顾界面、存储、备份和电脑展示尚未实现。

## 后续实施

M1 已通过编译与自动检查，设备验收待进行。M2 首批记录、标签和照片存储已落地，接下来补模板、回顾和快照契约，再进入 M3 的单主计时与整理流程。M3–M8 的完整范围仍按上一级开发计划执行。
