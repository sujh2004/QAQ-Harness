# Development

## Backend

```powershell
Set-Location backend
mvn test
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

如果本机 `8080` 被占用，可在当前 PowerShell 会话改用其他端口：

```powershell
$env:SERVER_PORT = "8081"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

前端需要同步设置 `$env:VITE_API_BASE_URL = "http://localhost:8081"`。恢复默认端口时执行 `Remove-Item Env:SERVER_PORT`。

非流式接口使用 `Result<T>`。业务错误抛出 `BusinessException`，由全局异常处理器转换；未知异常记录服务端日志并返回稳定错误码，不返回堆栈。

运行时代码不走 `BusinessException`：Tool 失败归一化为 `ToolExecutionResult` 与 `ToolErrorCode`，生命周期违规抛 `IllegalLifecycleTransitionException`，事件解码失败抛 `UnsupportedEventSchemaException`。等 Phase 2 出现 Controller 时再把它们映射到 HTTP 错误码。

### 数据库

```powershell
docker compose up -d mysql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/schema.sql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/demo-data.sql
```

`sql/schema.sql`（MySQL）与 `backend/src/test/resources/db/schema-h2.sql`（测试用 H2）必须同步修改。

## Frontend

```powershell
Set-Location frontend
npm install
npm run build
npm run dev
```

HTTP 客户端统一从 `src/api/http.ts` 导出。后续 SSE 使用 Fetch streaming，并按 session event 的 `seq` 幂等处理。

## Validation

Phase 4 的最小验收命令：

```powershell
Set-Location backend
mvn test
Set-Location ..\frontend
npm run build
```

`mvn test` 使用内存 H2 与 `db/schema-h2.sql`，不需要 Docker，也不需要任何模型 API Key。契约测试覆盖并发序号分配、分页重放一致性、状态机与非法迁移、取消幂等、重启恢复、Tool 管线的全部拒绝与失败路径、作用域收窄、结果截断与脱敏、SSE envelope 和事件 schema 版本策略。

用真实 MySQL 8 复核序号分配（需要 Docker，容器会执行 `sql/schema.sql`）：

```powershell
Set-Location backend
mvn test -Pmysql-it
```
