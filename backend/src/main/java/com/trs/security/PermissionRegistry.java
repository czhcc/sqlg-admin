package com.trs.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 权限目录注册器,启动时扫描所有 {@link RequirePermission} 注解,
 * 自动构建菜单-操作目录树。
 * <p>
 * 扫描结果供 GET /role/management/catalog 接口返回,替代 PermissionCatalog 中的静态菜单/操作定义。
 * Gremlin 等级、连接等级和危险操作仍由 {@link com.trs.user.entity.PermissionCatalog} 静态提供。
 *
 * @author czh
 * @date 2026/0715
 */
@Component
public class PermissionRegistry {

    private static final Logger log = LoggerFactory.getLogger(PermissionRegistry.class);

    private static final Map<String, String> MENU_LABELS = new LinkedHashMap<>();
    private static final Map<String, MenuGroupDef> MENU_GROUPS = new LinkedHashMap<>();

    static {
        MENU_LABELS.put("connection", "连接管理");
        MENU_LABELS.put("topology", "Topology 浏览");
        MENU_LABELS.put("vertex-type", "点类型管理");
        MENU_LABELS.put("edge-type", "边类型管理");
        MENU_LABELS.put("property-management", "属性管理");
        MENU_LABELS.put("vertex-data", "点数据管理");
        MENU_LABELS.put("edge-data", "边数据管理");
        MENU_LABELS.put("graph-explore", "图关系展开");
        MENU_LABELS.put("gremlin", "Gremlin 控制台");
        MENU_LABELS.put("import-export", "导入导出");
        MENU_LABELS.put("user-management", "用户管理");
        MENU_LABELS.put("role-management", "角色管理");
        MENU_LABELS.put("permission-overview", "权限总览");
        MENU_LABELS.put("login-log", "登录日志");
        MENU_LABELS.put("operation-log", "操作日志");

        MENU_GROUPS.put("user-permission", new MenuGroupDef("user-permission", "用户与权限",
                List.of("user-management", "role-management", "permission-overview")));
        MENU_GROUPS.put("audit-log", new MenuGroupDef("audit-log", "审计日志",
                List.of("login-log", "operation-log")));
    }

    private final ApplicationContext applicationContext;

    private List<Map<String, Object>> menuTree;

    public PermissionRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void scan() {
        Map<String, List<Map<String, String>>> menuOps = new TreeMap<>();

        try {
            RequestMappingHandlerMapping mapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
            Map<RequestMappingInfo, HandlerMethod> methods = mapping.getHandlerMethods();
            for (var entry : methods.entrySet()) {
                HandlerMethod handler = entry.getValue();
                RequirePermission ann = handler.getMethodAnnotation(RequirePermission.class);
                if (ann == null) continue;

                menuOps.computeIfAbsent(ann.menu(), k -> new ArrayList<>());
                Map<String, String> op = new LinkedHashMap<>();
                op.put("code", ann.code());
                op.put("label", ann.name());
                menuOps.get(ann.menu()).add(op);
            }
        } catch (Exception e) {
            log.error("Failed to scan @RequirePermission annotations", e);
        }

        menuTree = buildMenuTree(menuOps);
        log.info("PermissionRegistry scanned: {} menus, {} operations",
                menuOps.size(),
                menuOps.values().stream().mapToInt(List::size).sum());
    }

    private List<Map<String, Object>> buildMenuTree(Map<String, List<Map<String, String>>> menuOps) {
        List<Map<String, Object>> result = new ArrayList<>();

        Set<String> groupedMenus = new LinkedHashSet<>();
        for (MenuGroupDef group : MENU_GROUPS.values()) {
            groupedMenus.addAll(group.children);
        }

        for (var entry : MENU_LABELS.entrySet()) {
            String key = entry.getKey();
            if (groupedMenus.contains(key)) continue;

            List<Map<String, String>> ops = menuOps.getOrDefault(key, List.of());
            if (ops.isEmpty()) continue;

            result.add(buildMenu(key, entry.getValue(), ops));
        }

        for (MenuGroupDef group : MENU_GROUPS.values()) {
            List<Map<String, Object>> children = new ArrayList<>();
            for (String childKey : group.children) {
                List<Map<String, String>> ops = menuOps.getOrDefault(childKey, List.of());
                if (ops.isEmpty()) continue;
                children.add(buildMenu(childKey, MENU_LABELS.get(childKey), ops));
            }
            if (children.isEmpty()) continue;

            Map<String, Object> groupNode = new LinkedHashMap<>();
            groupNode.put("key", group.key);
            groupNode.put("label", group.label);
            groupNode.put("children", children);
            result.add(groupNode);
        }

        return result;
    }

    private Map<String, Object> buildMenu(String key, String label, List<Map<String, String>> operations) {
        Map<String, Map<String, String>> deduped = new LinkedHashMap<>();
        for (Map<String, String> op : operations) {
            deduped.putIfAbsent(op.get("code"), op);
        }
        List<Map<String, String>> sorted = new ArrayList<>(deduped.values());
        sorted.sort(Comparator.comparing(m -> m.get("code")));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        m.put("operations", sorted);
        return m;
    }

    public List<Map<String, Object>> getMenuTree() {
        return menuTree != null ? menuTree : List.of();
    }

    private record MenuGroupDef(String key, String label, List<String> children) {}
}
