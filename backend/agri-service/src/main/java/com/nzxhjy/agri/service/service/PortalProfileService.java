package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AesUtils;
import com.nzxhjy.agri.common.security.MaskUtils;
import com.nzxhjy.agri.service.entity.AuditFlow;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.AuditRecord;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.AuditFlowMapper;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.AuditRecordMapper;
import com.nzxhjy.agri.service.mapper.PortalUserInfoMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PortalProfileService {
    private final PortalAuthService portalAuthService;
    private final SysUserMapper userMapper;
    private final PortalUserInfoMapper infoMapper;
    private final AuditFlowMapper auditFlowMapper;
    private final AuditNodeMapper auditNodeMapper;
    private final AuditRecordMapper auditRecordMapper;
    private final MessageService messageService;
    private final AesUtils aesUtils;

    public ProfileView profile(Long userId) {
        SysUser user = portalAuthService.requirePortalUser(userId);
        PortalUserInfo info = requireInfo(userId);
        return new ProfileView(user.getId(), user.getUserType(), user.getPhone(), user.getEmail(), user.getRealName(),
                info.getCompanyName(), info.getContactName(), MaskUtils.bankCard(decrypt(info.getBankCard())),
                info.getBankName(), info.getCreditGrade(), defaultAmount(info.getCreditLimit()),
                defaultStatus(info.getAuthStatus()), info.getAuthAuditRemark());
    }

    @Transactional
    public void updateProfile(Long userId, String contactName, String email) {
        SysUser user = portalAuthService.requirePortalUser(userId);
        user.setEmail(email);
        userMapper.updateById(user);
        PortalUserInfo info = requireInfo(userId);
        info.setContactName(contactName);
        infoMapper.updateById(info);
    }

    public AuthStatusView authStatus(Long userId) {
        PortalUserInfo info = requireInfo(userId);
        return new AuthStatusView(defaultStatus(info.getAuthStatus()), info.getAuthSubmitTime(),
                info.getAuthAuditTime(), info.getAuthAuditRemark());
    }

    @Transactional
    public Long submitAuth(Long userId, AuthSubmission submission) {
        SysUser user = portalAuthService.requirePortalUser(userId);
        if (!user.getPhone().equals(submission.getPhone())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "认证手机号必须与注册手机号一致");
        }
        if (user.getUserType() == 2 && (isBlank(submission.getBusinessLicense())
                || isBlank(submission.getBusinessLicenseImg()))) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "法人用户必须提交营业执照号和营业执照图片");
        }
        PortalUserInfo info = requireInfo(userId);
        int currentStatus = defaultStatus(info.getAuthStatus());
        if (currentStatus == StatusEnums.AuthStatus.REVIEWING.value) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "实名认证正在审核中");
        }
        if (currentStatus == StatusEnums.AuthStatus.VERIFIED.value) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "实名认证已通过");
        }

        user.setRealName(submission.getRealName());
        userMapper.updateById(user);
        info.setContactName(isBlank(info.getContactName()) ? submission.getRealName() : info.getContactName());
        info.setIdCard(aesUtils.encrypt(submission.getIdCard()));
        info.setBusinessLicense(submission.getBusinessLicense());
        info.setBusinessLicenseImg(submission.getBusinessLicenseImg());
        info.setIdCardFront(submission.getIdCardFront());
        info.setIdCardBack(submission.getIdCardBack());
        info.setBankCard(aesUtils.encrypt(submission.getBankCard()));
        info.setBankName(submission.getBankName());
        info.setAuthStatus(StatusEnums.AuthStatus.REVIEWING.value);
        info.setAuthSubmitTime(LocalDateTime.now());
        info.setAuthAuditTime(null);
        info.setAuthAuditRemark(null);
        infoMapper.updateById(info);

        AuditFlow flow = auditFlowMapper.selectOne(Wrappers.<AuditFlow>lambdaQuery()
                .eq(AuditFlow::getBizType, StatusEnums.AuditBizType.AUTH.value)
                .eq(AuditFlow::getEnabled, 1));
        if (flow == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "实名认证审核流程未启用");
        }
        AuditNode node = auditNodeMapper.selectOne(Wrappers.<AuditNode>lambdaQuery()
                .eq(AuditNode::getFlowId, flow.getId()).orderByAsc(AuditNode::getNodeOrder).last("LIMIT 1"));
        if (node == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "实名认证审核节点未配置");
        }
        AuditRecord record = new AuditRecord();
        record.setBizType(StatusEnums.AuditBizType.AUTH.value);
        record.setBizId(userId);
        record.setBizNo(user.getPhone());
        record.setBizSummary("实名认证：" + submission.getRealName());
        record.setFlowNodeId(node.getId());
        record.setNodeName(node.getNodeName());
        record.setStatus(StatusEnums.AuditStatus.PENDING.value);
        record.setApplicantId(userId);
        record.setApplyTime(LocalDateTime.now());
        auditRecordMapper.insert(record);
        messageService.sendTodo(node.getId(), StatusEnums.AuditBizType.AUTH.value, info.getUserId());
        return record.getId();
    }

    private PortalUserInfo requireInfo(Long userId) {
        PortalUserInfo info = infoMapper.selectById(userId);
        if (info == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "门户用户档案不存在");
        }
        return info;
    }

    private String decrypt(String value) {
        return isBlank(value) ? value : aesUtils.decrypt(value);
    }

    private int defaultStatus(Integer status) {
        return status == null ? StatusEnums.AuthStatus.UNAUTHENTICATED.value : status;
    }

    private BigDecimal defaultAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Data
    @AllArgsConstructor
    public static class ProfileView {
        private Long userId;
        private Integer userType;
        private String phone;
        private String email;
        private String realName;
        private String companyName;
        private String contactName;
        private String bankCard;
        private String bankName;
        private String creditGrade;
        private BigDecimal creditLimit;
        private Integer authStatus;
        private String authRemark;
    }

    @Data
    @AllArgsConstructor
    public static class AuthStatusView {
        private Integer status;
        private LocalDateTime submitTime;
        private LocalDateTime auditTime;
        private String remark;
    }

    @Data
    public static class AuthSubmission {
        private String realName;
        private String idCard;
        private String businessLicense;
        private String businessLicenseImg;
        private String idCardFront;
        private String idCardBack;
        private String bankCard;
        private String bankName;
        private String phone;
    }
}
