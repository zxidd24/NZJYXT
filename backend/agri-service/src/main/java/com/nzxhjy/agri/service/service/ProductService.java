package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.AuditRecord;
import com.nzxhjy.agri.service.entity.Product;
import com.nzxhjy.agri.service.entity.ProductCategory;
import com.nzxhjy.agri.service.mapper.AuditFlowMapper;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.AuditRecordMapper;
import com.nzxhjy.agri.service.mapper.ProductCategoryMapper;
import com.nzxhjy.agri.service.mapper.ProductMapper;
import com.nzxhjy.agri.service.mapper.ProductReferenceMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductReferenceMapper productReferenceMapper;
    private final AuditFlowMapper auditFlowMapper;
    private final AuditNodeMapper auditNodeMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final MessageService messageService;

    @Transactional
    public Long create(Long operatorId, ProductCommand command) {
        validateCommand(command);
        ProductCategory category = requireCategory(command.categoryId());
        requireEnabledCategory(category);
        Product product = new Product();
        apply(product, command);
        product.setStatus(StatusEnums.ProductStatus.PENDING_AUDIT.value);
        product.setIsRecommend(0);
        product.setCreatedBy(operatorId);
        productMapper.insert(product);
        createAudit(product, operatorId, StatusEnums.AuditBizType.PRODUCT_SHELF.value, "商品上架：" + product.getName());
        return product.getId();
    }

    @Transactional
    public void update(Long operatorId, Long id, ProductCommand command) {
        validateCommand(command);
        Product product = requireProduct(id);
        requireEnabledCategory(requireCategory(command.categoryId()));
        apply(product, command);
        product.setStatus(StatusEnums.ProductStatus.PENDING_AUDIT.value);
        productMapper.updateById(product);
        createAudit(product, operatorId, StatusEnums.AuditBizType.PRODUCT_SHELF.value, "商品重新上架审核：" + product.getName());
    }

    @Transactional
    public void updatePriceStock(Long operatorId, Long id, BigDecimal price, Integer stock) {
        validatePriceStock(price, stock);
        Product product = requireProduct(id);
        if (product.getStatus() == null || (product.getStatus() != StatusEnums.ProductStatus.ON_SALE.value
                && product.getStatus() != StatusEnums.ProductStatus.OFF_SALE.value)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "只有已审核商品可以修改量价");
        }
        product.setPrice(price);
        product.setStock(stock);
        product.setStatus(StatusEnums.ProductStatus.PENDING_AUDIT.value);
        productMapper.updateById(product);
        createAudit(product, operatorId, StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value,
                "商品量价修改：" + product.getName());
    }

    @Transactional
    public void delete(Long id) {
        requireProduct(id);
        if (productReferenceMapper.countActiveOrderByProductId(id) > 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品存在进行中的订单，无法删除");
        }
        productMapper.deleteById(id);
    }

    public PageResult<ProductView> adminPage(int pageNum, int pageSize, Long categoryId, Integer status, String name) {
        var query = Wrappers.<Product>lambdaQuery().orderByDesc(Product::getCreatedAt).orderByDesc(Product::getId);
        if (categoryId != null) query.eq(Product::getCategoryId, categoryId);
        if (status != null) query.eq(Product::getStatus, status);
        if (name != null && !name.isBlank()) query.like(Product::getName, name.trim());
        return toPage(productMapper.selectPage(new Page<>(pageNum, pageSize), query), pageNum, pageSize);
    }

    public PageResult<ProductView> portalPage(int pageNum, int pageSize, Long categoryId, String keyword) {
        var query = Wrappers.<Product>lambdaQuery().eq(Product::getStatus, StatusEnums.ProductStatus.ON_SALE.value)
                .orderByDesc(Product::getIsRecommend).orderByAsc(Product::getSort).orderByDesc(Product::getCreatedAt);
        if (categoryId != null) query.in(Product::getCategoryId, categoryDescendantIds(categoryId));
        if (keyword != null && !keyword.isBlank()) query.and(q -> q.like(Product::getName, keyword.trim())
                .or().like(Product::getSubTitle, keyword.trim()).or().like(Product::getBrand, keyword.trim()));
        return toPage(productMapper.selectPage(new Page<>(pageNum, pageSize), query), pageNum, pageSize);
    }

    public List<ProductView> recommend(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<Product> products = productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, StatusEnums.ProductStatus.ON_SALE.value)
                .eq(Product::getIsRecommend, 1).orderByAsc(Product::getSort).orderByDesc(Product::getCreatedAt)
                .last("LIMIT " + safeLimit));
        return toViews(products);
    }

    public ProductView portalDetail(Long id) {
        Product product = requireProduct(id);
        if (product.getStatus() == null || product.getStatus() != StatusEnums.ProductStatus.ON_SALE.value) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品暂未上架");
        }
        return toView(product, categoryName(product.getCategoryId()));
    }

    public ProductView detail(Long id) {
        Product product = requireProduct(id);
        return toView(product, categoryName(product.getCategoryId()));
    }

    @Transactional
    public void shelf(Long id, Integer status) {
        if (status == null || (status != StatusEnums.ProductStatus.ON_SALE.value
                && status != StatusEnums.ProductStatus.OFF_SALE.value)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "上下架状态只能为1或2");
        }
        Product product = requireProduct(id);
        if (product.getStatus() == null || (product.getStatus() != StatusEnums.ProductStatus.ON_SALE.value
                && product.getStatus() != StatusEnums.ProductStatus.OFF_SALE.value)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品必须审核通过后才能上下架");
        }
        product.setStatus(status);
        productMapper.updateById(product);
    }

    @Transactional
    public void recommend(Long id, Integer recommend, Integer sort) {
        if (recommend == null || (recommend != 0 && recommend != 1)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "推荐状态只能为0或1");
        }
        Product product = requireProduct(id);
        product.setIsRecommend(recommend);
        if (sort != null) product.setSort(sort);
        productMapper.updateById(product);
    }

    public Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品不存在");
        return product;
    }

    public void updateStatus(Product product) {
        productMapper.updateById(product);
    }

    private void validateCommand(ProductCommand command) {
        if (command == null || command.categoryId() == null || command.name() == null || command.name().isBlank())
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品分类和名称不能为空");
        if (command.name().trim().length() > 200) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品名称不能超过200个字符");
        validatePriceStock(command.price(), command.stock());
        if (command.unit() != null && command.unit().length() > 10) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品单位不能超过10个字符");
        if (command.images() != null && command.images().length() > 1000) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品图片地址过长");
    }

    private void validatePriceStock(BigDecimal price, Integer stock) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品价格不能小于0");
        if (stock == null || stock < 0) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品库存不能小于0");
    }

    private ProductCategory requireCategory(Long id) {
        ProductCategory category = categoryMapper.selectById(id);
        if (category == null) throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "商品分类不存在");
        return category;
    }

    private void requireEnabledCategory(ProductCategory category) {
        if (!Integer.valueOf(1).equals(category.getStatus())) throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品分类已停用");
    }

    private void apply(Product product, ProductCommand command) {
        product.setCategoryId(command.categoryId());
        product.setName(command.name().trim());
        product.setSubTitle(trimToNull(command.subTitle()));
        product.setBrand(trimToNull(command.brand()));
        product.setPrice(command.price());
        product.setStock(command.stock());
        product.setUnit(trimToNull(command.unit()));
        product.setDescription(trimToNull(command.description()));
        product.setImages(trimToNull(command.images()));
    }

    private void createAudit(Product product, Long applicantId, int bizType, String summary) {
        long pending = auditRecordMapper.selectCount(Wrappers.<AuditRecord>lambdaQuery().eq(AuditRecord::getBizType, bizType)
                .eq(AuditRecord::getBizId, product.getId()).eq(AuditRecord::getStatus, StatusEnums.AuditStatus.PENDING.value));
        if (pending > 0) throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该商品已有待审核任务");
        AuditFlow flow = auditFlowMapper.selectOne(Wrappers.<AuditFlow>lambdaQuery().eq(AuditFlow::getBizType, bizType).eq(AuditFlow::getEnabled, 1));
        if (flow == null) throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品审核流程未启用");
        AuditNode node = auditNodeMapper.selectOne(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, flow.getId()).orderByAsc(AuditNode::getNodeOrder).last("LIMIT 1"));
        if (node == null) throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "商品审核节点未配置");
        AuditRecord record = new AuditRecord();
        record.setBizType(bizType);
        record.setBizId(product.getId());
        record.setBizNo(String.valueOf(product.getId()));
        record.setBizSummary(summary);
        record.setFlowNodeId(node.getId());
        record.setNodeName(node.getNodeName());
        record.setStatus(StatusEnums.AuditStatus.PENDING.value);
        record.setApplicantId(applicantId);
        record.setApplyTime(LocalDateTime.now());
        auditRecordMapper.insert(record);
        messageService.sendTodo(node.getId(), bizType, product.getId());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String categoryName(Long categoryId) {
        if (categoryId == null) return null;
        ProductCategory category = categoryMapper.selectById(categoryId);
        return category == null ? null : category.getName();
    }

    private List<Long> categoryDescendantIds(Long categoryId) {
        List<ProductCategory> all = categoryMapper.selectList(Wrappers.<ProductCategory>lambdaQuery());
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(categoryId);
        boolean changed;
        do {
            changed = false;
            for (ProductCategory category : all) {
                if (category.getParentId() != null && ids.contains(category.getParentId()) && !ids.contains(category.getId())) {
                    ids.add(category.getId());
                    changed = true;
                }
            }
        } while (changed);
        return ids;
    }

    private PageResult<ProductView> toPage(IPage<Product> page, int pageNum, int pageSize) {
        return new PageResult<>(page.getTotal(), pageNum, pageSize, toViews(page.getRecords()));
    }

    private List<ProductView> toViews(List<Product> products) {
        if (products == null || products.isEmpty()) return Collections.emptyList();
        Map<Long, String> categories = new LinkedHashMap<>();
        products.stream().map(Product::getCategoryId).filter(id -> id != null).distinct()
                .forEach(id -> { ProductCategory category = categoryMapper.selectById(id); if (category != null) categories.put(id, category.getName()); });
        return products.stream().map(product -> toView(product, categories.get(product.getCategoryId()))).toList();
    }

    private ProductView toView(Product product, String categoryName) {
        return new ProductView(product.getId(), product.getCategoryId(), categoryName, product.getName(), product.getSubTitle(),
                product.getBrand(), product.getPrice(), product.getStock(), product.getUnit(), product.getDescription(),
                product.getImages(), product.getStatus(), product.getIsRecommend(), product.getSort(), product.getCreatedBy(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    public record ProductCommand(Long categoryId, String name, String subTitle, String brand, BigDecimal price,
                                 Integer stock, String unit, String description, String images) { }

    @Data
    @AllArgsConstructor
    public static class ProductView {
        private Long id;
        private Long categoryId;
        private String categoryName;
        private String name;
        private String subTitle;
        private String brand;
        private BigDecimal price;
        private Integer stock;
        private String unit;
        private String description;
        private String images;
        private Integer status;
        private Integer isRecommend;
        private Integer sort;
        private Long createdBy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
