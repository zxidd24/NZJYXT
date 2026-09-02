package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_record")
public class AuditRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer bizType;
    private Long bizId;
    private String bizNo;
    private String bizSummary;
    private Long flowNodeId;
    private String nodeName;
    private Integer status;
    private Long applicantId;
    private LocalDateTime applyTime;
    private Long auditorId;
    private LocalDateTime auditTime;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
