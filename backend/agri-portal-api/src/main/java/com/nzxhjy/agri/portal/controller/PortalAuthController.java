package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.PortalAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalAuthController {
    private final PortalAuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Long>> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request.getPhone(), request.getPassword(),
                request.getUserType(), request.getCompanyName());
        return Result.success(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public Result<PortalAuthService.LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request.getPhone(), request.getPassword()));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(UserContext.getUserId(), request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContext.getUserId());
        return Result.success();
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度必须为6到64位")
        private String password;
        @NotBlank(message = "确认密码不能为空")
        private String confirmPassword;
        @NotNull(message = "用户类型不能为空")
        private Integer userType;
        @Size(max = 100, message = "企业名称不能超过100个字符")
        private String companyName;

        @AssertTrue(message = "两次输入的密码不一致")
        public boolean isPasswordConfirmed() {
            return password != null && password.equals(confirmPassword);
        }
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;
        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 64, message = "新密码长度必须为6到64位")
        private String newPassword;
    }
}
