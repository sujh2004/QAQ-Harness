# DevPilot

DevPilot 是面向企业研发与故障分析的多 Agent 平台。本目录按《DevPilot 企业研发多 Agent 平台实施规格书》从 Phase 0 开始实现。

当前状态：Phase 9 完成，MVP 闭环。Spring Boot 后端 + Vue 3 前端共八个页面；追加式 `session_event` 事件流、turn/step 状态机、取消与重启恢复、Tool 注册表与执行管线；项目 CRUD、日志查询、会话与消息投影、测试用例；七个证据工具、五个委派工具与两个知识检索工具、`demo-project/order-demo` 演示仓库与两套故障剧情；Supervisor 与五个专业 Agent；Skill 市场、沙箱与安装审批链；per-project 向量知识库；SSE 流式对话、Agent 轨迹实时展示与 `Last-Event-ID` 断线续传；Docker 全栈一键启动。195 个后端测试全绿，五个演示问题真实跑通——见[测试报告](docs/test-report.md)。没有伪造接口。

## 三分钟跑起来

只需要 Docker：

```powershell
cd devpilot
Copy-Item .env.example .env      # 然后把 DASHSCOPE_API_KEY 填进去
./scripts/bootstrap.ps1
```

macOS / Linux 用 `./scripts/bootstrap.sh`。脚本会检查 Docker、构建镜像、等后端健康检查通过，最后打印访问地址。首次约 5–10 分钟（下载依赖），之后几十秒。

- 前端 <http://localhost:5173>
- 后端 <http://localhost:8080/api/v1/health>
- MySQL `localhost:3307`（devpilot / devpilot）

数据库的表结构与演示数据由 MySQL 自己的初始化机制在首次启动时载入，知识库的七份演示文档在后端启动时自动导入并建索引——**没有需要手工执行的初始化步骤**。

复位到初始状态：`./scripts/bootstrap.ps1 -Reset`（删卷重来）。停止：`docker compose --profile full down`。

**没有 API Key 也能启动**：所有只读页面照常工作，只有 Agent 调用和知识检索会明确报「未配置模型」——缺 key 就该看起来像缺 key，而不是像坏了。

## 环境要求

只用 Docker 跑演示：Docker Desktop（或 Docker Engine + Compose v2）即可，以下都不需要。

本地开发才需要：

- JDK 21+
- Maven 3.9+
- Node.js 22.19+ 或 24+
- npm 10+

## 本地开发

三种数据库选择，按需要挑一种。

**最快：demo profile（H2 内存库，免 Docker）**

```powershell
cd backend
$env:DASHSCOPE_API_KEY = "sk-..."
mvn spring-boot:run "-Dspring-boot.run.profiles=demo,dashscope"
```

进程内建库、载入演示数据、自动导入知识文档；退出即清空，所以两次演示之间重启就是复位。

**贴近生产：MySQL**

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
Set-Location backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,dashscope"
```

MySQL 映射为宿主机 `3307` → 容器 `3306`。表结构与演示数据在容器首次创建数据目录时自动执行；如果卷已存在而需要重新载入：

```powershell
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/schema.sql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/demo-data.sql
```

`sql/schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，可重复执行。`sql/demo-data.sql` 载入订单服务演示项目与两套故障剧情的日志，时间锚定当前时刻，不会过期。

后端必须从 `devpilot/backend` 目录启动——演示项目的仓库路径是相对的，依赖工作目录。

## 当前可用接口

```text
GET    /api/v1/health

POST   /api/v1/chat/stream                   # SSE，支持 Last-Event-ID 断线续传

GET    /api/v1/projects
POST   /api/v1/projects
GET    /api/v1/projects/{id}
PUT    /api/v1/projects/{id}
POST   /api/v1/projects/{id}/validate-repository

GET    /api/v1/projects/{id}/logs?serviceName=&level=&keyword=&traceId=&startTime=&endTime=&page=&size=
GET    /api/v1/projects/{id}/logs/error-summary?hours=24
POST   /api/v1/projects/{id}/logs/import

POST   /api/v1/projects/{id}/sessions
GET    /api/v1/projects/{id}/sessions
GET    /api/v1/sessions/{sessionId}/messages
GET    /api/v1/sessions/{sessionId}/runs
GET    /api/v1/sessions/{sessionId}/events?afterSeq=&limit=
POST   /api/v1/sessions/{sessionId}/turns/{turnId}/cancel

GET    /api/v1/projects/{id}/test-cases
GET    /api/v1/test-cases/{id}
DELETE /api/v1/test-cases/{id}

GET    /api/v1/projects/{id}/knowledge
POST   /api/v1/projects/{id}/knowledge/upload
POST   /api/v1/projects/{id}/knowledge/reindex
GET    /api/v1/projects/{id}/knowledge/search?query=&topK=
DELETE /api/v1/projects/{id}/knowledge/{documentId}

GET    /api/v1/skills/marketplace            # 需配置 SKILL_MARKETPLACE_URL
GET    /api/v1/skills
POST   /api/v1/skills
DELETE /api/v1/skills/{skillKey}
GET    /api/v1/projects/{id}/skills
POST   /api/v1/projects/{id}/skills
POST   /api/v1/sessions/{sessionId}/skill-approvals

POST   /api/v1/debug/agents/{agentName}     # 仅 dev / demo profile
```

