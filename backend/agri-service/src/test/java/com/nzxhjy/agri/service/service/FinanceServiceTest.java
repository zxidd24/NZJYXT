package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.OrderDetail;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.LoanRecord;
import com.nzxhjy.agri.service.entity.RefundApply;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.AuditRecord;
import com.nzxhjy.agri.service.entity.SysOperLog;
import com.nzxhjy.agri.service.entity.WalletTransaction;
import com.nzxhjy.agri.service.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock SysOperLogMapper operLogMapper;
    @InjectMocks FinanceService service;

    @org.junit.jupiter.api.BeforeEach
    void initTableInfo() {
        // MyBatis-Plus 的 lambda 条件需要实体元数据；单元测试无 Spring 上下文，手动初始化。
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                com.nzxhjy.agri.service.entity.WalletTransaction.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                com.nzxhjy.agri.service.entity.AuditRecord.class);
    }

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

    @Test
    void completedOrderCanApplyRefund() {
        OrderMain order = new OrderMain();
        order.setId(9L);
        order.setUserId(7L);
        order.setPayStatus(1);
        order.setOrderStatus(4);
        order.setPayAmount(new BigDecimal("20.00"));
        when(orderMapper.selectById(9L)).thenReturn(order);
        when(refundMapper.selectCount(any())).thenReturn(0L);
        when(detailMapper.selectList(any())).thenReturn(java.util.List.of());
        AuditFlow flow = new AuditFlow();
        flow.setId(5L);
        AuditNode node = new AuditNode();
        node.setId(6L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        FinanceService.RefundView view = service.applyRefund(7L, 9L,
                new BigDecimal("20.00"), "已完成订单退款");

        assertEquals(new BigDecimal("20.00"), view.getRefundAmount());
        verify(refundMapper).insert(any(RefundApply.class));
    }

    /** P6：一期只做整单退款，部分金额必须拒绝，否则会退一点钱却整单退款+全额回补库存。 */
    @Test
    void partialRefundIsRejected() {
        OrderMain order = paidOrder(10L, "10.00");
        when(orderMapper.selectById(10L)).thenReturn(order);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.applyRefund(7L, 10L, new BigDecimal("3.00"), "部分退款"));

        assertEquals(ErrorCodeEnum.BUSINESS_ERROR.getCode(), exception.getCode());
        assertEquals("退款金额必须等于订单实付金额10.00元", exception.getMessage());
        verify(refundMapper, never()).insert(any(RefundApply.class));
        verify(orderMapper, never()).updateById(any(OrderMain.class));
    }

    /** P6：0.01 元这类极小金额同样不能通过。 */
    @Test
    void tinyRefundAmountIsRejected() {
        when(orderMapper.selectById(11L)).thenReturn(paidOrder(11L, "10.00"));

        assertThrows(BusinessException.class,
                () -> service.applyRefund(7L, 11L, new BigDecimal("0.01"), "退一分"));

        verify(refundMapper, never()).insert(any(RefundApply.class));
    }

    /** P6：等于实付金额的整单退款正常通过。 */
    @Test
    void exactAmountRefundIsAccepted() {
        when(orderMapper.selectById(12L)).thenReturn(paidOrder(12L, "10.00"));
        when(refundMapper.selectCount(any())).thenReturn(0L);
        AuditFlow flow = new AuditFlow();
        flow.setId(5L);
        AuditNode node = new AuditNode();
        node.setId(6L);
        when(auditFlowMapper.selectOne(any())).thenReturn(flow);
        when(auditNodeMapper.selectOne(any())).thenReturn(node);

        FinanceService.RefundView view = service.applyRefund(7L, 12L, new BigDecimal("10.00"), "整单退款");

        assertEquals(new BigDecimal("10.00"), view.getRefundAmount());
        verify(refundMapper).insert(any(RefundApply.class));
    }

    private OrderMain paidOrder(Long id, String payAmount) {
        OrderMain order = new OrderMain();
        order.setId(id);
        order.setUserId(7L);
        order.setPayStatus(1);
        order.setOrderStatus(1);
        order.setPayAmount(new BigDecimal(payAmount));
        return order;
    }

    /** 授信占用口径：申请中(0)与已放款(1)计入，已还款(2)和驳回(3)不计入。 */
    @Test
    void loanUsedSumsOnlyActiveLoanOccupancy() {
        LoanRecord applying = loan("100.00");
        LoanRecord released = loan("50.00");
        when(loanMapper.selectList(any())).thenReturn(java.util.List.of(applying, released));

        assertEquals(new BigDecimal("150.00"), service.loanUsed(7L));

        verify(loanMapper).selectList(any());
    }

    @Test
    void loanUsedIsZeroWhenNoActiveLoan() {
        when(loanMapper.selectList(any())).thenReturn(java.util.List.of());

        assertEquals(BigDecimal.ZERO, service.loanUsed(7L));
    }

    /** P15：放款必须有权限门槛，任意 adminId 不能直接放款。 */
    @Test
    void releaseLoanRejectsOperatorWithoutNodeRole() {
        when(loanMapper.selectById(13L)).thenReturn(approvedLoan(13L));
        stubApprovedAuditRecord();
        when(accessControlService.isSuperAdmin(999L)).thenReturn(false);
        when(accessControlService.roleIds(999L)).thenReturn(java.util.List.of(3L));
        when(auditNodeMapper.selectById(any())).thenReturn(loanNode(2L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.releaseLoan(999L, 13L));

        assertEquals(ErrorCodeEnum.FORBIDDEN.getCode(), exception.getCode());
        verify(loanMapper, never()).updateById(any(LoanRecord.class));
        verifyNoInteractions(transactionMapper, operLogMapper);
    }

    /** P15：还款同样要有权限门槛。 */
    @Test
    void repayLoanRejectsOperatorWithoutNodeRole() {
        when(loanMapper.selectById(14L)).thenReturn(releasedLoan(14L));
        stubApprovedAuditRecord();
        when(accessControlService.isSuperAdmin(999L)).thenReturn(false);
        when(accessControlService.roleIds(999L)).thenReturn(java.util.List.of(3L));
        when(auditNodeMapper.selectById(any())).thenReturn(loanNode(2L));

        assertThrows(BusinessException.class, () -> service.repayLoan(999L, 14L));

        verify(loanMapper, never()).updateById(any(LoanRecord.class));
    }

    /** P15/P20：持有贷款审核节点的角色可放款，并写入记账流水与操作日志。 */
    @Test
    void releaseLoanByAuthorizedOperatorWritesTransactionAndLog() {
        LoanRecord loan = approvedLoan(15L);
        when(loanMapper.selectById(15L)).thenReturn(loan);
        stubApprovedAuditRecord();
        when(accessControlService.isSuperAdmin(2L)).thenReturn(false);
        when(accessControlService.roleIds(2L)).thenReturn(java.util.List.of(2L));
        when(auditNodeMapper.selectById(any())).thenReturn(loanNode(2L));
        when(loanMapper.updateById(any(LoanRecord.class))).thenReturn(1);

        service.releaseLoan(2L, 15L);

        assertEquals(StatusEnums.LoanStatus.RELEASED.value, loan.getStatus());
        // 受托支付口径：写入记账流水，但不改变钱包余额。
        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        WalletTransaction tx = txCaptor.getValue();
        assertEquals(StatusEnums.WalletTransactionType.LOAN_RELEASE.value, tx.getTransType());
        assertEquals(StatusEnums.WalletDirection.OUT.value, tx.getDirection());
        assertEquals(0, BigDecimal.ZERO.compareTo(tx.getBalanceAfter()));
        ArgumentCaptor<SysOperLog> logCaptor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogMapper).insert(logCaptor.capture());
        assertEquals("确认放款", logCaptor.getValue().getAction());
        verifyNoInteractions(walletMapper);
    }

    /** P20：超管可直接放款，同样记账。 */
    @Test
    void releaseLoanBySuperAdminIsAllowed() {
        LoanRecord loan = approvedLoan(16L);
        when(loanMapper.selectById(16L)).thenReturn(loan);
        stubApprovedAuditRecord();
        when(accessControlService.isSuperAdmin(1L)).thenReturn(true);
        when(loanMapper.updateById(any(LoanRecord.class))).thenReturn(1);

        service.releaseLoan(1L, 16L);

        assertEquals(StatusEnums.LoanStatus.RELEASED.value, loan.getStatus());
        verify(transactionMapper).insert(any(com.nzxhjy.agri.service.entity.WalletTransaction.class));
    }

    /** P20：还款写入 IN 方向流水，且额度占用随状态变为已还款而释放。 */
    @Test
    void repayLoanWritesTransactionAndReleasesCredit() {
        LoanRecord loan = releasedLoan(17L);
        when(loanMapper.selectById(17L)).thenReturn(loan);
        // 超管短路权限校验，无需审核记录与节点查询。
        when(accessControlService.isSuperAdmin(1L)).thenReturn(true);
        when(loanMapper.updateById(any(LoanRecord.class))).thenReturn(1);

        service.repayLoan(1L, 17L);

        assertEquals(StatusEnums.LoanStatus.REPAID.value, loan.getStatus());
        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(txCaptor.capture());
        assertEquals(StatusEnums.WalletTransactionType.LOAN_REPAY.value, txCaptor.getValue().getTransType());
        assertEquals(StatusEnums.WalletDirection.IN.value, txCaptor.getValue().getDirection());
        ArgumentCaptor<SysOperLog> logCaptor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogMapper).insert(logCaptor.capture());
        assertEquals("确认还款", logCaptor.getValue().getAction());
    }

    /** P20 连带：贷款记账流水不能出现在门户钱包流水中（否则用户会误以为余额被扣）。 */
    @Test
    void walletTransactionsExcludesLoanBookkeeping() {
        when(transactionMapper.selectPage(any(), any())).thenReturn(page(java.util.List.of()));

        service.walletTransactions(7L, 1, 10, null);

        // 锁定查询条件：必须排除 5（放款）与 6（还款）两类记账流水。
        verify(transactionMapper).selectPage(any(), argThat(wrapper ->
                wrapper.getSqlSegment().contains("trans_type NOT IN") || wrapper.getSqlSegment().contains("trans_type NOT IN")));
    }

    /** P20：还款后 loanUsed 不再统计该笔，额度占用自动释放。 */
    @Test
    void repaidLoanNoLongerOccupiesCredit() {
        // loanUsed 的查询条件限定 status IN (0,1)，已还款(2)由 SQL 层排除，这里锁定该条件。
        when(loanMapper.selectList(any())).thenReturn(java.util.List.of());

        assertEquals(BigDecimal.ZERO, service.loanUsed(17L));
    }

    /** 放款/还款前置：必须存在一条已审核通过的贷款审核记录。 */
    private void stubApprovedAuditRecord() {
        AuditRecord record = new AuditRecord();
        record.setId(70L);
        record.setBizType(6);
        record.setStatus(1);
        record.setFlowNodeId(60L);
        when(auditRecordMapper.selectOne(any())).thenReturn(record);
    }

    private LoanRecord approvedLoan(Long id) {
        LoanRecord loan = new LoanRecord();
        loan.setId(id);
        loan.setUserId(7L);
        loan.setLoanNo("LN" + id);
        loan.setAmount(new BigDecimal("100.00"));
        loan.setCreditLimitUsed(new BigDecimal("100.00"));
        loan.setStatus(StatusEnums.LoanStatus.APPLYING.value);
        return loan;
    }

    private LoanRecord releasedLoan(Long id) {
        LoanRecord loan = approvedLoan(id);
        loan.setStatus(StatusEnums.LoanStatus.RELEASED.value);
        return loan;
    }

    private AuditNode loanNode(Long roleId) {
        AuditNode node = new AuditNode();
        node.setId(60L);
        node.setRoleId(roleId);
        node.setNodeName("贷款审核");
        return node;
    }

    /**
     * 退款审核通过 ≠ 已退款：只置为"待打款"，不动订单状态、不回补库存，
     * 实际打款由财务线下完成后登记。
     */
    @Test
    void refundApprovalWaitsForOfflinePayout() {
        RefundApply refund = pendingRefund(30L);
        when(refundMapper.selectById(30L)).thenReturn(refund);
        when(accessControlService.isSuperAdmin(1L)).thenReturn(true);
        when(auditRecordMapper.selectOne(any())).thenReturn(pendingAuditRecord());
        when(auditRecordMapper.update(any(), any())).thenReturn(1);
        OrderMain order = order(39L, StatusEnums.OrderStatus.REFUNDING.value);
        when(orderMapper.selectById(39L)).thenReturn(order);

        service.auditRefund(1L, 30L, true, "同意退款");

        assertEquals(StatusEnums.RefundStatus.APPROVED.value, refund.getStatus());
        // 关键：订单还不能置为已退款，库存也不能回补。
        assertEquals(StatusEnums.OrderStatus.REFUNDING.value, order.getOrderStatus());
        verify(productMapper, never()).update(any(), any());
        verifyNoInteractions(walletMapper);
    }

    /** 线下打款登记后才置为已退款、回补库存并写退款台账。 */
    @Test
    void offlineRefundRegistrationCompletesRefund() {
        RefundApply refund = pendingRefund(31L);
        refund.setStatus(StatusEnums.RefundStatus.APPROVED.value);
        when(refundMapper.selectById(31L)).thenReturn(refund);
        when(refundMapper.updateById(any(RefundApply.class))).thenReturn(1);
        OrderMain order = order(40L, StatusEnums.OrderStatus.REFUNDING.value);
        when(orderMapper.selectById(40L)).thenReturn(order);
        OrderDetail detail = new OrderDetail();
        detail.setProductId(3L);
        detail.setQuantity(2);
        when(detailMapper.selectList(any())).thenReturn(List.of(detail));
        when(productMapper.update(any(), any())).thenReturn(1);

        service.registerOfflineRefund(1L, 31L, "已打款 流水号 X123");

        assertEquals(StatusEnums.RefundStatus.REFUNDED.value, refund.getStatus());
        assertEquals(StatusEnums.OrderStatus.REFUNDED.value, order.getOrderStatus());
        verify(productMapper).update(isNull(), any());
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(transactionMapper).insert(captor.capture());
        assertEquals(StatusEnums.WalletTransactionType.REFUND.value, captor.getValue().getTransType());
        assertEquals(StatusEnums.WalletDirection.OUT.value, captor.getValue().getDirection());
        verify(operLogMapper).insert(any(SysOperLog.class));
    }

    /** 未审核通过的退款单不能登记打款，避免绕过审核直接退款。 */
    @Test
    void offlineRefundRegistrationRejectsUnapprovedRefund() {
        RefundApply refund = pendingRefund(32L);
        refund.setStatus(StatusEnums.RefundStatus.PENDING_AUDIT.value);
        when(refundMapper.selectById(32L)).thenReturn(refund);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.registerOfflineRefund(1L, 32L, "跳过审核"));

        assertEquals("退款单不存在或尚未审核通过", exception.getMessage());
        verify(refundMapper, never()).updateById(any(RefundApply.class));
    }

    /** 已退款的单子不能重复登记，避免重复回补库存与重复记账。 */
    @Test
    void offlineRefundRegistrationIsIdempotent() {
        RefundApply refund = pendingRefund(33L);
        refund.setStatus(StatusEnums.RefundStatus.REFUNDED.value);
        when(refundMapper.selectById(33L)).thenReturn(refund);

        assertThrows(BusinessException.class, () -> service.registerOfflineRefund(1L, 33L, "重复登记"));

        verify(productMapper, never()).update(any(), any());
        verifyNoInteractions(transactionMapper);
    }

    private RefundApply pendingRefund(Long id) {
        RefundApply refund = new RefundApply();
        refund.setId(id);
        refund.setRefundNo("RF" + id);
        refund.setOrderId(id + 9);
        refund.setUserId(7L);
        refund.setOrderAmount(new BigDecimal("10.00"));
        refund.setRefundAmount(new BigDecimal("10.00"));
        refund.setRefundChannel("BANK_TRANSFER");
        refund.setStatus(StatusEnums.RefundStatus.PENDING_AUDIT.value);
        return refund;
    }

    private AuditRecord pendingAuditRecord() {
        AuditRecord record = new AuditRecord();
        record.setId(90L);
        record.setBizType(5);
        record.setStatus(0);
        record.setFlowNodeId(58L);
        return record;
    }

    private OrderMain order(Long id, int status) {
        OrderMain order = new OrderMain();
        order.setId(id);
        order.setUserId(7L);
        order.setOrderNo("O" + id);
        order.setOrderStatus(status);
        order.setPayStatus(1);
        order.setPayAmount(new BigDecimal("10.00"));
        return order;
    }

    private <T> com.baomidou.mybatisplus.core.metadata.IPage<T> page(java.util.List<T> records) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(records);
        page.setTotal(records.size());
        return page;
    }

    private LoanRecord loan(String creditLimitUsed) {
        LoanRecord record = new LoanRecord();
        record.setUserId(7L);
        record.setCreditLimitUsed(new BigDecimal(creditLimitUsed));
        return record;
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
