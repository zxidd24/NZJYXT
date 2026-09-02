package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.ProductCategory;
import com.nzxhjy.agri.service.mapper.ProductCategoryMapper;
import com.nzxhjy.agri.service.mapper.ProductReferenceMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private static final int MAX_DEPTH = 3;
    private final ProductCategoryMapper categoryMapper;
    private final ProductReferenceMapper productReferenceMapper;

    public List<CategoryTreeItem> tree() {
        return buildTree(false);
    }

    public List<CategoryTreeItem> enabledTree() {
        return buildTree(true);
    }

    private List<CategoryTreeItem> buildTree(boolean enabledOnly) {
        var query = Wrappers.<ProductCategory>lambdaQuery()
                .orderByAsc(ProductCategory::getSort).orderByAsc(ProductCategory::getId);
        if (enabledOnly) {
            query.eq(ProductCategory::getStatus, 1);
        }
        List<ProductCategory> categories = categoryMapper.selectList(query);
        Map<Long, CategoryTreeItem> itemMap = new LinkedHashMap<>();
        categories.forEach(category -> itemMap.put(category.getId(), CategoryTreeItem.from(category)));
        List<CategoryTreeItem> roots = new ArrayList<>();
        itemMap.values().forEach(item -> {
            CategoryTreeItem parent = itemMap.get(item.getParentId());
            if (parent == null) {
                roots.add(item);
            } else {
                parent.getChildren().add(item);
            }
        });
        sortTree(roots);
        return roots;
    }

    public ProductCategory get(Long id) {
        return requireCategory(id);
    }

    @Transactional
    public Long create(Long parentId, String name, Integer sort, Integer status) {
        validateStatus(status);
        Long normalizedParentId = normalizeParentId(parentId);
        validateParent(normalizedParentId, null);
        ensureNameUnique(normalizedParentId, name, null);
        ProductCategory category = new ProductCategory();
        category.setParentId(normalizedParentId);
        category.setName(name.trim());
        category.setSort(sort == null ? 0 : sort);
        category.setStatus(status == null ? 1 : status);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional
    public void update(Long id, Long parentId, String name, Integer sort, Integer status) {
        validateStatus(status);
        ProductCategory category = requireCategory(id);
        Long normalizedParentId = normalizeParentId(parentId);
        if (id.equals(normalizedParentId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "分类不能作为自己的上级");
        }
        int parentDepth = validateParent(normalizedParentId, id);
        if (parentDepth + 1 + maxDescendantDepth(id) > MAX_DEPTH) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "移动后分类层级将超过三级");
        }
        ensureNameUnique(normalizedParentId, name, id);
        category.setParentId(normalizedParentId);
        category.setName(name.trim());
        category.setSort(sort == null ? category.getSort() : sort);
        category.setStatus(status == null ? category.getStatus() : status);
        categoryMapper.updateById(category);
    }

    @Transactional
    public void delete(Long id) {
        requireCategory(id);
        if (categoryMapper.selectCount(Wrappers.<ProductCategory>lambdaQuery()
                .eq(ProductCategory::getParentId, id)) > 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "请先删除子分类");
        }
        if (productReferenceMapper.countByCategoryId(id) > 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "分类已被商品使用，无法删除");
        }
        categoryMapper.deleteById(id);
    }

    private int validateParent(Long parentId, Long currentId) {
        int parentDepth = 0;
        Long cursor = parentId;
        while (cursor != 0L) {
            if (cursor.equals(currentId)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "分类层级存在循环");
            }
            ProductCategory parent = requireCategory(cursor);
            parentDepth++;
            if (parentDepth >= MAX_DEPTH) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品分类最多支持三级");
            }
            cursor = normalizeParentId(parent.getParentId());
        }
        return parentDepth;
    }

    private int maxDescendantDepth(Long categoryId) {
        List<ProductCategory> children = categoryMapper.selectList(Wrappers.<ProductCategory>lambdaQuery()
                .eq(ProductCategory::getParentId, categoryId));
        return children.stream().mapToInt(child -> 1 + maxDescendantDepth(child.getId())).max().orElse(0);
    }

    private void ensureNameUnique(Long parentId, String name, Long excludeId) {
        var query = Wrappers.<ProductCategory>lambdaQuery()
                .eq(ProductCategory::getParentId, parentId)
                .eq(ProductCategory::getName, name.trim());
        if (excludeId != null) {
            query.ne(ProductCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "同级分类名称已存在");
        }
    }

    private ProductCategory requireCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品分类不存在");
        }
        return category;
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "分类状态只能为启用或停用");
        }
    }

    private void sortTree(List<CategoryTreeItem> items) {
        items.sort(Comparator.comparing(CategoryTreeItem::getSort).thenComparing(CategoryTreeItem::getId));
        items.forEach(item -> sortTree(item.getChildren()));
    }

    @Data
    @AllArgsConstructor
    public static class CategoryTreeItem {
        private Long id;
        private Long parentId;
        private String name;
        private Integer sort;
        private Integer status;
        private List<CategoryTreeItem> children;

        private static CategoryTreeItem from(ProductCategory category) {
            return new CategoryTreeItem(category.getId(), category.getParentId(), category.getName(),
                    category.getSort(), category.getStatus(), new ArrayList<>());
        }
    }
}
