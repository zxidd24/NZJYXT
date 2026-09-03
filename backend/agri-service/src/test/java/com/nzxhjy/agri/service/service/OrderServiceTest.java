package com.nzxhjy.agri.service.service;

import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.service.entity.OrderDetail;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.entity.WalletTransaction;
import com.nzxhjy.agri.service.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
