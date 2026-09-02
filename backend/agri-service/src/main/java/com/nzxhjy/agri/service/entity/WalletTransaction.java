package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_transaction")
public class WalletTransaction {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long orderId;
    private String transNo;
    private BigDecimal amount;
    private Integer direction;
    private Integer transType;
    private Integer transStatus;
    private BigDecimal balanceAfter;
    private String remark;
    private String voucherUrl;
    private LocalDateTime createdAt;
}
