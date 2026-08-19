# DevPilot

DevPilot 是面向企业研发与故障分析的多 Agent 平台。本目录按《DevPilot 企业研发多 Agent 平台实施规格书》从 Phase 0 开始实现。

当前状态：Phase 6 Supervisor。已提供 Spring Boot 后端、Vue 3 前端与四个可用页面、MySQL 开发容器与自包含 demo profile；追加式 `session_event` 事件流、turn/step 状态机、取消与重启恢复、Tool 注册表与执行管线；项目 CRUD、系统日志查询、会话与消息投影、测试用例；七个证据工具与四个委派工具、`demo-project/order-demo` 演示仓库；Supervisor 与四个专业 Agent。Knowledge Agent 与 RAG 在 Phase 7，SSE 聊天在 Phase 8，目前没有伪造接口。

## 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 22.19+ 或 24+
- npm 10+
- Docker Compose（仅在需要本地 MySQL 时）

## 启动后端

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/schema.sql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/demo-data.sql
Set-Location backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

MySQL 映射为宿主机 `3307` → 容器 `3306`。健康检查：`GET http://localhost:8080/api/v1/health`。

`sql/schema.sql` 使用 `CREATE TABLE IF NOT EXISTS`，可重复执行；每次新增表后重新执行即可。`sql/demo-data.sql` 载入订单服务演示项目与两个故障剧情的日志，可重复执行。

## 当前可用接口

```text
GET    /api/v1/health

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

POST   /api/v1/debug/agents/{agentName}     # 仅 dev / demo profile
```

`POST /api/v1/chat/stream` 与知识库接口属于后续 Phase。

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
| `saveTestCases` | `TEST_CASE_WRITE` | 保存 Agent 设计的测试用例，**唯一的写操作** |

除 `saveTestCases` 外全部只读。代码读取限制在项目 `repositoryPath` 内：路径先规范化再解析符号链接并两次校验，`.env`、`*.key`、`*.pem`、`application-prod.yml`、`credentials.*` 等按文件名黑名单拒绝，只有白名单扩展名的文本文件可以打开，检索使用 Java NIO 而非 shell。

写操作要同时通过两道门：Agent profile 的 `allowMutating` 与 `app.runtime.tool.mutating-allow-list`，两者都在模型触及范围之外。

## Agent

由 `resources/agent-profiles/standard.yml` 与 `resources/prompts/agents/*.md` 组合而成，新增 Agent 是加 profile 条目，不改 Agent 循环。

| Agent | 可见工具 | 说明 |
|---|---|---|
| `supervisor` | 四个委派工具 | 判断该问谁、分派任务、汇总证据；看不到任何原始证据工具 |
| `debug_agent` | 代码 + 日志 | 通用排查助手 |
| `code_agent` | 代码 | 代码定位与调用链分析 |
| `log_agent` | 日志 | 故障现象定位与异常聚合 |
| `test_agent` | 代码 + 日志 + `saveTestCases` | 唯一允许写的 Agent |

Supervisor 的工具就是「问某个专业 Agent」（`askCodeAgent` 等），因此委派复用同一条 Tool 执行管线，专业 Agent 作为嵌套 run 出现，审计轨迹形成树。

Knowledge Agent 需要 RAG 工具，随 Phase 7 一起加入——没有工具的 Agent 只是空壳。

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

不激活时后端照常启动、所有读接口可用，只有 Agent 调用会明确报错「No chat model is configured」，不会返回编造的答案。DashScope 的自动配置在缺少 Key 时会直接抛异常，因此默认全部排除，由 `dashscope` profile 只打开 Chat 一项。

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

## 文档

- 产品与实施规格：[`../docs/DevPilot_企业研发多Agent平台_实施规格书.md`](../docs/DevPilot_企业研发多Agent平台_实施规格书.md)
- 架构边界：[`docs/architecture.md`](docs/architecture.md)
- 开发说明：[`docs/development.md`](docs/development.md)
