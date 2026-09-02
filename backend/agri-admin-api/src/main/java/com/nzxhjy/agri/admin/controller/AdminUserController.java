package com.nzxhjy.agri.admin.controller;

import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.RequirePermission;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.AdminUserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Email;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
@RequirePermission("admin:system")
@RequiredArgsConstructor
@Validated
public class AdminUserController {
    private final AdminUserManagementService userService;

    @GetMapping
    public Result<PageResult<AdminUserManagementService.AdminUserView>> page(
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.page(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<AdminUserManagementService.AdminUserView> get(@PathVariable Long id) {
        return Result.success(userService.get(id));
    }

    @PostMapping
    public Result<Map<String, Long>> create(@Valid @RequestBody CreateAdminUserRequest request) {
        Long id = userService.create(request.getUsername(), request.getPassword(), request.getRealName(),
                request.getPhone(), request.getEmail(), request.getStatus(), request.getRoleIds());
        return Result.success(Map.of("id", id));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateAdminUserRequest request) {
        userService.update(id, request.getUsername(), request.getPassword(), request.getRealName(),
                request.getPhone(), request.getEmail(), request.getStatus(), request.getRoleIds());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(UserContext.getUserId(), id);
        return Result.success();
    }

    @Data
    public static class CreateAdminUserRequest {
        @NotBlank(message = "登录账号不能为空")
        @Size(max = 50)
        private String username;
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须为6到64位")
        private String password;
        @Size(max = 50)
        private String realName;
        @Size(max = 20)
        private String phone;
        @Email(message = "邮箱格式不正确")
        @Size(max = 100)
        private String email;
        private Integer status;
        @NotEmpty(message = "至少分配一个角色")
        private List<Long> roleIds;
    }

    @Data
    public static class UpdateAdminUserRequest {
        @NotBlank(message = "登录账号不能为空")
        @Size(max = 50)
        private String username;
        @Size(min = 6, max = 64, message = "新密码长度必须为6到64位")
        private String password;
        @Size(max = 50)
        private String realName;
        @Size(max = 20)
        private String phone;
        @Email(message = "邮箱格式不正确")
        @Size(max = 100)
        private String email;
        private Integer status;
        @NotEmpty(message = "至少分配一个角色")
        private List<Long> roleIds;
    }
}
