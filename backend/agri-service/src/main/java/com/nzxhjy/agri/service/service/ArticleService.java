package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.service.entity.Article;
import com.nzxhjy.agri.service.entity.ArticleCategory;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.ArticleCategoryMapper;
import com.nzxhjy.agri.service.mapper.ArticleMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ArticleService {
    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;

    public List<CategoryView> categories() {
        return categoryMapper.selectList(Wrappers.<ArticleCategory>lambdaQuery()
                        .orderByAsc(ArticleCategory::getSort).orderByAsc(ArticleCategory::getId))
                .stream().map(this::categoryView).toList();
    }

    public CategoryView category(Long id) { return categoryView(requireCategory(id)); }

    @Transactional
    public Long createCategory(String name, Integer sort) {
        validateName(name);
        if (categoryMapper.selectCount(Wrappers.<ArticleCategory>lambdaQuery().eq(ArticleCategory::getName, name.trim())) > 0) throw duplicate("资讯栏目名称已存在");
        ArticleCategory category = new ArticleCategory(); category.setName(name.trim()); category.setSort(sort == null ? 0 : sort); categoryMapper.insert(category); return category.getId();
    }

    @Transactional
    public void updateCategory(Long id, String name, Integer sort) {
        validateName(name); ArticleCategory category = requireCategory(id);
        if (categoryMapper.selectCount(Wrappers.<ArticleCategory>lambdaQuery().eq(ArticleCategory::getName, name.trim()).ne(ArticleCategory::getId, id)) > 0) throw duplicate("资讯栏目名称已存在");
        category.setName(name.trim()); if (sort != null) category.setSort(sort); categoryMapper.updateById(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        requireCategory(id);
        if (articleMapper.selectCount(Wrappers.<Article>lambdaQuery().eq(Article::getCategoryId, id).eq(Article::getDeleted, 0)) > 0) throw business("栏目下仍有文章，无法删除");
        categoryMapper.deleteById(id);
    }

    @Transactional
    public Long saveArticle(Long adminId, Long id, String title, String content, Long categoryId, String source, Integer published, Integer sort) {
        if (title == null || title.isBlank()) throw invalid("文章标题不能为空");
        if (content == null || content.isBlank()) throw invalid("文章内容不能为空");
        if (categoryId != null) requireCategory(categoryId);
        Article article = id == null ? new Article() : requireArticle(id);
        if (id == null) { article.setAuthorId(adminId); article.setViewCount(0); article.setDeleted(0); }
        SysUser author = userMapper.selectById(adminId);
        article.setTitle(trim(title, 200)); article.setContent(content); article.setCategoryId(categoryId); article.setSource(trim(source, 100));
        article.setAuthor(author == null ? null : (author.getRealName() == null ? author.getUsername() : author.getRealName()));
        article.setIsPublished(published != null && published == 1 ? 1 : 0); article.setSort(sort == null ? 0 : sort);
        article.setPublishTime(article.getIsPublished() == 1 ? (article.getPublishTime() == null ? LocalDateTime.now() : article.getPublishTime()) : null);
        if (id == null) articleMapper.insert(article); else articleMapper.updateById(article);
        return article.getId();
    }

    public PageResult<ArticleView> adminPage(int pageNum, int pageSize, Long categoryId, Integer published, String keyword) {
        var query = Wrappers.<Article>lambdaQuery().eq(Article::getDeleted, 0).eq(categoryId != null, Article::getCategoryId, categoryId).eq(published != null, Article::getIsPublished, published).orderByDesc(Article::getSort).orderByDesc(Article::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) query.like(Article::getTitle, keyword.trim());
        IPage<Article> page = articleMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::articleView).toList());
    }

    public PageResult<ArticleView> mine(Long adminId, int pageNum, int pageSize) {
        IPage<Article> page = articleMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<Article>lambdaQuery().eq(Article::getDeleted, 0).eq(Article::getAuthorId, adminId).orderByDesc(Article::getCreatedAt));
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::articleView).toList());
    }

    public ArticleView detail(Long id) { return articleView(requireArticle(id)); }

    @Transactional
    public void deleteArticle(Long id) { Article article = requireArticle(id); article.setDeleted(1); articleMapper.updateById(article); }

    public List<ArticleSummary> portalList(Long categoryId, int limit) {
        return articleMapper.selectList(Wrappers.<Article>lambdaQuery().eq(Article::getDeleted, 0).eq(Article::getIsPublished, 1).eq(categoryId != null, Article::getCategoryId, categoryId).orderByDesc(Article::getSort).orderByDesc(Article::getPublishTime).last("LIMIT " + Math.min(Math.max(limit, 1), 100))).stream().map(this::summary).toList();
    }

    @Transactional
    public ArticleView portalDetail(Long id) {
        Article article = requireArticle(id);
        if (!Objects.equals(article.getIsPublished(), 1)) throw business("资讯不存在");
        articleMapper.update(null, Wrappers.<Article>lambdaUpdate().eq(Article::getId, id).setSql("view_count = COALESCE(view_count, 0) + 1"));
        article.setViewCount((article.getViewCount() == null ? 0 : article.getViewCount()) + 1);
        return articleView(article);
    }

    private ArticleCategory requireCategory(Long id) { ArticleCategory category = categoryMapper.selectById(id); if (category == null) throw business("资讯栏目不存在"); return category; }
    private Article requireArticle(Long id) { Article article = articleMapper.selectById(id); if (article == null || Objects.equals(article.getDeleted(), 1)) throw business("资讯文章不存在"); return article; }
    private ArticleView articleView(Article article) { return new ArticleView(article.getId(), article.getTitle(), article.getContent(), article.getCategoryId(), categoryName(article.getCategoryId()), article.getAuthor(), article.getAuthorId(), article.getSource(), article.getIsPublished(), article.getPublishTime(), article.getSort(), article.getViewCount(), article.getCreatedAt(), article.getUpdatedAt()); }
    private ArticleSummary summary(Article article) { return new ArticleSummary(article.getId(), article.getTitle(), article.getCategoryId(), categoryName(article.getCategoryId()), article.getAuthor(), article.getSource(), article.getPublishTime(), article.getViewCount()); }
    private CategoryView categoryView(ArticleCategory category) { return new CategoryView(category.getId(), category.getName(), category.getSort(), category.getCreatedAt()); }
    private String categoryName(Long id) { ArticleCategory category = id == null ? null : categoryMapper.selectById(id); return category == null ? null : category.getName(); }
    private void validateName(String name) { if (name == null || name.isBlank()) throw invalid("栏目名称不能为空"); }
    private String trim(String value, int max) { return value == null ? null : value.length() <= max ? value : value.substring(0, max); }
    private BusinessException invalid(String message) { return new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), message); }
    private BusinessException business(String message) { return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message); }
    private BusinessException duplicate(String message) { return new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), message); }

    @Data @AllArgsConstructor public static class CategoryView { private Long id; private String name; private Integer sort; private LocalDateTime createdAt; }
    @Data @AllArgsConstructor public static class ArticleView { private Long id; private String title; private String content; private Long categoryId; private String categoryName; private String author; private Long authorId; private String source; private Integer isPublished; private LocalDateTime publishTime; private Integer sort; private Integer viewCount; private LocalDateTime createdAt; private LocalDateTime updatedAt; }
    @Data @AllArgsConstructor public static class ArticleSummary { private Long id; private String title; private Long categoryId; private String categoryName; private String author; private String source; private LocalDateTime publishTime; private Integer viewCount; }
}
