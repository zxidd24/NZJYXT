package com.nzxhjy.agri.service.pay;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.OrderMain;
import com.nzxhjy.agri.service.entity.WalletAccount;
import com.nzxhjy.agri.service.entity.WalletTransaction;
import com.nzxhjy.agri.service.mapper.WalletAccountMapper;
import com.nzxhjy.agri.service.mapper.WalletTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * 钱包支付 —— <b>已停用</b>。
 *
 * <p>业务改为"线下银行转账 + 转账凭证审核"后，钱包支付不再是可用支付方式。
 * 本实现仍需保留（{@code PayService} 按 method 分发，删掉会导致 WALLET 落入
 * "支付方式不支持"，语义变成"未知方式"而非"已停用"）。</p>
 *
 * <p>真正的拦截点在 {@code OrderService.pay}：只要实现未声明
 * {@link PayService#awaitsExternalConfirmation()} 为 true，就会被拒绝，
 * 因此即使有人直接调用接口传 {@code WALLET} 也无法扣款下单。</p>
 */
@Service
@RequiredArgsConstructor
public class WalletPayService implements PayService {
    private static final DateTimeFormatter NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final WalletAccountMapper walletMapper;
    private final WalletTransactionMapper transactionMapper;

    @Override
    public String method() {
        return StatusEnums.PayMethod.WALLET.name();
    }

    /** 已停用，正常流程不会走到这里；保留实现仅用于维持支付方式注册表完整。 */
    @Override
    public PayOutcome pay(Long userId, OrderMain order) {
        BigDecimal amount = order.getPayAmount();
        // 行锁读取账户，随后用带余额条件的原子更新扣款，避免并发下扣成负数。
        WalletAccount account = walletMapper.selectByUserIdForUpdate(userId);
        if (account == null || account.getBalance() == null || account.getBalance().compareTo(amount) < 0) {
            throw business("钱包余额不足");
        }
        int updated = walletMapper.update(null, Wrappers.<WalletAccount>lambdaUpdate()
                .eq(WalletAccount::getId, account.getId())
                .ge(WalletAccount::getBalance, amount)
                .setSql("balance = balance - " + amount.toPlainString()));
        if (updated == 0) throw business("钱包余额不足");
        WalletTransaction tx = new WalletTransaction();
        tx.setUserId(userId);
        tx.setOrderId(order.getId());
        tx.setTransNo(generateNo());
        tx.setAmount(amount);
        tx.setDirection(StatusEnums.WalletDirection.OUT.value);
        tx.setTransType(StatusEnums.WalletTransactionType.PAYMENT.value);
        tx.setTransStatus(StatusEnums.WalletTransactionStatus.SUCCESS.value);
        tx.setBalanceAfter(account.getBalance().subtract(amount));
        tx.setRemark("订单支付");
        transactionMapper.insert(tx);
        return PayOutcome.PAID;
    }

    private String generateNo() {
        return "T" + LocalDateTime.now().format(NO_FORMAT) + String.format("%04d", new Random().nextInt(10000));
    }

    private BusinessException business(String message) {
        return new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), message);
    }
}
