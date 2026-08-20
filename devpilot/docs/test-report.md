# DevPilot 功能与性能测试报告

本报告的每个数字都来自一次真实运行，不是估算。测量脚本随仓库提供，可以重跑核对：

- `backend/target/e2e/run_demo_questions.py` — 五个演示问题的端到端记录
- `backend/target/e2e/measure_platform.py` — 平台自身开销的测量

## 1. 测试环境

| 项 | 值 |
|---|---|
| 运行日期 | 2026-08-20 |
| 硬件 | Windows 11，本地单机 |
| JDK | 23（`maven.compiler.release=21`） |
| 数据库 | 端到端问答用 H2（`demo` profile）；Docker 全栈用 MySQL 8.4 |
| 模型 | DashScope `qwen-plus`（对话）、`text-embedding-v3`（向量） |
| Agent 组合 | `standard@1`，Supervisor + 5 个专业 Agent |

一个前提要说清楚：**端到端耗时由模型主导，不代表平台性能**。第 4 节把平台自己负责的部分单独测出来，那才是这套系统的开销。

## 2. 自动化测试

`mvn test` 全量通过：**191 个测试，0 失败，0 错误**。契约测试全部使用脚本化的 Fake Provider，**不需要任何 API Key**——CI 不把模型的稳定性当作判定标准。

| 测试类 | 用例 | 覆盖的契约 |
|---|---:|---|
| SessionLifecycleTest | 9 | turn/step 生命周期与非法状态转移 |
| SessionEventStoreConcurrencyTest | 3 | 并发追加时 seq 唯一且连续 |
| SessionEventReplayTest | 4 | 从事件流重建的投影与实时一致 |
| RuntimeRecoveryTest | 3 | 重启把悬挂 turn 关成 `ABORTED_BY_RESTART` |
| TurnCancellationTest | 4 | 取消幂等且不再消耗模型调用 |
| SessionEventPayloadCodecTest / EnvelopeTest | 7 | 载荷编解码与 SSE envelope |
| **SessionEventBroadcasterTest** | 5 | 会话隔离、溢出丢弃而不阻塞生产者 |
| **ChatStreamTest** | 6 | SSE 帧、`Last-Event-ID` 续传、跨项目拒绝 |
| ToolRegistryTest | 13 | 校验、超时、限额、成对事件、脱敏 |
| ToolScopeTest / ToolResultLimitTest | 11 | 作用域只能收窄、结果截断 |
| CodeToolTest | 9 | 路径穿越、黑名单、不存在路径、跨项目 |
| LocalCodeRepositoryProviderTest | 17 | 符号链接、二进制、中文路径、大文件 |
| LogToolTest / LogQueryTest | 17 | projectId 隔离、过滤、traceId、limit |
| KnowledgeBaseTest | 9 | 项目隔离、删除不可召回、无匹配话术 |
| DocumentSplitterTest / ProjectVectorStoresTest | 6 | 切块边界、无 Embedding 时的降级 |
| AgentRuntimeTest / SupervisorRoutingTest / AgentProfileTest | 20 | Agent 循环、委派嵌套、profile 收窄 |
| SkillSandboxTest / SkillLifecycleTest | 17 | 沙箱六道控制、安装与审批链 |
| ChatMessageProjectionTest / SessionApiTest / ProjectApiTest / 其他 | 22 | 投影幂等与 REST 契约 |

## 3. 端到端功能验收

规格书第 32 节的五个演示问题，一次连续运行，**全部 `COMPLETED`，60 次工具调用无一失败**：

| 问题 | 耗时 | 首帧 | 事件数 | Agent 调度路径 | 工具调用 |
|---|---:|---:|---:|---|---:|
| Q1 创建订单接口在哪里？调用链是什么？ | 50.4s | 0.20s | 73 | supervisor → code_agent → log_agent → knowledge_agent | 11 |
| Q2 订单服务为什么频繁 500？（优惠券空指针） | 91.7s | 0.01s | 115 | supervisor → log_agent → code_agent → knowledge_agent → test_agent | 20 |
| Q3 订单查询大量超时？（连接池耗尽） | 58.0s | 0.03s | 66 | supervisor → log_agent → code_agent → knowledge_agent | 11 |
| Q4 判空规范与错误码分段是什么？ | 19.3s | 0.01s | 28 | supervisor → knowledge_agent | 4 |
| Q5 为空指针问题设计回归用例并保存 | 78.3s | 0.01s | 81 | supervisor → log_agent → code_agent → test_agent | 14 |

