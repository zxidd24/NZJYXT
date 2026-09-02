package com.nzxhjy.agri.admin;

import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.ProductCategory;
import com.nzxhjy.agri.service.mapper.ProductCategoryMapper;
import com.nzxhjy.agri.service.mapper.ProductReferenceMapper;
import com.nzxhjy.agri.service.service.CategoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryServiceTest {
    @Test
    void rejectsCategoryDeeperThanThreeLevels() {
        ProductCategoryMapper categoryMapper = mock(ProductCategoryMapper.class);
        CategoryService service = new CategoryService(categoryMapper, mock(ProductReferenceMapper.class));
        when(categoryMapper.selectById(3L)).thenReturn(category(3L, 2L));
        when(categoryMapper.selectById(2L)).thenReturn(category(2L, 1L));
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, 0L));

        assertThrows(BusinessException.class, () -> service.create(3L, "四级分类", 1, 1));
    }

    private ProductCategory category(Long id, Long parentId) {
        ProductCategory category = new ProductCategory();
        category.setId(id);
        category.setParentId(parentId);
        return category;
    }
}
