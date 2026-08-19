# DevPilot architecture

DevPilot 是模块化单体：Vue Web 通过 REST/SSE 访问 Spring Boot 应用，项目、日志、知识库与测试用例属于产品模块，Agent Runtime 属于可组合的运行时模块。

## 参考边界

本项目参考 `deepseek-harness-master` 的架构原则，但不依赖或修改其源码，不承诺兼容其 Cordis 插件、API、配置或会话格式。DevPilot 保留 Java 21、Spring Boot、Spring AI Alibaba 与 Vue 3 技术栈。

采用的原则包括：

- 能力按 Service Definition、Service Provider、Consumer 三个角色设计；
- 会话事件是模型上下文和 UI 时间线的唯一运行事实；
- 生命周期统一为 session、turn、step、model/tool；
- Tool 通过作用域注册表和受控执行管线调用；
- Agent 的 Prompt、模型路由和 Tool 视图由版本化 profile 组合；
- 权限由代码策略强制执行，写操作预留 Human-in-the-loop 审批。

## 模块依赖

```text
web/controller
      ↓
application services
      ↓
agent runtime definitions
      ↓
capability providers / persistence / model adapters
```

控制器不直接调用 Mapper；Agent 不直接调用具体 Provider；Tool 不直接推送 SSE；Spring AI Alibaba 类型不扩散到产品模块。

## 运行时包结构（Phase 1）

```text
com.devpilot.runtime
├── lifecycle/   turn/step/run/tool 的状态词汇、状态机、SessionLifecycleService、重启恢复
├── session/     SessionEvent、事件类型、payload、codec、SessionEventStore
│   └── persistence/  session_event 与 session_stream 的 MyBatis-Plus 实现
├── projection/  把事件流折叠成 SessionProjection（消息、turn、step、Agent 树、Tool 状态）
├── tool/        ToolDefinition、ToolScope、ToolPolicy、DefaultToolRegistry 执行管线、限额与脱敏
├── model/       ModelGateway 与请求、响应、可观测元数据
├── approval/    ApprovalService；MVP 默认拒绝写操作
└── stream/      SSE envelope、SseFrame 与旧 UI 事件名映射

com.devpilot.agent.runtime  AgentRuntime 接口（实现见 Phase 4）
```

依赖方向：`lifecycle` 只有状态词汇，不依赖其他运行时包；`session` 依赖 `lifecycle`；`projection`、`tool`、`stream` 依赖 `session`；产品模块只依赖这些接口，不依赖厂商 SDK。

## 事件流与序号分配

`session_event` 是运行事实的唯一来源，只追加不更新。聊天记录、Agent 运行树、Tool 调用记录与 SSE 推送都由 `SessionProjector` 从事件折叠得到，因此“实时状态”和“重放状态”由同一个函数产生，不会漂移。

序号由 `session_stream` 表分配：追加事务先 `SELECT next_seq ... FOR UPDATE` 锁住会话行，预留一段连续序号，再插入事件。规格书禁止的“先查 `MAX(seq)` 再无锁加一”不出现在代码中，`uk_session_seq(session_id, seq)` 只作为兜底约束。同一批事件在一个事务内提交，崩溃不会留下半个 turn。

`session_stream` 同时保存会话启动时固定的 profile 版本与能力集合，保证重放时语义不漂移。事件时间统一以 UTC 存入 `DATETIME(3)`，与服务器时区无关。

payload 按 `event_type + schema_version` 反序列化。未知事件类型一律拒绝；已知类型的未知版本对关键事件拒绝，对非关键事件（`assistant_delta`、`runtime_error`）保留原文并在投影中跳过。

## 生命周期与恢复

`turn → step → model/tool` 的状态迁移由枚举上的 `canTransitionTo` 约束，非法迁移抛 `IllegalLifecycleTransitionException`，不允许调用方靠字符串猜测状态。

