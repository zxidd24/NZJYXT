package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.common.security.MaskUtils;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.SysOperLog;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.PortalOrderStatsMapper;
import com.nzxhjy.agri.service.mapper.PortalUserInfoMapper;
import com.nzxhjy.agri.service.mapper.SysOperLogMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortalUserManagementService {
    private final SysUserMapper userMapper;
    private final PortalUserInfoMapper infoMapper;
    private final PortalOrderStatsMapper orderStatsMapper;
    private final SysOperLogMapper operLogMapper;
    private final AesUtils aesUtils;

    public PageResult<PortalUserListItem> page(int pageNum, int pageSize, Integer userType, String creditGrade) {
        List<Long> gradeUserIds = null;
        if (creditGrade != null && !creditGrade.isBlank()) {
            gradeUserIds = infoMapper.selectList(Wrappers.<PortalUserInfo>lambdaQuery()
                            .eq(PortalUserInfo::getCreditGrade, creditGrade.trim().toUpperCase())).stream()
                    .map(PortalUserInfo::getUserId).toList();
            if (gradeUserIds.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
        }
        var query = Wrappers.<SysUser>lambdaQuery().in(SysUser::getUserType, 1, 2)
                .eq(userType != null, SysUser::getUserType, userType)
                .in(gradeUserIds != null, SysUser::getId, gradeUserIds)
                .orderByDesc(SysUser::getId);
        IPage<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), query);
        List<Long> userIds = page.getRecords().stream().map(SysUser::getId).toList();
        Map<Long, PortalUserInfo> infoMap = userIds.isEmpty() ? Collections.emptyMap()
                : infoMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(PortalUserInfo::getUserId, Function.identity()));
        List<PortalUserListItem> list = page.getRecords().stream()
                .map(user -> toListItem(user, infoMap.get(user.getId()))).toList();
        return new PageResult<>(page.getTotal(), pageNum, pageSize, list);
    }

    public PortalUserDetail detail(Long userId) {
        SysUser user = requirePortalUser(userId);
        PortalUserInfo info = requireInfo(userId);
        return new PortalUserDetail(user.getId(), user.getUsername(), user.getRealName(), user.getUserType(),
                MaskUtils.phone(user.getPhone()), user.getEmail(), user.getStatus(), info.getCompanyName(),
                info.getContactName(), MaskUtils.idCard(decrypt(info.getIdCard())), info.getBusinessLicense(),
                info.getBusinessLicenseImg(), info.getIdCardFront(), info.getIdCardBack(),
                MaskUtils.bankCard(decrypt(info.getBankCard())), info.getBankName(), info.getCreditGrade(),
                defaultAmount(info.getCreditLimit()), defaultStatus(info.getAuthStatus()), info.getAuthSubmitTime(),
                info.getAuthAuditTime(), info.getAuthAuditRemark(), orderStatsMapper.countAll(userId),
                orderStatsMapper.countCompleted(userId), defaultAmount(orderStatsMapper.sumCompletedAmount(userId)));
    }

    @Transactional
    public void updateCredit(Long operatorId, Long userId, String grade, BigDecimal creditLimit) {
        requirePortalUser(userId);
        PortalUserInfo info = requireInfo(userId);
        String normalizedGrade = grade == null ? "" : grade.trim().toUpperCase();
        if (!List.of("A", "B", "C").contains(normalizedGrade)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "信用等级只能为A、B或C");
        }
        if (creditLimit == null || creditLimit.signum() < 0) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "授信额度不能小于0");
        }
        String beforeGrade = info.getCreditGrade();
        BigDecimal beforeLimit = defaultAmount(info.getCreditLimit());
        info.setCreditGrade(normalizedGrade);
        info.setCreditLimit(creditLimit);
        infoMapper.updateById(info);

        SysOperLog log = new SysOperLog();
        log.setOperatorId(operatorId);
        log.setModule("用户管理");
        log.setAction("用户评级定级");
        log.setTargetType("portal_user");
        log.setTargetId(String.valueOf(userId));
        log.setDetail(String.format("{\"beforeGrade\":\"%s\",\"beforeLimit\":%s,\"afterGrade\":\"%s\",\"afterLimit\":%s}",
                beforeGrade == null ? "" : beforeGrade, beforeLimit.toPlainString(), normalizedGrade, creditLimit.toPlainString()));
        operLogMapper.insert(log);
    }

    private PortalUserListItem toListItem(SysUser user, PortalUserInfo info) {
        return new PortalUserListItem(user.getId(), displayName(user, info), user.getUserType(),
                info == null ? null : info.getContactName(), user.getPhone(),
                info == null ? null : info.getCreditGrade(), info == null ? 0 : defaultStatus(info.getAuthStatus()),
                user.getStatus(), user.getCreatedAt());
    }

    private String displayName(SysUser user, PortalUserInfo info) {
        if (info != null && user.getUserType() == 2 && info.getCompanyName() != null) {
            return info.getCompanyName();
        }
        return user.getRealName() == null ? user.getUsername() : user.getRealName();
    }

    private SysUser requirePortalUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getUserType() == null || (user.getUserType() != 1 && user.getUserType() != 2)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "门户用户不存在");
        }
        return user;
    }

    private PortalUserInfo requireInfo(Long userId) {
        PortalUserInfo info = infoMapper.selectById(userId);
        if (info == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "门户用户档案不存在");
        }
        return info;
    }

    private String decrypt(String value) {
        return value == null || value.isBlank() ? value : aesUtils.decrypt(value);
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private int defaultStatus(Integer status) {
        return status == null ? 0 : status;
    }

    @Data
    @AllArgsConstructor
    public static class PortalUserListItem {
        private Long id;
        private String userName;
        private Integer userType;
        private String contactName;
        private String phone;
        private String creditGrade;
        private Integer authStatus;
        private Integer status;
        private java.time.LocalDateTime createdAt;
    }

    @Data
    @AllArgsConstructor
    public static class PortalUserDetail {
        private Long id;
        private String username;
        private String realName;
        private Integer userType;
        private String phone;
        private String email;
        private Integer status;
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
        private java.time.LocalDateTime authSubmitTime;
        private java.time.LocalDateTime authAuditTime;
        private String authAuditRemark;
        private Long orderCount;
        private Long completedOrderCount;
        private BigDecimal completedOrderAmount;
    }
}
