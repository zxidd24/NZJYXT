package com.nzxhjy.agri.service.pay;

import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.OrderMain;
import org.springframework.stereotype.Service;

/**
 * 额度支付（贷款受托支付）——预留实现，一期不开放。
 *
 * <p>《开发手册2.md》将 {@code pay_method=CREDIT} 标注为「贷款受托支付，预留」；
 * 真正落地需要一套独立的授信占用账本（校验剩余授信 → 占用额度 → 退款/还款时释放），
 * 并需先明确订单占用授信的释放时机与 {@code loan_record} 的口径合并，属二期专题。</p>
 *
 * <p>本类存在的意义：显式占位并明确拒绝，避免该支付方式被静默放行（历史缺陷见报告 P3）。</p>
 */
@Service
public class CreditPayService implements PayService {

    @Override
    public String method() {
        return StatusEnums.PayMethod.CREDIT.name();
    }

    @Override
    public PayOutcome pay(Long userId, OrderMain order) {
        throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "额度支付暂未开放，请选择其他支付方式");
    }
}
