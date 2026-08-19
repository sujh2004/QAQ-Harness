package com.demo.order;

import com.demo.coupon.CouponClient;
import com.demo.coupon.CouponInfo;
import com.demo.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单核心业务。
 *
 * <p>本类是 DevPilot 演示用的故障现场：createOrder 直接使用优惠券服务的返回值，
 * 当下游降级返回 null 时会抛出 NullPointerException。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final CouponClient couponClient;
    private final InventoryService inventoryService;

    public OrderService(OrderMapper orderMapper,
                        CouponClient couponClient,
                        InventoryService inventoryService) {
        this.orderMapper = orderMapper;
        this.couponClient = couponClient;
        this.inventoryService = inventoryService;
    }

    /**
     * 查询单个订单。
     */
    public Order getOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    /**
     * 校验下单请求的基本参数。
     */
    private void validate(CreateOrderRequest request) {
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.getSkuId() == null) {
            throw new IllegalArgumentException("skuId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    /**
     * 创建订单。
     *
     * <p>已知缺陷：第 86 行没有对 couponClient.query 的返回值判空。
     * 优惠券服务降级时该方法返回 null，导致 NullPointerException 并让接口返回 500。
     */
    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        validate(request);
        log.info("createOrder received request couponCode={}", request.getCouponCode());

        inventoryService.reserve(request.getSkuId(), request.getQuantity());

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setSkuId(request.getSkuId());
        order.setQuantity(request.getQuantity());
        order.setCreatedAt(LocalDateTime.now());

        BigDecimal amount = request.getUnitPrice()
                .multiply(BigDecimal.valueOf(request.getQuantity()));

        CouponInfo coupon = couponClient.query(request.getCouponCode());
        BigDecimal discount = coupon.getDiscountAmount();
        order.setAmount(amount.subtract(discount).max(BigDecimal.ZERO));

        orderMapper.insert(order);
        log.info("createOrder finished orderId={}", order.getId());
        return order;
    }
}