值得注意的是路由行为：Q4 只问规范，Supervisor **只调了知识库 Agent**，没有为了显得全面去翻代码和日志；Q3 则自己走完了「日志指纹 → 代码定位 → 历史复盘」三步。路由不是写死的流程。

### 两套故障剧情的结论质量

**Q2（优惠券空指针）** 定位到 `OrderService.java:85-86`：`couponClient.query()` 返回值未判空即调用 `getDiscountAmount()`，并引用 2025-11 复盘指出同一模式已导致过 P1 事故。

**Q3（连接池耗尽）** 定位到 `OrderMapper.java:20-21`：`listOrdersByUser` 无 `LIMIT` 子句；日志侧给出 HikariPool 30s 超时告警与 `SQLTimeoutException` 的先后顺序；知识侧命中 2026-03 复盘的同源结论与「所有 Mapper 查询强制带 limit」的规范。两条证据链独立成立，结论不是模型的先验。

两份答复都带**不确定性小节**（例如 Q3 明确写出「未确认实际 `maximumPoolSize` 配置值」），这是可用性的一部分：能说清自己不知道什么的结论才敢用。

### 测试用例落库

Q5 结束后 `test_case` 表新增 7 条用例，全部 `source=AGENT`、`priority=P0`，覆盖有券 / 无券 / 券服务降级三类输入，符合知识库中《测试规范》对回归范围的要求。`saveTestCases` 是全系统唯一的写工具，经 profile `allowMutating` 与部署白名单两道门。

### 一次被测出来的真实缺陷

首轮运行时 Q3 有 3 次工具调用失败：`code_agent` 反复猜测不存在的路径（`order-service/src/main/resources`），耗尽 6 步预算，`askCodeAgent` 以 `PROVIDER_ERROR` 结束，整题 80.2 秒、32 次工具调用。暴露出两个问题，都已修复：

1. **状态码归类错误**。路径不存在是参数问题，却被记成 `PROVIDER_ERROR`——等于告诉审计轨迹和模型「平台坏了」，而事实是模型问错了。已按 `ToolErrorCode` 映射到 `INVALID_ARGUMENT` / `DENIED` / `TIMEOUT`，并补了回归测试。
2. **`code_agent` 会猜目录名**。人设中补入「不要猜路径，报错说不存在就立刻改用 `searchCode` 按类名搜」以及步数预算提示。

修复后同一问题：**58.0 秒、11 次工具调用、零失败**。

## 4. 性能实测

模型耗时被剥离后，平台自身的开销（单位毫秒）：

| 操作 | 样本 | p50 | p95 | max |
|---|---:|---:|---:|---:|
| 日志检索（ERROR，分页 20） | 50 | 14.4 | 30.0 | 71.7 |
| 错误聚合（720 小时窗口） | 50 | 4.0 | 27.8 | 30.8 |
| 知识检索（topK=5，含 embedding 往返） | 20 | 146.2 | 228.4 | 239.4 |
| 会话回放（81 个事件） | 20 | 14.2 | 31.1 | 32.7 |
| SSE 首帧（附着已结束会话） | 10 | 20.3 | 28.6 | 32.9 |

证据工具在 Agent 运行中的实测耗时（42 次调用，剔除委派工具）：**p50 = 1ms，p95 = 176ms，max = 218ms**。代码与日志工具几乎不计时间，唯一有量级差别的是知识检索——它要走一次 embedding 网络往返，146ms 的中位数基本就是那次往返。

结论：**端到端 20–90 秒里，平台占不到 1 秒。** 剩下的全是模型推理与多轮往返。想让演示更快，该调的是 Agent 步数预算和路由策略，不是平台代码。

## 5. Docker 全栈验证

在删除全部数据卷后（`docker compose --profile full down -v`）重新拉起，验证「另一台电脑从零运行」：

