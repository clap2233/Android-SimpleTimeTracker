# M7 云备份与电脑只读

手机是唯一编辑端，电脑读取最后一次成功提交的快照。没有备份时显示空状态，不自动注入样例。

## 本地构建

沿用 Node.js 22（至少 22.12）、Fastify 5、TypeScript 和 Vite 8；版本锁定在 `package-lock.json`。网页无需访问外部字体或 CDN。

```powershell
npm.cmd ci
npm.cmd run check
npm.cmd test
npm.cmd run build
```

启动服务前设置 `LIFE_CLOCK_DATA_DIR`、`LIFE_CLOCK_WEB_DIR`、`LIFE_CLOCK_WRITE_TOKEN` 和 `LIFE_CLOCK_READ_TOKEN`。两个凭据必须不同且不少于 32 字符；生产安装脚本生成独立随机凭据，不打印其值。默认端口 4178，仅绑定 `127.0.0.1`。

## 备份协议

- `GET /api/backups/latest`：读取最近成功快照的固定身份。
- `POST /api/backups/prepare`：传入 `snapshotJson` 原始字符串和 `parentSnapshotId`（首次为 null），校验完整清单并返回缺图哈希。
- `PUT /api/backups/{snapshotId}/photos/{sha256}`：只接收该快照引用的图片，验证长度、SHA-256、格式和解码结果。
- `POST /api/backups/{snapshotId}/commit`：校验父版本及全部图片，先保存不可变快照，再原子发布 latest。
- `GET /api/backups/{snapshotId}`、`GET /api/backups/{snapshotId}/photos/{sha256}`：固定读取同一快照；不能读取未引用图片。

所有 API 使用 `Authorization: Bearer …`。只读凭据不能访问写接口；凭据不放入 URL 或请求日志。站点静态文件不含备份、照片或凭据。

手机重试保留同一个 snapshotId、截止时间及父版本，不将之后的新记录混入旧上传。放弃上传只删除本次任务；本地记录和服务器历史均保留。同 ID 不同内容、旧父版本及同数据集较早快照不能覆盖当前 latest。历史提交重试不重新置为最新；发布前中断可按父版本恢复提交。

记录、标签、照片、模板、周回顾和尝试均使用 v1 快照；保留 Android 原始兼容数据，并核对核心集合与兼容行。服务器清单上限 64 MiB（HTTP JSON 包装上限 128 MiB）；单图上限 50 MiB、解码上限一亿像素。支持 PNG、JPEG、WebP、GIF、BMP、HEIC/HEIF 和 AVIF，浏览器不支持的图片提示回到手机查看。大小上限属于实现边界，失败不修改已有备份。

服务器采用一个写进程，部署服务通过 `flock` 保证同一数据目录不会有两个写者；应用内部串行写入。提交和 latest 使用同目录临时文件、文件同步与原子重命名；Linux 同步目录。历史快照与照片不自动删除，失败留下的未发布文件不作为 latest。磁盘损坏或空间耗尽仍需运维检查。

## 网页口径

网页所有统计截止于快照 `createdAt`，之后的时间不算漏记。类别默认已确认，待整理与进行中单列；评分按唯一已确认经历计数，空评分保留为空。周视图涵盖 00–24 时，跨午夜裁切时间但保留一条经历身份；组合活动和多个标签不重复计时。

人物／环境筛选为组内任一、组间同时满足。保留／调整、重要活动、自选经历和尝试只读展示；来源删除时保留 caption 和文本，不按名称猜测关联。计划描述不计入实际时长或次数。网页用文本节点呈现用户输入，不执行其中的 HTML。

## 腾讯云部署

部署文件位于 `deploy/`，目标 `124.221.50.129`。现有 80 端口默认站点保留，Certbot 使用其 webroot 验证 IP；新增 443 HTTPS 代理至本机 4178。Linux Node 位于 `/opt/life-clock/node`，Python/Certbot 位于独立虚拟环境，不替换系统运行时。

应用 `/opt/life-clock/app`；数据 `/var/lib/life-clock/data`；读写凭据 `/etc/life-clock/service.env`；服务 `life-clock.service`；每六小时检查证书的 `life-clock-renew.timer`。IP 证书采用短有效期，续期演练及 Nginx 重载是验收内容。[官方 IP 证书说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)

手机与电脑的连接文件由安装脚本生成在服务器用户私有目录，只供所有者使用，不加入源码或公开静态站点。需要在腾讯云安全组／轻量服务器防火墙允许入站 TCP 443；如果服务器内部 HTTPS 正常、公网超时，不能宣称公网联调完成。

```bash
sudo systemctl status life-clock.service life-clock-renew.timer
sudo /opt/life-clock/certbot/bin/certbot renew --cert-name 124.221.50.129 --dry-run --run-deploy-hooks --deploy-hook '/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx'
```

## 验证范围

`test/backup.test.ts` 覆盖共享统计样例、原生 ZIP 的兼容行、跨夜与夏令时、非法内容、读写权限、坏图／缺图、BMP、幂等、旧请求及磁盘故障。Android 原生测试另验证加密凭据、断网、中断任务恢复、冻结快照、实际 Fastify 服务上传及设置入口。生产服务器不使用这些开发样例。

阶段实际结果与未通过项以仓库 `DEVELOPMENT.md` 为准；部署成功、服务内部 HTTPS、公网可达、真机上传分别记录，不互相替代。
