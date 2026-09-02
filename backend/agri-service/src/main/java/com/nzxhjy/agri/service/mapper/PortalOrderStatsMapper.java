package com.nzxhjy.agri.service.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

public interface PortalOrderStatsMapper {
    @Select("SELECT COUNT(*) FROM order_main WHERE user_id = #{userId} AND deleted = 0")
    long countAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM order_main WHERE user_id = #{userId} AND order_status = 4 AND deleted = 0")
    long countCompleted(@Param("userId") Long userId);

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_main WHERE user_id = #{userId} AND order_status = 4 AND deleted = 0")
    BigDecimal sumCompletedAmount(@Param("userId") Long userId);
}
