package com.nzxhjy.agri.service.service;

import com.nzxhjy.agri.service.mapper.DashboardStatsMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final DashboardStatsMapper statsMapper;

    public DashboardView overview() {
        int year = Year.now().getValue();
        List<MonthlyStat> annual = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            annual.add(new MonthlyStat(month, BigDecimal.ZERO, 0L));
        }
        statsMapper.annualStats(year).forEach(row -> annual.set(row.getMonthNumber() - 1,
                new MonthlyStat(row.getMonthNumber(), amount(row.getAmount()), value(row.getOrderCount()))));
        return new DashboardView(
                new Overview(statsMapper.paidAmount(), statsMapper.paidOrderCount(), statsMapper.registeredUserCount(),
                        statsMapper.todayOrderCount(), statsMapper.todayPaidAmount(), statsMapper.pendingTodoCount()),
                year, annual,
                statsMapper.topProducts().stream().map(row -> new ProductPriceStat(row.getProductId(), row.getProductName(),
                        value(row.getSoldQuantity()), amount(row.getAveragePrice()))).toList(),
                statsMapper.priceTrends().stream().map(row -> new PriceTrendStat(row.getDay(), row.getProductId(),
                        row.getProductName(), amount(row.getAveragePrice()))).toList(),
                statsMapper.categoryStats().stream().map(row -> new CategoryStat(row.getCategoryId(), row.getCategoryName(),
                        amount(row.getAmount()))).toList(),
                statsMapper.sevenDayStats().stream().map(row -> new DailyStat(row.getDay(), amount(row.getAmount()),
                        value(row.getOrderCount()))).toList());
    }

    private BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private long value(Long value) { return value == null ? 0L : value; }

    @Data @AllArgsConstructor
    public static class DashboardView {
        private Overview overview;
        private int year;
        private List<MonthlyStat> annualStats;
        private List<ProductPriceStat> topProducts;
        private List<PriceTrendStat> priceTrends;
        private List<CategoryStat> categoryStats;
        private List<DailyStat> sevenDayStats;
    }
    @Data @AllArgsConstructor
    public static class Overview {
        private BigDecimal totalAmount;
        private long totalOrderCount;
        private long registeredUserCount;
        private long todayOrderCount;
        private BigDecimal todayAmount;
        private long pendingTodoCount;
    }
    @Data @AllArgsConstructor public static class MonthlyStat { private int month; private BigDecimal amount; private long orderCount; }
    @Data @AllArgsConstructor public static class ProductPriceStat { private Long productId; private String productName; private long soldQuantity; private BigDecimal averagePrice; }
    @Data @AllArgsConstructor public static class PriceTrendStat { private LocalDate day; private Long productId; private String productName; private BigDecimal averagePrice; }
    @Data @AllArgsConstructor public static class CategoryStat { private Long categoryId; private String categoryName; private BigDecimal amount; }
    @Data @AllArgsConstructor public static class DailyStat { private LocalDate day; private BigDecimal amount; private long orderCount; }
}
