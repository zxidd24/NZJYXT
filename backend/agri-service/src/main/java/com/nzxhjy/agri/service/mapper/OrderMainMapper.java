package com.nzxhjy.agri.service.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.service.entity.OrderMain;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface OrderMainMapper extends BaseMapper<OrderMain> {
    @Select("SELECT * FROM order_main WHERE order_no = #{orderNo} AND user_id = #{userId} AND deleted = 0 FOR UPDATE")
    OrderMain selectOwnedByNoForUpdate(@Param("orderNo") String orderNo, @Param("userId") Long userId);

    @Select("""
            <script>
            SELECT om.*
            FROM order_main om
            WHERE om.deleted = 0
            <if test=\"status != null\">AND om.order_status = #{status}</if>
            <if test=\"orderNo != null and orderNo != ''\">AND om.order_no LIKE CONCAT('%', #{orderNo}, '%')</if>
            <if test=\"categoryId != null\">
                AND EXISTS (
                    SELECT 1 FROM order_detail od
                    WHERE od.order_id = om.id AND od.category_id = #{categoryId}
                )
            </if>
            ORDER BY om.created_at DESC
            </script>
            """)
    IPage<OrderMain> selectAdminPage(Page<OrderMain> page,
                                     @Param("status") Integer status,
                                     @Param("orderNo") String orderNo,
                                     @Param("categoryId") Long categoryId);
}
