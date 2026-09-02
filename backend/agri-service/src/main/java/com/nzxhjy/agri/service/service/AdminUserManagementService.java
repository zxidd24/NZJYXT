package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.common.model.PageResult;
import com.nzxhjy.agri.common.security.PasswordUtils;
import com.nzxhjy.agri.service.entity.SysRole;
import com.nzxhjy.agri.service.entity.SysUser;
import com.nzxhjy.agri.service.entity.SysUserRole;
import com.nzxhjy.agri.service.mapper.SysRoleMapper;
import com.nzxhjy.agri.service.mapper.SysUserMapper;
import com.nzxhjy.agri.service.mapper.SysUserRoleMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserManagementService {
    private static final int ADMIN_USER_TYPE = 3;
    private static final Long BUILT_IN_ADMIN_ID = 1L;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordUtils passwordUtils;
    private final AuthTokenService tokenService;

    public PageResult<AdminUserView> page(int pageNum, int pageSize, String keyword, Integer status) {
        var query = Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserType, ADMIN_USER_TYPE)
                .like(keyword != null && !keyword.isBlank(), SysUser::getUsername, keyword)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId);
        IPage<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return new PageResult<>(page.getTotal(), pageNum, pageSize,
                page.getRecords().stream().map(this::toView).toList());
    }

    public AdminUserView get(Long id) {
        return toView(requireAdmin(id));
    }

    @Transactional
    public Long create(String username, String password, String realName, String phone,
                       String email, Integer status, List<Long> roleIds) {
        validateStatus(status);
        ensureUsernameUnique(username, null);
        validateRoleIds(roleIds);
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPassword(passwordUtils.encode(password));
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setUserType(ADMIN_USER_TYPE);
        user.setStatus(status == null ? 1 : status);
        user.setDeleted(0);
        userMapper.insert(user);
        replaceRoles(user.getId(), roleIds);
        return user.getId();
    }

    @Transactional
    public void update(Long id, String username, String password, String realName, String phone,
                       String email, Integer status, List<Long> roleIds) {
        validateStatus(status);
        SysUser user = requireAdmin(id);
        ensureUsernameUnique(username, id);
        validateRoleIds(roleIds);
        Integer targetStatus = status == null ? user.getStatus() : status;
        if (BUILT_IN_ADMIN_ID.equals(id)
                && (!"admin".equals(username.trim()) || !Integer.valueOf(1).equals(targetStatus)
                || !roleIds.contains(BUILT_IN_ADMIN_ID))) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(),
                    "内置管理员必须保留admin账号、启用状态和超级管理员角色");
        }
        user.setUsername(username.trim());
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordUtils.encode(password));
        }
        user.setRealName(realName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus(targetStatus);
        userMapper.updateById(user);
        replaceRoles(id, roleIds);
        if (!Integer.valueOf(1).equals(user.getStatus()) || (password != null && !password.isBlank())) {
            tokenService.revoke(id);
        }
    }

    @Transactional
    public void delete(Long operatorId, Long id) {
        requireAdmin(id);
        if (BUILT_IN_ADMIN_ID.equals(id)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "内置管理员账户不可删除");
        }
        if (operatorId.equals(id)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "不能删除当前登录账户");
        }
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
        tokenService.revoke(id);
    }

    private AdminUserView toView(SysUser user) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, user.getId())).stream()
                .map(SysUserRole::getRoleId).toList();
        List<String> roleNames = roleIds.isEmpty() ? Collections.emptyList() : roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleName).toList();
        return new AdminUserView(user.getId(), user.getUsername(), user.getRealName(), user.getPhone(),
                user.getEmail(), user.getStatus(), roleIds, roleNames, user.getCreatedAt());
    }

    private SysUser requireAdmin(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null || !Integer.valueOf(ADMIN_USER_TYPE).equals(user.getUserType())) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "管理员账户不存在");
        }
        return user;
    }

    private void ensureUsernameUnique(String username, Long excludeId) {
        SysUser existing = userMapper.selectByUsernameIncludingDeleted(username.trim());
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "登录账号已存在");
        }
    }

    private void validateRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "至少分配一个角色");
        }
        List<Long> distinctIds = roleIds.stream().distinct().toList();
        if (roleMapper.selectBatchIds(distinctIds).size() != distinctIds.size()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "包含不存在的角色");
        }
    }

    private void validateStatus(Integer status) {
        if (status != null && status != 0 && status != 1) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "账户状态只能为启用或禁用");
        }
    }

    private void replaceRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        roleIds.stream().distinct().forEach(roleId -> userRoleMapper.insert(new SysUserRole(userId, roleId)));
    }

    @Data
    @AllArgsConstructor
    public static class AdminUserView {
        private Long id;
        private String username;
        private String realName;
        private String phone;
        private String email;
        private Integer status;
        private List<Long> roleIds;
        private List<String> roleNames;
        private java.time.LocalDateTime createdAt;
    }
}
