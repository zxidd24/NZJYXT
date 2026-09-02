package com.nzxhjy.agri.service.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.common.security.MaskUtils;
import com.nzxhjy.agri.service.entity.*;
import com.nzxhjy.agri.service.mapper.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final RefundApplyMapper refundMapper;
    private final OrderCommentMapper commentMapper;
    private final LoanRecordMapper loanMapper;
    private final InvoiceInfoMapper invoiceInfoMapper;
    private final InvoiceApplyMapper invoiceApplyMapper;
    private final OrderMainMapper orderMapper;
    private final OrderDetailMapper detailMapper;
    private final ProductMapper productMapper;
    private final SysUserMapper userMapper;
    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;
    private final PortalUserInfoMapper portalInfoMapper;
    private final AuditFlowMapper auditFlowMapper;
    private final AuditNodeMapper auditNodeMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final MessageService messageService;
    private final AccessControlService accessControlService;
    private final AesUtils aesUtils;

    @Transactional
    public RefundView applyRefund(Long userId, Long orderId, BigDecimal amount, String reason) {
        OrderMain order = ownedOrder(userId, orderId);
        if (!Objects.equals(order.getPayStatus(), StatusEnums.PayStatus.PAID.value)
                || !List.of(StatusEnums.OrderStatus.PENDING_AUDIT.value,
                StatusEnums.OrderStatus.PENDING_SHIPMENT.value,
                StatusEnums.OrderStatus.PENDING_RECEIPT.value,
                StatusEnums.OrderStatus.COMPLETED.value).contains(order.getOrderStatus())) {
            throw business("当前订单不可申请退款");
        }
        amount = validAmount(amount, "退款金额");
        if (amount.compareTo(defaultAmount(order.getPayAmount())) > 0) throw business("退款金额不能超过订单实付金额");
        long active = refundMapper.selectCount(Wrappers.<RefundApply>lambdaQuery().eq(RefundApply::getOrderId, orderId)
                .in(RefundApply::getStatus, StatusEnums.RefundStatus.PENDING_AUDIT.value, StatusEnums.RefundStatus.APPROVED.value));
        if (active > 0) throw business("该订单已有进行中的退款申请");
        RefundApply refund = new RefundApply();
        refund.setRefundNo(generateNo("RF")); refund.setOrderId(orderId); refund.setUserId(userId);
        refund.setOrderAmount(order.getPayAmount()); refund.setRefundAmount(amount); refund.setReason(trim(reason, 255));
        refund.setRefundChannel(order.getPayMethod()); refund.setPrevStatus(order.getOrderStatus());
        refund.setStatus(StatusEnums.RefundStatus.PENDING_AUDIT.value); refund.setApplyTime(LocalDateTime.now());
        refundMapper.insert(refund);
        createAudit(StatusEnums.AuditBizType.REFUND.value, refund.getId(), refund.getRefundNo(), "退款审核：" + order.getOrderNo(), userId);
        order.setOrderStatus(StatusEnums.OrderStatus.REFUNDING.value); orderMapper.updateById(order);
        return refundView(refund);
    }

    public RefundView refund(Long userId, Long orderId) {
        RefundApply refund = refundMapper.selectOne(Wrappers.<RefundApply>lambdaQuery().eq(RefundApply::getUserId, userId)
                .eq(RefundApply::getOrderId, orderId).orderByDesc(RefundApply::getId).last("LIMIT 1"));
        if (refund == null) throw business("退款申请不存在");
        return refundView(refund);
    }

    public PageResult<RefundView> refundPage(int pageNum, int pageSize, Integer status) {
        var query = Wrappers.<RefundApply>lambdaQuery().eq(status != null, RefundApply::getStatus, status).orderByDesc(RefundApply::getApplyTime);
        IPage<RefundApply> page = refundMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::refundView).toList());
    }

    @Transactional
    public void auditRefund(Long adminId, Long refundId, boolean approved, String remark) {
        RefundApply refund = refundMapper.selectById(refundId);
        if (refund == null || !Objects.equals(refund.getStatus(), StatusEnums.RefundStatus.PENDING_AUDIT.value)) throw business("退款申请不存在或已处理");
        AuditRecord record = auditRecordMapper.selectOne(Wrappers.<AuditRecord>lambdaQuery().eq(AuditRecord::getBizType, StatusEnums.AuditBizType.REFUND.value)
                .eq(AuditRecord::getBizId, refundId).eq(AuditRecord::getStatus, StatusEnums.AuditStatus.PENDING.value).orderByDesc(AuditRecord::getId).last("LIMIT 1"));
        if (record == null || !canAudit(adminId, record)) throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage());
        if (!approved && (remark == null || remark.isBlank())) throw business("驳回时必须填写原因");
        LocalDateTime now = LocalDateTime.now();
        updateAudit(record.getId(), adminId, approved, remark, now);
        OrderMain order = orderMapper.selectById(refund.getOrderId());
        if (!approved) {
            refund.setStatus(StatusEnums.RefundStatus.REJECTED.value); refund.setAuditTime(now); refund.setAuditorId(adminId); refund.setRemark(trim(remark, 255)); refundMapper.updateById(refund);
            if (order != null) { order.setOrderStatus(refund.getPrevStatus()); orderMapper.updateById(order); }
            messageService.send(refund.getUserId(), "REFUND_RESULT", 5, refund.getId(), java.util.Map.of("订单号", order == null ? "" : order.getOrderNo(), "结果", "驳回", "备注", remark == null ? "" : remark));
            return;
        }
        refund.setStatus(StatusEnums.RefundStatus.REFUNDED.value); refund.setAuditTime(now); refund.setAuditorId(adminId);
        String channel = refund.getRefundChannel();
        if ("WALLET".equalsIgnoreCase(channel)) creditWallet(refund.getUserId(), refund.getOrderId(), refund.getRefundAmount(), "订单退款");
        else refund.setRemark("待线下退款，已登记原路退款");
        refundMapper.updateById(refund);
        if (order != null) { restoreStock(order.getId()); order.setOrderStatus(StatusEnums.OrderStatus.REFUNDED.value); orderMapper.updateById(order); }
        messageService.send(refund.getUserId(), "REFUND_RESULT", 5, refund.getId(), java.util.Map.of("订单号", order == null ? "" : order.getOrderNo(), "结果", "通过", "备注", "金额" + refund.getRefundAmount() + "元"));
    }

    @Transactional
    public void addComment(Long userId, Long orderId, Long productId, Integer score, String content, String images) {
        OrderMain order = ownedOrder(userId, orderId);
        if (!Objects.equals(order.getOrderStatus(), StatusEnums.OrderStatus.COMPLETED.value)) throw business("订单完成后才能评价");
        if (score == null || score < 1 || score > 5) throw business("评分必须为1到5分");
        boolean productInOrder = detailMapper.selectCount(Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orderId).eq(OrderDetail::getProductId, productId)) > 0;
        if (!productInOrder) throw business("评价商品不在订单中");
        if (commentMapper.selectCount(Wrappers.<OrderComment>lambdaQuery().eq(OrderComment::getOrderId, orderId).eq(OrderComment::getProductId, productId)) > 0) throw business("该商品已评价");
        OrderComment comment = new OrderComment(); comment.setOrderId(orderId); comment.setUserId(userId); comment.setProductId(productId); comment.setScore(score); comment.setContent(trim(content, 500)); comment.setImages(trim(images, 1000)); comment.setCreatedAt(LocalDateTime.now()); commentMapper.insert(comment);
    }

    public PageResult<CommentView> commentPage(Long productId, int pageNum, int pageSize) {
        IPage<OrderComment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<OrderComment>lambdaQuery().eq(productId != null, OrderComment::getProductId, productId).orderByDesc(OrderComment::getCreatedAt));
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::commentView).toList());
    }

    public WalletView walletInfo(Long userId) { return walletView(account(userId)); }

    public PageResult<TransactionView> transactions(Long userId, int pageNum, int pageSize, Integer transType) {
        IPage<WalletTransaction> page = transactionMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<WalletTransaction>lambdaQuery().eq(userId != null, WalletTransaction::getUserId, userId).eq(transType != null, WalletTransaction::getTransType, transType).orderByDesc(WalletTransaction::getCreatedAt));
        return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::transactionView).toList());
    }

    @Transactional
    public WalletView deposit(Long userId, BigDecimal amount) { amount = validAmount(amount, "入金金额"); WalletAccount account = account(userId); updateBalance(account, amount, true); addTransaction(userId, null, amount, 1, StatusEnums.WalletTransactionType.DEPOSIT.value, "模拟入金"); return walletInfo(userId); }

    @Transactional
    public WalletView withdraw(Long userId, BigDecimal amount) { amount = validAmount(amount, "出金金额"); WalletAccount account = account(userId); int updated = walletMapper.update(null, Wrappers.<WalletAccount>lambdaUpdate().eq(WalletAccount::getId, account.getId()).ge(WalletAccount::getBalance, amount).setSql("balance = balance - " + amount.toPlainString())); if (updated == 0) throw business("钱包余额不足"); account.setBalance(defaultAmount(account.getBalance()).subtract(amount)); addTransaction(userId, null, amount, 2, StatusEnums.WalletTransactionType.WITHDRAW.value, "模拟提现至绑定银行卡"); return walletInfo(userId); }

    public List<InvoiceInfoView> invoiceInfos(Long userId) { return invoiceInfoMapper.selectList(Wrappers.<InvoiceInfo>lambdaQuery().eq(InvoiceInfo::getUserId, userId).eq(InvoiceInfo::getDeleted, 0).orderByDesc(InvoiceInfo::getIsDefault).orderByDesc(InvoiceInfo::getId)).stream().map(this::invoiceInfoView).toList(); }

    @Transactional
    public InvoiceInfoView saveInvoiceInfo(Long userId, Long id, Integer titleType, String title, String taxNo, String bankName, String bankAccount, String regAddress, String phone, Integer isDefault) {
        if (title == null || title.isBlank()) throw business("发票抬头不能为空");
        InvoiceInfo info = id == null ? new InvoiceInfo() : invoiceInfoMapper.selectById(id);
        if (id != null && (info == null || !Objects.equals(info.getUserId(), userId) || Objects.equals(info.getDeleted(), 1))) throw business("发票信息不存在");
        if (id == null) info.setUserId(userId);
        info.setTitleType(titleType == null ? 2 : titleType); info.setTitle(trim(title, 100)); info.setTaxNo(trim(taxNo, 50)); info.setBankName(trim(bankName, 50)); info.setBankAccount(bankAccount == null || bankAccount.isBlank() ? info.getBankAccount() : aesUtils.encrypt(bankAccount)); info.setRegAddress(trim(regAddress, 200)); info.setPhone(trim(phone, 20)); info.setIsDefault(Integer.valueOf(1).equals(isDefault) ? 1 : 0); info.setDeleted(0);
        if (info.getIsDefault() == 1) invoiceInfoMapper.update(null, Wrappers.<InvoiceInfo>lambdaUpdate().eq(InvoiceInfo::getUserId, userId).set(InvoiceInfo::getIsDefault, 0));
        if (id == null) invoiceInfoMapper.insert(info); else invoiceInfoMapper.updateById(info);
        return invoiceInfoView(info);
    }

    @Transactional public void deleteInvoiceInfo(Long userId, Long id) { InvoiceInfo info = invoiceInfoMapper.selectById(id); if (info == null || !Objects.equals(info.getUserId(), userId)) throw business("发票信息不存在"); info.setDeleted(1); invoiceInfoMapper.updateById(info); }

    @Transactional
    public InvoiceApplyView applyInvoice(Long userId, Long orderId, Long invoiceInfoId, BigDecimal amount) {
        OrderMain order = ownedOrder(userId, orderId); if (!Objects.equals(order.getPayStatus(), StatusEnums.PayStatus.PAID.value)) throw business("已支付订单才能申请开票");
        InvoiceInfo info = invoiceInfoMapper.selectById(invoiceInfoId); if (info == null || !Objects.equals(info.getUserId(), userId) || Objects.equals(info.getDeleted(), 1)) throw business("发票抬头不存在");
        amount = validAmount(amount, "开票金额"); if (amount.compareTo(defaultAmount(order.getPayAmount())) > 0) throw business("开票金额不能超过订单实付金额");
        BigDecimal issued = invoiceApplyMapper.selectList(Wrappers.<InvoiceApply>lambdaQuery().eq(InvoiceApply::getOrderId, orderId).in(InvoiceApply::getStatus, 0, 1)).stream().map(InvoiceApply::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); if (issued.add(amount).compareTo(defaultAmount(order.getPayAmount())) > 0) throw business("该订单可开票金额不足");
        InvoiceApply apply = new InvoiceApply(); apply.setApplyNo(generateNo("IV")); apply.setUserId(userId); apply.setOrderId(orderId); apply.setInvoiceInfoId(invoiceInfoId); apply.setAmount(amount); apply.setStatus(StatusEnums.InvoiceStatus.PENDING.value); apply.setApplyTime(LocalDateTime.now()); invoiceApplyMapper.insert(apply); return invoiceApplyView(apply);
    }

    public PageResult<InvoiceApplyView> invoicePage(Long userId, int pageNum, int pageSize, Integer status) { IPage<InvoiceApply> page = invoiceApplyMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<InvoiceApply>lambdaQuery().eq(userId != null, InvoiceApply::getUserId, userId).eq(status != null, InvoiceApply::getStatus, status).orderByDesc(InvoiceApply::getApplyTime)); return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::invoiceApplyView).toList()); }

    @Transactional public void issueInvoice(Long adminId, Long id, String invoiceNo, String remark) { InvoiceApply apply = invoiceApplyMapper.selectById(id); if (apply == null || !Objects.equals(apply.getStatus(), 0)) throw business("开票申请不存在或已处理"); if (invoiceNo == null || invoiceNo.isBlank()) throw business("发票号码不能为空"); apply.setStatus(1); apply.setInvoiceNo(trim(invoiceNo, 50)); apply.setIssuerId(adminId); apply.setIssueTime(LocalDateTime.now()); apply.setRemark(trim(remark, 255)); invoiceApplyMapper.updateById(apply); }
    @Transactional public void rejectInvoice(Long adminId, Long id, String remark) { InvoiceApply apply = invoiceApplyMapper.selectById(id); if (apply == null || !Objects.equals(apply.getStatus(), 0)) throw business("开票申请不存在或已处理"); apply.setStatus(2); apply.setIssuerId(adminId); apply.setIssueTime(LocalDateTime.now()); apply.setRemark(trim(remark, 255)); invoiceApplyMapper.updateById(apply); }

    @Transactional
    public LoanRecordView applyLoan(Long userId, BigDecimal amount) {
        amount = validAmount(amount, "贷款金额"); PortalUserInfo info = portalInfoMapper.selectById(userId); if (info == null) throw business("用户档案不存在"); BigDecimal limit = defaultAmount(info.getCreditLimit()); BigDecimal used = loanUsed(userId); if (used.add(amount).compareTo(limit) > 0) throw business("申请金额超过剩余授信额度");
        LoanRecord loan = new LoanRecord(); loan.setUserId(userId); loan.setLoanNo(generateNo("LN")); loan.setAmount(amount); loan.setCreditLimitUsed(amount); loan.setStatus(StatusEnums.LoanStatus.APPLYING.value); loan.setApplyTime(LocalDateTime.now()); loanMapper.insert(loan); createAudit(StatusEnums.AuditBizType.LOAN.value, loan.getId(), loan.getLoanNo(), "贷款审核：" + loan.getLoanNo(), userId); return loanView(loan);
    }

    public LoanInfo loanInfo(Long userId) { PortalUserInfo info = portalInfoMapper.selectById(userId); BigDecimal limit = info == null ? BigDecimal.ZERO : defaultAmount(info.getCreditLimit()); BigDecimal used = loanUsed(userId); return new LoanInfo(limit, used, limit.subtract(used).max(BigDecimal.ZERO), loanPage(userId, 1, 100, null).getList()); }
    public PageResult<LoanRecordView> loanPage(Long userId, int pageNum, int pageSize, Integer status) { IPage<LoanRecord> page = loanMapper.selectPage(new Page<>(pageNum, pageSize), Wrappers.<LoanRecord>lambdaQuery().eq(userId != null, LoanRecord::getUserId, userId).eq(status != null, LoanRecord::getStatus, status).orderByDesc(LoanRecord::getApplyTime)); return new PageResult<>(page.getTotal(), pageNum, pageSize, page.getRecords().stream().map(this::loanView).toList()); }
    @Transactional public void auditLoan(Long adminId, Long id, boolean approved, String remark) { LoanRecord loan = loanMapper.selectById(id); if (loan == null || !Objects.equals(loan.getStatus(), 0)) throw business("贷款申请不存在或已处理"); AuditRecord record = pendingAudit(StatusEnums.AuditBizType.LOAN.value, id); if (record == null || !canAudit(adminId, record)) throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage()); if (!approved && (remark == null || remark.isBlank())) throw business("驳回时必须填写原因"); updateAudit(record.getId(), adminId, approved, remark, LocalDateTime.now()); if (!approved) { loan.setStatus(3); loan.setAuditRemark(trim(remark, 255)); } loan.setAuditTime(LocalDateTime.now()); loan.setAuditorId(adminId); loanMapper.updateById(loan); messageService.send(loan.getUserId(), "LOAN_RESULT", 6, loan.getId(), java.util.Map.of("贷款编号", loan.getLoanNo(), "结果", approved ? "通过" : "驳回", "备注", remark == null ? "" : remark)); }
    @Transactional public void releaseLoan(Long adminId, Long id) { LoanRecord loan = loanMapper.selectById(id); if (loan == null || !Objects.equals(loan.getStatus(), 0)) throw business("贷款申请状态不正确"); AuditRecord record = auditRecordMapper.selectOne(Wrappers.<AuditRecord>lambdaQuery().eq(AuditRecord::getBizType, 6).eq(AuditRecord::getBizId, id).eq(AuditRecord::getStatus, 1).last("LIMIT 1")); if (record == null) throw business("贷款尚未审核通过"); loan.setStatus(1); loan.setReleaseTime(LocalDateTime.now()); loanMapper.updateById(loan); }
    @Transactional public void repayLoan(Long adminId, Long id) { LoanRecord loan = loanMapper.selectById(id); if (loan == null || !Objects.equals(loan.getStatus(), 1)) throw business("贷款尚未放款"); loan.setStatus(2); loan.setRepayTime(LocalDateTime.now()); loanMapper.updateById(loan); }

    public PageResult<TransactionView> accountDetail(int pageNum, int pageSize, Integer transType) { return transactions(null, pageNum, pageSize, transType); }
    public List<IncomeExpense> incomeExpense(String period, LocalDate date) { var query = Wrappers.<WalletTransaction>query().eq("trans_status", 1); if (date != null) query.ge("created_at", date.atStartOfDay()).lt("created_at", date.plusDays(1).atStartOfDay()); List<WalletTransaction> rows = transactionMapper.selectList(query); Map<String, BigDecimal[]> groups = new TreeMap<>(); for (WalletTransaction tx : rows) { if (tx.getCreatedAt() == null) continue; String key = "month".equalsIgnoreCase(period) ? tx.getCreatedAt().toLocalDate().withDayOfMonth(1).toString() : tx.getCreatedAt().toLocalDate().toString(); BigDecimal[] sum = groups.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}); if (tx.getDirection() != null && tx.getDirection() == 1) sum[0] = sum[0].add(defaultAmount(tx.getAmount())); else sum[1] = sum[1].add(defaultAmount(tx.getAmount())); } return groups.entrySet().stream().map(e -> new IncomeExpense(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[0].subtract(e.getValue()[1]))).toList(); }
    public byte[] statementExport(Integer transType) { List<TransactionView> rows = accountDetail(1, 10000, transType).getList(); try (ByteArrayOutputStream out = new ByteArrayOutputStream()) { EasyExcel.write(out, StatementRow.class).sheet("对账凭据").doWrite(rows.stream().map(r -> new StatementRow(r.getTransNo(), r.getOrderId(), r.getAmount(), r.getDirection(), r.getTransType(), r.getBalanceAfter(), r.getCreatedAt())).toList()); return out.toByteArray(); } catch (Exception e) { throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR.getCode(), "对账凭据导出失败"); } }
    @Transactional public void attachVoucher(Long id, String voucherUrl) { WalletTransaction tx = transactionMapper.selectById(id); if (tx == null) throw business("资金流水不存在"); tx.setVoucherUrl(trim(voucherUrl, 255)); transactionMapper.updateById(tx); }

    private void createAudit(int bizType, Long bizId, String bizNo, String summary, Long applicantId) { AuditFlow flow = auditFlowMapper.selectOne(Wrappers.<AuditFlow>lambdaQuery().eq(AuditFlow::getBizType, bizType).eq(AuditFlow::getEnabled, 1)); AuditNode node = flow == null ? null : auditNodeMapper.selectOne(Wrappers.<AuditNode>lambdaQuery().eq(AuditNode::getFlowId, flow.getId()).orderByAsc(AuditNode::getNodeOrder).last("LIMIT 1")); if (node == null) throw business("审核流程未配置"); AuditRecord record = new AuditRecord(); record.setBizType(bizType); record.setBizId(bizId); record.setBizNo(bizNo); record.setBizSummary(summary); record.setFlowNodeId(node.getId()); record.setNodeName(node.getNodeName()); record.setStatus(0); record.setApplicantId(applicantId); record.setApplyTime(LocalDateTime.now()); auditRecordMapper.insert(record); messageService.sendTodo(node.getId(), bizType, bizId); }
    private AuditRecord pendingAudit(int bizType, Long bizId) { return auditRecordMapper.selectOne(Wrappers.<AuditRecord>lambdaQuery().eq(AuditRecord::getBizType, bizType).eq(AuditRecord::getBizId, bizId).eq(AuditRecord::getStatus, 0).last("LIMIT 1")); }
    private void updateAudit(Long id, Long adminId, boolean approved, String remark, LocalDateTime now) { int updated = auditRecordMapper.update(null, Wrappers.<AuditRecord>lambdaUpdate().eq(AuditRecord::getId, id).eq(AuditRecord::getStatus, 0).set(AuditRecord::getStatus, approved ? 1 : 2).set(AuditRecord::getAuditorId, adminId).set(AuditRecord::getAuditTime, now).set(AuditRecord::getRemark, trim(remark, 255))); if (updated == 0) throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该任务已处理"); }
    private boolean canAudit(Long userId, AuditRecord record) { if (accessControlService.isSuperAdmin(userId)) return true; AuditNode node = auditNodeMapper.selectById(record.getFlowNodeId()); return node != null && accessControlService.roleIds(userId).contains(node.getRoleId()); }
    private void restoreStock(Long orderId) { detailMapper.selectList(Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, orderId)).forEach(d -> productMapper.update(null, Wrappers.<Product>lambdaUpdate().eq(Product::getId, d.getProductId()).setSql("stock = stock + " + d.getQuantity()))); }
    private void creditWallet(Long userId, Long orderId, BigDecimal amount, String remark) { WalletAccount account = account(userId); updateBalance(account, amount, true); addTransaction(userId, orderId, amount, 1, 4, remark); }
    private void updateBalance(WalletAccount account, BigDecimal amount, boolean plus) { String sql = plus ? "balance = balance + " + amount.toPlainString() : "balance = balance - " + amount.toPlainString(); if (walletMapper.update(null, Wrappers.<WalletAccount>lambdaUpdate().eq(WalletAccount::getId, account.getId()).setSql(sql)) == 0) throw business("钱包更新失败"); account.setBalance(defaultAmount(account.getBalance()).add(plus ? amount : amount.negate())); }
    private void addTransaction(Long userId, Long orderId, BigDecimal amount, int direction, int type, String remark) { WalletAccount account = account(userId); WalletTransaction tx = new WalletTransaction(); tx.setUserId(userId); tx.setOrderId(orderId); tx.setTransNo(generateNo("WT")); tx.setAmount(amount); tx.setDirection(direction); tx.setTransType(type); tx.setTransStatus(1); tx.setBalanceAfter(defaultAmount(account.getBalance())); tx.setRemark(remark); transactionMapper.insert(tx); }
    private WalletAccount account(Long userId) { WalletAccount account = walletMapper.selectOne(Wrappers.<WalletAccount>lambdaQuery().eq(WalletAccount::getUserId, userId)); if (account == null) { account = new WalletAccount(); account.setUserId(userId); account.setBalance(BigDecimal.ZERO); account.setFrozenAmount(BigDecimal.ZERO); walletMapper.insert(account); } return account; }
    private OrderMain ownedOrder(Long userId, Long orderId) { OrderMain order = orderMapper.selectById(orderId); if (order == null || !Objects.equals(order.getUserId(), userId)) throw business("订单不存在"); return order; }
    private BigDecimal loanUsed(Long userId) { return loanMapper.selectList(Wrappers.<LoanRecord>lambdaQuery().eq(LoanRecord::getUserId, userId).in(LoanRecord::getStatus, 0, 1)).stream().map(LoanRecord::getCreditLimitUsed).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private RefundView refundView(RefundApply r) { String productName = detailMapper.selectList(Wrappers.<OrderDetail>lambdaQuery().eq(OrderDetail::getOrderId, r.getOrderId())).stream().map(OrderDetail::getProductName).filter(Objects::nonNull).findFirst().orElse(""); return new RefundView(r.getId(), r.getRefundNo(), r.getOrderId(), productName, r.getOrderAmount(), r.getRefundAmount(), r.getReason(), r.getStatus(), r.getRefundChannel(), r.getApplyTime(), r.getAuditTime(), r.getRemark()); }
    private CommentView commentView(OrderComment c) { Product p = productMapper.selectById(c.getProductId()); SysUser u = c.getUserId() == null ? null : userMapper.selectById(c.getUserId()); return new CommentView(c.getId(), c.getOrderId(), c.getProductId(), p == null ? null : p.getName(), c.getUserId(), u == null ? null : u.getRealName(), c.getScore(), c.getContent(), c.getImages(), c.getCreatedAt()); }
    private WalletView walletView(WalletAccount a) { return new WalletView(a.getUserId(), defaultAmount(a.getBalance()), defaultAmount(a.getFrozenAmount())); }
    private TransactionView transactionView(WalletTransaction t) { return new TransactionView(t.getId(), t.getTransNo(), t.getOrderId(), t.getAmount(), t.getDirection(), t.getTransType(), t.getTransStatus(), t.getBalanceAfter(), t.getRemark(), t.getVoucherUrl(), t.getCreatedAt()); }
    private InvoiceInfoView invoiceInfoView(InvoiceInfo i) { String account = i.getBankAccount() == null ? null : aesUtils.decrypt(i.getBankAccount()); return new InvoiceInfoView(i.getId(), i.getTitleType(), i.getTitle(), i.getTaxNo(), i.getBankName(), account == null ? null : MaskUtils.bankCard(account), i.getRegAddress(), i.getPhone(), i.getIsDefault()); }
    private InvoiceApplyView invoiceApplyView(InvoiceApply i) { return new InvoiceApplyView(i.getId(), i.getApplyNo(), i.getOrderId(), i.getInvoiceInfoId(), i.getAmount(), i.getStatus(), i.getApplyTime(), i.getIssueTime(), i.getInvoiceNo(), i.getRemark()); }
    private LoanRecordView loanView(LoanRecord l) { return new LoanRecordView(l.getId(), l.getLoanNo(), l.getAmount(), l.getCreditLimitUsed(), l.getStatus(), l.getApplyTime(), l.getAuditTime(), l.getReleaseTime(), l.getRepayTime(), l.getAuditRemark()); }
    private BigDecimal validAmount(BigDecimal v, String name) { if (v == null || v.signum() <= 0 || v.scale() > 2) throw business(name + "必须为正数且最多两位小数"); return v.setScale(2); }
    private BigDecimal defaultAmount(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String trim(String v, int max) { if (v == null) return null; return v.length() <= max ? v : v.substring(0, max); }
    private String generateNo(String prefix) { return prefix + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now()) + String.format("%03d", new Random().nextInt(1000)); }
    private BusinessException business(String message) { return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message); }

    @Data @AllArgsConstructor public static class RefundView { private Long id; private String refundNo; private Long orderId; private String productName; private BigDecimal orderAmount; private BigDecimal refundAmount; private String reason; private Integer status; private String refundChannel; private LocalDateTime applyTime; private LocalDateTime auditTime; private String remark; }
    @Data @AllArgsConstructor public static class CommentView { private Long id; private Long orderId; private Long productId; private String productName; private Long userId; private String buyerName; private Integer score; private String content; private String images; private LocalDateTime createdAt; }
    @Data @AllArgsConstructor public static class WalletView { private Long userId; private BigDecimal balance; private BigDecimal frozenAmount; }
    @Data @AllArgsConstructor public static class TransactionView { private Long id; private String transNo; private Long orderId; private BigDecimal amount; private Integer direction; private Integer transType; private Integer transStatus; private BigDecimal balanceAfter; private String remark; private String voucherUrl; private LocalDateTime createdAt; }
    @Data @AllArgsConstructor public static class InvoiceInfoView { private Long id; private Integer titleType; private String title; private String taxNo; private String bankName; private String bankAccount; private String regAddress; private String phone; private Integer isDefault; }
    @Data @AllArgsConstructor public static class InvoiceApplyView { private Long id; private String applyNo; private Long orderId; private Long invoiceInfoId; private BigDecimal amount; private Integer status; private LocalDateTime applyTime; private LocalDateTime issueTime; private String invoiceNo; private String remark; }
    @Data @AllArgsConstructor public static class LoanRecordView { private Long id; private String loanNo; private BigDecimal amount; private BigDecimal creditLimitUsed; private Integer status; private LocalDateTime applyTime; private LocalDateTime auditTime; private LocalDateTime releaseTime; private LocalDateTime repayTime; private String auditRemark; }
    @Data @AllArgsConstructor public static class LoanInfo { private BigDecimal creditLimit; private BigDecimal used; private BigDecimal remaining; private List<LoanRecordView> records; }
    @Data @AllArgsConstructor public static class IncomeExpense { private String period; private BigDecimal income; private BigDecimal expense; private BigDecimal net; }
    @Data @AllArgsConstructor public static class StatementRow { private String transNo; private Long orderId; private BigDecimal amount; private Integer direction; private Integer transType; private BigDecimal balanceAfter; private LocalDateTime createdAt; }
}
