# 完整快照格式 v1

状态：M6 已接入本地 ZIP 导出／恢复及 CSV／ICS 扩展，正在完成交付验证；服务器接口属于 M7，尚未接入。样例只用于开发验证，不初始化用户数据，也不用于网页“尚无备份”时的假数据。

## 编码与身份

JSON 使用 UTF-8。`schemaVersion` 为整数 `1`；`dataSetId` 是数据集 UUID，整体恢复时保留；`snapshotId` 是本次快照 UUID，同一次重试保持不变；`createdAt` 为 Unix 毫秒，是所有统计与运行区间的截止时刻。

本地实体仍使用 `Long` ID，公开 JSON 中的实体 ID 使用十进制字符串，避免 JavaScript 丢失整数精度。正 ID 不含符号或前导零；记录的 `typeId = "0"` 表示未分类，不新增类别。时间戳使用整数毫秒，必须能被客户端准确表示。空值保持 `null`，不将空评分变成 0 或默认分。

## 顶层集合

| 字段 | 内容 |
| --- | --- |
| `recordTypes` | 十类及用户自定义主类别；`id/name/icon/color/defaultDuration/note/hidden`，对应已有 RecordType |
| `categories` | 上游类别分组；`id/name/color/note` |
| `recordTypeCategories` | 主类别与分组关系：`recordTypeId/categoryId` |
| `tags` | `id/name/icon/color/iconColorSource/note/archived/valueType/valueSuffix/purpose`；用途为 GENERAL、PERSON、ENVIRONMENT，数值类型为 NONE 或 NUMERIC |
| `recordTypeTags`、`defaultRecordTypeTags` | 已有类别与标签关系：`recordTypeId/tagId` |
| `records` | `id/typeId/timeStarted/timeEnded/comment/tags/reviewState/summary/zoneId/templateId`；状态为 PENDING 或 CONFIRMED |
| `runningRecords` | `id/timeStarted/comment/tags/summary/zoneId/templateId`；沿用运行记录 ID 等于类别 ID 的现有模型，最多一个 |
| `ratingTagIds` | `happinessTagId/meaningTagId`，两者不同且引用已有数值标签；不按标签名称识别 |
| `photos` | `id/recordId/runningRecordId/relativePath/sha256/position/mimeType/sizeBytes`；归属二选一，必须引用存在的记录 |
| `templates` | 复用记录快捷方式 ID：`id/typeId/name/summary/comment/tags`；不包含照片或内置评分标签 |
| `settingShortcuts` | 保留上游设置快捷方式：`id/action`；动作名称沿用已有枚举，恢复不绕过单主活动约束 |
| `weeklyReviews` | `id/weekStart/zoneId/timeAbundance/pleasantRecords/unpleasantRecords/keep/adjust/attentionRecordIds/pinnedTemplateIds` |
| `trials` | `id/reviewId/source/description/relatedRecordIds`；回顾删除后 reviewId 可空，尝试文字继续保留 |
| `businessSettings` | 记录排序、显示、提醒与回顾所需设置，沿用原属性名称及单位；99 个字段的名称、存储键及类型已冻结在 `data_local/snapshot/SnapshotPrefs.kt`。设备权限、URI、自动备份状态、设备集成及服务器凭据不导出 |
| `upstreamData` | 可选的 Android 上游兼容数据，含 `databaseVersion` 与允许表名下的原始行；用于保留目标、规则、收藏、筛选、旧字段等。数据库整数存为字符串。核心集合与兼容行必须一致，未知表／列和冲突值拒绝；不导出内部恢复标记 |
| `repairRecordIds` | 显式标明原有异常区间的记录 ID；必须与区间校验结果一致。恢复预览列出数量，保留起止和内容，以待整理状态保存，不自动凑整或删除 |

颜色保留 `colorId` 和 `colorInt` 两个属性，后者为有符号 ARGB 整数的字符串或空串，与现有 AppColor 保持一致。提醒间隔属性 `activityReminderDuration` 沿用秒单位；事件和评分时间使用毫秒。

每条记录的 `tags` 为 `{tagId, numericValue, ratedAt}` 列表，同一标签在一条记录中只出现一次。两个内置评分的值只能是 1–5 整数或 null；其他旧数值标签保持原数值，不套用评分范围。