## 流式对话（Phase 8）

`POST /api/v1/chat/stream` 返回 `text/event-stream`。它推送的**不是 token 流，而是 `session_event` 的实时投影**：每一帧都对应一条已经落库的事件，SSE 的 `id` 就是事件 `seq`。

```http
POST /api/v1/chat/stream
Content-Type: application/json
Accept: text/event-stream
Last-Event-ID: 42        # 可选，重连时带上

{"projectId": 1, "sessionId": "session_...", "message": "order-service 最近为什么报错？"}
```

```text
id: 43
event: agent_started
data: {"sessionId":"session_...","seq":43,"eventType":"agent_started","runId":"run_...","payload":{...}}
```

三条规则决定了它的行为：

- **先订阅再回放**。服务端在开始读历史之前就订阅了实时流，因此不存在「查询之后、订阅之前」这个丢事件的窗口。
- **seq 单调递增**。客户端按 `(sessionId, seq)` 幂等消费，重复帧直接丢弃；这让重连不需要任何约定。
- **慢客户端不拖慢 Agent**。订阅队列有界，塞满即结束该连接并让客户端用 `Last-Event-ID` 重连补齐——事件仍在日志里，一条都不会少。

不带 `message` 的请求表示**只附着不提问**：回放缺失事件后跟随正在进行的 turn，用于刷新页面或断线重连，不会重复开一轮对话。

取消仍走 `POST /api/v1/sessions/{sessionId}/turns/{turnId}/cancel`，`turnId` 从 `turn_started` 帧里拿。

前端用 `fetch` + `ReadableStream` 读流（`EventSource` 只能发 GET、无法带 body 与自定义头），页面刷新后由 `GET /api/v1/sessions/{id}/events` 回放重建同一条时间线。

## 已注册的 Tool

Tool 只能经 `ToolRegistry` 调用，目前没有对外的 HTTP 入口——Phase 4 的 Agent 才是调用方。

| Tool | 权限 | 说明 |
|---|---|---|
| `listFiles` | `CODE_READ` | 列出仓库目录下的文件 |
| `searchCode` | `CODE_READ` | 按关键词检索代码，返回文件、行号与上下文 |
| `readCodeFile` | `CODE_READ` | 读取单个文件的行区间 |
| `searchLogs` | `LOG_READ` | 按服务、级别、关键词、时间范围检索日志，limit ≤ 100 |
| `getLogByTraceId` | `LOG_READ` | 取一次请求的全部日志 |
| `getRecentErrorSummary` | `LOG_READ` | 按服务与异常类型聚合近期错误 |
| `searchKnowledge` | `KNOWLEDGE_READ` | 检索本项目知识库，返回带出处与相关度的段落 |
| `listKnowledgeDocuments` | `KNOWLEDGE_READ` | 列出项目已导入的知识文档 |
| `saveTestCases` | `TEST_CASE_WRITE` | 保存 Agent 设计的测试用例，**唯一的写操作** |

除 `saveTestCases` 外全部只读。代码读取限制在项目 `repositoryPath` 内：路径先规范化再解析符号链接并两次校验，`.env`、`*.key`、`*.pem`、`application-prod.yml`、`credentials.*` 等按文件名黑名单拒绝，只有白名单扩展名的文本文件可以打开，检索使用 Java NIO 而非 shell。

写操作要同时通过两道门：Agent profile 的 `allowMutating` 与 `app.runtime.tool.mutating-allow-list`，两者都在模型触及范围之外。

## Agent

由 `resources/agent-profiles/standard.yml` 与 `resources/prompts/agents/*.md` 组合而成，新增 Agent 是加 profile 条目，不改 Agent 循环。

| Agent | 可见工具 | 说明 |
|---|---|---|
| `supervisor` | 五个委派工具 | 判断该问谁、分派任务、汇总证据；看不到任何原始证据工具 |
| `debug_agent` | 代码 + 日志 | 通用排查助手 |
| `code_agent` | 代码 | 代码定位与调用链分析 |
| `log_agent` | 日志 | 故障现象定位与异常聚合 |
| `knowledge_agent` | 知识库 | 检索项目文档、规范与历史故障复盘，回答必须带出处 |
| `test_agent` | 代码 + 日志 + 知识库 + `saveTestCases` | 唯一允许写的 Agent |

