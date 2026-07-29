package com.trs.modules.diagnostic.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收集所有 {@link DiagnosticCheck} 实现,按 category 分组并对外提供查询。
 * <p>
 * Spring 启动时通过构造器注入所有 {@code @Component} 的 DiagnosticCheck 实例。
 *
 * @author czh
 * @date 2026/0728
 */
@Component
public class DiagnosticCheckRegistry {

    private final Map<String, DiagnosticCheck> byId;
    private final Map<String, List<DiagnosticCheck>> byCategory;

    public DiagnosticCheckRegistry(List<DiagnosticCheck> checks) {
        this.byId = new LinkedHashMap<>();
        this.byCategory = new LinkedHashMap<>();
        for (DiagnosticCheck c : checks) {
            byId.put(c.id(), c);
            byCategory.computeIfAbsent(c.category(), k -> new ArrayList<>()).add(c);
        }
        byCategory.values().forEach(list ->
                list.sort(Comparator.comparing(DiagnosticCheck::id)));
    }

    public DiagnosticCheck get(String id) {
        return byId.get(id);
    }

    public Collection<DiagnosticCheck> all() {
        return byId.values();
    }

    public Map<String, List<DiagnosticCheck>> grouped() {
        return byCategory;
    }

    public List<DiagnosticCheck> resolve(List<String> ids) {
        List<DiagnosticCheck> result = new ArrayList<>();
        for (String id : ids) {
            DiagnosticCheck c = byId.get(id);
            if (c != null) result.add(c);
        }
        return result;
    }

    /**
     * 返回供前端渲染的元数据(不含执行逻辑)。
     */
    public List<Map<String, Object>> toMetadata() {
        return byCategory.entrySet().stream()
                .map(e -> {
                    Map<String, Object> group = new LinkedHashMap<>();
                    group.put("category", e.getKey());
                    group.put("checks", e.getValue().stream().map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", c.id());
                        m.put("displayName", c.displayName());
                        m.put("description", c.description());
                        m.put("isHeavy", c.isHeavy());
                        return m;
                    }).collect(Collectors.toList()));
                    return group;
                })
                .collect(Collectors.toList());
    }
}
