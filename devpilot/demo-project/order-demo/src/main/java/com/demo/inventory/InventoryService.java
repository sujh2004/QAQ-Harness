package com.demo.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 库存服务。 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public void reserve(Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("skuId and quantity are required");
        }
        log.info("stock reserved for skuId={} quantity={}", skuId, quantity);
    }

    public void release(Long skuId, Integer quantity) {
        log.info("stock released for skuId={} quantity={}", skuId, quantity);
    }
}
