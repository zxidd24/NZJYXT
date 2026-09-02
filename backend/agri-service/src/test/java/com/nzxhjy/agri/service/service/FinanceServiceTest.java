package com.nzxhjy.agri.service.service;

import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.LoanRecord;
import com.nzxhjy.agri.service.entity.RefundApply;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinanceServiceTest {
    @Mock RefundApplyMapper refundMapper;
    @Mock OrderCommentMapper commentMapper;
    @Mock LoanRecordMapper loanMapper;
    @Mock InvoiceInfoMapper invoiceInfoMapper;
    @Mock InvoiceApplyMapper invoiceApplyMapper;
    @Mock OrderMainMapper orderMapper;
    @Mock OrderDetailMapper detailMapper;
    @Mock ProductMapper productMapper;
    @Mock SysUserMapper userMapper;
    @Mock WalletAccountMapper walletMapper;
    @Mock WalletTransactionMapper transactionMapper;
    @Mock PortalUserInfoMapper portalInfoMapper;
    @Mock AuditFlowMapper auditFlowMapper;
    @Mock AuditNodeMapper auditNodeMapper;
    @Mock AuditRecordMapper auditRecordMapper;
    @Mock MessageService messageService;
    @Mock AccessControlService accessControlService;
    @Mock AesUtils aesUtils;
    @InjectMocks FinanceService service;

    @Test
    void depositUpdatesBalanceAndWritesTransaction() {
        WalletAccount account = wallet(7L, "100.00");
        when(walletMapper.selectOne(any())).thenReturn(account);
        when(walletMapper.update(any(), any())).thenReturn(1);

        FinanceService.WalletView view = service.deposit(7L, new BigDecimal("50.00"));

        assertEquals(new BigDecimal("150.00"), view.getBalance());
        verify(transactionMapper).insert(any(com.nzxhjy.agri.service.entity.WalletTransaction.class));
        verify(walletMapper, times(3)).selectOne(any());
    }

    @Test
    void withdrawFailsWhenAtomicBalanceUpdateAffectsNoRows() {
        WalletAccount account = wallet(7L, "10.00");
        when(walletMapper.selectOne(any())).thenReturn(account);
        when(walletMapper.update(any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.withdraw(7L, new BigDecimal("20.00")));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        verifyNoInteractions(transactionMapper);
    }

    @Test
    void loanApplicationCannotExceedRemainingCredit() {
        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(7L);
        info.setCreditLimit(new BigDecimal("100.00"));
        when(portalInfoMapper.selectById(7L)).thenReturn(info);
        when(loanMapper.selectList(any())).thenReturn(java.util.List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyLoan(7L, new BigDecimal("100.01")));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        verify(loanMapper, never()).insert(any(LoanRecord.class));
    }

    @Test
    void refundAmountCannotExceedPaidAmount() {
        OrderMain order = new OrderMain();
        order.setId(8L);
        order.setUserId(7L);
        order.setPayStatus(1);
        order.setOrderStatus(1);
        order.setPayAmount(new BigDecimal("20.00"));
        when(orderMapper.selectById(8L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyRefund(7L, 8L, new BigDecimal("20.01"), "重复申请"));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        verify(refundMapper, never()).insert(any(RefundApply.class));
    }

    private WalletAccount wallet(Long userId, String balance) {
        WalletAccount account = new WalletAccount();
        account.setId(1L);
        account.setUserId(userId);
        account.setBalance(new BigDecimal(balance));
        account.setFrozenAmount(BigDecimal.ZERO);
        return account;
    }
}
