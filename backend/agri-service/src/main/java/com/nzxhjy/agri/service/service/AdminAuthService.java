package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.security.AuthConstants;
import com.nzxhjy.agri.common.security.PasswordUtils;
import com.nzxhjy.agri.service.entity.SysRole;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final int ADMIN_USER_TYPE = 3;
    private final SysUserMapper userMapper;
    private final PasswordUtils passwordUtils;
    private final AuthTokenService tokenService;
    private final AccessControlService accessControlService;

    public LoginResult login(String username, String password) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getUserType, ADMIN_USER_TYPE)
                .eq(SysUser::getStatus, 1));
        if (user == null || !passwordUtils.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED.getCode(), "用户名或密码错误");
        }
        String token = tokenService.issue(user.getId(), AuthConstants.ADMIN_CLIENT);
        return new LoginResult(token, currentUser(user));
    }

    public CurrentUser currentUser(Long userId) {
        return currentUser(requireAdmin(userId));
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = requireAdmin(userId);
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

    private SysUser requireAdmin(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getUserType() == null || user.getUserType() != ADMIN_USER_TYPE
                || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED.getCode(), "管理员账户不可用");
        }
        return user;
    }

    private CurrentUser currentUser(SysUser user) {
        List<SysRole> roles = accessControlService.roles(user.getId());
        return new CurrentUser(user.getId(), user.getUsername(), user.getRealName(),
                roles.stream().map(RoleSummary::from).toList(), accessControlService.permissionCodes(user.getId()),
                accessControlService.menuTree(user.getId()));
    }

    @Data
    @AllArgsConstructor
    public static class LoginResult {
        private String token;
        private CurrentUser user;
    }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        private Long id;
        private String username;
        private String realName;
        private List<RoleSummary> roles;
        private Set<String> permissions;
        private List<AccessControlService.PermissionTreeItem> menus;
    }

    @Data
    @AllArgsConstructor
    public static class RoleSummary {
        private Long id;
        private String roleName;

        private static RoleSummary from(SysRole role) {
            return new RoleSummary(role.getId(), role.getRoleName());
        }
    }
}
