package com.demo.coupon;

import java.math.BigDecimal;

/** 优惠券信息。 */
public class CouponInfo {

    private String code;
    private BigDecimal discountAmount;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }
}