Supervisor 的工具就是「问某个专业 Agent」（`askCodeAgent` 等），因此委派复用同一条 Tool 执行管线，专业 Agent 作为嵌套 run 出现，审计轨迹形成树。

## 技能市场

Agent 的能力可以在运行时扩展：从一个 HTTPS 清单安装脚本包，装完就是一个新工具。这条路径的每一步都是**人的决定**，模型一步也插不上手。

```
GET    /api/v1/skills/marketplace          # 浏览，什么也不安装
POST   /api/v1/skills                      # 安装：写盘并记录 sha256
GET    /api/v1/projects/{id}/skills        # 本项目启用了哪些
POST   /api/v1/projects/{id}/skills        # 启用 / 停用
POST   /api/v1/sessions/{id}/skill-approvals   # 在这次会话里批准执行
```

**三道人工闸门**，缺一不可：

1. **安装** — 有人从市场装下来。清单只能走 HTTPS：能被中途改写的市场就是一条代码执行通道。安装时校验运行时白名单、每个文件路径必须留在包内、入口必须是包里真实存在的文件，并记录内容的 SHA-256。
2. **项目启用** — 装了不等于能用。没启用时，**Agent 连这个工具的存在都看不到**——模型无法拒绝使用一个从没被展示过的东西。
3. **会话审批** — Agent 要执行它时，仍然需要人在这次对话里点头。审批只对这一个会话有效。

profile 里的 `allowSkills` 只声明「这类 Agent 可以用技能」——技能是运行时装的，profile 无法预先写出名字。装哪些由人按项目决定，跑不跑由人按会话决定。

**沙箱六道控制**：解释器白名单（不在名单上的运行时根本起不来）、入口路径两次校验、清空环境变量后只注入白名单项（模型密钥与数据库口令都拿不到）、参数走 stdin JSON 而不进命令行、每次执行给一个空的临时工作目录、超时即销毁整棵进程树并限制输出大小。沙箱同时把子进程的 I/O 编码钉死为 UTF-8，否则宿主机的代码页会把中文输出变成乱码交给模型当证据。

仓库自带两个可审阅的演示技能（`demo-data/skills/`）：`stacktrace-digest` 把 Java 堆栈压成异常链与最上层应用帧，`log-burst-window` 把时间戳画成分钟直方图定位爆发窗口。它们通过 GitHub raw 以 HTTPS 发布，`SKILL_MARKETPLACE_URL` 默认指向那份清单。改了脚本后重新生成清单：

```powershell
python scripts/build-marketplace.py
```

## 知识库（Phase 7）
每个项目一个独立向量库（`data/vector/project-{id}.json`），物理隔离而不是元数据过滤——检索结果永远只来自本项目。文档按 Markdown 标题切块、邻块重叠，每个段落携带出处（文档名、类型）与相关度分数；`similarityThreshold` 以下的命中按「未找到」处理，工具层明确告诉模型**不要用通用知识补充**。

导入与检索：

```
POST /api/v1/projects/1/knowledge/upload
{"documentName": "错误码规范.md", "documentType": "standards", "content": "# 错误码规范\n..."}

GET  /api/v1/projects/1/knowledge/search?query=优惠券 null 判空&topK=3
```

需要 Embedding 模型：激活 `dashscope` profile（`text-embedding-v3`）。未配置时知识接口返回 42202 并说明缺什么，其余功能不受影响。demo profile 启动时自动导入 `backend/src/main/resources/demo-knowledge/` 下的七份演示文档（架构、API/Java/测试规范、错误码、两份故障复盘）并完成索引，重复启动会重建索引而不是叠加。

dev / demo profile 下可单独调用某个 Agent：

```
POST /api/v1/debug/agents/log_agent
{"projectId": 1, "message": "最近有哪些 ERROR？"}
```

Agent 循环每轮从**已提交事件**投影模型历史，因此模型看到的内容一定先被记录；工具只经 `ToolRegistry` 执行，作用域、鉴权、超时与限额无法绕过；步数上限由 profile 决定。

运行 Agent 需要 `DASHSCOPE_API_KEY`，并激活 `dashscope` profile：

```powershell
$env:DASHSCOPE_API_KEY = "sk-..."
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,dashscope"
```

不激活时后端照常启动、所有读接口可用，只有 Agent 调用会明确报错「No chat model is configured」，不会返回编造的答案。DashScope 的自动配置在缺少 Key 时会直接抛异常，因此默认全部排除，由 `dashscope` profile 只打开 Chat 与 Embedding 两项。