| 检查项 | 结果 |
|---|---|
| MySQL 首次启动自动执行 `schema.sql` + `demo-data.sql` | 通过 |
| 后端健康检查转为 `healthy` | 通过 |
| 容器内仓库路径解析 | `/workspace/demo-project/order-demo`，可读 |
| 演示日志可查 | 6 条 ERROR |
| 知识库自动导入 | 7 份文档 / 41 段 |
| 前端首页与深链接（history 模式回退） | 均 HTTP 200 |
| 真实对话闭环 | `COMPLETED`，45.9s，66 帧，supervisor → log_agent → code_agent → knowledge_agent |

## 6. 验收对照（规格书第 31 节）

| 条目 | 状态 | 依据 |
|---|---|---|
| 创建项目并绑定本地 demo Git 仓库 | 通过 | ProjectApiTest；容器内路径校验 |
| 导入并筛选日志 | 通过 | LogQueryTest；日志页 |
| 导入项目知识文档并完成向量化 | 通过 | 7 份 / 41 段，`INDEXED` |
| Code Tool 搜索并读取真实代码 | 通过 | CodeToolTest；Q1/Q2/Q3 |
| Log Tool 查询真实 MySQL 日志 | 通过 | Docker 栈 MySQL 实测 |
| Knowledge Tool 按 projectId RAG 检索 | 通过 | KnowledgeBaseTest 隔离用例 |
| 4 个专业 Agent 均可独立运行 | 通过 | `/api/v1/debug/agents/{name}` |
| Supervisor 动态选择 Agent | 通过 | Q4 只调知识库，未牵连代码日志 |
| 一次请求中调用多个 Agent | 通过 | Q2 一轮内四个专业 Agent |
| Test Agent 生成并保存测试用例 | 通过 | Q5 落库 7 条 |
| Web 聊天支持 SSE | 通过 | ChatStreamTest；对话页 |
| Web 可见 Agent/Tool 执行轨迹 | 通过 | 对话页轨迹树、会话页事件流 |
| 对话历史可以恢复 | 通过 | 刷新由事件回放重建 |
| seq / 重放 / 终态 / 重启恢复契约 | 通过 | 对应四个测试类 |
| `Last-Event-ID` 断线续传且幂等 | 通过 | 契约测试 + 真实断连实测无重复无空洞 |
| 模型可见内容均可从事件日志重建 | 通过 | ModelHistoryProjector 只读事件流 |
| profile 收窄 Tool 且执行时二次鉴权 | 通过 | AgentProfileTest、ToolScopeTest |
| Agent Run 与 Tool Call 有审计记录 | 通过 | 成对事件，投影为运行树 |
| 路径穿越测试通过 | 通过 | CodeToolTest、LocalCodeRepositoryProviderTest |
| API Key 不进入 Git | 通过 | 仅存 `.env`（已 gitignore）；黑名单拒读 |
| E2E 五个演示问题稳定运行 | 通过 | 本报告第 3 节 |
| README 指导另一台电脑从零运行 | 通过 | 第 5 节全栈验证 |
| 后端测试通过 | 通过 | 191 / 0 失败 |
| 前端生产构建通过 | 通过 | `npm run build` |

## 7. 已知限制

诚实地列出来，比留给别人发现好：

1. **SSE 订阅是进程内的。** 多实例部署需要在事件存储与广播器之间加共享总线（Redis pub/sub）。客户端协议不用改——实时通道漏掉的，回放本来就能覆盖。
2. **向量库是 `SimpleVectorStore`（内存 + JSON 文件）。** 演示语料量级够用，企业级需要换成 pgvector 或 Milvus；`ProjectVectorStores` 是唯一需要改的类。
3. **根 Agent 的模型历史包含专家的 `assistant_message`**，而这些内容它已经作为工具结果收到过一次。不影响正确性，但白占上下文；`assistant_message` 现在带 `runId`，可以精确过滤。
4. **MySQL 集成测试（`-Pmysql-it`）未纳入常规回归**，日常跑 H2。
5. **符号链接逃逸测试在未开启开发者模式的 Windows 上会 skip**，Linux CI 正常执行。
6. **单次演示的模型耗时波动较大**（同一问题 58–92 秒），与 DashScope 当时的排队情况有关，不是平台抖动。
