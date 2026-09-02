package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_main")
public class OrderMain {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal payAmount;
    private BigDecimal freight;
    private Integer orderStatus;
    private Integer payStatus;
    private LocalDateTime payTime;
    private String payMethod;
    private Long addressId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Long auditNodeId;
    private LocalDateTime cancelTime;
    private String buyerNote;
    private String sellerNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
