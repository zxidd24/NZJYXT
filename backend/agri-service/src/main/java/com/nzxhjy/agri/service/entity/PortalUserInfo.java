package com.nzxhjy.agri.service.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("portal_user_info")
public class PortalUserInfo {
    @TableId
    private Long userId;
    private String companyName;
    private String contactName;
    private String idCard;
    private String businessLicense;
    private String businessLicenseImg;
    private String idCardFront;
    private String idCardBack;
    private String bankCard;
    private String bankName;
    private String creditGrade;
    private BigDecimal creditLimit;
    private Integer authStatus;
    private LocalDateTime authSubmitTime;
    private LocalDateTime authAuditTime;
    private String authAuditRemark;
}
