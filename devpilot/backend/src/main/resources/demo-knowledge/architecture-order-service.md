# 订单服务架构说明

## 总体结构

订单服务（order-demo）是交易链路的核心服务，采用 Spring Boot 单体分层架构：Controller 接收请求，Service 编排业务，Mapper 访问数据库。对外通过 REST 暴露接口，对内通过 Feign 风格的客户端调用优惠券、库存、支付三个下游服务。

## 服务与依赖

- order-service：订单创建、查询。依赖 coupon-service（券信息）、inventory-service（库存占用）、payment-service（支付）。
- coupon-service：优惠券核销。内部依赖缓存节点，缓存不可用时按降级策略返回 null 并记录 WARN。
- inventory-service：库存占用与释放，无外部依赖。

## 关键类职责

- OrderController：入口层，只做参数校验与转发，不写业务逻辑。
- OrderService：订单编排。createOrder 依次调用验券、占库存、落库。
- CouponClient：优惠券服务的远程调用封装。
- OrderMapper：订单表访问，走 HikariCP 连接池。

## 已知薄弱点

- CouponClient 降级时返回 null 而不是显式的业务对象，调用方必须自行判空，历史上多次引发空指针问题。
- 订单查询接口与订单写入共用一个连接池，大促等高并发读取场景下连接等待时间会放大。

## 部署与容量

单实例部署，JVM 堆 2G。数据库连接池 HikariCP 最大 10 个连接，超时 30 秒。日常 QPS 约 50，大促峰值约 400。