契约测试使用脚本化的 Fake Provider，**不需要任何密钥**。

如果 `8080` 已被其他进程占用，可以改用 `8081`：

```powershell
$env:SERVER_PORT = "8081"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

此时健康检查地址为 `http://localhost:8081/api/v1/health`。若同时启动前端，请在前端终端设置 `$env:VITE_API_BASE_URL = "http://localhost:8081"`。

后端测试：

```powershell
Set-Location backend
mvn test
```

契约测试默认跑 H2，不需要 Docker，也不需要任何模型 API Key。若要用真实 MySQL 8 验证并发序号分配：

```powershell
mvn test -Pmysql-it
```

## 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

生产构建：

```powershell
npm run build
```

默认前端地址为 `http://localhost:5173`，后端地址通过 `VITE_API_BASE_URL` 配置。

八个页面：项目列表、项目概览（仓库校验与错误聚合）、**智能对话**（SSE 流式回答 + Agent 轨迹树）、**知识库**（导入、检索、重建索引）、日志检索、**测试用例**（Agent 生成并落库的回归方案）、**技能市场**（浏览、审阅源码、安装、按项目启停）、会话与事件流。对话页与知识库页需要后端激活 `dashscope` profile；其余页面不需要任何密钥。

## 配置

开发配置示例见 `.env.example` 与 `backend/src/main/resources/application-dev.yml.example`。真实 API Key、数据库密码和本地路径不得提交。

Phase 1 新增的运行时配置项：

| 配置 | 默认值 | 说明 |
|---|---|---|
| `app.repository.base-dir` | 后端工作目录 | 项目相对仓库路径的解析基准，避免把用户目录写进数据 |
| `app.repository.max-file-bytes` | `262144` | Code Tool 可读取的最大文件 |
| `app.repository.max-read-lines` | `2000` | 单次读取的最大行数 |
| `app.repository.denied-file-patterns` | 见配置 | 敏感文件黑名单，按文件名 glob 匹配 |
| `app.repository.searchable-extensions` | 见配置 | 允许打开的文本文件扩展名 |
| `app.runtime.recovery.enabled` | `true` | 启动时关闭上次进程遗留的悬挂 turn |
| `app.runtime.profile.version` | `standard@1` | 会话创建时固定的 Agent profile 版本 |
| `app.runtime.tool.default-timeout` | `30s` | Tool 未声明超时时的默认值 |
| `app.runtime.tool.max-result-items` | `100` | Tool 结果条数上限 |
| `app.runtime.tool.max-result-bytes` | `65536` | Tool 结果字节上限 |
| `app.runtime.tool.max-concurrent-executions` | `8` | Tool 执行线程池大小 |
| `app.runtime.tool.mutating-allow-list` | 空 | 允许写操作的 Tool 白名单；Phase 5 才会加入 `saveTestCases` |
| `app.runtime.chat.agent` | `supervisor` | 聊天请求分派到的 Agent；路由由 Supervisor 决定，不由浏览器指定 |
| `app.runtime.chat.stream-timeout` | `10m` | 单条 SSE 连接的最长存活时间，超时后客户端带 `Last-Event-ID` 重连 |
| `app.runtime.chat.max-concurrent-turns` | `4` | 同时运行的聊天 turn 数上限，超出立即拒绝而不是无界排队 |
| `app.runtime.chat.queue-capacity` | `256` | 慢客户端可积压的事件数，超出即结束连接让其重连补齐 |
| `app.runtime.chat.replay-limit` | `2000` | 单次回放读取的事件页大小 |
| `app.knowledge.vector-dir` | `./data/vector` | per-project 向量库持久化目录 |
| `app.knowledge.chunk-size` | `800` | 目标块长度（字符），先按标题切再按长度切 |
| `app.knowledge.chunk-overlap` | `150` | 相邻块的重叠字符数 |
| `app.knowledge.top-k` | `5` | 单次检索返回的最大段数 |
| `app.knowledge.similarity-threshold` | `0.6` | 相关度下限，低于按「未找到」处理 |
| `app.knowledge.seed-demo-documents` | `false` | 启动时导入内置演示语料；demo profile 与 Docker 栈开启，已有文档则跳过 |

## 文档

- 产品与实施规格：[`../docs/DevPilot_企业研发多Agent平台_实施规格书.md`](../docs/DevPilot_企业研发多Agent平台_实施规格书.md)
- 架构边界：[`docs/architecture.md`](docs/architecture.md)
- 开发说明：[`docs/development.md`](docs/development.md)
- **功能与性能测试报告**：[`docs/test-report.md`](docs/test-report.md)
- **演示脚本**：[`docs/demo-script.md`](docs/demo-script.md)
