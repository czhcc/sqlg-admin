package com.trs.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.security.PermissionRegistry;
import com.trs.user.entity.PermissionCatalog;
import com.trs.user.entity.Role;
import com.trs.user.mapper.RoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色管理服务,提供角色列表、详情、CRUD、权限配置、成员管理和连接授权。
 *
 * @author czh
 * @date 2026/0715
 */
@Service
public class RoleManagementService {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private final RoleMapper roleMapper;
    private final GraphConnectionMapper connectionMapper;
    private final PermissionRegistry permissionRegistry;

    public RoleManagementService(RoleMapper roleMapper, GraphConnectionMapper connectionMapper,
                                  PermissionRegistry permissionRegistry) {
        this.roleMapper = roleMapper;
        this.connectionMapper = connectionMapper;
        this.permissionRegistry = permissionRegistry;
    }

    // ==================== 列表 ====================

    public Map<String, Object> page(String keyword, Short status) {
        List<Role> roles = roleMapper.selectAll(keyword, status);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Role r : roles) {
            rows.add(toListDto(r));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("rows", rows);
        return result;
    }

    // ==================== 详情 ====================

    public Map<String, Object> getDetail(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        return toDetailDto(r);
    }

    // ==================== 基本信息 CRUD ====================

    public void updateBasic(Long id, String roleName, String description, Short status) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        r.setRoleName(roleName);
        r.setDescription(description);
        if (status != null) r.setStatus(status);
        roleMapper.update(r);
    }

    public void delete(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        if (Boolean.TRUE.equals(r.getIsBuiltin()))
            throw new IllegalArgumentException("内置角色不可删除");
        long userCount = roleMapper.countUsersByRoleKey(r.getRoleKey());
        if (userCount > 0)
            throw new IllegalArgumentException("该角色下还有 " + userCount + " 个用户,无法删除");
        roleMapper.deleteById(id);
    }

    // ==================== 权限配置 ====================

    public void updateMenuPermissions(Long id, List<String> menus) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        r.setMenuPermissions(toJson(menus));
        roleMapper.update(r);
    }

    public void updateOperationPermissions(Long id, List<String> operations) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        r.setOperationPermissions(toJson(operations));
        roleMapper.update(r);
    }

    public void updateGremlinPermission(Long id, String level) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        r.setGremlinPermission(level);
        roleMapper.update(r);
    }

    public void updateDangerousPermissions(Long id, List<String> dangerousOps) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + id);
        r.setDangerousPermissions(toJson(dangerousOps != null ? dangerousOps : List.of()));
        roleMapper.update(r);
    }

    // ==================== 可见连接 ====================

    public Map<String, Object> getConnectionAuth(Long roleId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);

        List<GraphConnection> allConns = connectionMapper.selectAll(null);
        List<Map<String, Object>> auths = roleMapper.selectConnectionAuth(roleId);
        Map<Long, String> authMap = new LinkedHashMap<>();
        for (Map<String, Object> a : auths) {
            authMap.put(((Number) a.get("connectionId")).longValue(), (String) a.get("accessLevel"));
        }

        String defaultLevel = r.getConnectionDefault();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (GraphConnection c : allConns) {
            String level = authMap.getOrDefault(c.getId(), defaultLevel);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("connectionId", c.getId());
            m.put("connectionName", c.getName());
            m.put("dbType", c.getDbType());
            m.put("status", c.getStatus());
            m.put("visible", !"NONE".equals(level));
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("defaultVisible", !"NONE".equals(defaultLevel));
        result.put("connections", rows);
        return result;
    }

    public void updateConnectionAuth(Long roleId, Long connectionId, boolean visible) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);
        String accessLevel = visible ? "READ" : "NONE";
        roleMapper.upsertConnectionAuth(roleId, connectionId, accessLevel);
    }

    /**
     * 计算给定角色集合下可见的连接ID集合。
     * 连接对某角色可见 = 显式授权存在且级别非 NONE,或无显式授权且角色默认级别非 NONE。
     * 只要任意一个角色可见,该连接即对用户可见。
     *
     * @param roleKeys 角色编码集合
     * @return 可见的连接ID集合; 角色为空时返回 null 表示不做过滤
     */
    public java.util.Set<Long> getVisibleConnectionIds(java.util.Set<String> roleKeys) {
        if (roleKeys == null || roleKeys.isEmpty()) return null;

        java.util.List<Role> roles = new java.util.ArrayList<>();
        for (String key : roleKeys) {
            Role r = roleMapper.selectByKey(key);
            if (r != null) roles.add(r);
        }
        if (roles.isEmpty()) return java.util.Set.of();

        java.util.List<Long> roleIds = roles.stream().map(Role::getId).collect(java.util.stream.Collectors.toList());
        java.util.List<Map<String, Object>> auths = roleMapper.selectConnectionAuthForRoles(roleIds);

        java.util.Map<Long, java.util.Map<Long, String>> authByRole = new java.util.HashMap<>();
        for (Map<String, Object> a : auths) {
            Long rid = ((Number) a.get("roleId")).longValue();
            Long connId = ((Number) a.get("connectionId")).longValue();
            String level = (String) a.get("accessLevel");
            authByRole.computeIfAbsent(rid, k -> new java.util.HashMap<>()).put(connId, level);
        }

        List<GraphConnection> allConns = connectionMapper.selectAll(null);
        java.util.Set<Long> result = new java.util.HashSet<>();
        for (GraphConnection c : allConns) {
            for (Role r : roles) {
                java.util.Map<Long, String> connMap = authByRole.get(r.getId());
                String level = connMap != null ? connMap.get(c.getId()) : null;
                boolean roleVisible = level != null ? !"NONE".equals(level) : !"NONE".equals(r.getConnectionDefault());
                if (roleVisible) { result.add(c.getId()); break; }
            }
        }
        return result;
    }

    // ==================== 用户成员 ====================

    public Map<String, Object> getMembers(Long roleId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);
        List<Long> userIds = roleMapper.selectUserIdsByRoleKey(r.getRoleKey());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleId", roleId);
        result.put("roleKey", r.getRoleKey());
        result.put("roleName", r.getRoleName());
        result.put("userIds", userIds);
        result.put("count", userIds.size());
        return result;
    }

    public void addMembers(Long roleId, List<Long> userIds) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);
        for (Long uid : userIds) {
            roleMapper.addRoleToUser(uid, r.getRoleKey());
        }
    }

    public void removeMember(Long roleId, Long userId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);
        roleMapper.removeRoleFromUser(userId, r.getRoleKey());
    }

    // ==================== 权限目录 ====================

    public Map<String, Object> getCatalog() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", permissionRegistry.getMenuTree());
        result.put("gremlinLevels", PermissionCatalog.gremlinLevels());
        result.put("connectionLevels", PermissionCatalog.connectionLevels());
        result.put("dangerousOps", PermissionCatalog.dangerousOps());
        return result;
    }

    // ==================== DTO ====================

    private Map<String, Object> toListDto(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("roleKey", r.getRoleKey());
        m.put("roleName", r.getRoleName());
        m.put("description", r.getDescription());
        m.put("status", r.getStatus());
        m.put("isBuiltin", r.getIsBuiltin());

        long userCount = roleMapper.countUsersByRoleKey(r.getRoleKey());
        m.put("userCount", userCount);

        List<GraphConnection> conns = connectionMapper.selectAll(null);
        long connCount = conns.stream().filter(c -> c.getStatus() != null && c.getStatus() == 1).count();
        m.put("connectionCount", connCount);

        List<String> opPerms = parseJsonList(r.getOperationPermissions());
        List<String> dangerousPerms = parseJsonList(r.getDangerousPermissions());
        m.put("permissionCount", opPerms.size() + dangerousPerms.size());

        m.put("updateTime", r.getUpdateTime());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    private Map<String, Object> toDetailDto(Role r) {
        Map<String, Object> m = toListDto(r);
        m.put("menuPermissions", parseJsonList(r.getMenuPermissions()));
        m.put("operationPermissions", parseJsonList(r.getOperationPermissions()));
        m.put("gremlinPermission", r.getGremlinPermission());
        m.put("dangerousPermissions", parseJsonList(r.getDangerousPermissions()));
        m.put("connectionDefault", r.getConnectionDefault());
        return m;
    }

    // ==================== helpers ====================

    private String toJson(List<String> list) {
        try {
            return MAPPER.writeValueAsString(list != null ? list : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, STR_LIST);
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", json, e);
            return List.of();
        }
    }
}
