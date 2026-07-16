package com.trs.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.entity.LoginLog;
import com.trs.modules.log.mapper.LoginLogMapper;
import com.trs.user.entity.Role;
import com.trs.user.entity.User;
import com.trs.user.mapper.RoleMapper;
import com.trs.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统用户服务,提供登录校验、用户 CRUD、角色分配、权限计算等能力。
 *
 * @author czh
 * @date 2026/0714
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final GraphConnectionMapper connectionMapper;
    private final LoginLogMapper loginLogMapper;
    private final RoleMapper roleMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       GraphConnectionMapper connectionMapper, LoginLogMapper loginLogMapper,
                       RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.connectionMapper = connectionMapper;
        this.loginLogMapper = loginLogMapper;
        this.roleMapper = roleMapper;
    }

    // ==================== 登录相关 (原有) ====================

    public User findByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    public boolean checkPassword(User user, String rawPassword) {
        return user != null
                && user.getStatus() != null
                && user.getStatus() == 1
                && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public void changePassword(Long id, String rawPassword) {
        userMapper.updatePassword(id, passwordEncoder.encode(rawPassword));
    }

    public void updateLastLoginTime(Long id) {
        userMapper.updateLastLoginTime(id);
    }

    // ==================== 用户管理 CRUD ====================

    public Map<String, Object> page(int page, int size, String keyword, Short status) {
        if (size > 200) size = 200;
        int offset = (page - 1) * size;

        List<User> users = userMapper.selectPage(offset, size, keyword, status);
        long total = userMapper.selectCount(keyword, status);

        long connectionCount = connectionMapper.selectAll(null).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .count();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            rows.add(toListDto(u, connectionCount));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    public Map<String, Object> getDetail(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        return toDetailDto(u);
    }

    public Long create(String username, String password, String nickname, String email,
                       String phone, String roles, String remark, Short status) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("用户名不能为空");
        if (password == null || password.length() < 6) throw new IllegalArgumentException("密码不能为空且至少 6 位");
        if (userMapper.countByUsername(username, null) > 0)
            throw new IllegalArgumentException("用户名已存在: " + username);

        User u = new User();
        u.setUsername(username.trim());
        u.setPassword(passwordEncoder.encode(password));
        u.setNickname(nickname);
        u.setEmail(email);
        u.setPhone(phone);
        u.setRoles(roles);
        u.setRemark(remark);
        u.setStatus(status != null ? status : 1);
        userMapper.insert(u);
        return u.getId();
    }

    public void update(Long id, String nickname, String email, String phone, String remark, Short status) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        u.setNickname(nickname);
        u.setEmail(email);
        u.setPhone(phone);
        u.setRemark(remark);
        if (status != null) u.setStatus(status);
        userMapper.update(u);
    }

    public void updateStatus(Long id, Short status) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        if (status != 0 && status != 1) throw new IllegalArgumentException("状态值无效: " + status);
        userMapper.updateStatus(id, status);
    }

    public void resetPassword(Long id, String newPassword) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        if (newPassword == null || newPassword.length() < 6)
            throw new IllegalArgumentException("密码至少 6 位");
        userMapper.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    public void assignRoles(Long id, List<String> roleKeys) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);

        List<String> validKeys = new ArrayList<>();
        for (String key : roleKeys) {
            Role r = roleMapper.selectByKey(key);
            if (r == null) throw new IllegalArgumentException("未知角色: " + key);
            validKeys.add(r.getRoleKey());
        }
        String csv = validKeys.isEmpty() ? null : String.join(",", validKeys);
        userMapper.updateRoles(id, csv);
    }

    public void delete(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        if ("admin".equals(u.getUsername()))
            throw new IllegalArgumentException("不能删除初始管理员账号");
        userMapper.deleteById(id);
    }

    // ==================== 权限计算 ====================

    private static final String SUPER_ADMIN_KEY = "SUPER_ADMIN";

    private boolean isSuperAdmin(User u) {
        if (u == null || u.getRoles() == null) return false;
        return Arrays.asList(u.getRoles().split(",")).stream()
                .map(String::trim).anyMatch(SUPER_ADMIN_KEY::equals);
    }

    public Map<String, Object> getEffectivePermissions(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);

        if (isSuperAdmin(u)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("roles", List.of(Map.of("key", SUPER_ADMIN_KEY, "label", "超级管理员")));
            result.put("menus", List.of("*"));
            result.put("operations", List.of("*"));
            result.put("gremlin", "DANGEROUS");
            result.put("dangerousOps", List.of("*"));
            result.put("allowDangerousOps", true);
            return result;
        }

        List<Role> roles = loadUserRoles(u);

        Set<String> menus = new LinkedHashSet<>();
        Set<String> operations = new LinkedHashSet<>();
        Set<String> gremlinLevels = new LinkedHashSet<>();
        Set<String> dangerousPerms = new LinkedHashSet<>();

        for (Role r : roles) {
            menus.addAll(parseJsonList(r.getMenuPermissions()));
            operations.addAll(parseJsonList(r.getOperationPermissions()));
            gremlinLevels.add(r.getGremlinPermission());
            dangerousPerms.addAll(parseJsonList(r.getDangerousPermissions()));
        }

        operations.removeIf(op -> {
            String requiredQualification = com.trs.user.entity.PermissionCatalog.dangerousOpMap().get(op);
            return requiredQualification != null && !dangerousPerms.contains(requiredQualification);
        });

        String mergedGremlin;
        if (gremlinLevels.contains("DANGEROUS")) mergedGremlin = "DANGEROUS";
        else if (gremlinLevels.contains("WRITE")) mergedGremlin = "WRITE";
        else if (gremlinLevels.contains("READ_ONLY")) mergedGremlin = "READ_ONLY";
        else mergedGremlin = "NONE";

        List<Map<String, String>> roleBriefs = new ArrayList<>();
        for (Role r : roles) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", r.getRoleKey());
            m.put("label", r.getRoleName());
            roleBriefs.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", roleBriefs);
        result.put("menus", new ArrayList<>(menus));
        result.put("operations", new ArrayList<>(operations));
        result.put("gremlin", mergedGremlin);
        result.put("dangerousOps", new ArrayList<>(dangerousPerms));
        result.put("allowDangerousOps", !dangerousPerms.isEmpty());
        return result;
    }

    public Map<String, Object> getConnectionPermissions(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);

        List<GraphConnection> allConns = connectionMapper.selectAll(null).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .toList();

        if (isSuperAdmin(u)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("permissions", List.of(Map.of(
                    "role", SUPER_ADMIN_KEY, "roleLabel", "超级管理员",
                    "default", "ADMIN")));
            result.put("accessibleCount", allConns.size());
            result.put("mergedAccess", "ADMIN");
            return result;
        }

        List<Role> roles = loadUserRoles(u);

        List<Map<String, Object>> connPerms = new ArrayList<>();
        Set<Long> accessibleConnIds = new LinkedHashSet<>();
        String mergedAccess = "NONE";

        for (Role r : roles) {
            String defaultLevel = r.getConnectionDefault();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r.getRoleKey());
            m.put("roleLabel", r.getRoleName());
            m.put("default", defaultLevel);
            connPerms.add(m);

            if (!"NONE".equals(defaultLevel)) {
                accessibleConnIds.addAll(allConns.stream().map(GraphConnection::getId).toList());
                if ("ADMIN".equals(defaultLevel)) mergedAccess = "ADMIN";
                else if ("WRITE".equals(defaultLevel) && !"ADMIN".equals(mergedAccess)) mergedAccess = "WRITE";
                else if ("READ".equals(defaultLevel) && "NONE".equals(mergedAccess)) mergedAccess = "READ";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissions", connPerms);
        result.put("accessibleCount", accessibleConnIds.size());
        result.put("mergedAccess", mergedAccess);
        return result;
    }

    // ==================== 角色加载 ====================

    private List<Role> loadUserRoles(User u) {
        if (u.getRoles() == null || u.getRoles().isBlank()) return List.of();
        List<Role> result = new ArrayList<>();
        for (String key : u.getRoles().split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) continue;
            Role r = roleMapper.selectByKey(trimmed);
            if (r != null && r.getStatus() != null && r.getStatus() == 1) result.add(r);
        }
        return result;
    }

    // ==================== 登录日志查询 ====================

    public Map<String, Object> getLoginLogs(Long userId, int page, int size) {
        if (size > 200) size = 200;
        int offset = (page - 1) * size;
        List<LoginLog> rows = loginLogMapper.selectByUserId(userId, offset, size);
        long total = loginLogMapper.countByUserId(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("rows", rows);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ==================== DTO 转换 ====================

    private Map<String, Object> toListDto(User u, long connectionCount) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("status", u.getStatus());
        m.put("remark", u.getRemark());

        List<Role> roles = loadUserRoles(u);
        List<Map<String, String>> roleBriefs = new ArrayList<>();
        for (Role r : roles) {
            Map<String, String> rm = new LinkedHashMap<>();
            rm.put("key", r.getRoleKey());
            rm.put("label", r.getRoleName());
            roleBriefs.add(rm);
        }
        m.put("roles", roleBriefs);
        m.put("rolesDisplay", roles.stream().map(Role::getRoleName).reduce((a, b) -> a + ", " + b).orElse(""));

        boolean hasAnyRole = !roles.isEmpty();
        m.put("accessibleConnectionCount", hasAnyRole ? connectionCount : 0);

        m.put("lastLoginTime", u.getLastLoginTime());
        m.put("createTime", u.getCreateTime());
        m.put("updateTime", u.getUpdateTime());
        return m;
    }

    private Map<String, Object> toDetailDto(User u) {
        Map<String, Object> m = toListDto(
                u, connectionMapper.selectAll(null).stream()
                        .filter(c -> c.getStatus() != null && c.getStatus() == 1).count());
        m.put("rolesCsv", u.getRoles());
        return m;
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
