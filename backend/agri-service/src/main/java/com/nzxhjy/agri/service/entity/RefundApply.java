package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_apply")
public class RefundApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private Long orderId;
    private Long userId;
    private BigDecimal orderAmount;
    private BigDecimal refundAmount;
    private String reason;
    private Integer status;
    private String refundChannel;
    private Integer prevStatus;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private Long auditorId;
    private String remark;
}
