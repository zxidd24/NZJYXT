package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.redis.RedisUtils;
import com.nzxhjy.agri.service.entity.OrderDetail;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.Product;
import com.nzxhjy.agri.service.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
