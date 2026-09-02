package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/article-category")
@RequirePermission("admin:article")
@RequiredArgsConstructor
public class ArticleCategoryController {
    private final ArticleService articleService;
    @GetMapping public Result<List<ArticleService.CategoryView>> list() { return Result.success(articleService.categories()); }
    @GetMapping("/{id}") public Result<ArticleService.CategoryView> get(@PathVariable Long id) { return Result.success(articleService.category(id)); }
    @PostMapping public Result<Map<String, Long>> create(@Valid @RequestBody CategoryRequest request) { return Result.success(Map.of("id", articleService.createCategory(request.name, request.sort))); }
    @PutMapping("/{id}") public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) { articleService.updateCategory(id, request.name, request.sort); return Result.success(); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { articleService.deleteCategory(id); return Result.success(); }
    @Data public static class CategoryRequest { @NotBlank @Size(max = 50) String name; Integer sort; }
}
