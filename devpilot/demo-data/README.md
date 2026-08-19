# Demo data

可复现的演示数据以 SQL 形式提供，见 [`../sql/demo-data.sql`](../sql/demo-data.sql)：一个订单服务演示项目，加上规格书第 14 节的两个故障剧情日志（优惠券服务返回 null 导致空指针、连接池耗尽导致查询超时）。

```powershell
docker compose exec -T mysql mysql -udevpilot -pdevpilot devpilot < sql/demo-data.sql
```

该脚本可重复执行，会先清掉 `project_id = 1` 的旧数据。

演示项目的本地源码仓库位于 [`../demo-project/order-demo`](../demo-project/order-demo)，`sql/demo-data.sql` 已把项目 1 指向它。知识库文档在 Phase 7 加入。
