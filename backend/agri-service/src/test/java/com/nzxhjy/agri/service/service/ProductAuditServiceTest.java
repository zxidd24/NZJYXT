package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 商品审核状态机测试。
 *
 * <p>覆盖本次修复的两个数据正确性问题：量价审核通过不得把已下架商品自动上架；
 * 审核驳回必须恢复提交前状态与量价，而不是一律置为“审核驳回”。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProductAuditServiceTest {
    @Mock ProductMapper productMapper;
    @Mock ProductCategoryMapper categoryMapper;
    @Mock ProductReferenceMapper productReferenceMapper;
    @Mock AuditFlowMapper auditFlowMapper;
    @Mock AuditNodeMapper auditNodeMapper;
    @Mock AuditRecordMapper auditRecordMapper;
    @Mock MessageService messageService;
    ProductService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Product.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditRecord.class);
        service = new ProductService(productMapper, categoryMapper, productReferenceMapper,
                auditFlowMapper, auditNodeMapper, auditRecordMapper, messageService);
    }

    /** 已下架商品修改量价，审核通过后必须仍是已下架，不能被静默上架。 */
    @Test
    void priceStockApprovalKeepsOriginalOffSaleStatus() {
        Product product = product(1L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(1L)).thenReturn(product);
        String snapshot = "{\"status\":2,\"price\":\"10.00\",\"stock\":100}";

        service.applyAuditResult(1L, StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value, true, snapshot);

        assertEquals(StatusEnums.ProductStatus.OFF_SALE.value, product.getStatus());
        verify(productMapper).updateById(product);
    }

    /** 在售商品修改量价，审核通过后必须仍是已上架。 */
    @Test
    void priceStockApprovalKeepsOriginalOnSaleStatus() {
        Product product = product(2L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(2L)).thenReturn(product);
        String snapshot = "{\"status\":1,\"price\":\"10.00\",\"stock\":100}";

        service.applyAuditResult(2L, StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value, true, snapshot);

        assertEquals(StatusEnums.ProductStatus.ON_SALE.value, product.getStatus());
    }

    /** 量价审核驳回：状态恢复为提交前状态，价格与库存一并回滚。 */
    @Test
    void priceStockRejectionRestoresStatusAndAmount() {
        Product product = product(3L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        product.setPrice(new BigDecimal("99.00"));
        product.setStock(5);
        when(productMapper.selectById(3L)).thenReturn(product);
        String snapshot = "{\"status\":1,\"price\":\"10.00\",\"stock\":100}";

        service.applyAuditResult(3L, StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value, false, snapshot);

        assertEquals(StatusEnums.ProductStatus.ON_SALE.value, product.getStatus());
        assertEquals(new BigDecimal("10.00"), product.getPrice());
        assertEquals(100, product.getStock());
    }

    /** 上架审核驳回：恢复提交前状态，商品仍可在售。 */
    @Test
    void shelfRejectionRestoresPreviousStatus() {
        Product product = product(4L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(4L)).thenReturn(product);
        String snapshot = "{\"status\":1,\"price\":\"10.00\",\"stock\":100}";

        service.applyAuditResult(4L, StatusEnums.AuditBizType.PRODUCT_SHELF.value, false, snapshot);

        assertEquals(StatusEnums.ProductStatus.ON_SALE.value, product.getStatus());
    }

    /** 上架审核通过：正常上架。 */
    @Test
    void shelfApprovalPutsProductOnSale() {
        Product product = product(5L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(5L)).thenReturn(product);

        service.applyAuditResult(5L, StatusEnums.AuditBizType.PRODUCT_SHELF.value, true,
                "{\"status\":2,\"price\":\"10.00\",\"stock\":100}");

        assertEquals(StatusEnums.ProductStatus.ON_SALE.value, product.getStatus());
    }

    /** 新建商品的首次上架审核被驳回（无快照）：进入审核驳回状态。 */
    @Test
    void firstShelfRejectionWithoutSnapshotMarksRejected() {
        Product product = product(6L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(6L)).thenReturn(product);

        service.applyAuditResult(6L, StatusEnums.AuditBizType.PRODUCT_SHELF.value, false, null);

        assertEquals(StatusEnums.ProductStatus.REJECTED.value, product.getStatus());
    }

    /** 快照损坏时不阻断审核，降级为“无快照”规则。 */
    @Test
    void brokenSnapshotFallsBackToRejected() {
        Product product = product(7L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(7L)).thenReturn(product);

        service.applyAuditResult(7L, StatusEnums.AuditBizType.PRODUCT_SHELF.value, false, "not-a-json");

        assertEquals(StatusEnums.ProductStatus.REJECTED.value, product.getStatus());
    }

    /** 修改量价时保存快照：必须在覆盖价格之前取值，否则快照就是新值。 */
    @Test
    void priceStockUpdateStoresSnapshotOfOriginalValues() {
        Product product = product(8L, StatusEnums.ProductStatus.ON_SALE.value);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(100);
        when(productMapper.selectById(8L)).thenReturn(product);
        when(auditRecordMapper.selectCount(any())).thenReturn(0L);
        AuditFlow flow = new AuditFlow();
        flow.setId(3L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        AuditNode node = new AuditNode();
        node.setId(30L);
        node.setNodeName("量价审核");
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        service.updatePriceStock(1L, 8L, new BigDecimal("12.50"), 80);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(captor.capture());
        String snapshot = captor.getValue().getBizSnapshot();
        // 快照记录的是提交前状态：已上架、原价 10.00、原库存 100。
        assertEquals("{\"status\":1,\"price\":\"10.00\",\"stock\":100}", snapshot);
        assertEquals(new BigDecimal("12.50"), product.getPrice());
        assertEquals(80, product.getStock());
        assertEquals(StatusEnums.ProductStatus.PENDING_AUDIT.value, product.getStatus());
    }

    /** 审核中的商品不可再次提交量价，避免覆盖待审核版本。 */
    @Test
    void priceStockUpdateRejectsWhenAuditPending() {
        Product product = product(9L, StatusEnums.ProductStatus.ON_SALE.value);
        when(productMapper.selectById(9L)).thenReturn(product);
        when(auditRecordMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.updatePriceStock(1L, 9L, new BigDecimal("12.50"), 80));

        assertEquals(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), exception.getCode());
        assertEquals("该商品正在审核中，请等待审核完成后再操作", exception.getMessage());
        verify(productMapper, never()).updateById(any(Product.class));
    }

    /** 审核中的商品不可再次编辑，避免必然失败与覆盖待审核版本。 */
    @Test
    void editRejectsWhenAuditPending() {
        Product product = product(10L, StatusEnums.ProductStatus.PENDING_AUDIT.value);
        when(productMapper.selectById(10L)).thenReturn(product);
        when(auditRecordMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(1L, 10L, command()));

        assertEquals(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), exception.getCode());
        verify(productMapper, never()).updateById(any(Product.class));
        verify(auditRecordMapper, never()).insert(any(AuditRecord.class));
    }

    /** 新建商品首次提交没有历史状态，不写快照。 */
    @Test
    void createDoesNotStoreSnapshot() {
        ProductCategory category = new ProductCategory();
        category.setId(1L);
        category.setStatus(1);
        when(categoryMapper.selectById(1L)).thenReturn(category);
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Product.class).setId(11L);
            return 1;
        });
        AuditFlow flow = new AuditFlow();
        flow.setId(2L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        AuditNode node = new AuditNode();
        node.setId(20L);
        node.setNodeName("上架审核");
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        service.create(1L, command());

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditRecordMapper).insert(captor.capture());
        assertNull(captor.getValue().getBizSnapshot());
    }

    private ProductService.ProductCommand command() {
        return new ProductService.ProductCommand(1L, "复合肥", null, null, new BigDecimal("10.00"), 100, "袋", null, null);
    }

    private Product product(Long id, int status) {
        Product product = new Product();
        product.setId(id);
        product.setName("复合肥");
        product.setCategoryId(1L);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(100);
        product.setStatus(status);
        return product;
    }
}
