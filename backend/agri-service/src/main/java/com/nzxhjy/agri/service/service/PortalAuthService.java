package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AuthConstants;
import com.nzxhjy.agri.common.security.PasswordUtils;
import com.nzxhjy.agri.service.entity.PortalUserInfo;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.PortalUserInfoMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PortalAuthService {
    private final SysUserMapper userMapper;
    private final PortalUserInfoMapper portalUserInfoMapper;
    private final PasswordUtils passwordUtils;
    private final AuthTokenService tokenService;

    @Transactional
    public Long register(String phone, String password, Integer userType, String companyName) {
        if (userType == null || (userType != 1 && userType != 2)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "用户类型只能为自然人或法人");
        }
        if (userType == 2 && (companyName == null || companyName.isBlank())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "法人用户必须填写企业名称");
        }
        if (userMapper.selectByUsernameIncludingDeleted(phone) != null) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "该手机号已注册");
        }
        SysUser user = new SysUser();
        user.setUsername(phone);
        user.setPhone(phone);
        user.setPassword(passwordUtils.encode(password));
        user.setUserType(userType);
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);

        PortalUserInfo info = new PortalUserInfo();
        info.setUserId(user.getId());
        info.setCompanyName(userType == 2 ? companyName.trim() : null);
        info.setAuthStatus(0);
        portalUserInfoMapper.insert(info);
        return user.getId();
    }

    public LoginResult login(String phone, String password) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, phone)
                .in(SysUser::getUserType, 1, 2)
                .eq(SysUser::getStatus, 1));
        if (user == null || !passwordUtils.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED.getCode(), "手机号或密码错误");
        }
        return new LoginResult(tokenService.issue(user.getId(), AuthConstants.PORTAL_CLIENT),
                user.getId(), user.getPhone(), user.getUserType());
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = requirePortalUser(userId);
        if (!passwordUtils.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "原密码错误");
        }
        user.setPassword(passwordUtils.encode(newPassword));
        userMapper.updateById(user);
        tokenService.revoke(userId);
    }

    public void logout(Long userId) {
        tokenService.revoke(userId);
    }

    public SysUser requirePortalUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getUserType() == null || (user.getUserType() != 1 && user.getUserType() != 2)
                || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED.getCode(), "门户账户不可用");
        }
        return user;
    }

    @Data
    @AllArgsConstructor
    public static class LoginResult {
        private String token;
        private Long userId;
        private String phone;
        private Integer userType;
    }
}
