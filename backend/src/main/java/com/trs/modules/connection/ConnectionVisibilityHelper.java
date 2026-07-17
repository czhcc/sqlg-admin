package com.trs.modules.connection;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.user.entity.User;
import com.trs.user.service.RoleManagementService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 连接可见性过滤工具,根据当前登录用户的角色过滤出其可见的已启用连接。
 * 供所有业务模块的连接列表端点复用,确保不可见的连接不会出现在任何模块的下拉列表中。
 *
 * @author czh
 * @date 2026/0716
 */
@Component
public class ConnectionVisibilityHelper {

    private final GraphConnectionMapper connectionMapper;
    private final RoleManagementService roleManagementService;

    public ConnectionVisibilityHelper(GraphConnectionMapper connectionMapper,
                                       RoleManagementService roleManagementService) {
        this.connectionMapper = connectionMapper;
        this.roleManagementService = roleManagementService;
    }

    /**
     * 返回当前用户可见且已启用的连接实体列表(未排序)。
     * 无角色或未登录用户看到全部已启用连接(兼容旧逻辑)。
     */
    public List<GraphConnection> listEnabledForCurrentUser() {
        List<GraphConnection> all = connectionMapper.selectAll(null).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .collect(Collectors.toList());

        User user = currentUser();
        if (user == null) return all;

        Set<String> roleKeys = parseRoleKeys(user.getRoles());
        if (roleKeys.isEmpty() || roleKeys.contains(SUPER_ADMIN_KEY)) return all;

        Set<Long> visibleIds = roleManagementService.getVisibleConnectionIds(roleKeys);
        if (visibleIds == null) return all;
        return all.stream().filter(c -> visibleIds.contains(c.getId())).collect(Collectors.toList());
    }

    private static final String SUPER_ADMIN_KEY = "SUPER_ADMIN";

    /**
     * 返回当前用户可见连接的 DTO 列表(默认连接排在前),供前端下拉直接使用。
     */
    public List<Map<String, Object>> listConnectionDtosForCurrentUser() {
        return listEnabledForCurrentUser().stream()
                .sorted((a, b) -> {
                    int da = Boolean.TRUE.equals(a.getIsDefault()) ? 0 : 1;
                    int db = Boolean.TRUE.equals(b.getIsDefault()) ? 0 : 1;
                    return Integer.compare(da, db);
                })
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getName());
                    m.put("dbType", c.getDbType());
                    m.put("isDefault", Boolean.TRUE.equals(c.getIsDefault()));
                    return m;
                })
                .collect(Collectors.toList());
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        return auth.getPrincipal() instanceof User u ? u : null;
    }

    private Set<String> parseRoleKeys(String roles) {
        if (roles == null || roles.isBlank()) return Set.of();
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
