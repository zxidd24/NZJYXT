package com.nzxhjy.agri.portal.controller;

import com.nzxhjy.agri.common.model.Result;
import com.nzxhjy.agri.common.security.UserContext;
import com.nzxhjy.agri.service.service.PortalProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalProfileController {
    private final PortalProfileService profileService;

    @GetMapping("/profile")
    public Result<PortalProfileService.ProfileView> profile() {
        return Result.success(profileService.profile(UserContext.getUserId()));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@Valid @RequestBody ProfileRequest request) {
        profileService.updateProfile(UserContext.getUserId(), request.getContactName(), request.getEmail());
        return Result.success();
    }

    @PostMapping("/auth/submit")
    public Result<Map<String, Long>> submitAuth(@Valid @RequestBody AuthSubmissionRequest request) {
        return Result.success(Map.of("recordId",
                profileService.submitAuth(UserContext.getUserId(), request.toSubmission())));
    }

    @GetMapping("/auth/status")
    public Result<PortalProfileService.AuthStatusView> authStatus() {
        return Result.success(profileService.authStatus(UserContext.getUserId()));
    }

    @Data
    public static class ProfileRequest {
        @Size(max = 50, message = "联系人不能超过50个字符")
        private String contactName;
        @Email(message = "邮箱格式不正确")
        @Size(max = 100, message = "邮箱不能超过100个字符")
        private String email;
    }

    @Data
    public static class AuthSubmissionRequest {
        @NotBlank(message = "真实姓名不能为空")
        @Size(max = 50)
        private String realName;
        @NotBlank(message = "身份证号不能为空")
        @Pattern(regexp = "^\\d{17}[0-9Xx]$", message = "身份证号格式不正确")
        private String idCard;
        @Size(max = 100)
        private String businessLicense;
        @Size(max = 255)
        private String businessLicenseImg;
        @NotBlank(message = "身份证正面图片不能为空")
        @Size(max = 255)
        private String idCardFront;
        @NotBlank(message = "身份证反面图片不能为空")
        @Size(max = 255)
        private String idCardBack;
        @NotBlank(message = "银行卡号不能为空")
        @Pattern(regexp = "^\\d{8,30}$", message = "银行卡号格式不正确")
        private String bankCard;
        @NotBlank(message = "开户行不能为空")
        @Size(max = 50)
        private String bankName;
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        private PortalProfileService.AuthSubmission toSubmission() {
            PortalProfileService.AuthSubmission submission = new PortalProfileService.AuthSubmission();
            submission.setRealName(realName);
            submission.setIdCard(idCard);
            submission.setBusinessLicense(businessLicense);
            submission.setBusinessLicenseImg(businessLicenseImg);
            submission.setIdCardFront(idCardFront);
            submission.setIdCardBack(idCardBack);
            submission.setBankCard(bankCard);
            submission.setBankName(bankName);
            submission.setPhone(phone);
            return submission;
        }
    }
}
