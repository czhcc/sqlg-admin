package com.trs.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.security.PermissionRegistry;
import com.trs.user.entity.PermissionCatalog;
import com.trs.user.entity.Role;
import com.trs.user.entity.User;
import com.trs.user.mapper.RoleMapper;
import com.trs.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限总览服务,计算用户通过所有角色获得的最终有效权限,并追踪每项权限的来源角色。
 * <p>
 * 该服务为只读分析工具,不修改任何权限配置。主要输出包括:
 * <ul>
 *   <li>菜单权限(含来源角色)</li>
 *   <li>操作权限(按菜单分组,含来源角色)</li>
 *   <li>可见连接(含来源角色)</li>
 *   <li>Gremlin 权限级别与能力分解</li>
 *   <li>危险操作资格(含来源角色)</li>
 *   <li>配置检查(识别无效、矛盾或不完整的权限配置)</li>
 * </ul>
 *
 * @author czh
 * @date 2026/0716
 */
@Service
public class PermissionOverviewService {

    private static final Logger log = LoggerFactory.getLogger(PermissionOverviewService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private static final String SUPER_ADMIN_KEY = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final GraphConnectionMapper connectionMapper;
    private final PermissionRegistry permissionRegistry;

    public PermissionOverviewService(UserMapper userMapper, RoleMapper roleMapper,
                                      GraphConnectionMapper connectionMapper,
                                      PermissionRegistry permissionRegistry) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.connectionMapper = connectionMapper;
        this.permissionRegistry = permissionRegistry;
    }

    // ==================== 用户搜索 ====================

    /**
     * 搜索用户列表(排除 admin),供权限总览左侧选择。
     *
     * @param keyword 用户名/昵称/邮箱关键词
     * @param status  状态过滤
     * @return 用户摘要列表
     */
    public Map<String, Object> searchUsers(String keyword, Short status) {
        List<User> users = userMapper.selectAllForOverview(keyword, status);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("status", u.getStatus());
            m.put("isSuperAdmin", isSuperAdmin(u));

            List<Role> roles = loadUserRoles(u);
            List<Map<String, String>> roleBriefs = new ArrayList<>();
            for (Role r : roles) {
                Map<String, String> rm = new LinkedHashMap<>();
                rm.put("key", r.getRoleKey());
                rm.put("label", r.getRoleName());
                roleBriefs.add(rm);
            }
            m.put("roles", roleBriefs);
            m.put("roleCount", roles.size());
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("rows", rows);
        return result;
    }

    // ==================== 权限总览 ====================

    /**
     * 计算指定用户的完整权限总览。
     *
     * @param userId 用户ID
     * @return 权限总览数据
     */
    public Map<String, Object> getUserPermissionOverview(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new IllegalArgumentException("用户不存在: " + userId);

        if (isSuperAdmin(u)) {
            return buildSuperAdminOverview(u);
        }

        List<Role> roles = loadUserRoles(u);
        return buildNormalOverview(u, roles);
    }

    // ==================== 角色搜索 ====================

    /**
     * 搜索角色列表,供权限总览左侧选择。
     *
     * @param keyword 角色编码/名称关键词
     * @param status  状态过滤
     * @return 角色摘要列表
     */
    public Map<String, Object> searchRoles(String keyword, Short status) {
        List<Role> roles = roleMapper.selectAll(keyword, status);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Role r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("roleKey", r.getRoleKey());
            m.put("roleName", r.getRoleName());
            m.put("description", r.getDescription());
            m.put("status", r.getStatus());
            m.put("isBuiltin", r.getIsBuiltin());
            m.put("userCount", roleMapper.countUsersByRoleKey(r.getRoleKey()));
            rows.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", rows.size());
        result.put("rows", rows);
        return result;
    }

    // ==================== 角色权限总览 ====================

    /**
     * 计算指定角色的完整权限总览,包括角色成员、菜单权限、操作权限、
     * 可见连接、Gremlin 权限、危险操作资格和角色级配置检查。
     *
     * @param roleId 角色ID
     * @return 角色权限总览数据
     */
    public Map<String, Object> getRolePermissionOverview(Long roleId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null) throw new IllegalArgumentException("角色不存在: " + roleId);

        Set<String> menus = new LinkedHashSet<>(parseJsonList(r.getMenuPermissions()));
        Set<String> operations = new LinkedHashSet<>(parseJsonList(r.getOperationPermissions()));
        Set<String> dangerous = new LinkedHashSet<>(parseJsonList(r.getDangerousPermissions()));
        String gremlinLevel = r.getGremlinPermission() != null ? r.getGremlinPermission() : "NONE";

        List<Map<String, Object>> menuTree = permissionRegistry.getMenuTree();

        List<Map<String, Object>> menusResult = buildMenusWithSources(menuTree, menus, List.of(r));
        List<Map<String, Object>> operationsResult = buildOperationsWithSources(menuTree, operations, List.of(r));

        List<GraphConnection> allConns = connectionMapper.selectAll(null);
        List<Map<String, Object>> connectionsResult = buildConnectionsWithSources(allConns, List.of(r));
        long visibleConnCount = connectionsResult.stream().filter(c -> Boolean.TRUE.equals(c.get("visible"))).count();

        Set<String> gremlinLevels = new LinkedHashSet<>();
        gremlinLevels.add(gremlinLevel);
        Map<String, Object> gremlinResult = buildGremlinWithSources(List.of(r), gremlinLevels, gremlinLevel, menus);

        List<Map<String, Object>> dangerousResult = buildDangerousWithSources(dangerous, List.of(r));

        List<Map<String, Object>> members = buildRoleMembers(r);

        List<Map<String, Object>> configChecks = buildConfigChecks(
                menus, operations, dangerous, gremlinLevel, visibleConnCount, menuTree, "角色");

        int menuCount = (int) menusResult.stream().filter(m -> Boolean.TRUE.equals(m.get("granted"))).count();
        int opCount = countGrantedOperations(operationsResult);
        int dangerousCount = (int) dangerousResult.stream().filter(d -> Boolean.TRUE.equals(d.get("granted"))).count();

