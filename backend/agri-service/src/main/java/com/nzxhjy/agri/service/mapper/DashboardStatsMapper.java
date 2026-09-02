package com.nzxhjy.agri.service.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DashboardStatsMapper {
    String PAID_ORDER = "pay_status = 1 AND order_status <> 5 AND deleted = 0";

    @Select("SELECT COUNT(*) FROM order_main WHERE " + PAID_ORDER)
    long paidOrderCount();

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_main WHERE " + PAID_ORDER)
    BigDecimal paidAmount();

    @Select("SELECT COUNT(*) FROM sys_user WHERE user_type IN (1, 2) AND deleted = 0")
    long registeredUserCount();

    @Select("SELECT COUNT(*) FROM order_main WHERE " + PAID_ORDER + " AND created_at >= CURDATE()")
    long todayOrderCount();

    @Select("SELECT COALESCE(SUM(pay_amount), 0) FROM order_main WHERE " + PAID_ORDER + " AND created_at >= CURDATE()")
    BigDecimal todayPaidAmount();

    @Select("SELECT COUNT(*) FROM audit_record WHERE status = 0")
    long pendingTodoCount();

    @Select("SELECT MONTH(created_at) AS monthNumber, COALESCE(SUM(pay_amount), 0) AS amount, COUNT(*) AS orderCount FROM order_main WHERE " + PAID_ORDER + " AND YEAR(created_at) = #{year} GROUP BY MONTH(created_at) ORDER BY monthNumber")
    List<MonthlyStat> annualStats(@Param("year") int year);

    @Select("SELECT d.product_id AS productId, d.product_name AS productName, COALESCE(SUM(d.quantity), 0) AS soldQuantity, COALESCE(AVG(d.product_price), 0) AS averagePrice FROM order_detail d JOIN order_main o ON o.id = d.order_id WHERE o.pay_status = 1 AND o.order_status <> 5 AND o.deleted = 0 AND o.created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY d.product_id, d.product_name ORDER BY soldQuantity DESC LIMIT 10")
    List<ProductPriceStat> topProducts();

    @Select("SELECT DATE(o.created_at) AS day, d.product_id AS productId, d.product_name AS productName, COALESCE(AVG(d.product_price), 0) AS averagePrice FROM order_detail d JOIN order_main o ON o.id = d.order_id WHERE o.pay_status = 1 AND o.order_status <> 5 AND o.deleted = 0 AND o.created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY) GROUP BY DATE(o.created_at), d.product_id, d.product_name ORDER BY day, productId")
    List<PriceTrendStat> priceTrends();

    @Select("SELECT d.category_id AS categoryId, COALESCE(d.category_name, '未分类') AS categoryName, COALESCE(SUM(d.total_price), 0) AS amount FROM order_detail d JOIN order_main o ON o.id = d.order_id WHERE o.pay_status = 1 AND o.order_status <> 5 AND o.deleted = 0 GROUP BY d.category_id, d.category_name ORDER BY amount DESC")
    List<CategoryStat> categoryStats();

    @Select("SELECT DATE(created_at) AS day, COALESCE(SUM(pay_amount), 0) AS amount, COUNT(*) AS orderCount FROM order_main WHERE " + PAID_ORDER + " AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at) ORDER BY day")
    List<DailyStat> sevenDayStats();

    @Data
    class MonthlyStat { private Integer monthNumber; private BigDecimal amount; private Long orderCount; }
    @Data
    class ProductPriceStat { private Long productId; private String productName; private Long soldQuantity; private BigDecimal averagePrice; }
    @Data
    class PriceTrendStat { private LocalDate day; private Long productId; private String productName; private BigDecimal averagePrice; }
    @Data
    class CategoryStat { private Long categoryId; private String categoryName; private BigDecimal amount; }
    @Data
    class DailyStat { private LocalDate day; private BigDecimal amount; private Long orderCount; }
}
