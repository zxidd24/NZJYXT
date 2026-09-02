package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("audit_flow")
public class AuditFlow {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer bizType;
    private String flowName;
    private Integer enabled;
}
