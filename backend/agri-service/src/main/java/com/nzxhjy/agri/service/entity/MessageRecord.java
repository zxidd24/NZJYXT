package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_record")
public class MessageRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String configCode;
    private String title;
    private String content;
    private Integer bizType;
    private Long bizId;
    private Integer isRead;
    private LocalDateTime readTime;
    private LocalDateTime createdAt;
}
