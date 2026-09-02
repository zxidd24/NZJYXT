package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/product")
@RequirePermission("admin:product")
@RequiredArgsConstructor
@Validated
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public Result<java.util.Map<String, Long>> create(@Valid @RequestBody ProductRequest request) {
        Long id = productService.create(UserContext.getUserId(), request.toCommand());
        return Result.success(java.util.Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        productService.update(UserContext.getUserId(), id, request.toCommand());
        return Result.success();
    }

    @PutMapping("/{id}/price-stock")
    public Result<Void> updatePriceStock(@PathVariable Long id, @Valid @RequestBody PriceStockRequest request) {
        productService.updatePriceStock(UserContext.getUserId(), id, request.getPrice(), request.getStock());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    @GetMapping("/page")
    public Result<PageResult<ProductService.ProductView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String name) {
        return Result.success(productService.adminPage(pageNum, pageSize, categoryId, status, name));
    }

    @GetMapping("/{id}")
    public Result<ProductService.ProductView> detail(@PathVariable Long id) {
        return Result.success(productService.detail(id));
    }

    @PutMapping("/{id}/shelf")
    public Result<Void> shelf(@PathVariable Long id, @Valid @RequestBody ShelfRequest request) {
        productService.shelf(id, request.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}/recommend")
    public Result<Void> recommend(@PathVariable Long id, @Valid @RequestBody RecommendRequest request) {
        productService.recommend(id, request.getRecommend(), request.getSort());
        return Result.success();
    }

    @Data
    public static class ProductRequest {
        @NotNull(message = "商品分类不能为空")
        private Long categoryId;
        @NotBlank(message = "商品名称不能为空")
        @Size(max = 200, message = "商品名称不能超过200个字符")
        private String name;
        @Size(max = 300, message = "副标题不能超过300个字符")
        private String subTitle;
        @Size(max = 50, message = "品牌不能超过50个字符")
        private String brand;
        @NotNull(message = "商品价格不能为空")
        @DecimalMin(value = "0", message = "商品价格不能小于0")
        private BigDecimal price;
        @NotNull(message = "商品库存不能为空")
        @Min(value = 0, message = "商品库存不能小于0")
        private Integer stock;
        @Size(max = 10, message = "商品单位不能超过10个字符")
        private String unit;
        private String description;
        @Size(max = 1000, message = "商品图片地址不能超过1000个字符")
        private String images;

        private ProductService.ProductCommand toCommand() {
            return new ProductService.ProductCommand(categoryId, name, subTitle, brand, price, stock, unit, description, images);
        }
    }

    @Data
    public static class PriceStockRequest {
        @NotNull(message = "商品价格不能为空")
        @DecimalMin(value = "0", message = "商品价格不能小于0")
        private BigDecimal price;
        @NotNull(message = "商品库存不能为空")
        @Min(value = 0, message = "商品库存不能小于0")
        private Integer stock;
    }

    @Data
    public static class ShelfRequest {
        @NotNull(message = "上下架状态不能为空")
        private Integer status;
    }

    @Data
    public static class RecommendRequest {
        @NotNull(message = "推荐状态不能为空")
        private Integer recommend;
        @Min(0)
        private Integer sort;
    }
}
