package com.trs.user.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限目录,静态定义平台所有可用菜单、操作和危险操作。
 * 供角色管理界面渲染权限树,也供权限校验时查找编码含义。
 *
 * @author czh
 * @date 2026/0715
 */
public final class PermissionCatalog {

    private PermissionCatalog() {}

    public static final String GREMLIN_READ_ONLY = "READ_ONLY";
    public static final String GREMLIN_WRITE = "WRITE";
    public static final String GREMLIN_DANGEROUS = "DANGEROUS";

    public static final String CONN_NONE = "NONE";
    public static final String CONN_READ = "READ";
    public static final String CONN_WRITE = "WRITE";
    public static final String CONN_ADMIN = "ADMIN";

    public static final String DANGEROUS_BATCH_DELETE = "dangerous:batch_delete";
    public static final String DANGEROUS_DATA_CLEAR = "dangerous:data_clear";
    public static final String DANGEROUS_TOPOLOGY_DELETE = "dangerous:topology_delete";
    public static final String DANGEROUS_GREMLIN_EXECUTE = "dangerous:gremlin_execute";
    public static final String DANGEROUS_IMPORT_OVERWRITE = "dangerous:import_overwrite";

    public static List<Map<String, Object>> menuTree() {
        List<Map<String, Object>> menus = new ArrayList<>();
        menus.add(menu("connection", "连接管理", ops(
                op("connection:view", "查看"),
                op("connection:create", "新增"),
                op("connection:update", "编辑"),
                op("connection:delete", "删除"),
                op("connection:test", "测试连接")
        )));
        menus.add(menu("topology", "Topology 浏览", ops(
                op("topology:view", "查看拓扑"),
                op("topology:refresh", "刷新缓存")
        )));
        menus.add(menu("vertex-type", "点类型管理", ops(
                op("vertex_type:view", "查看"),
                op("vertex_type:create", "新增"),
                op("vertex_type:update", "编辑"),
                op("vertex_type:delete", "删除")
        )));
        menus.add(menu("edge-type", "边类型管理", ops(
                op("edge_type:view", "查看"),
                op("edge_type:create", "新增"),
                op("edge_type:delete", "删除")
        )));
        menus.add(menu("property-management", "属性管理", ops(
                op("property:view", "查看"),
                op("property:create", "新增属性"),
                op("property:update", "编辑属性"),
                op("property:delete", "删除属性"),
                op("property:index", "索引管理")
        )));
        menus.add(menu("vertex-data", "点数据管理", ops(
                op("vertex_data:view", "查看"),
                op("vertex_data:create", "新增"),
                op("vertex_data:update", "编辑"),
                op("vertex_data:delete", "删除"),
                op("vertex_data:batch_delete", "批量删除"),
                op("vertex_data:clear", "清空")
        )));
        menus.add(menu("edge-data", "边数据管理", ops(
                op("edge_data:view", "查看"),
                op("edge_data:create", "新增"),
                op("edge_data:update", "编辑"),
                op("edge_data:delete", "删除"),
                op("edge_data:batch_delete", "批量删除"),
                op("edge_data:clear", "清空")
        )));
        menus.add(menu("graph-explore", "图关系展开", ops(
                op("graph_explore:view", "查看图谱"),
                op("graph_explore:expand", "展开节点")
        )));
        menus.add(menu("gremlin", "Gremlin 控制台", ops(
                op("gremlin:execute", "执行查询")
        )));
        menus.add(menu("import-export", "导入导出", ops(
                op("io:export", "导出数据"),
                op("io:import", "导入数据"),
                op("io:topology_export", "导出 Topology"),
                op("io:topology_import", "导入 Topology")
        )));
        menus.add(group("user-permission", "用户与权限", List.of(
                menu("user-management", "用户管理", ops(
                        op("user:view", "查看"),
                        op("user:create", "新增"),
                        op("user:update", "编辑"),
                        op("user:delete", "删除"),
                        op("user:reset_password", "重置密码"),
                        op("user:assign_roles", "分配角色")
                )),
                menu("role-management", "角色管理", ops(
                        op("role:view", "查看"),
                        op("role:update", "编辑"),
                        op("role:assign_users", "分配用户")
                )),
                menu("permission-overview", "权限总览", ops(
                        op("permission:view", "查看")
                ))
        )));
        menus.add(group("audit-log", "审计日志", List.of(
                menu("login-log", "登录日志", ops(
                        op("login_log:view", "查看")
                )),
                menu("operation-log", "操作日志", ops(
                        op("operation_log:view", "查看")
                ))
        )));
        return menus;
    }

