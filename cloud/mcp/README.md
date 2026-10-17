# 生活时间钟：Codex 只读分析

手机手动备份成功后，用 `begin_analysis` 固定快照，再用 `query_snapshot` 查询经历、规划、回顾或统计。默认范围为备份时刻前七天；注明数据截至时间与缺失信息。`read_context` 提供章节目录或分页笔记，原始材料与产品原则分开。

Node.js 22 运行 `server.mjs`，STDIO 只输出 JSON-RPC，仅发出固定路径的 HTTPS GET，不接受任意 URL 或写操作。`launch.mjs` 从仓库外私有 JSON 读取 `serverUrl`／`aiToken`。不要使用手机写入凭据或浏览器凭据。

```powershell
codex mcp add life-clock -- node --use-system-ca C:\path\to\cloud\mcp\launch.mjs C:\private\ai-connection.json
```

本机已使用绝对 Node 路径注册，连接文件在工具目录 `private/m7/`，不随源码交付。配置依据 [Codex MCP 官方说明](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)。

本机 Node 22.23.3 支持 `--use-system-ca`。Windows 上的 Avast HTTPS 检查使用系统信任证书，Node 默认信任库不包含该证书，因此这里只为 MCP 进程启用系统信任库，仍验证证书和服务器身份；不设置 `NODE_TLS_REJECT_UNAUTHORIZED=0`。其他机器按其可信环境配置，无须导入本机证书。

2026-10-17 公网 HTTPS 健康检查通过，新启动的 STDIO 进程实测读到四章 Context，并正确返回“尚无成功备份”。当前 Codex 对话内的实际工具也已连接成功，返回相同的无备份提示。修改启动参数后已有进程需要重新连接才会生效。工具发现、服务权限、备份存在、网络可达和实际分析分别验收；尚未完成真实备份分析。

分析应区分记录事实、可能解释、可尝试的调整和缺失信息。不按类别推定负意义，不把空评分／恢复感当零，不自动改计划。
