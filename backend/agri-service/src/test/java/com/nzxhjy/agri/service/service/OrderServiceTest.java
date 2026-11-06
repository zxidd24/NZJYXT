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
import com.nzxhjy.agri.service.entity.OrderAttachment;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.entity.WalletTransaction;
import com.nzxhjy.agri.service.mapper.*;
import com.nzxhjy.agri.service.pay.CreditPayService;
import com.nzxhjy.agri.service.pay.MockBankTransferPayService;
import com.nzxhjy.agri.service.pay.WalletPayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    @Mock RedisUtils redisUtils;
    @Mock AuditFlowMapper auditFlowMapper;
    @Mock AuditNodeMapper auditNodeMapper;
    @Mock AuditRecordMapper auditRecordMapper;
    @Mock MessageService messageService;
    @Mock AccessControlService accessControlService;
    OrderService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // MyBatis-Plus 的 lambda 条件需要实体元数据；单元测试无 Spring 上下文，手动初始化。
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderMain.class);
        // 支付方式实现按真实对象装配，覆盖钱包支付的扣款、流水与并发保护逻辑。
        service = new OrderService(cartMapper, orderMapper, detailMapper, attachmentMapper, productMapper,
                categoryMapper, addressMapper, userInfoMapper, walletMapper, transactionMapper, redisUtils,
                auditFlowMapper, auditNodeMapper, auditRecordMapper, messageService, accessControlService,
                List.of(new WalletPayService(walletMapper, transactionMapper),
                        new MockBankTransferPayService("农资现货交易平台", "中国银行", "6217000000000000"),
                        new CreditPayService()));
    }

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

    /** P3：额度支付一期未开放，必须拒绝，不得静默放行成免费订单。 */
    @Test
    void creditPaymentIsRejectedAndOrderStaysUnpaid() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.pay(7L, order.getOrderNo(), "CREDIT"));

        // 钱包/额度均已停用，统一由"非外部确认支付方式"拦截。
        assertEquals("该支付方式已停用，请使用银行转账并上传转账凭证", exception.getMessage());
        // 关键：订单未被标记支付，未创建审核任务，也未产生任何资金动作。
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        assertEquals(StatusEnums.OrderStatus.PENDING_PAYMENT.value, order.getOrderStatus());
        assertNull(order.getPayMethod());
        verify(orderMapper, never()).updateById(any(OrderMain.class));
        verifyNoInteractions(walletMapper, transactionMapper, auditRecordMapper);
    }

    @Test
    void creditPaymentIsRejectedCaseInsensitively() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> service.pay(7L, order.getOrderNo(), "credit"));
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
    }

    /** 未知支付方式仍应被拒绝（保留原有防护）。 */
    @Test
    void unknownPayMethodIsRejected() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.pay(7L, order.getOrderNo(), "ALIPAY"));

        assertEquals("支付方式不支持", exception.getMessage());
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        verifyNoInteractions(walletMapper, transactionMapper);
    }

    /** P4：银行转账未经到账确认不得标记支付成功，订单应保持待付款并返回打款账户。 */
    @Test
    void bankTransferWaitsForConfirmationAndStaysUnpaid() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        OrderService.PayResult result = service.pay(7L, order.getOrderNo(), "BANK_TRANSFER");

        assertEquals(true, result.isSuccess());
        assertEquals(true, result.isAwaitingPayment());
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        assertEquals(StatusEnums.OrderStatus.PENDING_PAYMENT.value, order.getOrderStatus());
        assertEquals("BANK_TRANSFER", order.getPayMethod());
        // 返回平台结算账户供买家线下打款
        assertEquals("中国银行", result.getSettlementAccount().bankName());
        assertEquals("6217000000000000", result.getSettlementAccount().accountNo());
        // 未经确认不得创建审核任务、不得产生资金动作
        verifyNoInteractions(walletMapper, transactionMapper, auditRecordMapper);
    }

    /** 确认到账后才流转订单并创建审核任务。 */
    @Test
    void confirmingBankTransferMovesOrderIntoAudit() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        order.setPayMethod("BANK_TRANSFER");
        when(orderMapper.selectById(11L)).thenReturn(order);
        // 前置：买家已上传转账凭证。
        when(attachmentMapper.selectCount(any())).thenReturn(1L);
        AuditFlow flow = new AuditFlow();
        flow.setId(4L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        AuditNode node = new AuditNode();
        node.setId(5L);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        OrderService.PayResult result = service.confirmBankTransferReceived(11L);

        assertEquals(StatusEnums.PayStatus.PAID.value, order.getPayStatus());
        assertEquals(StatusEnums.OrderStatus.PENDING_AUDIT.value, order.getOrderStatus());
        assertEquals("到账已确认，订单进入审核", result.getMessage());
        verify(auditRecordMapper).insert(any(AuditRecord.class));
        verifyNoInteractions(walletMapper, transactionMapper);
    }

    /** 确认到账幂等：已支付订单重复确认不再建审核任务。 */
    @Test
    void confirmingBankTransferIsIdempotent() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.PAID.value);
        order.setPayMethod("BANK_TRANSFER");
        when(orderMapper.selectById(11L)).thenReturn(order);

        OrderService.PayResult result = service.confirmBankTransferReceived(11L);

        assertEquals("订单已支付", result.getMessage());
        verify(orderMapper, never()).updateById(any(OrderMain.class));
        verifyNoInteractions(auditRecordMapper);
    }

    /** 钱包支付订单不适用"确认到账"，应拒绝。 */
    @Test
    void confirmingReceivedRejectsWalletOrder() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        order.setPayMethod("WALLET");
        when(orderMapper.selectById(11L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmBankTransferReceived(11L));

        assertEquals("该订单无需确认到账", exception.getMessage());
    }

    /** 等待到账确认的订单不得被超时任务自动取消，避免"款已汇、单已取消"。 */
    @Test
    void cancelExpiredSkipsOrdersAwaitingExternalConfirmation() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        service.cancelExpired();

        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.AbstractWrapper<OrderMain, ?, ?>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.AbstractWrapper.class);
        verify(orderMapper).selectList(captor.capture());
        com.baomidou.mybatisplus.core.conditions.AbstractWrapper<OrderMain, ?, ?> wrapper = captor.getValue();
        String sql = String.valueOf(wrapper.getTargetSql());
        assertTrue(sql.contains("pay_method IS NULL OR pay_method NOT IN"),
                "超时取消应排除等待到账的支付方式，实际SQL=" + sql);
        // 绑定的排除值即"等待外部到账确认"的支付方式集合
        assertTrue(String.valueOf(wrapper.getParamNameValuePairs()).contains("BANK_TRANSFER"),
                "排除集合应含 BANK_TRANSFER，实际参数=" + wrapper.getParamNameValuePairs());
    }

    /** 钱包支付已下线：任何入口都不应再扣钱包余额。 */
    @Test
    void walletPaymentIsNoLongerAccepted() {
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        assertThrows(BusinessException.class, () -> service.pay(7L, order.getOrderNo(), "WALLET"));

        verifyNoInteractions(walletMapper, transactionMapper);
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

    /** 改为线下转账后，下单不再扣钱包：订单停在待付款并返回平台收款账户。 */
    @Test
    void checkoutCreatesUnpaidOrderWithoutDebitingWallet() {
        OrderMain order = prepareCheckout();
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        OrderService checkoutService = transactionalService(manager, status);

        OrderService.PayResult result = checkoutService.checkout(7L, "token", 1L,
                List.of(new OrderService.ItemCommand(3L, 1)), null);

        assertEquals(true, result.isSuccess());
        assertEquals(true, result.isAwaitingPayment());
        // 关键：钱一分没动，订单仍未支付、仍待付款。
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        assertEquals(StatusEnums.OrderStatus.PENDING_PAYMENT.value, order.getOrderStatus());
        assertEquals("6217000000000000", result.getSettlementAccount().accountNo());
        verify(walletMapper, never()).update(any(), any());
        verifyNoInteractions(transactionMapper, auditRecordMapper);
        verify(manager).commit(status);
    }

    /** 余额为 0 也能下单——转账流程不依赖钱包余额。 */
    @Test
    void checkoutSucceedsEvenWithZeroWalletBalance() {
        prepareCheckout();

        OrderService.PayResult result = service.checkout(7L, "token", 1L,
                List.of(new OrderService.ItemCommand(3L, 1)), null);

        assertEquals(true, result.isAwaitingPayment());
        verify(walletMapper, never()).update(any(), any());
    }

    /** 上传转账凭证：写附件、转待审核、建审核任务，但不置已支付。 */
    @Test
    void uploadVoucherMovesOrderToAuditWithoutMarkingPaid() {
        OrderMain order = order(21L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectById(21L)).thenReturn(order);
        AuditFlow flow = new AuditFlow();
        flow.setId(4L);
        AuditNode node = new AuditNode();
        node.setId(5L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        OrderService.PayResult result = service.uploadTransferVoucher(7L, 21L, "/uploads/v.jpg", "v.jpg");

        assertEquals(StatusEnums.OrderStatus.PENDING_AUDIT.value, order.getOrderStatus());
        // 关键：钱未确认，pay_status 必须还是未支付。
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        assertEquals(StatusEnums.PayMethod.BANK_TRANSFER.name(), order.getPayMethod());
        ArgumentCaptor<OrderAttachment> attachmentCaptor = ArgumentCaptor.forClass(OrderAttachment.class);
        verify(attachmentMapper).insert(attachmentCaptor.capture());
        assertEquals(StatusEnums.OrderAttachmentType.TRANSFER_VOUCHER.value, attachmentCaptor.getValue().getType());
        assertEquals("/uploads/v.jpg", attachmentCaptor.getValue().getFileUrl());
        verify(auditRecordMapper).insert(any(AuditRecord.class));
        // 凭证已提交，不再处于"等待买家付款"状态。
        assertEquals(false, result.isAwaitingPayment());
        verifyNoInteractions(walletMapper, transactionMapper);
    }

    /** 只能对本人订单上传凭证。 */
    @Test
    void uploadVoucherRejectsOtherUsersOrder() {
        when(orderMapper.selectById(22L)).thenReturn(order(22L, 8L,
                StatusEnums.OrderStatus.PENDING_PAYMENT.value, StatusEnums.PayStatus.UNPAID.value));

        assertThrows(BusinessException.class,
                () -> service.uploadTransferVoucher(7L, 22L, "/uploads/v.jpg", "v.jpg"));

        verify(attachmentMapper, never()).insert(any(OrderAttachment.class));
    }

    /** 非待付款状态不能上传凭证，避免重复发起审核。 */
    @Test
    void uploadVoucherRejectsNonPendingPaymentOrder() {
        when(orderMapper.selectById(23L)).thenReturn(order(23L, 7L,
                StatusEnums.OrderStatus.PENDING_AUDIT.value, StatusEnums.PayStatus.UNPAID.value));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.uploadTransferVoucher(7L, 23L, "/uploads/v.jpg", "v.jpg"));

        assertEquals("只有待付款订单可以上传转账凭证", exception.getMessage());
    }

    /** 凭证文件为空必须拒绝。 */
    @Test
    void uploadVoucherRejectsBlankFile() {
        when(orderMapper.selectById(24L)).thenReturn(order(24L, 7L,
                StatusEnums.OrderStatus.PENDING_PAYMENT.value, StatusEnums.PayStatus.UNPAID.value));

        assertThrows(BusinessException.class,
                () -> service.uploadTransferVoucher(7L, 24L, "  ", "v.jpg"));

        verify(attachmentMapper, never()).insert(any(OrderAttachment.class));
    }

    /** 门户只允许上传盖章回传单，转账凭证不能走通用附件接口。 */
    @Test
    void addAttachmentRejectsTransferVoucherType() {
        when(orderMapper.selectById(25L)).thenReturn(order(25L, 7L,
                StatusEnums.OrderStatus.PENDING_PAYMENT.value, StatusEnums.PayStatus.UNPAID.value));

        assertThrows(BusinessException.class,
                () -> service.addAttachment(7L, 25L, StatusEnums.OrderAttachmentType.TRANSFER_VOUCHER.value,
                        "/uploads/x.jpg", "x.jpg"));

        verify(attachmentMapper, never()).insert(any(OrderAttachment.class));
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

    /**
     * 转账订单被驳回：钱已收到平台账户，无法自动退回，
     * 必须写一条"待线下打款"的退款台账，便于财务跟进与报表体现。
     */
    @Test
    void rejectionOfTransferOrderRecordsPendingOfflineRefund() {
        prepareRejection(5L, "BANK_TRANSFER");
        prepareRestoredStock();

        service.auditOrder(9L, 21L, false, "审核不通过");

        verify(productMapper).update(isNull(), any());
        // 不碰钱包余额，但必须留下退款台账。
        verifyNoInteractions(walletMapper);
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        WalletTransaction ledger = captor.getValue();
        assertEquals(StatusEnums.WalletTransactionType.REFUND.value, ledger.getTransType());
        assertEquals(StatusEnums.WalletDirection.OUT.value, ledger.getDirection());
        assertEquals("订单驳回退款（待线下打款）", ledger.getRemark());
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

    /**
     * 订单审核终审通过 = 货款核对到账：
     * 必须置 pay_status=1、记录到账时间，并写一条收款台账让财务报表可见。
     */
    @Test
    void finalAuditApprovalConfirmsPaymentAndRecordsLedger() {
        AuditRecord record = new AuditRecord();
        record.setId(31L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(5L);
        when(auditRecordMapper.selectById(31L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.UNPAID.value);
        order.setPayMethod("BANK_TRANSFER");
        when(orderMapper.selectById(11L)).thenReturn(order);
        AuditNode current = new AuditNode();
        current.setId(5L);
        current.setFlowId(4L);
        current.setNodeOrder(1);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);
        // 流程只有一个节点 → next 为 null，走终审分支。
        when(auditNodeMapper.selectOne(any())).thenReturn(null);

        service.auditOrder(9L, 31L, true, null);

        assertEquals(StatusEnums.OrderStatus.PENDING_SHIPMENT.value, order.getOrderStatus());
        assertEquals(StatusEnums.PayStatus.PAID.value, order.getPayStatus());
        assertTrue(order.getPayTime() != null, "到账时间必须记录");
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        WalletTransaction ledger = captor.getValue();
        assertEquals(StatusEnums.WalletTransactionType.TRANSFER_PAYMENT.value, ledger.getTransType());
        assertEquals(StatusEnums.WalletDirection.IN.value, ledger.getDirection());
        // 记账台账不动钱包余额。
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.getBalanceAfter()));
        verifyNoInteractions(walletMapper);
    }

    /** 终审通过但订单已是已支付（异常重复）时不重复记账。 */
    @Test
    void finalAuditApprovalIsIdempotentWhenAlreadyPaid() {
        AuditRecord record = new AuditRecord();
        record.setId(32L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(5L);
        when(auditRecordMapper.selectById(32L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.PAID.value);
        when(orderMapper.selectById(11L)).thenReturn(order);
        AuditNode current = new AuditNode();
        current.setId(5L);
        current.setFlowId(4L);
        current.setNodeOrder(1);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);
        when(auditNodeMapper.selectOne(any())).thenReturn(null);

        service.auditOrder(9L, 32L, true, null);

        verifyNoInteractions(transactionMapper);
    }

    /** 多节点流程：非终审节点只建下一节点，不得提前确认到账。 */
    @Test
    void intermediateAuditNodeDoesNotConfirmPayment() {
        AuditRecord record = new AuditRecord();
        record.setId(33L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(5L);
        when(auditRecordMapper.selectById(33L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(9L)).thenReturn(true);
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectById(11L)).thenReturn(order);
        AuditNode current = new AuditNode();
        current.setId(5L);
        current.setFlowId(4L);
        current.setNodeOrder(1);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);
        AuditNode next = new AuditNode();
        next.setId(6L);
        next.setFlowId(4L);
        next.setNodeName("主管审核");
        next.setNodeOrder(2);
        when(auditNodeMapper.selectOne(any())).thenReturn(next);

        service.auditOrder(9L, 33L, true, "进入下一节点");

        // 还在流程中：订单仍待审核、仍未支付。
        assertEquals(StatusEnums.OrderStatus.PENDING_AUDIT.value, order.getOrderStatus());
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        verifyNoInteractions(transactionMapper);
        verify(auditRecordMapper).insert(any(AuditRecord.class));
    }

    /** 订单审核改为一级：持有流程任一节点角色即可审核（销售或主管都能审）。 */
    @Test
    void orderAuditAllowsAnyRoleInFlow() {
        AuditRecord record = new AuditRecord();
        record.setId(34L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(5L);
        when(auditRecordMapper.selectById(34L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(8L)).thenReturn(false);
        when(accessControlService.roleIds(8L)).thenReturn(List.of(3L));
        AuditNode current = new AuditNode();
        current.setId(5L);
        current.setFlowId(4L);
        current.setNodeOrder(1);
        current.setRoleId(2L);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(11L, 7L, StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectById(11L)).thenReturn(order);
        when(auditNodeMapper.selectOne(any())).thenReturn(null);

        // 销售员(role 3)虽不是当前节点角色(role 2)，但订单审核允许销售/主管任一人审核。
        service.auditOrder(8L, 34L, true, null);

        assertEquals(StatusEnums.OrderStatus.PENDING_SHIPMENT.value, order.getOrderStatus());
    }

    /** 完全不属于该流程的角色仍然不能审核。 */
    @Test
    void orderAuditRejectsRoleOutsideFlow() {
        AuditRecord record = new AuditRecord();
        record.setId(35L);
        record.setBizType(StatusEnums.AuditBizType.ORDER.value);
        record.setBizId(11L);
        record.setFlowNodeId(5L);
        when(auditRecordMapper.selectById(35L)).thenReturn(record);
        when(accessControlService.isSuperAdmin(8L)).thenReturn(false);
        when(accessControlService.roleIds(8L)).thenReturn(List.of(4L));
        AuditNode current = new AuditNode();
        current.setId(5L);
        current.setFlowId(4L);
        current.setNodeOrder(1);
        current.setRoleId(2L);
        when(auditNodeMapper.selectById(5L)).thenReturn(current);

        // 客服(role 4)既非销售也非主管，无订单审核权。
        assertThrows(BusinessException.class, () -> service.auditOrder(8L, 35L, true, null));

        verify(auditRecordMapper, never()).update(any(), any());
    }

    /** 钱包支付已下线：直连 pay 接口传 WALLET 也必须被拒，不能绕过转账流程。 */
    @Test
    void payRejectsWalletMethodSinceItIsRetired() {
        OrderMain order = order(41L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        when(orderMapper.selectOwnedByNoForUpdate(order.getOrderNo(), 7L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.pay(7L, order.getOrderNo(), "WALLET"));

        assertEquals("该支付方式已停用，请使用银行转账并上传转账凭证", exception.getMessage());
        assertEquals(StatusEnums.PayStatus.UNPAID.value, order.getPayStatus());
        verifyNoInteractions(walletMapper, transactionMapper, auditRecordMapper);
    }

    /** 未上传凭证时不允许"确认到账"，否则会绕过凭证环节。 */
    @Test
    void confirmReceivedRequiresTransferVoucher() {
        OrderMain order = order(42L, 7L, StatusEnums.OrderStatus.PENDING_PAYMENT.value,
                StatusEnums.PayStatus.UNPAID.value);
        order.setPayMethod("BANK_TRANSFER");
        when(orderMapper.selectById(42L)).thenReturn(order);
        when(attachmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirmBankTransferReceived(42L));

        assertEquals("请先由买家上传转账凭证，再确认到账", exception.getMessage());
        verify(auditRecordMapper, never()).insert(any(AuditRecord.class));
    }

    /** 确认完成要求已上传客户盖章回传单；缺失时必须拒绝，前端据此禁用按钮。 */
    @Test
    void confirmRejectsWhenCustomerReceiptMissing() {
        when(orderMapper.selectById(51L)).thenReturn(order(51L, 7L,
                StatusEnums.OrderStatus.PENDING_RECEIPT.value, StatusEnums.PayStatus.PAID.value));
        when(attachmentMapper.selectCount(any())).thenReturn(0L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(1L, 51L));

        assertEquals("请先上传客户盖章回传单", exception.getMessage());
        verify(orderMapper, never()).updateById(any(OrderMain.class));
    }

    /** 已上传回传单时确认完成成功。 */
    @Test
    void confirmSucceedsWhenCustomerReceiptExists() {
        OrderMain order = order(52L, 7L, StatusEnums.OrderStatus.PENDING_RECEIPT.value,
                StatusEnums.PayStatus.PAID.value);
        when(orderMapper.selectById(52L)).thenReturn(order);
        when(attachmentMapper.selectCount(any())).thenReturn(1L);

        service.confirm(1L, 52L);

        assertEquals(StatusEnums.OrderStatus.COMPLETED.value, order.getOrderStatus());
        verify(orderMapper).updateById(order);
    }

    /** 非待收货状态不可确认完成。 */
    @Test
    void confirmRejectsOrderNotInPendingReceipt() {
        when(orderMapper.selectById(53L)).thenReturn(order(53L, 7L,
                StatusEnums.OrderStatus.PENDING_SHIPMENT.value, StatusEnums.PayStatus.PAID.value));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(1L, 53L));

        assertEquals("当前订单不可确认", exception.getMessage());
        verifyNoInteractions(attachmentMapper);
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

    private OrderMain prepareCheckout() {
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
        // 线下转账流程不再读写钱包，此处不再打钱包桩。
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
