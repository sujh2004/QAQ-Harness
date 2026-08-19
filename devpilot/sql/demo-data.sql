-- DevPilot demo data (MySQL 8.0+). Run after sql/schema.sql.
-- Reproduces the two incident scenarios from the specification so the platform can be
-- demonstrated without a live production system.
--
-- repository_path is relative and resolved against app.repository.base-dir, which defaults to the
-- backend working directory. Running the backend from devpilot/backend therefore resolves
-- ../demo-project/order-demo to devpilot/demo-project/order-demo. The demo repository itself is
-- added in Phase 3; until then validate-repository reports it as missing, which is accurate.

DELETE FROM chat_message WHERE session_id IN (SELECT id FROM chat_session WHERE project_id = 1);
DELETE FROM chat_session WHERE project_id = 1;
DELETE FROM system_log WHERE project_id = 1;
DELETE FROM dev_project WHERE id = 1;

INSERT INTO dev_project (id, name, code, description, repository_path, default_branch, status)
VALUES (1, '订单服务演示项目', 'order-demo',
        '用于演示故障诊断闭环的订单服务，包含优惠券空指针与数据库超时两个剧情',
        '../demo-project/order-demo', 'main', 1);

-- Scenario 1: couponClient returns null and OrderService.createOrder throws NullPointerException.
INSERT INTO system_log
    (project_id, service_name, level, trace_id, logger, message, exception_type, stack_trace, log_time)
VALUES
    (1, 'order-service', 'INFO', 't-1001', 'com.demo.order.OrderController',
     'createOrder received request couponCode=SUMMER50', NULL, NULL, '2026-08-16 10:31:01'),
    (1, 'order-service', 'WARN', 't-1001', 'com.demo.order.CouponClient',
     'coupon service responded 503, returning null', NULL, NULL, '2026-08-16 10:31:02'),
    (1, 'order-service', 'ERROR', 't-1001', 'com.demo.order.OrderService',
     'Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null',
     'java.lang.NullPointerException',
     'java.lang.NullPointerException: Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null
	at com.demo.order.OrderService.createOrder(OrderService.java:86)
	at com.demo.order.OrderController.create(OrderController.java:42)',
     '2026-08-16 10:31:02'),
    (1, 'order-service', 'ERROR', 't-1002', 'com.demo.order.OrderService',
     'Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null',
     'java.lang.NullPointerException',
     'java.lang.NullPointerException: Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null
	at com.demo.order.OrderService.createOrder(OrderService.java:86)',
     '2026-08-16 10:34:18'),
    (1, 'order-service', 'ERROR', 't-1003', 'com.demo.order.OrderService',
     'Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null',
     'java.lang.NullPointerException',
     'java.lang.NullPointerException: Cannot invoke "CouponInfo.getDiscountAmount()" because "coupon" is null
	at com.demo.order.OrderService.createOrder(OrderService.java:86)',
     '2026-08-16 10:41:55'),
    (1, 'coupon-service', 'ERROR', 't-1001', 'com.demo.coupon.CouponController',
     'coupon lookup failed, upstream cache unavailable', 'java.io.IOException',
     'java.io.IOException: cache node unreachable
	at com.demo.coupon.CouponController.query(CouponController.java:37)',
     '2026-08-16 10:31:02'),

-- Scenario 2: the connection pool is exhausted and queries time out.
    (1, 'order-service', 'WARN', 't-2001', 'com.zaxxer.hikari.pool.HikariPool',
     'HikariPool-1 - Connection is not available, request timed out after 30000ms', NULL, NULL,
     '2026-08-17 21:12:40'),
    (1, 'order-service', 'ERROR', 't-2001', 'com.demo.order.OrderMapper',
     'query listOrdersByUser timed out', 'java.sql.SQLTimeoutException',
     'java.sql.SQLTimeoutException: Timeout after 30000ms of waiting for a connection
	at com.demo.order.OrderMapper.listOrdersByUser(OrderMapper.java:54)',
     '2026-08-17 21:12:41'),
    (1, 'order-service', 'ERROR', 't-2002', 'com.demo.order.OrderMapper',
     'query listOrdersByUser timed out', 'java.sql.SQLTimeoutException',
     'java.sql.SQLTimeoutException: Timeout after 30000ms of waiting for a connection
	at com.demo.order.OrderMapper.listOrdersByUser(OrderMapper.java:54)',
     '2026-08-17 21:13:09'),
    (1, 'inventory-service', 'INFO', 't-2001', 'com.demo.inventory.InventoryService',
     'stock reserved for orderId=90231', NULL, NULL, '2026-08-17 21:12:39');