        Map<String, Object> roleDto = new LinkedHashMap<>();
        roleDto.put("id", r.getId());
        roleDto.put("roleKey", r.getRoleKey());
        roleDto.put("roleName", r.getRoleName());
        roleDto.put("description", r.getDescription());
        roleDto.put("status", r.getStatus());
        roleDto.put("isBuiltin", r.getIsBuiltin());
        roleDto.put("userCount", members.size());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("userCount", members.size());
        summary.put("menuCount", menuCount);
        summary.put("operationCount", opCount);
        summary.put("visibleConnectionCount", visibleConnCount);
        summary.put("gremlinLevel", gremlinLevel);
        summary.put("gremlinLevelLabel", gremlinLevelLabel(gremlinLevel));
        summary.put("dangerousCount", dangerousCount);
        summary.put("warningCount", configChecks.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", roleDto);
        result.put("summary", summary);
        result.put("members", members);
        result.put("menus", menusResult);
        result.put("operations", operationsResult);
        result.put("connections", connectionsResult);
        result.put("gremlin", gremlinResult);
        result.put("dangerous", dangerousResult);
        result.put("configChecks", configChecks);
        return result;
    }

    private List<Map<String, Object>> buildRoleMembers(Role r) {
        List<Long> userIds = roleMapper.selectUserIdsByRoleKey(r.getRoleKey());
        List<Map<String, Object>> members = new ArrayList<>();
        for (Long uid : userIds) {
            User u = userMapper.selectById(uid);
            if (u == null) continue;

            List<String> otherRoleKeys = new ArrayList<>();
            if (u.getRoles() != null && !u.getRoles().isBlank()) {
                for (String key : u.getRoles().split(",")) {
                    String trimmed = key.trim();
                    if (!trimmed.isEmpty() && !trimmed.equals(r.getRoleKey())) {
                        otherRoleKeys.add(trimmed);
                    }
                }
            }

            List<String> otherRoleLabels = new ArrayList<>();
            for (String key : otherRoleKeys) {
                Role other = roleMapper.selectByKey(key);
                otherRoleLabels.add(other != null ? other.getRoleName() : key);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("status", u.getStatus());
            m.put("otherRoles", otherRoleLabels);
            members.add(m);
        }
        return members;
    }

    // ==================== 权限反查 ====================

    /**
     * 反查某项权限分配给了哪些角色和用户。
     *
     * @param type 权限类型: menu / operation / connection / gremlin / dangerous
     * @param code 权限编码
     * @return 反查结果,包含拥有该权限的角色列表、用户列表和危险操作分析(如适用)
     */
    public Map<String, Object> lookupPermission(String type, String code) {
        List<Role> allRoles = roleMapper.selectAll(null, null);
        List<User> allUsers = loadAllUsersIncludingAdmin();
        List<Map<String, Object>> menuTree = permissionRegistry.getMenuTree();
        List<GraphConnection> allConns = connectionMapper.selectAll(null);

        List<Map<String, Object>> userEffPerms = new ArrayList<>();
        for (User u : allUsers) {
            userEffPerms.add(computeEffectiveForLookup(u));
        }

        List<Long> roleIds = allRoles.stream().map(Role::getId).toList();
        List<Map<String, Object>> authRecords = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectConnectionAuthForRoles(roleIds);
        Map<Long, Map<Long, String>> authByRole = buildAuthByRoleMap(authRecords);
        Map<Long, Role> roleById = new LinkedHashMap<>();
        for (Role r : allRoles) {
            roleById.put(r.getId(), r);
        }

        LookupContext ctx = new LookupContext(allRoles, roleById, userEffPerms, menuTree, allConns, authByRole);

        return switch (type) {
            case "menu" -> lookupMenu(code, ctx);
            case "operation" -> lookupOperation(code, ctx);
            case "connection" -> lookupConnection(code, ctx);
            case "gremlin" -> lookupGremlin(code, ctx);
            case "dangerous" -> lookupDangerous(code, ctx);
            default -> throw new IllegalArgumentException("未知权限类型: " + type);
        };
    }

    private record LookupContext(
            List<Role> allRoles,
            Map<Long, Role> roleById,
            List<Map<String, Object>> userEffPerms,
            List<Map<String, Object>> menuTree,
            List<GraphConnection> allConns,
            Map<Long, Map<Long, String>> authByRole
    ) {}

    private Map<String, Object> lookupMenu(String code, LookupContext ctx) {
        String label = getMenuLabel(ctx.menuTree(), code);

        List<Map<String, Object>> roles = ctx.allRoles().stream()
                .filter(r -> parseJsonList(r.getMenuPermissions()).contains(code))
                .map(this::formatRoleForLookup)
                .toList();

        List<Map<String, Object>> users = ctx.userEffPerms().stream()
                .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "menus").contains(code))
                .map(this::formatUserForLookup)
                .toList();

