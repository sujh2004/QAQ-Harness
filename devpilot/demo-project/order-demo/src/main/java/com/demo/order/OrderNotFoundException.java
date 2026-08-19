package com.demo.order;

/** 订单不存在。 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("order not found: " + orderId);
    }
}
