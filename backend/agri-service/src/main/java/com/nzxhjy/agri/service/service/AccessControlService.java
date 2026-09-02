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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessControlService {
    private static final Long SUPER_ADMIN_ROLE_ID = 1L;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    public List<Long> roleIds(Long userId) {
        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, userId)).stream()
                .map(SysUserRole::getRoleId).distinct().toList();
    }

    public boolean isSuperAdmin(Long userId) {
        return roleIds(userId).contains(SUPER_ADMIN_ROLE_ID);
    }

    public List<SysRole> roles(Long userId) {
        List<Long> roleIds = roleIds(userId);
        return roleIds.isEmpty() ? Collections.emptyList() : roleMapper.selectBatchIds(roleIds);
    }

    public List<SysPermission> permissions(Long userId) {
        List<Long> roleIds = roleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        Wrappers.<SysRolePermission>lambdaQuery().in(SysRolePermission::getRoleId, roleIds)).stream()
                .map(SysRolePermission::getPermissionId).distinct().toList();
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysPermission> permissions = permissionMapper.selectBatchIds(permissionIds);
        permissions.sort(Comparator.comparing(SysPermission::getSort).thenComparing(SysPermission::getId));
        return permissions;
    }

    public boolean hasPermission(Long userId, String permission) {
        return permissions(userId).stream().anyMatch(item -> permission.equals(item.getPerms()));
    }

    public void requirePermission(Long userId, String permission) {
        if (!hasPermission(userId, permission)) {
            throw new BusinessException(ErrorCodeEnum.FORBIDDEN.getCode(), ErrorCodeEnum.FORBIDDEN.getMessage());
        }
    }

    public Set<String> permissionCodes(Long userId) {
        return permissions(userId).stream().map(SysPermission::getPerms)
                .filter(value -> value != null && !value.isBlank()).collect(Collectors.toSet());
    }

    public List<PermissionTreeItem> menuTree(Long userId) {
        List<SysPermission> menus = permissions(userId).stream()
                .filter(item -> Integer.valueOf(1).equals(item.getType())).toList();
        Map<Long, PermissionTreeItem> itemMap = new LinkedHashMap<>();
        menus.forEach(permission -> itemMap.put(permission.getId(), PermissionTreeItem.from(permission)));
        List<PermissionTreeItem> roots = new ArrayList<>();
        itemMap.values().forEach(item -> {
            PermissionTreeItem parent = itemMap.get(item.getParentId());
            if (parent == null) {
                roots.add(item);
            } else {
                parent.getChildren().add(item);
            }
        });
        return roots;
    }

    @Data
    @AllArgsConstructor
    public static class PermissionTreeItem {
        private Long id;
        private Long parentId;
        private String name;
        private String perms;
        private String url;
        private Integer sort;
        private List<PermissionTreeItem> children;

        private static PermissionTreeItem from(SysPermission permission) {
            return new PermissionTreeItem(permission.getId(), permission.getParentId(), permission.getName(),
                    permission.getPerms(), permission.getUrl(), permission.getSort(), new ArrayList<>());
        }
    }
}
