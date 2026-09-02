package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.service.service.CategoryService;
import com.nzxhjy.agri.service.service.ProductService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Validated
public class PortalProductController {
    private final ProductService productService;
    private final CategoryService categoryService;

    @GetMapping("/product/recommend")
    public Result<List<ProductService.ProductView>> recommend(@RequestParam(defaultValue = "8") @Min(1) @Max(50) int limit) {
        return Result.success(productService.recommend(limit));
    }

    @GetMapping("/category/tree")
    public Result<List<CategoryService.CategoryTreeItem>> categoryTree() {
        return Result.success(categoryService.enabledTree());
    }

    @GetMapping("/product/page")
    public Result<PageResult<ProductService.ProductView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword) {
        return Result.success(productService.portalPage(pageNum, pageSize, categoryId, keyword));
    }

    @GetMapping("/product/{id}")
    public Result<ProductService.ProductView> detail(@PathVariable Long id) {
        return Result.success(productService.portalDetail(id));
    }
}
