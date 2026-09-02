package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.service.entity.SysPermission;
import com.nzxhjy.agri.service.service.RoleManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/role")
@RequirePermission("admin:system")
@RequiredArgsConstructor
public class RoleController {
    private final RoleManagementService roleService;

    @GetMapping
    public Result<List<RoleManagementService.RoleView>> list() {
        return Result.success(roleService.list());
    }

    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions() {
        return Result.success(roleService.permissions());
    }

    @GetMapping("/{id}")
    public Result<RoleManagementService.RoleView> get(@PathVariable Long id) {
        return Result.success(roleService.get(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody RoleRequest request) {
        return Result.success(Map.of("id", roleService.create(request.getRoleName(), request.getDescription())));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        roleService.update(id, request.getRoleName(), request.getDescription());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> setPermissions(@PathVariable Long id, @Valid @RequestBody PermissionRequest request) {
        roleService.setPermissions(id, request.getPermissionIds());
        return Result.success();
    }

    @Data
    public static class RoleRequest {
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称不能超过50个字符")
        private String roleName;
        @Size(max = 200, message = "角色描述不能超过200个字符")
        private String description;
    }

    @Data
    public static class PermissionRequest {
        @NotNull(message = "权限列表不能为空")
        private List<Long> permissionIds;
    }
}
