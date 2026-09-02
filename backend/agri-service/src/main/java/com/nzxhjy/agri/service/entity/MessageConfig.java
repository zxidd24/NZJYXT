package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("message_config")
public class MessageConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String title;
    private String contentTemplate;
    private Integer enabled;
}
