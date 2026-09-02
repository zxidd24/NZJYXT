package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.AuditTaskService;
import com.nzxhjy.agri.service.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/admin/task")
@RequirePermission("admin:task")
@RequiredArgsConstructor
@Validated
public class AuditTaskController {
    private final AuditTaskService taskService;
    private final OrderService orderService;

    @GetMapping("/pending")
    public Result<PageResult<AuditTaskService.TaskView>> pending(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.success(taskService.pending(UserContext.getUserId(), pageNum, pageSize));
    }

    @GetMapping("/done")
    public Result<PageResult<AuditTaskService.TaskView>> done(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.success(taskService.done(UserContext.getUserId(), pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public Result<AuditTaskService.TaskDetail> detail(@PathVariable Long id) {
        return Result.success(taskService.detail(UserContext.getUserId(), id));
    }

    @PostMapping("/audit-auth")
    public Result<Void> auditAuth(@Valid @RequestBody AuthAuditRequest request) {
        taskService.auditAuth(UserContext.getUserId(), request.getRecordId(), request.getApproved(), request.getRemark());
        return Result.success();
    }

    @PostMapping("/audit-product")
    public Result<Void> auditProduct(@Valid @RequestBody ProductAuditRequest request) {
        taskService.auditProduct(UserContext.getUserId(), request.getRecordId(), request.getApproved(), request.getRemark());
        return Result.success();
    }

    @PostMapping("/audit-order")
    public Result<Void> auditOrder(@Valid @RequestBody ProductAuditRequest request) {
        orderService.auditOrder(UserContext.getUserId(), request.getRecordId(), request.getApproved(), request.getRemark());
        return Result.success();
    }

    @Data
    public static class AuthAuditRequest {
        @NotNull(message = "审核任务ID不能为空")
        private Long recordId;
        @NotNull(message = "审核结果不能为空")
        private Boolean approved;
        @Size(max = 255, message = "审核备注不能超过255个字符")
        private String remark;
    }

    @Data
    public static class ProductAuditRequest {
        @NotNull(message = "审核任务ID不能为空")
        private Long recordId;
        @NotNull(message = "审核结果不能为空")
        private Boolean approved;
        @Size(max = 255, message = "审核备注不能超过255个字符")
        private String remark;
    }
}