    public static List<Map<String, Object>> gremlinLevels() {
        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(level(GREMLIN_READ_ONLY, "只读查询", "g.V()、g.E()、has()、valueMap()、count()、path() 等"));
        levels.add(level(GREMLIN_WRITE, "数据写入", "addV()、addE()、property() 等"));
        levels.add(level(GREMLIN_DANGEROUS, "危险操作", "drop()、批量删除、清空数据等"));
        return levels;
    }

    public static List<Map<String, Object>> connectionLevels() {
        List<Map<String, Object>> levels = new ArrayList<>();
        levels.add(level(CONN_NONE, "NONE", "不会出现在角色的连接选择中"));
        levels.add(level(CONN_READ, "READ", "可以查看 Topology、点边数据、执行只读查询"));
        levels.add(level(CONN_WRITE, "WRITE", "包含 READ,并可新增、编辑点边数据"));
        levels.add(level(CONN_ADMIN, "ADMIN", "包含 WRITE,并可修改 Topology、属性和索引"));
        return levels;
    }

    public static List<Map<String, Object>> dangerousOps() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(dangerousQualification(DANGEROUS_BATCH_DELETE, "数据批量删除",
                "允许执行已授权的批量删除点、批量删除边操作",
                List.of("vertex_data:batch_delete", "edge_data:batch_delete")));
        items.add(dangerousQualification(DANGEROUS_DATA_CLEAR, "数据清空",
                "允许执行已授权的清空点数据、清空边数据操作",
                List.of("vertex_data:clear", "edge_data:clear")));
        items.add(dangerousQualification(DANGEROUS_TOPOLOGY_DELETE, "Topology 删除",
                "允许执行已授权的删除 Schema、VertexLabel、EdgeLabel、属性和索引操作",
                List.of("vertex_type:delete", "edge_type:delete", "property:delete")));
        items.add(dangerousQualification(DANGEROUS_GREMLIN_EXECUTE, "危险 Gremlin",
                "允许执行已授权的 drop、批量删除等危险 Gremlin",
                List.of("gremlin:execute")));
        items.add(dangerousQualification(DANGEROUS_IMPORT_OVERWRITE, "覆盖导入",
                "允许执行覆盖已有数据或 Topology 的导入操作",
                List.of("io:import", "io:topology_import")));
        return items;
    }

    /**
     * 操作权限编码 → 所需危险操作资格的映射。
     * 当用户执行这些操作时,除了基础操作权限外,还必须拥有对应的危险操作资格。
     */
    public static Map<String, String> dangerousOpMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map<String, Object> dq : dangerousOps()) {
            @SuppressWarnings("unchecked")
            List<String> ops = (List<String>) dq.get("operations");
            String code = (String) dq.get("code");
            for (String op : ops) {
                map.put(op, code);
            }
        }
        return map;
    }

    // ==================== builders ====================

    private static Map<String, Object> menu(String key, String label, List<Map<String, String>> operations) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("operations", operations);
        return m;
    }

    private static Map<String, Object> group(String key, String label, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("children", children);
        return m;
    }

    private static Map<String, String> op(String code, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        return m;
    }

    private static List<Map<String, String>> ops(Map<String, String>... ops) {
        return List.of(ops);
    }

    private static Map<String, Object> level(String value, String label, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> dangerousQualification(String code, String label,
                                                                String description, List<String> operations) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        m.put("description", description);
        m.put("operations", operations);
        return m;
    }
}
