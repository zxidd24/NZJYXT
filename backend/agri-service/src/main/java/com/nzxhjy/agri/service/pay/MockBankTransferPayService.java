package com.nzxhjy.agri.service.pay;

import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.service.entity.OrderMain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 银行转账支付（一期 Mock）。对应《开发手册2.md》第十四章分期策略。
 *
 * <p><b>一期行为</b>：不产生任何资金动作，仅向买家返回平台结算账户信息，
 * 订单保持待付款；待财务人工确认到账（或二期银行回调）后才置为已支付。
 * 由此杜绝"未到账即标记支付成功"的假支付缺陷（报告 P4）。</p>
 *
 * <p><b>二期替换点</b>：新增 {@code BocPayServiceImpl} 实现本接口并交由配置择一启用，
 * 在 {@link #pay} 中调用中行「生成支付订单」接口；到账由回调驱动，
 * 回调统一走 {@code OrderService.confirmBankTransferReceived}，本类其余逻辑无需改动。</p>
 */
@Service
public class MockBankTransferPayService implements PayService {

    private final SettlementAccount settlementAccount;

    public MockBankTransferPayService(
            @Value("${agri.pay.bank.payee-name:农资现货交易平台}") String payeeName,
            @Value("${agri.pay.bank.bank-name:中国银行}") String bankName,
            @Value("${agri.pay.bank.account-no:}") String accountNo) {
        this.settlementAccount = new SettlementAccount(payeeName, bankName, accountNo);
    }

    @Override
    public String method() {
        return StatusEnums.PayMethod.BANK_TRANSFER.name();
    }

    @Override
    public PayOutcome pay(Long userId, OrderMain order) {
        // TODO 二期：调用中行「生成支付订单」（订单号、金额、附言），
        //  返回支付订单号/收款虚拟账号；到账由「到账通知回调」验签后驱动
        //  OrderService.confirmBankTransferReceived，不得在此直接放行。
        return PayOutcome.PENDING;
    }

    @Override
    public boolean awaitsExternalConfirmation() {
        return true;
    }

    @Override
    public SettlementAccount settlementAccount() {
        return settlementAccount;
    }
}