`endTurn` 在写入 `turn_ended` 前，先在同一批事件里关闭该 turn 遗留的 Tool 调用、Agent 运行和 step，保证每个开始事件都有终态。取消是幂等的：已终态的 turn 再次取消不追加事件，直接返回当前终态。

`RuntimeRecoveryService` 在应用就绪时扫描只有 `turn_started` 没有 `turn_ended` 的会话，以 `ABORTED_BY_RESTART` 关闭它们，不假装任务仍在运行。无法解码的会话被记录为错误并计数，不静默跳过。

## Tool 执行管线

所有模型发起的调用只能经过 `ToolRegistry.execute`：

```text
作用域解析 → 参数绑定与 Bean Validation → 追加 tool_call_requested
  → ToolPolicy 鉴权 → 需要时走 ApprovalService
  → 并发与超时控制 → 执行 Provider
  → 结果截断与脱敏 → 追加 tool_call_finished
```

未知 Tool、越权 Tool、参数非法、超时和 Provider 异常都归一化为结构化结果，并同样留下成对事件，被拒绝的调用与成功的调用一样可审计。Provider 抛出的任意异常只把异常类型交给模型，完整堆栈留在服务端日志；`ToolExecutionException` 用于 Provider 主动给出可展示的安全消息。

可见性与可执行性是两道检查：profile 决定模型能看到哪些 Tool，`DefaultDenyToolPolicy` 在执行时重新鉴权，默认拒绝未知 Agent、缺失权限和一切写操作，只有显式白名单加上允许写的作用域才放行。`ToolScope.narrow` 只做交集，`requireNarrowerThan` 拒绝任何试图扩权的 profile。

## 能力三角色

每个可替换能力分成三层，Phase 3 的代码检索是第一个完整例子：

| 角色 | 类 | 责任 |
|---|---|---|
| Service Definition | `com.devpilot.code.CodeRepositoryService` | 稳定接口、请求与结果 DTO、错误语义 |
| Service Provider | `com.devpilot.code.provider.LocalCodeRepositoryProvider` | 访问本地目录，执行路径校验、黑名单与限额 |
| Consumer | `com.devpilot.agent.tool.code.CodeSearchTools` | 注册成模型可见 Tool，定义 schema 与展示摘要 |

日志能力同理：`LogService` 是定义与实现，`com.devpilot.agent.tool.logs.LogTools` 是 Consumer。Agent 只看到 Consumer 注册的 Tool，Tool 只依赖 Definition，具体 Provider 不向模型暴露。把本地仓库换成 GitLab、把 MySQL 日志换成 Loki，只需另写 Provider。

## 代码读取的安全边界

`LocalCodeRepositoryProvider` 是唯一接触文件系统的类，它执行四道检查：

1. 拒绝绝对路径；
2. `root.resolve(x).normalize()` 后必须仍以仓库根开头，挡住 `../`；
3. `toRealPath()` 解析符号链接后再校验一次，挡住指向仓库外的链接；
4. 按文件名 glob 黑名单拒绝敏感文件，并只允许白名单扩展名的文本文件。

此外还有大小上限、二进制探测（NUL 字节）、结果条数上限，以及 `.git`、`node_modules`、`target` 等目录的剪枝。检索用 `Files.walkFileTree`，模型输入永远不会进入命令行。

Tool 参数里的 `projectId` 会与会话所属项目比对，不一致直接拒绝——项目归属由运行时决定，不由模型声明。

## Agent 循环

Agent 循环是 DevPilot 自己的（`agent.runtime.DefaultAgentRuntime`），不是框架的，因为它跨越的每条边界都必须变成事件。每轮迭代：

```text
开启 step
  → 从已提交事件投影模型历史（ModelHistoryProjector）
  → 经 ModelGateway 发起至多一次模型请求
  → 模型若请求工具，逐个经 ToolRegistry 执行，结束 step 后继续下一轮
  → 模型若给出答案，记录 assistant_message，结束 step 与 run
```

