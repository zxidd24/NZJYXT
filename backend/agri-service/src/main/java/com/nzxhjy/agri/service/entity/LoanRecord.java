package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("loan_record")
public class LoanRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String loanNo;
    private BigDecimal amount;
    private BigDecimal creditLimitUsed;
    private Integer status;
    private LocalDateTime applyTime;
    private LocalDateTime auditTime;
    private Long auditorId;
    private String auditRemark;
    private LocalDateTime releaseTime;
    private LocalDateTime repayTime;
    private LocalDateTime createdAt;
}
