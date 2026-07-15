package com.trs.user.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定角色定义及其权限矩阵。
 * <p>
 * 平台内置 6 个角色,权限由角色枚举静态声明,不存入数据库。
 * 用户可同时拥有多个角色,有效权限取并集。
 *
 * @author czh
 * @date 2026/0714
 */
public enum RoleDefinition {

    SUPER_ADMIN(
            "SUPER_ADMIN", "超级管理员",
            "拥有全部权限,包括用户管理和危险操作",
            List.of("*"),
            List.of("*"),
            "ADMIN",
            true,
            "ALL", "READ_WRITE"
    ),

    DB_ADMIN(
            "DB_ADMIN", "图库管理员",
            "管理图库结构、点边数据、导入导出,可执行危险操作",
            List.of("connection", "topology", "vertex-type", "edge-type", "property-management",
                    "vertex-data", "edge-data", "graph-explore", "gremlin", "import-export", "operation-log"),
            List.of("CREATE", "UPDATE", "DELETE", "CLEAR", "IMPORT", "EXPORT", "QUERY", "EXECUTE"),
            "READ_WRITE",
            true,
            "ALL", "READ_WRITE"
    ),

    DEVELOPER(
            "DEVELOPER", "开发人员",
            "开发调试用: 管理 Topology、点边类型与数据、Gremlin 查询",
            List.of("topology", "vertex-type", "edge-type", "property-management",
                    "vertex-data", "edge-data", "graph-explore", "gremlin", "import-export"),
            List.of("CREATE", "UPDATE", "DELETE", "QUERY", "EXPORT"),
            "READ_WRITE",
            false,
            "ALL", "READ_WRITE"
    ),

    DATA_MAINTAINER(
            "DATA_MAINTAINER", "数据维护人员",
            "维护点边数据,支持批量导入导出与清空",
            List.of("topology", "vertex-data", "edge-data", "import-export"),
            List.of("CREATE", "UPDATE", "DELETE", "CLEAR", "IMPORT", "EXPORT", "QUERY"),
            "READ_WRITE",
            true,
            "ALL", "READ_WRITE"
    ),

    READONLY_USER(
            "READONLY_USER", "只读查询人员",
            "仅可查看 Topology、浏览图关系、执行只读 Gremlin",
            List.of("topology", "graph-explore", "gremlin"),
            List.of("QUERY"),
            "READ_ONLY",
            false,
            "ALL", "READ"
    ),

    OPERATOR(
            "OPERATOR", "运维人员",
            "管理连接配置、查看操作日志、只读 Gremlin",
            List.of("connection", "topology", "import-export", "operation-log"),
            List.of("CREATE", "UPDATE", "DELETE", "QUERY"),
            "READ_ONLY",
            true,
            "ALL", "READ_WRITE"
    );

    private final String key;
    private final String label;
    private final String description;
    private final List<String> menuPermissions;
    private final List<String> operationPermissions;
    private final String gremlinPermission;
    private final boolean allowDangerousOps;
    private final String connectionScope;
    private final String connectionAccess;

    RoleDefinition(String key, String label, String description,
                   List<String> menuPermissions, List<String> operationPermissions,
                   String gremlinPermission, boolean allowDangerousOps,
                   String connectionScope, String connectionAccess) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.menuPermissions = menuPermissions;
        this.operationPermissions = operationPermissions;
        this.gremlinPermission = gremlinPermission;
        this.allowDangerousOps = allowDangerousOps;
        this.connectionScope = connectionScope;
        this.connectionAccess = connectionAccess;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public List<String> getMenuPermissions() { return menuPermissions; }
    public List<String> getOperationPermissions() { return operationPermissions; }
    public String getGremlinPermission() { return gremlinPermission; }
    public boolean isAllowDangerousOps() { return allowDangerousOps; }
    public String getConnectionScope() { return connectionScope; }
    public String getConnectionAccess() { return connectionAccess; }

    public static RoleDefinition fromKey(String key) {
        if (key == null) return null;
        for (RoleDefinition r : values()) {
            if (r.key.equals(key)) return r;
        }
        return null;
    }

    public static List<RoleDefinition> all() {
        return Arrays.asList(values());
    }

    /**
     * 将逗号分隔的 role key 字符串解析为角色列表。
     *
     * @param rolesCsv 逗号分隔的角色 key,如 "SUPER_ADMIN,DEVELOPER"
     * @return 角色列表,忽略无法识别的 key
     */
    public static List<RoleDefinition> parse(String rolesCsv) {
        if (rolesCsv == null || rolesCsv.isBlank()) return List.of();
        List<RoleDefinition> result = new ArrayList<>();
        for (String part : rolesCsv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            RoleDefinition r = fromKey(trimmed);
            if (r != null) result.add(r);
        }
        return result;
    }

    /**
     * 计算多角色合并后的有效权限。
     *
     * @param roles 用户拥有的角色列表
     * @return 合并后的权限快照
     */
    public static Map<String, Object> computeEffectivePermissions(List<RoleDefinition> roles) {
        Set<String> menus = new LinkedHashSet<>();
        Set<String> ops = new LinkedHashSet<>();
        Set<String> gremlinLevels = new LinkedHashSet<>();
        boolean anyDangerous = false;
        boolean hasWildcard = false;
        boolean allMenus = false;

        for (RoleDefinition r : roles) {
            for (String m : r.menuPermissions) {
                if ("*".equals(m)) { allMenus = true; hasWildcard = true; }
                menus.add(m);
            }
            for (String op : r.operationPermissions) {
                if ("*".equals(op)) hasWildcard = true;
                ops.add(op);
            }
            gremlinLevels.add(r.gremlinPermission);
            if (r.allowDangerousOps) anyDangerous = true;
        }

        if (allMenus) menus.add("*");
        if (hasWildcard) ops.add("*");

        String mergedGremlin;
        if (gremlinLevels.contains("ADMIN")) mergedGremlin = "ADMIN";
        else if (gremlinLevels.contains("READ_WRITE")) mergedGremlin = "READ_WRITE";
        else if (gremlinLevels.contains("READ_ONLY")) mergedGremlin = "READ_ONLY";
        else mergedGremlin = "NONE";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("menus", new ArrayList<>(menus));
        result.put("operations", new ArrayList<>(ops));
        result.put("gremlin", mergedGremlin);
        result.put("allowDangerousOps", anyDangerous);
        return result;
    }

    /**
     * 计算多角色合并后的连接权限。
     *
     * @param roles 用户拥有的角色列表
     * @return 每个来源角色的连接权限信息
     */
    public static List<Map<String, Object>> computeConnectionPermissions(List<RoleDefinition> roles) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RoleDefinition r : roles) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r.key);
            m.put("roleLabel", r.label);
            m.put("scope", r.connectionScope);
            m.put("access", r.connectionAccess);
            result.add(m);
        }
        return result;
    }

    /**
     * 将角色列表序列化为逗号分隔的字符串,用于数据库存储。
     *
     * @param roles 角色列表
     * @return 逗号分隔的 role key 字符串
     */
    public static String toCsv(List<RoleDefinition> roles) {
        if (roles == null || roles.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(roles.get(i).key);
        }
        return sb.toString();
    }

    /**
     * 将角色列表转换为前端友好的摘要信息。
     *
     * @param roles 角色列表
     * @return 每个角色的 key + label
     */
    public static List<Map<String, String>> toBrief(List<RoleDefinition> roles) {
        List<Map<String, String>> result = new ArrayList<>();
        for (RoleDefinition r : roles) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("key", r.key);
            m.put("label", r.label);
            result.add(m);
        }
        return result;
    }
}
