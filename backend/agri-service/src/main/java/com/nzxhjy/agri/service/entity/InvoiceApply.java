package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("invoice_apply")
public class InvoiceApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String applyNo;
    private Long userId;
    private Long orderId;
    private Long invoiceInfoId;
    private BigDecimal amount;
    private Integer status;
    private LocalDateTime applyTime;
    private LocalDateTime issueTime;
    private Long issuerId;
    private String invoiceNo;
    private String remark;
}
