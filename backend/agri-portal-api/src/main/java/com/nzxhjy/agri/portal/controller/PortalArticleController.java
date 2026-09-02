package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.service.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portal/article")
@RequiredArgsConstructor
public class PortalArticleController {
    private final ArticleService articleService;
    @GetMapping("/list") public Result<List<ArticleService.ArticleSummary>> list(@RequestParam(required = false) Long categoryId, @RequestParam(defaultValue = "20") int limit) { return Result.success(articleService.portalList(categoryId, limit)); }
    @GetMapping("/{id}") public Result<ArticleService.ArticleView> detail(@PathVariable Long id) { return Result.success(articleService.portalDetail(id)); }
}
