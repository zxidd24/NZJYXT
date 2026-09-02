package com.nzxhjy.agri.service.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nzxhjy.agri.common.enums.ErrorCodeEnum;
import com.nzxhjy.agri.common.exception.BusinessException;
import com.nzxhjy.agri.service.entity.SysPermission;
import com.nzxhjy.agri.service.entity.SysRole;
import com.nzxhjy.agri.service.entity.SysRolePermission;
import com.nzxhjy.agri.service.entity.SysUserRole;
import com.nzxhjy.agri.service.mapper.SysPermissionMapper;
import com.nzxhjy.agri.service.mapper.SysRoleMapper;
import com.nzxhjy.agri.service.mapper.SysRolePermissionMapper;
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
public class RoleManagementService {
    private static final Long SUPER_ADMIN_ROLE_ID = 1L;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<RoleView> list() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId)).stream()
                .map(this::toView).toList();
    }

    public List<SysPermission> permissions() {
        return permissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                .orderByAsc(SysPermission::getSort).orderByAsc(SysPermission::getId));
    }

    public RoleView get(Long id) {
        return toView(requireRole(id));
    }

    @Transactional
    public Long create(String roleName, String description) {
        ensureNameUnique(roleName, null);
        SysRole role = new SysRole();
        role.setRoleName(roleName.trim());
        role.setDescription(description);
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional
    public void update(Long id, String roleName, String description) {
        SysRole role = requireRole(id);
        ensureNameUnique(roleName, id);
        role.setRoleName(roleName.trim());
        role.setDescription(description);
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        requireRole(id);
        if (SUPER_ADMIN_ROLE_ID.equals(id)) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "超级管理员角色不可删除");
        }
        Long userCount = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, id));
        if (userCount > 0) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "角色仍有关联账户，无法删除");
        }
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Transactional
    public void setPermissions(Long roleId, List<Long> permissionIds) {
        requireRole(roleId);
        List<Long> distinctIds = permissionIds == null ? Collections.emptyList() : permissionIds.stream().distinct().toList();
        if (!distinctIds.isEmpty() && permissionMapper.selectBatchIds(distinctIds).size() != distinctIds.size()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAM.getCode(), "包含不存在的权限");
        }
        if (SUPER_ADMIN_ROLE_ID.equals(roleId)
                && permissionMapper.selectCount(null) != distinctIds.size()) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "超级管理员必须保留全部权限");
        }
        rolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, roleId));
        distinctIds.forEach(permissionId -> rolePermissionMapper.insert(new SysRolePermission(roleId, permissionId)));
    }

    private RoleView toView(SysRole role) {
        List<Long> permissionIds = rolePermissionMapper.selectList(Wrappers.<SysRolePermission>lambdaQuery()
                        .eq(SysRolePermission::getRoleId, role.getId())).stream()
                .map(SysRolePermission::getPermissionId).toList();
        return new RoleView(role.getId(), role.getRoleName(), role.getDescription(), permissionIds);
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.BUSINESS_ERROR.getCode(), "角色不存在");
        }
        return role;
    }

    private void ensureNameUnique(String roleName, Long excludeId) {
        var query = Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleName, roleName.trim());
        if (excludeId != null) {
            query.ne(SysRole::getId, excludeId);
        }
        if (roleMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCodeEnum.DUPLICATE_SUBMIT.getCode(), "角色名称已存在");
        }
    }

    @Data
    @AllArgsConstructor
    public static class RoleView {
        private Long id;
        private String roleName;
        private String description;
        private List<Long> permissionIds;
    }
}
