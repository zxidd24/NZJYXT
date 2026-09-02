package com.nzxhjy.agri.common.enums;

import lombok.Getter;

@Getter
public enum ErrorCodeEnum {
    SUCCESS(0, "success"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "没有访问权限"),
    INVALID_PARAM(10001, "参数校验失败"),
    BUSINESS_ERROR(20000, "业务处理失败"),
    STOCK_NOT_ENOUGH(20101, "库存不足"),
    ORDER_STATUS_NOT_ALLOWED(20102, "订单状态不允许当前操作"),
    DUPLICATE_SUBMIT(20103, "请勿重复提交"),
    SYSTEM_ERROR(50000, "系统异常");

    private final int code;
    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
