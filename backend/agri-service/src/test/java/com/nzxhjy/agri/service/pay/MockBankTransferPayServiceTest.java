package com.nzxhjy.agri.service.pay;

import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.OrderMain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 银行转账一期限定行为测试。
 *
 * <p>一期由买家主动向平台结算账户汇款，账号是唯一可执行的收款信息；
 * 未配置账号时必须明确拒绝，而不是返回空账号让买家无从付款。</p>
 */
class MockBankTransferPayServiceTest {

    @Test
    void rejectsWhenSettlementAccountNotConfigured() {
        MockBankTransferPayService service =
                new MockBankTransferPayService("农资现货交易平台", "中国银行", "");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.pay(7L, new OrderMain()));

        assertEquals("平台结算账户未配置，暂不支持银行转账，请联系平台", exception.getMessage());
    }

    @Test
    void rejectsWhenSettlementAccountIsBlank() {
        MockBankTransferPayService service =
                new MockBankTransferPayService("农资现货交易平台", "中国银行", "   ");

        assertThrows(BusinessException.class, () -> service.pay(7L, new OrderMain()));
    }

    @Test
    void returnsPendingWhenAccountConfigured() {
        MockBankTransferPayService service =
                new MockBankTransferPayService("农资现货交易平台", "中国银行", "6217000012345678");

        assertEquals(PayOutcome.PENDING, service.pay(7L, new OrderMain()));
    }

    @Test
    void rejectsWhenPayeeNameMissing() {
        // 收款人名称缺失同样无法完成汇款，属于配置不完整。
        MockBankTransferPayService service =
                new MockBankTransferPayService(" ", "中国银行", "6217000012345678");

        assertThrows(BusinessException.class, () -> service.pay(7L, new OrderMain()));
    }
}
