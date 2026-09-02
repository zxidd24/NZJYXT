package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("invoice_info")
public class InvoiceInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer titleType;
    private String title;
    private String taxNo;
    private String bankName;
    private String bankAccount;
    private String regAddress;
    private String phone;
    private Integer isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
