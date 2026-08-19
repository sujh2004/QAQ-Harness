# SQL

`schema.sql` 是 MySQL 8 的建表脚本，使用 `CREATE TABLE IF NOT EXISTS`，可重复执行。

Phase 1 只包含两张运行时表：

- `session_event`：追加式会话事件，运行事实的唯一来源；`uk_session_seq(session_id, seq)` 保证同一会话内序号唯一。
- `session_stream`：会话事件流的序号分配器与运行元数据（`next_seq`、`profile_version`、能力集合快照）。追加事件时通过 `SELECT ... FOR UPDATE` 锁定该行再分配 `seq`，不使用“先查最大值再加一”。

项目、日志、会话投影与测试用例等业务表在 Phase 2 之后加入。

## 应用建表脚本

```powershell
docker compose up -d mysql
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/schema.sql
```

后端契约测试使用 H2，对应脚本为 `backend/src/test/resources/db/schema-h2.sql`，由测试 profile 自动执行。两份脚本必须同步修改。
