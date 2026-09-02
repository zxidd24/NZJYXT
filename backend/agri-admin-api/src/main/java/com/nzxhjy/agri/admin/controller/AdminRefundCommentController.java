package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.FinanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequirePermission("admin:order")
@RequiredArgsConstructor
public class AdminRefundCommentController {
    private final FinanceService financeService;
    @GetMapping("/refund/page") public Result<PageResult<FinanceService.RefundView>> refundPage(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) Integer status) { return Result.success(financeService.refundPage(pageNum, Math.min(pageSize, 100), status)); }
    @PostMapping("/refund/audit") public Result<Void> auditRefund(@Valid @RequestBody AuditRequest request) { financeService.auditRefund(UserContext.getUserId(), request.id, request.approved, request.remark); return Result.success(); }
    @GetMapping("/comment/page") public Result<PageResult<FinanceService.CommentView>> comments(@RequestParam(required = false) Long productId, @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "10") int pageSize) { return Result.success(financeService.commentPage(productId, pageNum, Math.min(pageSize, 100))); }
    @Data public static class AuditRequest { @NotNull Long id; @NotNull Boolean approved; @Size(max = 255) String remark; }
}