关键约束由结构保证而非约定：模型历史只来自事件流，所以"模型看得到的都已被记录"；工具只经 `ToolRegistry`，所以作用域、鉴权、超时、限额与成对事件无法绕过；步数上限来自 profile，耗尽即失败而不是空转。每轮开始前检查 turn 是否已终态，用户取消后不会再烧一次模型调用。

Spring AI Alibaba 的定位是 `ModelGateway` 的一个 Provider，不是 Agent 循环本身——规格书 10.1 的 `ReactAgent` 代码只是 Provider 方向示意，实际以"模型可见内容必须先落事件"为准。`SpringAiModelGateway` 适配任意 Spring AI `ChatModel`（DashScope 或其他厂商），并**显式关闭 Spring AI 的内部工具执行**：模型只负责说要调哪个工具，执行一律回到 `ToolRegistry`，否则作用域、鉴权、超时、限额与成对事件都会被绕过。工具 schema 通过只声明不执行的 `SchemaOnlyToolCallback` 发布，真被调用即抛异常，因为那意味着接线出了问题。

DashScope 的自动配置在缺少 API Key 时会抛异常并拖垮整个上下文，所以默认全部排除，由 `dashscope` profile 只重新打开 Chat 一项。没有 Key 时应用照常启动，Agent 调用给出明确报错而不是编造答案。

Agent 的人设、模型路由、步数与可见 Tool 全部来自 `resources/agent-profiles/standard.yml` 与 `resources/prompts/`，新增 Agent 是加 profile 条目，不是改循环。profile 声明的作用域必须是应用能力的子集，试图扩权会让应用启动失败。

## 写操作的两道门

MVP 只有一个会写数据的工具：`saveTestCases`。它要同时通过两道互相独立、都在模型触及范围之外的门：

1. Agent profile 的 `allowMutating`——只有 `test_agent` 为 true；
2. `app.runtime.tool.mutating-allow-list`——部署级白名单。

任何一道不满足，`DefaultDenyToolPolicy` 就拒绝，并照常留下成对事件。写入的归属由运行时决定而非模型声明：工具用 `ToolExecutionContext` 里的 sessionId 覆盖模型传来的值。

## 读取投影

`chat_message` 只是 `user_message` 与 `assistant_message` 事件的读取投影，用于高效分页；它不是第二套消息事实。每行带 `source_seq` 指向来源事件，配合 `(session_id, source_seq)` 唯一键让投影幂等，并可用 `ChatMessageProjection.rebuild` 从事件流整体重建。

投影通过 `SessionEventListener` 在事务提交后触发：事件先落库，再通知投影。这样投影里的错误不会回滚已记录的事实，代价是投影可能短暂落后——这正是每个投影都必须可重建的原因。`agent_run` 与 `tool_call_record` 在后续 Phase 需要报表时按同样方式加入，运行状态仍从事件流投影。

## 分阶段约束

Phase 0 建立工程、配置、统一错误响应和健康检查。Phase 1 实现 Session Event Log、生命周期、Tool Registry 与运行时接口，不创建 Controller、业务表或空壳 Agent。Phase 2 加入项目、日志与会话业务：`dev_project`、`system_log`、`chat_session` 与作为投影的 `chat_message`，以及对应的 REST 接口和 demo 数据。Phase 3 加入 Code Tool 与 Log Tool 及 `demo-project/order-demo` 演示仓库，全部只读且经 `ToolRegistry` 执行；此阶段不建 Supervisor，Fake Model Provider 仍只存在于测试源码。Phase 4 实现自研 Agent 循环与 Spring AI Provider。Phase 5 加入 `code_agent`、`log_agent`、`test_agent` 与 `saveTestCases`；Knowledge Agent 依赖 Phase 7 的 RAG 工具，没有工具的 Agent 只是空壳，因此推迟到 RAG 之后。Supervisor 属于 Phase 6。

