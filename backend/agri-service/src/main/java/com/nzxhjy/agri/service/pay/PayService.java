package com.nzxhjy.agri.service.pay;

import com.nzxhjy.agri.service.entity.OrderMain;

/**
 * 支付方式抽象，对应《开发手册2.md》第十四章「中行结算对接」一期分期策略。
 *
 * <p>每种 {@code order_main.pay_method} 取值对应一个实现；二期接入中行时新增
 * {@code BocPayServiceImpl} 替换 Mock 实现即可，无需改动订单状态机。</p>
 */
public interface PayService {
    /** 支付方式标识，取值与 {@code order_main.pay_method} 一致。 */
    String method();

    /**
     * 发起支付。实现方只负责资金动作，订单状态由 {@code OrderService} 按返回值统一推进；
     * 抛出异常表示支付失败，外层事务回滚。
     *
     * @return {@link PayOutcome#PAID} 表示资金已到账、订单可流转；
     *         {@link PayOutcome#PENDING} 表示等待外部到账确认、订单保持待付款
     */
    PayOutcome pay(Long userId, OrderMain order);

    /**
     * 该支付方式是否需等待外部到账确认。等待期间订单保持待付款，
     * 且不参与待付款超时自动取消（避免"款已汇、单已取消"）。
     */
    default boolean awaitsExternalConfirmation() {
        return false;
    }

    /**
     * 平台结算账户信息，供线下打款类支付向买家展示打款目标账户。
     * 非线下支付返回 {@code null}。
     */
    default SettlementAccount settlementAccount() {
        return null;
    }
}
