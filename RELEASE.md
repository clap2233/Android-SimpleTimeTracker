# UI2 界面更新与可复现构建

当前版本：`1.59-life-clock-ui2`，versionCode 158，数据库 37，minSdk 21／targetSdk 35。正式候选包使用 `app.lifetimeclock.android`、既有发布证书及 R8 压缩；开发包仍使用 `.debug` 和既有开发证书。历史数据库迁移未修改，UI2 可以覆盖升级已安装的 RC1／RC2／UI1 正式签名包。

UI2 采用暖米白、森林绿、浅色卡片及三张原创本地奶油小猫插画，配套暖深绿主题。活动卡把类别原色放在图标底盘和色签中，周图保留类别原色。时间页使用柔和分段按钮，回顾按内容分组，详情保留直接可见的摘要、评分、照片与情境入口；设置六组采用图标底盘和说明，导航使用圆线图标与选中胶囊。没有改变用户保存的类别颜色、图标、排列设置或业务数据。界面实图与本轮验证见 [UI_REDESIGN.md](UI_REDESIGN.md)。

M8 正在验收；公网 443 与真机后台兼容仍未通过，不标为正式首版完成。准确产物哈希、检查结果和限制记录在 [DEVELOPMENT.md](DEVELOPMENT.md)。使用与迁移见 [USER_GUIDE.md](USER_GUIDE.md)。

RC2 补齐 Android 5–7（API 21–25）对服务器证书的信任：保留平台原有信任和主机名验证，额外使用官方 ISRG Root X1；Android 8 以上继续使用默认 HTTPS。根证书是公有证书，不含私钥；来源为 [官方 PEM](https://letsencrypt.org/certs/isrgrootx1.pem)，DER SHA-256 为 `96bcec06264976f37460779acf28c5a7cfe8a3c0aae11a8ffcee05c0bddf08c6`。该兼容选择依据 [官方说明](https://letsencrypt.org/docs/certificate-compatibility/)，在 API21／API35 验证生产链连接成功、错误 IP 与不受信链拒绝。诊断经受限 SSH 字节转发进行，不代表公网入口已通；不安装或信任本机 Avast 替换证书。

API21 的实际发布包经独立只读认证端点收到 401 并显示正确提示；经系统文件选择器恢复、重新导出完整 ZIP，身份、评分空值、照片及回顾往返比较通过。生产备份点击被自动审批拒绝，原因是可能发送记录／照片；本轮使用隔离测试端点替代，生产上传仍待单独验收。

RC1／RC2／UI1／UI2 共用签名证书 SHA-256：`d7af0e293bf86fc1fc898e3f223f30328f4b83e7a9aff4caaafe7f397a64cb9e`。本工作区的私密签名目录为 `../.life-clock-tools/private/m8/`，仅当前 Windows 用户与 SYSTEM 可访问；不要纳入源码交付。默认首版不展示番茄入口与目标页，恢复旧设置不会重新开启这些入口，原始设置和历史数据仍完整保留。

## 构建

工具链及准备步骤见 [README_LIFE_CLOCK.md](README_LIFE_CLOCK.md)。

```powershell
.\scripts\build-android.ps1 -Tasks ':app:assembleBaseRelease'
```

未签名 APK 为 `app/build/outputs/apk/base/release/app-base-release-unsigned.apk`。首次构建需联网下载已锁定依赖，缓存齐全后可加 `-Offline`。同样源码可构建，Android 打包时间／工具环境可能影响 APK 的逐字节哈希。

中文工作路径通过 ASCII junction 构建，别名已被其他目录占用时脚本会停止；指定干净的进程 `TEMP`／`TMP` 后重试，不删除未知目录。不要把私有工具缓存打进源码包。

## 私密发布签名

发布密钥与口令文件保存在仓库之外的私密目录；后续正式包升级必须继续使用相同证书。源码、公开交付包及服务器部署包均不包含私钥或口令。签名脚本读取口令文件，避免把口令放在命令行或日志。

```powershell
.\scripts\sign-release.ps1 `
  -UnsignedApk '.\app\build\outputs\apk\base\release\app-base-release-unsigned.apk' `
  -OutputApk 'D:\release\life-time-clock-ui1.apk' `
  -KeyStore 'D:\private\life-clock-release.p12' `
  -PasswordFile 'D:\private\signing-password.txt'
```

默认别名 `life-clock`，PKCS12 密钥使用与密钥库相同的口令。脚本使用本地 SDK build-tools 34.0.0 的 apksigner，签名验证成功才生成目标文件，并输出 SHA-256。已存在的目标文件会拒绝覆盖。私钥和口令需要由项目所有者在独立安全位置保存，否则将无法发布同证书升级包。

## 验证开发数据迁移

仅在**隔离模拟器**执行，测试准备会清空测试开发包数据。正式候选包与开发包同时安装。

1. 构建 `:app:assembleBaseDebug` 与 `:app:assembleBaseDebugAndroidTest`，安装到指定模拟器。
2. 运行 `LifeClockFullBackupTest#exportReleaseMigrationFixture` 并传入 `-e releaseFixture true`。它通过生产备份路径输出测试开发包 cache 中的 `m8-migration.zip`，涵盖评分空值、情境标签、两张照片、模板、周回顾、已删除来源文字及运行记录。
3. 把 ZIP 放入模拟器下载目录，通过发布 APK 的**真实系统文件选择器**恢复；核对预览、回顾、照片，重启应用再导出完整 ZIP。
4. 使用 `scripts/verify-migration.py` 比较源 ZIP 与发布 APK 导出的 ZIP。验证数据集和固定身份保持、业务集合不丢、运行记录截止到源快照时刻、附件正确转移及图片哈希一致。

```powershell
python .\scripts\verify-migration.py source.zip restored.zip
```

这验证独立包名及发布压缩后的实际迁移；用户手机上的真实数据仍按使用说明手动迁入，保留原开发版和备份。

## 源码与服务

源码交付包含当前未提交的实际改动、Gradle Wrapper、锁定依赖、数据库 schemas、共用样例、原生测试及部署脚本；保留上游 [GPL-3.0-or-later](LICENSE.md) 和版权信息。上游固定提交记录在 README 中。不包含 `.git`、缓存、构建目录、用户备份、服务器凭据及签名材料。

服务端可复现步骤、协议与权限检查见 [cloud/README.md](cloud/README.md)。手机凭据 `writeToken` 与电脑凭据 `readToken` 分开保存；仅发布 APK／源码不需要复制私密连接文件。
