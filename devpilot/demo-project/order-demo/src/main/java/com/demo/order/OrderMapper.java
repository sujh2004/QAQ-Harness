package com.demo.order;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单数据访问。
 *
 * <p>listOrdersByUser 在连接池耗尽时会抛出 SQLTimeoutException，是演示的第二个故障剧情。
 */
@Mapper
public interface OrderMapper {

    @Select("SELECT * FROM orders WHERE id = #{id}")
    Order selectById(@Param("id") Long id);

    @Select("SELECT * FROM orders WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Order> listOrdersByUser(@Param("userId") Long userId);

    int insert(Order order);
}
