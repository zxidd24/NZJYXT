package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.enums.StatusEnums;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.service.entity.AuditNode;
import com.nzxhjy.agri.service.entity.AuditRecord;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.mapper.AuditNodeMapper;
import com.nzxhjy.agri.service.mapper.AuditRecordMapper;
import com.nzxhjy.agri.service.mapper.PortalUserInfoMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditTaskService {
    private final AuditRecordMapper auditRecordMapper;
    private final AuditNodeMapper auditNodeMapper;
    private final PortalUserInfoMapper portalUserInfoMapper;
    private final MessageService messageService;
    private final AccessControlService accessControlService;
    private final PortalUserManagementService portalUserManagementService;
    private final ProductService productService;
    private final OrderService orderService;

    public PageResult<TaskView> pending(Long userId, int pageNum, int pageSize) {
        var query = Wrappers.<AuditRecord>lambdaQuery()
                .eq(AuditRecord::getStatus, StatusEnums.AuditStatus.PENDING.value)
                .orderByAsc(AuditRecord::getApplyTime);
        if (!accessControlService.isSuperAdmin(userId)) {
            List<Long> roleIds = accessControlService.roleIds(userId);
            if (roleIds.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
            List<Long> nodeIds = auditNodeMapper.selectList(Wrappers.<AuditNode>lambdaQuery()
                            .in(AuditNode::getRoleId, roleIds)).stream()
                    .map(AuditNode::getId).toList();
            if (nodeIds.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
            query.in(AuditRecord::getFlowNodeId, nodeIds);
        }
        IPage<AuditRecord> page = auditRecordMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return toPageResult(page, pageNum, pageSize);
    }

    public PageResult<TaskView> done(Long userId, int pageNum, int pageSize) {
        IPage<AuditRecord> page = auditRecordMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AuditRecord>lambdaQuery()
                        .eq(AuditRecord::getAuditorId, userId)
                        .in(AuditRecord::getStatus, StatusEnums.AuditStatus.APPROVED.value,
                                StatusEnums.AuditStatus.REJECTED.value)
                        .orderByDesc(AuditRecord::getAuditTime));
        return toPageResult(page, pageNum, pageSize);
    }

    public TaskDetail detail(Long userId, Long recordId) {
        AuditRecord record = requireRecord(recordId);
        if (record.getStatus() == StatusEnums.AuditStatus.PENDING.value && !canAudit(userId, record)
                || record.getStatus() != StatusEnums.AuditStatus.PENDING.value
                && !userId.equals(record.getAuditorId()) && !accessControlService.isSuperAdmin(userId)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage());
        }
        Object bizDetail = record.getBizType() == StatusEnums.AuditBizType.AUTH.value
                ? portalUserManagementService.detail(record.getBizId())
                : (record.getBizType() == StatusEnums.AuditBizType.PRODUCT_SHELF.value
                || record.getBizType() == StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value)
                ? productService.detail(record.getBizId())
                : record.getBizType() == StatusEnums.AuditBizType.ORDER.value
                ? orderService.detail(record.getBizId(), record.getApplicantId()) : null;
        return new TaskDetail(toView(record), bizDetail);
    }

    @Transactional
    public void auditAuth(Long auditorId, Long recordId, boolean approved, String remark) {
        AuditRecord record = requireRecord(recordId);
        if (record.getBizType() != StatusEnums.AuditBizType.AUTH.value) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "该任务不是实名认证审核");
        }
        if (!canAudit(auditorId, record)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage());
        }
        if (!approved && (remark == null || remark.isBlank())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "驳回时必须填写原因");
        }
        int auditStatus = approved ? StatusEnums.AuditStatus.APPROVED.value : StatusEnums.AuditStatus.REJECTED.value;
        LocalDateTime now = LocalDateTime.now();
        int updated = auditRecordMapper.update(null, Wrappers.<AuditRecord>lambdaUpdate()
                .eq(AuditRecord::getId, recordId)
                .eq(AuditRecord::getStatus, StatusEnums.AuditStatus.PENDING.value)
                .set(AuditRecord::getStatus, auditStatus)
                .set(AuditRecord::getAuditorId, auditorId)
                .set(AuditRecord::getAuditTime, now)
                .set(AuditRecord::getRemark, remark));
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该任务已处理");
        }

        PortalUserInfo info = portalUserInfoMapper.selectById(record.getBizId());
        if (info == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "实名认证资料不存在");
        }
        info.setAuthStatus(approved ? StatusEnums.AuthStatus.VERIFIED.value : StatusEnums.AuthStatus.REJECTED.value);
        info.setAuthAuditTime(now);
        info.setAuthAuditRemark(remark);
        portalUserInfoMapper.updateById(info);

        messageService.send(record.getApplicantId(), "AUTH_RESULT", StatusEnums.AuditBizType.AUTH.value,
                record.getBizId(), java.util.Map.of("结果", approved ? "通过" : "驳回", "备注", remark == null ? "" : remark));
    }

    @Transactional
    public void auditProduct(Long auditorId, Long recordId, boolean approved, String remark) {
        AuditRecord record = requireRecord(recordId);
        if (record.getBizType() != StatusEnums.AuditBizType.PRODUCT_SHELF.value
                && record.getBizType() != StatusEnums.AuditBizType.PRODUCT_PRICE_STOCK.value) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "该任务不是商品审核");
        }
        if (!canAudit(auditorId, record)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage());
        }
        if (!approved && (remark == null || remark.isBlank())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "驳回时必须填写原因");
        }
        int auditStatus = approved ? StatusEnums.AuditStatus.APPROVED.value : StatusEnums.AuditStatus.REJECTED.value;
        LocalDateTime now = LocalDateTime.now();
        int updated = auditRecordMapper.update(null, Wrappers.<AuditRecord>lambdaUpdate()
                .eq(AuditRecord::getId, recordId)
                .eq(AuditRecord::getStatus, StatusEnums.AuditStatus.PENDING.value)
                .set(AuditRecord::getStatus, auditStatus)
                .set(AuditRecord::getAuditorId, auditorId)
                .set(AuditRecord::getAuditTime, now)
                .set(AuditRecord::getRemark, remark));
        if (updated == 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该任务已处理");
        }
        var product = productService.requireProduct(record.getBizId());
        product.setStatus(approved ? StatusEnums.ProductStatus.ON_SALE.value : StatusEnums.ProductStatus.REJECTED.value);
        productService.updateStatus(product);

        messageService.send(record.getApplicantId(), "PRODUCT_AUDIT_RESULT", record.getBizType(), product.getId(),
                java.util.Map.of("商品名", product.getName(), "结果", approved ? "通过" : "驳回", "备注", remark == null ? "" : remark));
    }

    private boolean canAudit(Long userId, AuditRecord record) {
        if (record.getStatus() != StatusEnums.AuditStatus.PENDING.value) {
            return false;
        }
        if (accessControlService.isSuperAdmin(userId)) {
            return true;
        }
        AuditNode node = auditNodeMapper.selectById(record.getFlowNodeId());
        return node != null && accessControlService.roleIds(userId).contains(node.getRoleId());
    }

    private AuditRecord requireRecord(Long id) {
        AuditRecord record = auditRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "审核任务不存在");
        }
        return record;
    }

    private PageResult<TaskView> toPageResult(IPage<AuditRecord> page, int pageNum, int pageSize) {
        return new PageResult<>(page.getTotal(), pageNum, pageSize,
                page.getRecords().stream().map(this::toView).toList());
    }

    private TaskView toView(AuditRecord record) {
        return new TaskView(record.getId(), record.getBizType(), bizTypeName(record.getBizType()),
                record.getBizId(), record.getBizNo(), record.getBizSummary(), record.getNodeName(),
                record.getStatus(), record.getApplicantId(), record.getApplyTime(), record.getAuditorId(),
                record.getAuditTime(), record.getRemark());
    }

    private String bizTypeName(Integer bizType) {
        return switch (bizType) {
            case 1 -> "实名认证";
            case 2 -> "商品上架";
            case 3 -> "商品量价修改";
            case 4 -> "订单审核";
            case 5 -> "退款审核";
            case 6 -> "贷款审核";
            default -> "未知业务";
        };
    }

    @Data
    @AllArgsConstructor
    public static class TaskView {
        private Long id;
        private Integer bizType;
        private String bizTypeName;
        private Long bizId;
        private String bizNo;
        private String bizSummary;
        private String nodeName;
        private Integer status;
        private Long applicantId;
        private LocalDateTime applyTime;
        private Long auditorId;
        private LocalDateTime auditTime;
        private String remark;
    }

    @Data
    @AllArgsConstructor
    public static class TaskDetail {
        private TaskView task;
        private Object bizDetail;
    }
}
