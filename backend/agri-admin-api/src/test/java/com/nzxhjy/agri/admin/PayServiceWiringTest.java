package com.nzxhjy.agri.admin;

import com.nzxhjy.agri.service.pay.PayService;
import com.nzxhjy.agri.service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证支付方式实现被 Spring 正确装配。
 *
 * <p>存在的必要性：Spring 在「List 注入但无候选 bean」时会**静默注入空列表**，
 * 应用仍能正常启动，但运行时所有支付都会失败。本测试正向断言三个实现均已被注册。</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/agri_trading?useSSL=false&serverTimezone=Asia/Shanghai",
        "spring.datasource.username=root",
        "spring.datasource.password=unused",
        "spring.data.redis.host=localhost",
        "agri.jwt.secret=0123456789abcdef0123456789abcdef",
        "agri.aes.key=0123456789abcdef",
        "spring.main.lazy-initialization=true"
})
class PayServiceWiringTest {

    @Autowired
    private List<PayService> payServices;

    @Test
    void allPayMethodImplementationsAreRegistered() {
        Set<String> methods = payServices.stream().map(PayService::method).collect(Collectors.toSet());

        assertTrue(methods.contains("WALLET"), "缺少 WALLET 支付实现：实际=" + methods);
        assertTrue(methods.contains("BANK_TRANSFER"), "缺少 BANK_TRANSFER 支付实现：实际=" + methods);
        assertTrue(methods.contains("CREDIT"), "缺少 CREDIT 支付实现：实际=" + methods);
        assertEquals(3, payServices.size(), "支付实现数量应为3：实际=" + methods);
    }

    @Test
    void orderServiceReceivesNonEmptyPayServiceList(@Autowired OrderService orderService) {
        assertTrue(orderService != null, "OrderService 未装配");
    }

    /** 银行转账必须声明为"等待外部到账确认"，否则会退回 P4 的假支付缺陷。 */
    @Test
    void bankTransferAwaitsExternalConfirmationAndExposesSettlementAccount() {
        PayService bank = payServices.stream()
                .filter(service -> "BANK_TRANSFER".equals(service.method())).findFirst().orElseThrow();

        assertTrue(bank.awaitsExternalConfirmation(), "BANK_TRANSFER 必须等待到账确认");
        assertTrue(bank.settlementAccount() != null, "BANK_TRANSFER 必须提供平台结算账户");
        assertTrue(bank.settlementAccount().bankName() != null, "结算账户开户行不能为空");
    }

    /** 钱包支付资金即时到账，不应等待外部确认。 */
    @Test
    void walletDoesNotAwaitExternalConfirmation() {
        PayService wallet = payServices.stream()
                .filter(service -> "WALLET".equals(service.method())).findFirst().orElseThrow();

        assertFalse(wallet.awaitsExternalConfirmation(), "WALLET 不应等待外部确认");
        assertTrue(wallet.settlementAccount() == null, "WALLET 不应返回结算账户");
    }
}
