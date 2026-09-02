package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.entity.ProductCategory;
import com.nzxhjy.agri.service.service.CategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/category")
@RequirePermission("admin:system")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public Result<List<CategoryService.CategoryTreeItem>> tree() {
        return Result.success(categoryService.tree());
    }

    @GetMapping("/{id}")
    public Result<ProductCategory> get(@PathVariable Long id) {
        return Result.success(categoryService.get(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CategoryRequest request) {
        return Result.success(Map.of("id", categoryService.create(request.getParentId(), request.getName(),
                request.getSort(), request.getStatus())));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
        categoryService.update(id, request.getParentId(), request.getName(), request.getSort(), request.getStatus());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.success();
    }

    @Data
    public static class CategoryRequest {
        private Long parentId;
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 50)
        private String name;
        private Integer sort;
        private Integer status;
    }
}
