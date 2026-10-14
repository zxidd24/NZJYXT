package com.nzxhjy.agri.service.pay;

/**
 * 平台结算账户信息，用于银行转账场景下向买家展示线下打款目标账户。
 *
 * <p>《开发手册2.md》第十四章：平台在中行开立结算账户统一收款。一期账户信息由配置注入，
 * 二期接入中行后可由「账户查询」接口动态获取。</p>
 */
public record SettlementAccount(String payeeName, String bankName, String accountNo) {
}
