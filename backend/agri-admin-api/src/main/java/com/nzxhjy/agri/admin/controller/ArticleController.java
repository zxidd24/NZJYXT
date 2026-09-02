package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/article")
@RequirePermission("admin:article")
@RequiredArgsConstructor
public class ArticleController {
    private final ArticleService articleService;
    @PostMapping public Result<Map<String, Long>> save(@Valid @RequestBody ArticleRequest request) { return Result.success(Map.of("id", articleService.saveArticle(UserContext.getUserId(), request.id, request.title, request.content, request.categoryId, request.source, request.isPublished, request.sort))); }
    @GetMapping("/page") public Result<PageResult<ArticleService.ArticleView>> page(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Integer isPublished, @RequestParam(required = false) String keyword) { return Result.success(articleService.adminPage(pageNum, Math.min(pageSize, 100), categoryId, isPublished, keyword)); }
    @GetMapping("/mine") public Result<PageResult<ArticleService.ArticleView>> mine(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) { return Result.success(articleService.mine(UserContext.getUserId(), pageNum, Math.min(pageSize, 100))); }
    @GetMapping("/{id}") public Result<ArticleService.ArticleView> get(@PathVariable Long id) { return Result.success(articleService.detail(id)); }
    @DeleteMapping("/{id}") public Result<Void> delete(@PathVariable Long id) { articleService.deleteArticle(id); return Result.success(); }
    @Data public static class ArticleRequest { Long id; @NotBlank @Size(max = 200) String title; @NotBlank String content; Long categoryId; @Size(max = 100) String source; Integer isPublished; Integer sort; }
}
