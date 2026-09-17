package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.Product;
import com.nzxhjy.agri.service.entity.UserAddress;
import com.nzxhjy.agri.service.entity.OrderDetail;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.AuditRecord;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.entity.WalletTransaction;
import com.nzxhjy.agri.service.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock ShoppingCartMapper cartMapper;
    @Mock OrderMainMapper orderMapper;
    @Mock OrderDetailMapper detailMapper;
    @Mock OrderAttachmentMapper attachmentMapper;
    @Mock ProductMapper productMapper;
    @Mock ProductCategoryMapper categoryMapper;
    @Mock UserAddressMapper addressMapper;
    @Mock PortalUserInfoMapper userInfoMapper;
    @Mock WalletAccountMapper walletMapper;
    @Mock WalletTransactionMapper transactionMapper;
    @Mock RefundApplyMapper refundMapper;
    @Mock RedisUtils redisUtils;
    @Mock AuditFlowMapper auditFlowMapper;
    @Mock AuditNodeMapper auditNodeMapper;
    @Mock AuditRecordMapper auditRecordMapper;
    @Mock MessageService messageService;
    @Mock AccessControlService accessControlService;
    @InjectMocks OrderService service;

    @Test
    void orderTokenIsStoredForTenMinutes() {
        String token = service.orderToken(7L);

        verify(redisUtils).set(eq("agri:order:token:" + token), eq("7"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void createRejectsTokenOwnedByAnotherUser() {
        when(redisUtils.get("agri:order:token:token")).thenReturn("8");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(7L, "token", 1L, List.of(new OrderService.ItemCommand(1L, 1)), null));

        assertEquals(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), exception.getCode());
        verifyNoInteractions(userInfoMapper, productMapper, orderMapper);
    }

    @Test
    void payIsIdempotentWhenOrderWasAlreadyPaid() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.PAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        OrderService.PayResult result = service.pay(7L, order.getOrderNo(), "WALLET");

        assertEquals(true, result.isSuccess());
        assertEquals("订单已支付", result.getMessage());
        verify(orderMapper, never()).updateById(any(OrderMain.class));
        verifyNoInteractions(walletMapper, transactionMapper, auditRecordMapper);
    }

    @Test
    void walletPaymentReadsAccountWithLockAndRecordsLockedBalance() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        WalletAccount account = new WalletAccount();
        account.setId(3L);
        account.setUserId(7L);
        account.setBalance(new BigDecimal("20.00"));
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);
        when(walletMapper.selectByUserIdForUpdate(7L)).thenReturn(account);
        when(walletMapper.update(any(), any())).thenReturn(1);
        AuditFlow flow = new AuditFlow();
        flow.setId(4L);
        flow.setBizType(StatusEnums.AuditBizType.ORDER.value);
        flow.setEnabled(1);
        AuditNode node = new AuditNode();
        node.setId(5L);
        node.setFlowId(flow.getId());
        node.setNodeOrder(1);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        service.pay(7L, order.getOrderNo(), "WALLET");

        verify(walletMapper).selectByUserIdForUpdate(7L);
        verify(transactionMapper).insert(argThat((WalletTransaction tx) -> new BigDecimal("10.00").equals(tx.getBalanceAfter())));
    }

    @Test
    void voucherPdfContainsXrefAndUnicodeText() throws Exception {
        Method method = OrderService.class.getDeclaredMethod("simplePdf", String.class);
        method.setAccessible(true);
        byte[] pdf = (byte[]) method.invoke(service, "收货人：张三\n订单号：O-1");
        String raw = new String(pdf, java.nio.charset.StandardCharsets.US_ASCII);

        assertEquals(true, raw.startsWith("%PDF-1.4"));
        assertEquals(true, raw.contains("/BaseFont/STSong-Light"));
        assertEquals(true, raw.contains("xref\n"));
        assertEquals(true, raw.contains("startxref\n"));
        Files.write(Path.of("target/order-voucher-test.pdf"), pdf);
    }

    @Test
    void cancelRestoresReservedStockAndMarksOrderCancelled() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        OrderDetail detail = new OrderDetail();
        detail.setProductId(3L);
        detail.setQuantity(4);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(detailMapper.selectList(any())).thenReturn(List.of(detail));
        when(productMapper.update(any(), any())).thenReturn(1);
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.cancel(7L, order.getOrderNo());

        assertEquals(StatusEnums.OrderStatus.CANCELLED.value, order.getOrderStatus());
        verify(productMapper).update(isNull(), any());
        verify(orderMapper).update(isNull(), any());
    }

    @Test
    void createRejectsInvalidQuantityBeforeWritingOrder() {
        when(redisUtils.get("agri:order:token:token")).thenReturn("7");
        when(userInfoMapper.selectById(7L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(7L, "token", 1L, List.of(new OrderService.ItemCommand(1L, 0)), null));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        verify(orderMapper, never()).insert(any(OrderMain.class));
    }

    @Test
    void checkoutRollsBackWholeTransactionWhenBalanceIsInsufficient() {
        prepareCheckout(new BigDecimal("9.99"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService checkoutService = transactionalService(manager, status);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> checkoutService.checkout(7L, "token", 1L, List.of(new OrderService.ItemCommand(3L, 1)), null));

        assertEquals("钱包余额不足", exception.getMessage());
        // 确认已执行的库存、购物车和订单写入都属于最终回滚的同一个事务。
        verify(productMapper).update(isNull(), any());
        verify(cartMapper).delete(any());
        verify(orderMapper).insert(any(OrderMain.class));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verify(walletMapper, never()).update(any(), any());
        verifyNoInteractions(transactionMapper, auditRecordMapper);
    }

    @Test
    void checkoutRollsBackWhenConditionalWalletDebitFails() {
        prepareCheckout(new BigDecimal("10.00"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService checkoutService = transactionalService(manager, status);
        when(walletMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> checkoutService.checkout(7L, "token", 1L, List.of(new OrderService.ItemCommand(3L, 1)), null));

        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verifyNoInteractions(transactionMapper, auditRecordMapper);
    }

    @Test
    void checkoutCommitsOnceWhenBalanceExactlyCoversPayment() {
        OrderMain order = prepareCheckout(new BigDecimal("10.00"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService checkoutService = transactionalService(manager, status);
        when(walletMapper.update(any(), any())).thenReturn(1);
        AuditFlow flow = new AuditFlow();
        flow.setId(4L);
        AuditNode node = new AuditNode();
        node.setId(5L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        OrderService.PayResult result = checkoutService.checkout(7L, "token", 1L,
                List.of(new OrderService.ItemCommand(3L, 1)), null);

        assertEquals(true, result.isSuccess());
        assertEquals(StatusEnums.OrderStatus.PENDING_AUDIT.value, order.getOrderStatus());
        assertEquals(StatusEnums.PayStatus.PAID.value, order.getPayStatus());
        verify(productMapper).update(isNull(), any());
        verify(cartMapper).delete(any());
        verify(orderMapper).insert(any(OrderMain.class));
        verify(walletMapper).update(isNull(), any());
        verify(transactionMapper).insert(argThat((WalletTransaction tx) -> BigDecimal.ZERO.compareTo(tx.getBalanceAfter()) == 0));
        verify(manager).commit(status);
        verify(manager, never()).rollback(any());
    }

    @Test
    void checkoutRollsBackPaymentWhenAuditCreationFails() {
        prepareCheckout(new BigDecimal("20.00"));
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService checkoutService = transactionalService(manager, status);
        when(walletMapper.update(any(), any())).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> checkoutService.checkout(7L, "token", 1L, List.of(new OrderService.ItemCommand(3L, 1)), null));

        assertEquals("订单审核流程未配置", exception.getMessage());
        verify(transactionMapper).insert(any(WalletTransaction.class));
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {5L, 6L})
    void rejectionAtEitherAuditNodeRefundsWalletOnce(Long nodeId) {
        OrderMain order = prepareRejection(nodeId, "WALLET");
        prepareRestoredStock();
        prepareRefundWallet();
        when(walletMapper.update(any(), any())).thenReturn(1);
        when(transactionMapper.insert(any(WalletTransaction.class))).thenReturn(1);
        when(auditRecordMapper.update(any(), any())).thenReturn(1, 0);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService auditService = transactionalService(manager, status);

        auditService.auditOrder(9L, 21L, false, "审核不通过");
        assertThrows(BusinessException.class,
                () -> auditService.auditOrder(9L, 21L, false, "重复驳回"));

        assertEquals(StatusEnums.OrderStatus.CANCELLED.value, order.getOrderStatus());
        verify(productMapper).update(isNull(), any());
        verify(walletMapper).selectByUserIdForUpdate(7L);
        verify(walletMapper).update(isNull(), any());
        verify(transactionMapper).insert(argThat((WalletTransaction tx) ->
                Long.valueOf(7L).equals(tx.getUserId()) && Long.valueOf(11L).equals(tx.getOrderId())
                        && new BigDecimal("10.00").equals(tx.getAmount())
                        && new BigDecimal("30.00").equals(tx.getBalanceAfter())
                        && tx.getDirection() == StatusEnums.WalletDirection.IN.value
                        && tx.getTransType() == StatusEnums.WalletTransactionType.REFUND.value
                        && tx.getTransStatus() == StatusEnums.WalletTransactionStatus.SUCCESS.value));
        verify(manager).commit(status);
        verify(manager).rollback(status);
    }

    @Test
    void rejectionRollsBackWhenWalletRefundFails() {
        prepareRejection(5L, "WALLET");
        prepareRestoredStock();
        prepareRefundWallet();
        when(walletMapper.update(any(), any())).thenReturn(0);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService auditService = transactionalService(manager, status);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> auditService.auditOrder(9L, 21L, false, "审核不通过"));

        assertEquals("钱包退款失败", exception.getMessage());
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verifyNoInteractions(transactionMapper, messageService);
    }

    @Test
    void rejectionRollsBackWhenRefundTransactionCannotBeRecorded() {
        prepareRejection(5L, "WALLET");
        prepareRestoredStock();
        prepareRefundWallet();
        when(walletMapper.update(any(), any())).thenReturn(1);
        when(transactionMapper.insert(any(WalletTransaction.class))).thenReturn(0);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService auditService = transactionalService(manager, status);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> auditService.auditOrder(9L, 21L, false, "审核不通过"));

        assertEquals("退款流水记录失败", exception.getMessage());
        verify(manager).rollback(status);
        verify(manager, never()).commit(any());
        verifyNoInteractions(messageService);
    }

    @Test
    void rejectionDoesNotRefundWhenOrderStatusChangesConcurrently() {
        prepareRejection(5L, "WALLET");
        when(orderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.auditOrder(9L, 21L, false, "审核不通过"));

        verifyNoInteractions(productMapper, walletMapper, transactionMapper, messageService);
    }

    @Test
    void rejectionOfAlreadyCancelledOrderDoesNotRefundAgain() {
        OrderMain order = prepareRejection(5L, "WALLET");
        order.setOrderStatus(StatusEnums.OrderStatus.CANCELLED.value);

        assertThrows(BusinessException.class,
                () -> service.auditOrder(9L, 21L, false, "审核不通过"));

        verifyNoInteractions(productMapper, walletMapper, transactionMapper, messageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANK_TRANSFER", "CREDIT"})
    void rejectionDoesNotCreditWalletForOtherPaymentMethods(String method) {
        prepareRejection(5L, method);
        prepareRestoredStock();

        service.auditOrder(9L, 21L, false, "审核不通过");

        verify(productMapper).update(isNull(), any());
        verifyNoInteractions(walletMapper, transactionMapper);
    }

    @Test
    void auditApprovalDoesNotRefundWallet() {
        OrderMain order = prepareRejection(5L, "WALLET");
        AuditNode current = new AuditNode();
        current.setFlowId(4L);
        current.setNodeOrder(2);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);

        service.auditOrder(9L, 21L, true, null);

        assertEquals(StatusEnums.OrderStatus.PENDING_SHIPMENT.value, order.getOrderStatus());
        verifyNoInteractions(productMapper, walletMapper, transactionMapper);
    }

    private OrderMain prepareRejection(Long nodeId, String method) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditRecord.class);
        AuditRecord record = new AuditRecord();
        record.setId(21L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(nodeId);
        when(auditRecordMapper.selectById(21L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.PAID.value);
        order.setPayMethod(method);
        when(orderMapper.selectById(11L)).thenReturn(order);
        return order;
    }

    private void prepareRestoredStock() {
        when(orderMapper.update(any(), any())).thenReturn(1);
        OrderDetail detail = new OrderDetail();
        detail.setProductId(3L);
        detail.setQuantity(2);
        when(detailMapper.selectList(any())).thenReturn(List.of(detail));
        when(productMapper.update(any(), any())).thenReturn(1);
    }

    private void prepareRefundWallet() {
        WalletAccount account = new WalletAccount();
        account.setId(3L);
        account.setUserId(7L);
        account.setBalance(new BigDecimal("20.00"));
        when(walletMapper.selectByUserIdForUpdate(7L)).thenReturn(account);
    }

    private OrderService transactionalService(PlatformTransactionManager manager, TransactionStatus status) {
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(interceptor);
        return (OrderService) factory.getProxy();
    }

    private OrderMain prepareCheckout(BigDecimal balance) {
        when(redisUtils.get("agri:order:token:token")).thenReturn("7");
        PortalUserInfo info = new PortalUserInfo();
        info.setAuthStatus(StatusEnums.AuthStatus.VERIFIED.value);
        when(userInfoMapper.selectById(7L)).thenReturn(info);
        UserAddress address = new UserAddress();
        address.setDetailAddress("测试收货地址");
        when(addressMapper.selectOne(any())).thenReturn(address);
        Product product = new Product();
        product.setId(3L);
        product.setStatus(StatusEnums.ProductStatus.ON_SALE.value);
        product.setPrice(new BigDecimal("10.00"));
        when(productMapper.selectById(3L)).thenReturn(product);
        when(productMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.insert(any(OrderMain.class))).thenAnswer(invocation -> {
            OrderMain created = invocation.getArgument(0);
            created.setId(order.getId());
            return 1;
        });
        when(orderMapper.selectById(11L)).thenReturn(order);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);
        WalletAccount account = new WalletAccount();
        account.setId(3L);
        account.setUserId(7L);
        account.setBalance(balance);
        when(walletMapper.selectByUserIdForUpdate(7L)).thenReturn(account);
        return order;
    }

    private OrderMain order(Long id, Long userId, int status, int payStatus) {
        OrderMain order = new OrderMain();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo("O-" + id);
        order.setOrderStatus(status);
        order.setPayStatus(payStatus);
        order.setPayAmount(new BigDecimal("10.00"));
        return order;
    }
}
