package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.PortalUserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/admin/portal-user")
@RequirePermission("admin:portal-user")
@RequiredArgsConstructor
@Validated
public class PortalUserController {
    private final PortalUserManagementService portalUserService;

    @GetMapping("/page")
    public Result<PageResult<PortalUserManagementService.PortalUserListItem>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) Integer userType,
            @RequestParam(required = false) String creditGrade) {
        return Result.success(portalUserService.page(pageNum, pageSize, userType, creditGrade));
    }

    @GetMapping("/{id}")
    public Result<PortalUserManagementService.PortalUserDetail> detail(@PathVariable Long id) {
        return Result.success(portalUserService.detail(id));
    }

    @PutMapping("/{id}/credit")
    public Result<Void> updateCredit(@PathVariable Long id, @Valid @RequestBody CreditRequest request) {
        portalUserService.updateCredit(UserContext.getUserId(), id, request.getCreditGrade(), request.getCreditLimit());
        return Result.success();
    }

    @Data
    public static class CreditRequest {
        @NotBlank(message = "信用等级不能为空")
        private String creditGrade;
        @DecimalMin(value = "0", message = "授信额度不能小于0")
        private BigDecimal creditLimit;
    }
}
