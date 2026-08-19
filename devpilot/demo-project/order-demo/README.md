# order-demo

DevPilot 演示用的最小订单服务源码。它**不参与 DevPilot 的构建**，只作为 Code Tool 的只读检索对象。

## 故障剧情

1. **优惠券空指针**：`OrderService.createOrder` 第 86 行直接使用 `couponClient.query(...)` 的返回值。
   优惠券服务降级时该方法返回 `null`，抛出 `NullPointerException`，接口返回 500。
   对应日志见 `sql/demo-data.sql` 中 traceId `t-1001` / `t-1002` / `t-1003`。

2. **连接池耗尽**：`OrderMapper.listOrdersByUser` 在 HikariCP 无可用连接时抛出 `SQLTimeoutException`。
   对应日志 traceId `t-2001` / `t-2002`。

## 敏感文件

`src/main/resources/application-prod.yml` 与 `.env` 是**假**的敏感文件，用来验证 Code Tool 的黑名单确实拒绝读取它们。里面没有任何真实凭据。

## 绑定到 DevPilot

`sql/demo-data.sql` 已把项目 1 的 `repository_path` 设为 `../demo-project/order-demo`，从 `backend/` 启动后端时即可解析到本目录。验证：

```text
POST /api/v1/projects/1/validate-repository
```