        return buildLookupResponse("menu", code, label, roles, users, null);
    }

    private Map<String, Object> lookupOperation(String code, LookupContext ctx) {
        String label = getOperationLabel(ctx.menuTree(), code);

        List<Map<String, Object>> roles = ctx.allRoles().stream()
                .filter(r -> parseJsonList(r.getOperationPermissions()).contains(code))
                .map(this::formatRoleForLookup)
                .toList();

        List<Map<String, Object>> users = ctx.userEffPerms().stream()
                .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "operations").contains(code))
                .map(this::formatUserForLookup)
                .toList();

        Map<String, String> dangerousOpMap = PermissionCatalog.dangerousOpMap();
        String requiredQual = dangerousOpMap.get(code);

        Map<String, Object> dangerousAnalysis = null;
        if (requiredQual != null) {
            Map<String, Object> dq = findDangerousQual(requiredQual);
            @SuppressWarnings("unchecked")
            List<String> relatedOps = (List<String>) dq.get("operations");
            dangerousAnalysis = buildDangerousAnalysis(
                    requiredQual, (String) dq.get("label"), relatedOps, code, ctx);
        }

        return buildLookupResponse("operation", code, label, roles, users, dangerousAnalysis);
    }

    private Map<String, Object> lookupConnection(String code, LookupContext ctx) {
        Long connId = Long.parseLong(code);
        GraphConnection conn = ctx.allConns().stream()
                .filter(c -> c.getId().equals(connId))
                .findFirst().orElse(null);
        String label = conn != null ? conn.getName() : code;

        List<Map<String, Object>> roles = ctx.allRoles().stream()
                .filter(r -> isConnVisibleForRole(r, connId, ctx.authByRole()))
                .map(this::formatRoleForLookup)
                .toList();

        List<Map<String, Object>> users = ctx.userEffPerms().stream()
                .filter(ue -> userCanSeeConn(ue, connId, ctx))
                .map(this::formatUserForLookup)
                .toList();

        return buildLookupResponse("connection", code, label, roles, users, null);
    }

    private Map<String, Object> lookupGremlin(String code, LookupContext ctx) {
        String label = gremlinCapabilityLabel(code);

        List<Map<String, Object>> roles;
        List<Map<String, Object>> users;

        if ("console_access".equals(code)) {
            roles = ctx.allRoles().stream()
                    .filter(r -> parseJsonList(r.getMenuPermissions()).contains("gremlin"))
                    .map(this::formatRoleForLookup)
                    .toList();
            users = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "menus").contains("gremlin"))
                    .map(this::formatUserForLookup)
                    .toList();
        } else {
            int threshold = gremlinCapabilityOrdinal(code);
            roles = ctx.allRoles().stream()
                    .filter(r -> gremlinLevelOrdinal(r.getGremlinPermission()) >= threshold)
                    .map(this::formatRoleForLookup)
                    .toList();
            users = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue)
                            || gremlinLevelOrdinal((String) ue.get("gremlinLevel")) >= threshold)
                    .map(this::formatUserForLookup)
                    .toList();
        }

        return buildLookupResponse("gremlin", code, label, roles, users, null);
    }

    private Map<String, Object> lookupDangerous(String code, LookupContext ctx) {
        Map<String, Object> dq = findDangerousQual(code);
        String label = (String) dq.get("label");
        @SuppressWarnings("unchecked")
        List<String> relatedOps = (List<String>) dq.get("operations");

        List<Map<String, Object>> roles = ctx.allRoles().stream()
                .filter(r -> parseJsonList(r.getDangerousPermissions()).contains(code))
                .map(this::formatRoleForLookup)
                .toList();

        List<Map<String, Object>> users = ctx.userEffPerms().stream()
                .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "dangerous").contains(code))
                .map(this::formatUserForLookup)
                .toList();

        Map<String, Object> dangerousAnalysis = buildDangerousAnalysis(code, label, relatedOps, null, ctx);

        return buildLookupResponse("dangerous", code, label, roles, users, dangerousAnalysis);
    }

    private Map<String, Object> buildDangerousAnalysis(
            String qualCode, String qualLabel, List<String> relatedOps,
            String primaryOpCode, LookupContext ctx) {

        List<Map<String, Object>> usersWithBaseOp;
        List<Map<String, Object>> usersWithQual;
        List<Map<String, Object>> usersWhoCanExecute;

        if (primaryOpCode != null) {
            usersWithBaseOp = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "operations").contains(primaryOpCode))
                    .map(this::formatUserForLookup).toList();
            usersWithQual = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "dangerous").contains(qualCode))
                    .map(this::formatUserForLookup).toList();
            usersWhoCanExecute = ctx.userEffPerms().stream()
                    .filter(ue -> {
                        if (isSuperAdminEff(ue)) return true;
                        return getEffSet(ue, "operations").contains(primaryOpCode)
                                && getEffSet(ue, "dangerous").contains(qualCode);
                    })
                    .map(this::formatUserForLookup).toList();
        } else {
            usersWithBaseOp = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue)
                            || relatedOps.stream().anyMatch(getEffSet(ue, "operations")::contains))
                    .map(this::formatUserForLookup).toList();
            usersWithQual = ctx.userEffPerms().stream()
                    .filter(ue -> isSuperAdminEff(ue) || getEffSet(ue, "dangerous").contains(qualCode))
                    .map(this::formatUserForLookup).toList();
            usersWhoCanExecute = ctx.userEffPerms().stream()
                    .filter(ue -> {
                        if (isSuperAdminEff(ue)) return true;
                        Set<?> ops = getEffSet(ue, "operations");
                        Set<?> dangerous = getEffSet(ue, "dangerous");
                        return dangerous.contains(qualCode) && relatedOps.stream().anyMatch(ops::contains);
                    })
                    .map(this::formatUserForLookup).toList();
        }

        List<Map<String, String>> relatedOpsWithLabels = relatedOps.stream().map(op -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("code", op);
            m.put("label", getOperationLabel(ctx.menuTree(), op));
            return m;
        }).toList();

        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("qualificationCode", qualCode);
        analysis.put("qualificationLabel", qualLabel);
        analysis.put("relatedOperations", relatedOpsWithLabels);
        analysis.put("usersWithBaseOpCount", usersWithBaseOp.size());
        analysis.put("usersWithQualificationCount", usersWithQual.size());
        analysis.put("usersWhoCanExecuteCount", usersWhoCanExecute.size());
        analysis.put("usersWhoCanExecute", usersWhoCanExecute);
        return analysis;
    }

    private Map<String, Object> buildLookupResponse(String type, String code, String label,
                                                       List<Map<String, Object>> roles,
                                                       List<Map<String, Object>> users,
                                                       Map<String, Object> dangerousAnalysis) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", type);
        query.put("code", code);
        query.put("label", label);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("roles", roles);
        result.put("users", users);
        result.put("dangerousAnalysis", dangerousAnalysis);
        return result;
    }

    private List<User> loadAllUsersIncludingAdmin() {
        List<User> users = new ArrayList<>(userMapper.selectAllForOverview(null, null));
        User admin = userMapper.selectByUsername("admin");
        if (admin != null && users.stream().noneMatch(u -> u.getId().equals(admin.getId()))) {
            users.add(0, admin);
        }
        return users;
    }

    private Map<String, Object> computeEffectiveForLookup(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("status", u.getStatus());

        if (isSuperAdmin(u)) {
            m.put("isSuperAdmin", true);
            m.put("roleLabels", List.of("超级管理员"));
            return m;
        }

        List<Role> roles = loadUserRoles(u);
        Set<String> menus = new LinkedHashSet<>();
        Set<String> operations = new LinkedHashSet<>();
        Set<String> dangerous = new LinkedHashSet<>();
        Set<String> gremlinLevels = new LinkedHashSet<>();
        List<Long> roleIds = new ArrayList<>();
        List<String> roleLabels = new ArrayList<>();

        for (Role r : roles) {
            menus.addAll(parseJsonList(r.getMenuPermissions()));
            operations.addAll(parseJsonList(r.getOperationPermissions()));
            dangerous.addAll(parseJsonList(r.getDangerousPermissions()));
            if (r.getGremlinPermission() != null) {
                gremlinLevels.add(r.getGremlinPermission());
            }
            roleIds.add(r.getId());
            roleLabels.add(r.getRoleName());
        }

        m.put("isSuperAdmin", false);
        m.put("roleLabels", roleLabels);
        m.put("menus", menus);
        m.put("operations", operations);
        m.put("dangerous", dangerous);
        m.put("gremlinLevel", mergeGremlinLevel(gremlinLevels));
        m.put("roleIds", roleIds);
        return m;
    }

    private Map<String, Object> formatRoleForLookup(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", r.getRoleKey());
        m.put("label", r.getRoleName());
        m.put("isBuiltin", r.getIsBuiltin());
        m.put("status", r.getStatus());
        return m;
    }

    private Map<String, Object> formatUserForLookup(Map<String, Object> ue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ue.get("id"));
        m.put("username", ue.get("username"));
        m.put("nickname", ue.get("nickname"));
        m.put("status", ue.get("status"));
        m.put("isSuperAdmin", ue.get("isSuperAdmin"));
        m.put("roleLabels", ue.get("roleLabels"));
        return m;
    }

    private boolean isSuperAdminEff(Map<String, Object> ue) {
        return Boolean.TRUE.equals(ue.get("isSuperAdmin"));
    }

    @SuppressWarnings("unchecked")
    private Set<String> getEffSet(Map<String, Object> ue, String key) {
        Object val = ue.get(key);
        return val instanceof Set<?> ? (Set<String>) val : Set.of();
    }

    private boolean isConnVisibleForRole(Role r, Long connId, Map<Long, Map<Long, String>> authByRole) {
        Map<Long, String> connMap = authByRole.get(r.getId());
        String level = connMap != null ? connMap.get(connId) : null;
        return level != null ? !"NONE".equals(level) : !"NONE".equals(r.getConnectionDefault());
    }

    @SuppressWarnings("unchecked")
    private boolean userCanSeeConn(Map<String, Object> ue, Long connId, LookupContext ctx) {
        if (isSuperAdminEff(ue)) return true;
        List<Long> roleIds = (List<Long>) ue.get("roleIds");
        if (roleIds == null || roleIds.isEmpty()) return false;
        for (Long rid : roleIds) {
            Role r = ctx.roleById().get(rid);
            if (r != null && isConnVisibleForRole(r, connId, ctx.authByRole())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, Map<Long, String>> buildAuthByRoleMap(List<Map<String, Object>> authRecords) {
        Map<Long, Map<Long, String>> authByRole = new LinkedHashMap<>();
        for (Map<String, Object> a : authRecords) {
            Long rid = ((Number) a.get("roleId")).longValue();
            Long connId = ((Number) a.get("connectionId")).longValue();
            String level = (String) a.get("accessLevel");
            authByRole.computeIfAbsent(rid, k -> new LinkedHashMap<>()).put(connId, level);
        }
        return authByRole;
    }

    private Map<String, Object> findDangerousQual(String code) {
        return PermissionCatalog.dangerousOps().stream()
                .filter(d -> code.equals(d.get("code")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知危险操作资格: " + code));
    }

    @SuppressWarnings("unchecked")
    private String getOperationLabel(List<Map<String, Object>> menuTree, String opCode) {
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                String found = getOperationLabel((List<Map<String, Object>>) node.get("children"), opCode);
                if (found != null) return found;
            } else {
                List<Map<String, String>> ops = (List<Map<String, String>>) node.get("operations");
                if (ops != null) {
                    for (Map<String, String> op : ops) {
                        if (opCode.equals(op.get("code"))) return op.get("label");
                    }
                }
            }
        }
        return opCode;
    }

    private String gremlinCapabilityLabel(String code) {
        return switch (code) {
            case "console_access" -> "Gremlin 控制台访问";
            case "read_query" -> "只读查询";
            case "data_write" -> "数据写入";
            case "dangerous_gremlin" -> "危险 Gremlin";
            default -> code;
        };
    }

    private int gremlinCapabilityOrdinal(String code) {
        return switch (code) {
            case "read_query" -> 1;
            case "data_write" -> 2;
            case "dangerous_gremlin" -> 3;
            default -> 0;
        };
    }

    // ==================== SUPER_ADMIN 特殊处理 ====================

    private Map<String, Object> buildSuperAdminOverview(User u) {
        Map<String, Object> userDto = buildUserDto(u, true);

        List<Map<String, Object>> menuTree = permissionRegistry.getMenuTree();

        long totalMenus = countMenus(menuTree);
        long totalOps = countOperations(menuTree);
        List<GraphConnection> allConns = connectionMapper.selectAll(null);
        long enabledConns = allConns.stream().filter(c -> c.getStatus() != null && c.getStatus() == 1).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("roleCount", 1);
        summary.put("menuCount", totalMenus);
        summary.put("operationCount", totalOps);
        summary.put("visibleConnectionCount", enabledConns);
        summary.put("gremlinLevel", "DANGEROUS");
        summary.put("gremlinLevelLabel", "危险操作");
        summary.put("dangerousCount", 5);
        summary.put("warningCount", 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userDto);
        result.put("summary", summary);
        result.put("menus", buildSuperAdminMenus(menuTree));
        result.put("operations", buildSuperAdminOperations(menuTree));
        result.put("connections", buildSuperAdminConnections(allConns));
        result.put("gremlin", buildSuperAdminGremlin(u));
        result.put("dangerous", buildSuperAdminDangerous());
        result.put("configChecks", List.of());
        return result;
    }

    // ==================== 普通用户权限计算 ====================

    private Map<String, Object> buildNormalOverview(User u, List<Role> roles) {
        Map<String, Object> userDto = buildUserDto(u, false);

        List<Map<String, Object>> menuTree = permissionRegistry.getMenuTree();

        Set<String> effectiveMenus = new LinkedHashSet<>();
        Set<String> effectiveOps = new LinkedHashSet<>();
        Set<String> effectiveDangerous = new LinkedHashSet<>();
        Set<String> gremlinLevels = new LinkedHashSet<>();

        for (Role r : roles) {
            effectiveMenus.addAll(parseJsonList(r.getMenuPermissions()));
            effectiveOps.addAll(parseJsonList(r.getOperationPermissions()));
            effectiveDangerous.addAll(parseJsonList(r.getDangerousPermissions()));
            if (r.getGremlinPermission() != null) {
                gremlinLevels.add(r.getGremlinPermission());
            }
        }

        String mergedGremlin = mergeGremlinLevel(gremlinLevels);

        List<Map<String, Object>> menusResult = buildMenusWithSources(menuTree, effectiveMenus, roles);
        List<Map<String, Object>> operationsResult = buildOperationsWithSources(menuTree, effectiveOps, roles);

        List<GraphConnection> allConns = connectionMapper.selectAll(null);
        List<Map<String, Object>> connectionsResult = buildConnectionsWithSources(allConns, roles);
        long visibleConnCount = connectionsResult.stream().filter(c -> Boolean.TRUE.equals(c.get("visible"))).count();

        Map<String, Object> gremlinResult = buildGremlinWithSources(roles, gremlinLevels, mergedGremlin, effectiveMenus);

        List<Map<String, Object>> dangerousResult = buildDangerousWithSources(effectiveDangerous, roles);

        List<Map<String, Object>> configChecks = buildConfigChecks(
                effectiveMenus, effectiveOps, effectiveDangerous,
                mergedGremlin, visibleConnCount, menuTree);

        int menuCount = (int) menusResult.stream().filter(m -> Boolean.TRUE.equals(m.get("granted"))).count();
        int opCount = countGrantedOperations(operationsResult);
        int dangerousCount = (int) dangerousResult.stream().filter(d -> Boolean.TRUE.equals(d.get("granted"))).count();
        int warningCount = configChecks.size();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("roleCount", roles.size());
        summary.put("menuCount", menuCount);
        summary.put("operationCount", opCount);
        summary.put("visibleConnectionCount", visibleConnCount);
        summary.put("gremlinLevel", mergedGremlin);
        summary.put("gremlinLevelLabel", gremlinLevelLabel(mergedGremlin));
        summary.put("dangerousCount", dangerousCount);
        summary.put("warningCount", warningCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", userDto);
        result.put("summary", summary);
        result.put("menus", menusResult);
        result.put("operations", operationsResult);
        result.put("connections", connectionsResult);
        result.put("gremlin", gremlinResult);
        result.put("dangerous", dangerousResult);
        result.put("configChecks", configChecks);
        return result;
    }

    // ==================== 菜单权限(含来源) ====================

    private List<Map<String, Object>> buildMenusWithSources(
            List<Map<String, Object>> menuTree,
            Set<String> effectiveMenus,
            List<Role> roles) {

        List<Map<String, Object>> result = new ArrayList<>();
        flattenMenuForDisplay(menuTree, effectiveMenus, roles, result, null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenMenuForDisplay(
            List<Map<String, Object>> menuTree,
            Set<String> effectiveMenus,
            List<Role> roles,
            List<Map<String, Object>> result,
            String groupLabel) {

        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
                flattenMenuForDisplay(children, effectiveMenus, roles, result, (String) node.get("label"));
                continue;
            }

            String key = (String) node.get("key");
            String label = (String) node.get("label");
            boolean granted = effectiveMenus.contains(key);

            List<String> sources = new ArrayList<>();
            if (granted) {
                for (Role r : roles) {
                    if (parseJsonList(r.getMenuPermissions()).contains(key)) {
                        sources.add(r.getRoleName());
                    }
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("label", label);
            m.put("group", groupLabel);
            m.put("granted", granted);
            m.put("sources", sources);
            result.add(m);
        }
    }

    // ==================== 操作权限(按菜单分组,含来源) ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildOperationsWithSources(
            List<Map<String, Object>> menuTree,
            Set<String> effectiveOps,
            List<Role> roles) {

        List<Map<String, Object>> result = new ArrayList<>();
        collectOperationsFromTree(menuTree, effectiveOps, roles, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectOperationsFromTree(
            List<Map<String, Object>> menuTree,
            Set<String> effectiveOps,
            List<Role> roles,
            List<Map<String, Object>> result) {

        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
                collectOperationsFromTree(children, effectiveOps, roles, result);
                continue;
            }

            String menuKey = (String) node.get("key");
            String menuLabel = (String) node.get("label");
            List<Map<String, String>> ops = (List<Map<String, String>>) node.get("operations");
            if (ops == null || ops.isEmpty()) continue;

            List<Map<String, Object>> opDetails = new ArrayList<>();
            for (Map<String, String> op : ops) {
                String code = op.get("code");
                String opLabel = op.get("label");
                boolean granted = effectiveOps.contains(code);

                List<String> sources = new ArrayList<>();
                if (granted) {
                    for (Role r : roles) {
                        if (parseJsonList(r.getOperationPermissions()).contains(code)) {
                            sources.add(r.getRoleName());
                        }
                    }
                }

                Map<String, Object> od = new LinkedHashMap<>();
                od.put("code", code);
                od.put("label", opLabel);
                od.put("granted", granted);
                od.put("sources", sources);
                opDetails.add(od);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("menuKey", menuKey);
            m.put("menuLabel", menuLabel);
            m.put("operations", opDetails);
            result.add(m);
        }
    }

    // ==================== 可见连接(含来源) ====================

    private List<Map<String, Object>> buildConnectionsWithSources(
            List<GraphConnection> allConns,
            List<Role> roles) {

        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<Map<String, Object>> authRecords = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectConnectionAuthForRoles(roleIds);

        Map<Long, Map<Long, String>> authByRole = new LinkedHashMap<>();
        for (Map<String, Object> a : authRecords) {
            Long rid = ((Number) a.get("roleId")).longValue();
            Long connId = ((Number) a.get("connectionId")).longValue();
            String level = (String) a.get("accessLevel");
            authByRole.computeIfAbsent(rid, k -> new LinkedHashMap<>()).put(connId, level);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (GraphConnection c : allConns) {
            List<String> sources = new ArrayList<>();
            for (Role r : roles) {
                Map<Long, String> connMap = authByRole.get(r.getId());
                String level = connMap != null ? connMap.get(c.getId()) : null;
                boolean roleVisible = level != null
                        ? !"NONE".equals(level)
                        : !"NONE".equals(r.getConnectionDefault());
                if (roleVisible) {
                    sources.add(r.getRoleName());
                }
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("dbType", c.getDbType());
            m.put("status", c.getStatus());
            m.put("visible", !sources.isEmpty());
            m.put("sources", sources);
            result.add(m);
        }
        return result;
    }

    // ==================== Gremlin 权限(含来源) ====================

    private Map<String, Object> buildGremlinWithSources(
            List<Role> roles,
            Set<String> gremlinLevels,
            String mergedGremlin,
            Set<String> effectiveMenus) {

        boolean hasGremlinMenu = effectiveMenus.contains("gremlin");

        List<Map<String, Object>> details = new ArrayList<>();
        for (Role r : roles) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("role", r.getRoleKey());
            d.put("roleLabel", r.getRoleName());
            d.put("level", r.getGremlinPermission());
            d.put("levelLabel", gremlinLevelLabel(r.getGremlinPermission()));
            details.add(d);
        }

        List<Map<String, Object>> capabilities = new ArrayList<>();

        capabilities.add(buildGremlinCapability(
                "console_access", "Gremlin 控制台访问", hasGremlinMenu,
                roles, role -> parseJsonList(role.getMenuPermissions()).contains("gremlin")));

        capabilities.add(buildGremlinCapability(
                "read_query", "只读查询", gremlinLevelOrdinal(mergedGremlin) >= 1,
                roles, role -> gremlinLevelOrdinal(role.getGremlinPermission()) >= 1));

        capabilities.add(buildGremlinCapability(
                "data_write", "数据写入", gremlinLevelOrdinal(mergedGremlin) >= 2,
                roles, role -> gremlinLevelOrdinal(role.getGremlinPermission()) >= 2));

        capabilities.add(buildGremlinCapability(
                "dangerous_gremlin", "危险 Gremlin", gremlinLevelOrdinal(mergedGremlin) >= 3,
                roles, role -> gremlinLevelOrdinal(role.getGremlinPermission()) >= 3));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", mergedGremlin);
        result.put("levelLabel", gremlinLevelLabel(mergedGremlin));
        result.put("details", details);
        result.put("capabilities", capabilities);
        return result;
    }

    private Map<String, Object> buildGremlinCapability(
            String key, String label, boolean granted,
            List<Role> roles, java.util.function.Predicate<Role> sourceMatcher) {

        List<String> sources = new ArrayList<>();
        if (granted) {
            for (Role r : roles) {
                if (sourceMatcher.test(r)) {
                    sources.add(r.getRoleName());
                }
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("granted", granted);
        m.put("sources", sources);
        return m;
    }

    // ==================== 危险操作资格(含来源) ====================

    private List<Map<String, Object>> buildDangerousWithSources(
            Set<String> effectiveDangerous,
            List<Role> roles) {

        List<Map<String, Object>> catalog = PermissionCatalog.dangerousOps();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> dq : catalog) {
            String code = (String) dq.get("code");
            String label = (String) dq.get("label");
            String description = (String) dq.get("description");
            @SuppressWarnings("unchecked")
            List<String> requiredOps = (List<String>) dq.get("operations");
            boolean granted = effectiveDangerous.contains(code);

            List<String> sources = new ArrayList<>();
            if (granted) {
                for (Role r : roles) {
                    if (parseJsonList(r.getDangerousPermissions()).contains(code)) {
                        sources.add(r.getRoleName());
                    }
                }
            }

            boolean hasAnyBaseOp = requiredOps.stream().anyMatch(effectiveDangerous::contains);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("label", label);
            m.put("description", description);
            m.put("granted", granted);
            m.put("sources", sources);
            m.put("requiredOperations", requiredOps);
            result.add(m);
        }
        return result;
    }

    // ==================== 配置检查 ====================

    private List<Map<String, Object>> buildConfigChecks(
            Set<String> effectiveMenus,
            Set<String> effectiveOps,
            Set<String> effectiveDangerous,
            String mergedGremlin,
            long visibleConnCount,
            List<Map<String, Object>> menuTree) {
        return buildConfigChecks(effectiveMenus, effectiveOps, effectiveDangerous,
                mergedGremlin, visibleConnCount, menuTree, "用户");
    }

    private List<Map<String, Object>> buildConfigChecks(
            Set<String> effectiveMenus,
            Set<String> effectiveOps,
            Set<String> effectiveDangerous,
            String mergedGremlin,
            long visibleConnCount,
            List<Map<String, Object>> menuTree,
            String subject) {

        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, String> opToMenu = buildOperationToMenuMap(menuTree);
        Map<String, String> dangerousOpMap = PermissionCatalog.dangerousOpMap();
        Map<String, Map<String, Object>> dangerousCatalog = buildDangerousCatalogMap();

        // Check 1: 操作权限已授予但对应菜单不可见
        for (String opCode : effectiveOps) {
            String menuKey = opToMenu.get(opCode);
            if (menuKey != null && !effectiveMenus.contains(menuKey)) {
                String menuLabel = getMenuLabel(menuTree, menuKey);
                checks.add(configCheck("warning", "menu_operation_mismatch",
                        subject + "拥有 " + opCode + " 操作权限，但没有「" + menuLabel + "」菜单权限。" +
                        "该" + subject + "通过接口可能仍有操作权限，但无法从界面进入对应页面。"));
            }
        }

        // Check 2: 危险操作资格已授予但缺少所有基础操作权限
        for (Map<String, Object> dq : PermissionCatalog.dangerousOps()) {
            String qualCode = (String) dq.get("code");
            String qualLabel = (String) dq.get("label");
            @SuppressWarnings("unchecked")
            List<String> requiredOps = (List<String>) dq.get("operations");

            if (effectiveDangerous.contains(qualCode)) {
                boolean hasAnyBase = requiredOps.stream().anyMatch(effectiveOps::contains);
                if (!hasAnyBase) {
                    checks.add(configCheck("warning", "dangerous_without_base_op",
                            subject + "拥有「" + qualLabel + "」危险操作资格，但没有 " +
                            String.join("、", requiredOps) + " 操作权限。该危险操作资格当前不会生效。"));
                }
            }
        }

        // Check 3: 基础危险操作已授予但缺少危险操作资格
        for (String opCode : effectiveOps) {
            String requiredQual = dangerousOpMap.get(opCode);
            if (requiredQual != null && !effectiveDangerous.contains(requiredQual)) {
                Map<String, Object> dq = dangerousCatalog.get(requiredQual);
                String qualLabel = dq != null ? (String) dq.get("label") : requiredQual;
                checks.add(configCheck("warning", "base_op_without_dangerous",
                        subject + "拥有 " + opCode + " 操作权限，但没有「" + qualLabel +
                        "」危险操作资格。" + subject + "仍然不能执行该操作。"));
            }
        }

        // Check 4: Gremlin 菜单与执行权限不一致
        boolean hasGremlinMenu = effectiveMenus.contains("gremlin");
        boolean hasGremlinCapability = gremlinLevelOrdinal(mergedGremlin) > 0;
        boolean hasGremlinExecute = effectiveOps.contains("gremlin:execute");

        if (hasGremlinMenu && !hasGremlinCapability) {
            checks.add(configCheck("warning", "gremlin_menu_without_capability",
                    subject + "可以访问 Gremlin 控制台，但没有 gremlin 查询权限。"));
        }
        if (hasGremlinCapability && !hasGremlinMenu) {
            checks.add(configCheck("warning", "gremlin_capability_without_menu",
                    subject + "拥有 Gremlin 查询权限，但没有 Gremlin 控制台菜单权限。"));
        }
        if (hasGremlinExecute && !hasGremlinMenu) {
            checks.add(configCheck("warning", "gremlin_execute_without_menu",
                    subject + "拥有 gremlin:execute 操作权限，但没有 Gremlin 控制台菜单权限。"));
        }

        // Check 5: 有数据操作权限但无可见连接
        boolean hasDataOp = effectiveOps.stream().anyMatch(op ->
                op.startsWith("vertex_data:") || op.startsWith("edge_data:") ||
                op.startsWith("topology:") || op.startsWith("graph_explore:"));
        if (hasDataOp && visibleConnCount == 0) {
            checks.add(configCheck("warning", "data_op_without_connection",
                    subject + "拥有数据操作权限，但没有任何可见连接。该权限当前无法实际使用。"));
        }

        return checks;
    }

    private Map<String, Object> configCheck(String level, String type, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("type", type);
        m.put("message", message);
        return m;
    }

    // ==================== SUPER_ADMIN 构建辅助 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSuperAdminMenus(List<Map<String, Object>> menuTree) {
        List<Map<String, Object>> result = new ArrayList<>();
        flattenSuperAdminMenus(menuTree, result, null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void flattenSuperAdminMenus(List<Map<String, Object>> menuTree,
                                          List<Map<String, Object>> result,
                                          String groupLabel) {
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                flattenSuperAdminMenus((List<Map<String, Object>>) node.get("children"), result, (String) node.get("label"));
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", node.get("key"));
            m.put("label", node.get("label"));
            m.put("group", groupLabel);
            m.put("granted", true);
            m.put("sources", List.of("超级管理员"));
            result.add(m);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSuperAdminOperations(List<Map<String, Object>> menuTree) {
        List<Map<String, Object>> result = new ArrayList<>();
        collectSuperAdminOps(menuTree, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectSuperAdminOps(List<Map<String, Object>> menuTree, List<Map<String, Object>> result) {
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                collectSuperAdminOps((List<Map<String, Object>>) node.get("children"), result);
                continue;
            }
            List<Map<String, String>> ops = (List<Map<String, String>>) node.get("operations");
            if (ops == null || ops.isEmpty()) continue;

            List<Map<String, Object>> opDetails = new ArrayList<>();
            for (Map<String, String> op : ops) {
                Map<String, Object> od = new LinkedHashMap<>();
                od.put("code", op.get("code"));
                od.put("label", op.get("label"));
                od.put("granted", true);
                od.put("sources", List.of("超级管理员"));
                opDetails.add(od);
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("menuKey", node.get("key"));
            m.put("menuLabel", node.get("label"));
            m.put("operations", opDetails);
            result.add(m);
        }
    }

    private List<Map<String, Object>> buildSuperAdminConnections(List<GraphConnection> allConns) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (GraphConnection c : allConns) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("dbType", c.getDbType());
            m.put("status", c.getStatus());
            m.put("visible", c.getStatus() != null && c.getStatus() == 1);
            m.put("sources", List.of("超级管理员"));
            result.add(m);
        }
        return result;
    }

    private Map<String, Object> buildSuperAdminGremlin(User u) {
        List<Map<String, Object>> capabilities = new ArrayList<>();
        for (String[] cap : new String[][]{
                {"console_access", "Gremlin 控制台访问"},
                {"read_query", "只读查询"},
                {"data_write", "数据写入"},
                {"dangerous_gremlin", "危险 Gremlin"}}) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", cap[0]);
            m.put("label", cap[1]);
            m.put("granted", true);
            m.put("sources", List.of("超级管理员"));
            capabilities.add(m);
        }

        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("role", SUPER_ADMIN_KEY);
        d.put("roleLabel", "超级管理员");
        d.put("level", "DANGEROUS");
        d.put("levelLabel", "危险操作");
        details.add(d);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", "DANGEROUS");
        result.put("levelLabel", "危险操作");
        result.put("details", details);
        result.put("capabilities", capabilities);
        return result;
    }

    private List<Map<String, Object>> buildSuperAdminDangerous() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> dq : PermissionCatalog.dangerousOps()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", dq.get("code"));
            m.put("label", dq.get("label"));
            m.put("description", dq.get("description"));
            m.put("granted", true);
            m.put("sources", List.of("超级管理员"));
            m.put("requiredOperations", dq.get("operations"));
            result.add(m);
        }
        return result;
    }

    // ==================== 工具方法 ====================

    private Map<String, Object> buildUserDto(User u, boolean isSuperAdmin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("status", u.getStatus());
        m.put("isSuperAdmin", isSuperAdmin);

        List<Role> roles = isSuperAdmin ? List.of() : loadUserRoles(u);
        List<Map<String, String>> roleBriefs = new ArrayList<>();
        if (isSuperAdmin) {
            Map<String, String> rm = new LinkedHashMap<>();
            rm.put("key", SUPER_ADMIN_KEY);
            rm.put("label", "超级管理员");
            roleBriefs.add(rm);
        } else {
            for (Role r : roles) {
                Map<String, String> rm = new LinkedHashMap<>();
                rm.put("key", r.getRoleKey());
                rm.put("label", r.getRoleName());
                roleBriefs.add(rm);
            }
        }
        m.put("roles", roleBriefs);
        return m;
    }

    private boolean isSuperAdmin(User u) {
        if (u == null || u.getRoles() == null) return false;
        return Arrays.stream(u.getRoles().split(","))
                .map(String::trim)
                .anyMatch(SUPER_ADMIN_KEY::equals);
    }

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

    private String mergeGremlinLevel(Set<String> levels) {
        if (levels.contains("DANGEROUS")) return "DANGEROUS";
        if (levels.contains("WRITE")) return "WRITE";
        if (levels.contains("READ_ONLY")) return "READ_ONLY";
        return "NONE";
    }

    private int gremlinLevelOrdinal(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "READ_ONLY" -> 1;
            case "WRITE" -> 2;
            case "DANGEROUS" -> 3;
            default -> 0;
        };
    }

    private String gremlinLevelLabel(String level) {
        if (level == null) return "无";
        return switch (level) {
            case "READ_ONLY" -> "只读查询";
            case "WRITE" -> "数据写入";
            case "DANGEROUS" -> "危险操作";
            default -> "无";
        };
    }

    @SuppressWarnings("unchecked")
    private long countMenus(List<Map<String, Object>> menuTree) {
        long count = 0;
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                count += countMenus((List<Map<String, Object>>) node.get("children"));
            } else {
                count++;
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private long countOperations(List<Map<String, Object>> menuTree) {
        long count = 0;
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                count += countOperations((List<Map<String, Object>>) node.get("children"));
            } else {
                List<?> ops = (List<?>) node.get("operations");
                if (ops != null) count += ops.size();
            }
        }
        return count;
    }

    private int countGrantedOperations(List<Map<String, Object>> operationsResult) {
        int count = 0;
        for (Map<String, Object> menuGroup : operationsResult) {
            Object opsObj = menuGroup.get("operations");
            if (opsObj instanceof List<?> ops) {
                for (Object o : ops) {
                    if (o instanceof Map<?, ?> op && Boolean.TRUE.equals(op.get("granted"))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildOperationToMenuMap(List<Map<String, Object>> menuTree) {
        Map<String, String> map = new LinkedHashMap<>();
        buildOpToMenuRecursive(menuTree, map);
        return map;
    }

    @SuppressWarnings("unchecked")
    private void buildOpToMenuRecursive(List<Map<String, Object>> menuTree, Map<String, String> map) {
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                buildOpToMenuRecursive((List<Map<String, Object>>) node.get("children"), map);
                continue;
            }
            String menuKey = (String) node.get("key");
            List<Map<String, String>> ops = (List<Map<String, String>>) node.get("operations");
            if (ops == null) continue;
            for (Map<String, String> op : ops) {
                map.put(op.get("code"), menuKey);
            }
        }
    }

    private Map<String, Map<String, Object>> buildDangerousCatalogMap() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> dq : PermissionCatalog.dangerousOps()) {
            map.put((String) dq.get("code"), dq);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private String getMenuLabel(List<Map<String, Object>> menuTree, String menuKey) {
        for (Map<String, Object> node : menuTree) {
            if (node.containsKey("children")) {
                String found = getMenuLabel((List<Map<String, Object>>) node.get("children"), menuKey);
                if (found != null) return found;
            } else if (menuKey.equals(node.get("key"))) {
                return (String) node.get("label");
            }
        }
        return menuKey;
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
