package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("order_attachment")
public class OrderAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer type;
    private String fileUrl;
    private String fileName;
    private Long uploadedBy;
    private LocalDateTime createdAt;
}
