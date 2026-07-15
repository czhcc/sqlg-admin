package com.trs.user.service;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.entity.LoginLog;
import com.trs.modules.log.mapper.LoginLogMapper;
import com.trs.user.entity.RoleDefinition;
import com.trs.user.entity.User;
import com.trs.user.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统用户服务,提供登录校验、用户 CRUD、角色分配、权限计算等能力。
 *
 * @author czh
 * @date 2026/0714
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final GraphConnectionMapper connectionMapper;
    private final LoginLogMapper loginLogMapper;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       GraphConnectionMapper connectionMapper, LoginLogMapper loginLogMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.connectionMapper = connectionMapper;
        this.loginLogMapper = loginLogMapper;
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

        List<RoleDefinition> validRoles = new ArrayList<>();
        for (String key : roleKeys) {
            RoleDefinition r = RoleDefinition.fromKey(key);
            if (r == null) throw new IllegalArgumentException("未知角色: " + key);
            validRoles.add(r);
        }
        userMapper.updateRoles(id, RoleDefinition.toCsv(validRoles));
    }

    public void delete(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        if ("admin".equals(u.getUsername()))
            throw new IllegalArgumentException("不能删除初始管理员账号");
        userMapper.deleteById(id);
    }

    // ==================== 权限计算 ====================

    public Map<String, Object> getEffectivePermissions(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        List<RoleDefinition> roles = RoleDefinition.parse(u.getRoles());
        Map<String, Object> perms = RoleDefinition.computeEffectivePermissions(roles);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", RoleDefinition.toBrief(roles));
        result.putAll(perms);
        return result;
    }

    public Map<String, Object> getConnectionPermissions(Long id) {
        User u = userMapper.selectById(id);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + id);
        List<RoleDefinition> roles = RoleDefinition.parse(u.getRoles());
        List<Map<String, Object>> connPerms = RoleDefinition.computeConnectionPermissions(roles);

        long accessibleCount = 0;
        String mergedAccess = "NONE";
        for (Map<String, Object> cp : connPerms) {
            String access = (String) cp.get("access");
            if ("READ_WRITE".equals(access) || "READ".equals(access)) {
                accessibleCount = connectionMapper.selectAll(null).stream()
                        .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                        .count();
                if ("READ_WRITE".equals(access)) mergedAccess = "READ_WRITE";
                else if (!"READ_WRITE".equals(mergedAccess)) mergedAccess = "READ";
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("permissions", connPerms);
        result.put("accessibleCount", accessibleCount);
        result.put("mergedAccess", mergedAccess);
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

        List<RoleDefinition> roles = RoleDefinition.parse(u.getRoles());
        m.put("roles", RoleDefinition.toBrief(roles));
        m.put("rolesDisplay", roles.stream().map(RoleDefinition::getLabel).reduce((a, b) -> a + ", " + b).orElse(""));

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
}
