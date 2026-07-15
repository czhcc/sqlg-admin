package com.trs.security;

import com.trs.user.entity.Role;
import com.trs.user.entity.User;
import com.trs.user.mapper.RoleMapper;
import com.trs.user.mapper.UserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 操作权限校验器,根据当前登录用户的角色合并后的 operationPermissions 判断是否有权执行指定操作。
 * SUPER_ADMIN 用户拥有通配权限。
 *
 * @author czh
 * @date 2026/0715
 */
@Component
public class PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};
    private static final String SUPER_ADMIN_KEY = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public PermissionChecker(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 校验当前用户是否拥有指定操作权限。无权限时抛出 SecurityException。
     *
     * @param code 操作权限编码,如 "vertex_data:clear"
     */
    public void require(String code) {
        if (!hasPermission(code)) {
            throw new SecurityException("无操作权限: " + code);
        }
    }

    /**
     * 判断当前用户是否拥有指定操作权限。
     *
     * @param code 操作权限编码
     * @return true 有权限
     */
    public boolean hasPermission(String code) {
        User user = currentUser();
        if (user == null) return false;

        if (isSuperAdmin(user)) return true;

        Set<String> allOps = collectOperationPermissions(user);
        return allOps.contains(code);
    }

    private boolean isSuperAdmin(User u) {
        if (u == null || u.getRoles() == null) return false;
        return Arrays.stream(u.getRoles().split(","))
                .map(String::trim)
                .anyMatch(SUPER_ADMIN_KEY::equals);
    }

    private Set<String> collectOperationPermissions(User u) {
        Set<String> ops = new HashSet<>();
        if (u.getRoles() == null || u.getRoles().isBlank()) return ops;

        for (String key : u.getRoles().split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) continue;
            Role r = roleMapper.selectByKey(trimmed);
            if (r == null || r.getStatus() == null || r.getStatus() != 1) continue;
            ops.addAll(parseJsonList(r.getOperationPermissions()));
        }
        return ops;
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        if (!(principal instanceof User pu)) return null;
        return userMapper.selectById(pu.getId());
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, STR_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }
}
