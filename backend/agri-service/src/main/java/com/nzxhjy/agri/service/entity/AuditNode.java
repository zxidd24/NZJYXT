package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("audit_node")
public class AuditNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long flowId;
    private String nodeName;
    private Long roleId;
    private Integer nodeOrder;
}
