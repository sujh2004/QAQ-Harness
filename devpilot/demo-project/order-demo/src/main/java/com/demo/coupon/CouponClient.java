package com.demo.coupon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 优惠券服务客户端。
 *
 * <p>下游不可用时本方法返回 null 而不是抛异常，这是演示故障的根因之一：
 * 调用方 OrderService.createOrder 没有对返回值判空。
 */
@Component
public class CouponClient {

    private static final Logger log = LoggerFactory.getLogger(CouponClient.class);

    public CouponInfo query(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        try {
            return fetchFromRemote(couponCode);
        } catch (RuntimeException exception) {
            log.warn("coupon service responded {}, returning null", exception.getMessage());
            return null;
        }
    }

    private CouponInfo fetchFromRemote(String couponCode) {
        throw new IllegalStateException("503");
    }
}
