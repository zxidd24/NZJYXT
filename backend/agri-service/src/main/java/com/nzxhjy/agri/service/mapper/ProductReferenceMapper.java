package com.nzxhjy.agri.service.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductReferenceMapper {
    @Select("SELECT COUNT(*) FROM product WHERE category_id = #{categoryId} AND deleted = 0")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    @Select("SELECT COUNT(*) FROM order_detail d JOIN order_main o ON o.id = d.order_id "
            + "WHERE d.product_id = #{productId} AND o.order_status IN (0,1,2,3,6) AND o.deleted = 0")
    long countActiveOrderByProductId(@Param("productId") Long productId);
}