旧快照缺少模板、设置快捷方式、周回顾、尝试或关系集合时，读取为 `[]`；缺少可选回顾字段时读取为空，不补造历史。未知 schemaVersion 在修改当前数据前拒绝。数据库内回顾的 `content_json` 只是内部存储，不能直接当成完整快照。

## 回顾引用与图片

经历引用为 `{recordId, caption}`。保留／调整为 `{text, recordId, templateId}`，两个来源最多选择一个；可以仅有文字。来源记录、模板、置顶及后续经历引用属于软引用，允许来源已删除；保存的文字和 caption 不跟随删除，也不通过名称重新匹配。每周一及回顾时区共同确定一份回顾，置顶允许 0–3 个不同模板。

图片清单保存相对路径、SHA-256、MIME 和字节数；不保存手机绝对路径。路径必须处于 `photos/` 内，拒绝绝对路径、路径穿越与反斜杠。文件存在、大小和哈希均验证后才能恢复或提交；缩略图可重建。相同文件可被多个照片关联引用，时间账本仍按事件身份计数。

本地 ZIP 第一项为 UTF-8 `snapshot.json`，其后仅包含清单照片及对应目录。重复条目、未知文件、损坏图片、错误大小／哈希和不支持版本在替换前拒绝。清单上限 64 MiB，单张照片沿用 50 MiB 导入上限；恢复按清单逐项限制解压长度并核查空间。照片在公开快照中使用 `photos/<sha256>.<格式扩展>`，恢复旧快照的安全路径时也转换为内容哈希路径；已存在不同内容的目标文件不覆盖。

运行记录展示裁切到 createdAt，恢复也只保留至该时刻的实际区间；继续记录从恢复当下新开。零时长运行段的评分和照片必须在恢复预览中明确安排归属。

恢复将运行记录转换为新 ID 的结束记录，末端为 createdAt、状态为待整理，标签／评分和照片转移到该记录；零时长段保留为待修复，不继续累计恢复之后的空档。

## 本地恢复提交与中断处理

先暂存并验证整个包，在临时数据库检查结构和外键，再提供替换预览。用户确认后，保存恢复前完整 ZIP，并支持从备份菜单导出。不可变的哈希照片先写入私有目录，原照片不覆盖；随后在一个 Room 事务中替换业务数据。

这一实现使用不可变媒体来避免数据库文件与照片目录的双重切换。共享设置跨数据库事务的中断一致性通过原设置日志与版本 37 的内部 `restore_operation_id` 保证：操作 UUID 每次独立生成；启动检查数据库标记，未提交则恢复原设置，已提交则保留新设置。该标记不属于公开快照，也不改变 dataset ID。

恢复期间阻止计时／编辑写入；提交后清理全部数据缓存并刷新通知与界面。数据库或设置写入失败时回滚；复制失败不会覆盖原媒体。未引用的新媒体可能暂留，以免删除仍被另一份数据使用的文件；原有效记录不受影响。

CSV 是带上下文的查看出口，包含摘要、备注、人物／环境、双评分及评分时间、时区、状态和照片数量，空评分不变成 0；完整恢复使用 ZIP。ICS 每个经历只有一个带 dataset ID 的稳定 UID，带 UTC 起止及体验描述，使用 CRLF、转义与 UTF-8 安全折行，[格式依据 RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)。异常区间不导出为有效日历事件。

## 共用样例与预期

[快照样例](fixtures/snapshot-v1.json) 与 [预期结果](fixtures/expected-v1.json) 覆盖跨午夜、未分类、待整理、进行中、空评分、组合活动、多标签、照片、非空回顾及尝试关联。周起点为 2026-10-05 00:00（Asia/Shanghai），快照截止为 2026-10-06 10:00。

- 已过去 34 小时，已记录区间并集为 1 小时 57 分 40 秒；未记录为 32 小时 2 分 20 秒。
- 已确认 90 分钟、待整理 12 分 40 秒、进行中 15 分钟，分别展示。
- 已确认事件 4 条；快乐和意义各已评 2 条、未评 2 条；跨夜和组合活动不增加评分样本。
- 洗碗＋播客以两个普通标签筛选，仍只有 1 条、30 分钟。

样例由 `fixtures/build_fixture.py` 生成，`fixtures/test_fixture.py` 验证引用、图片、时间裁切和预期值。它们只验证协议样例；手机和网页统计实现仍需独立对照同一预期结果。
