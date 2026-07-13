package com.trs.modules.gremlin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trs.config.PlatformConfig;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.gremlin.entity.GremlinQueryFavorite;
import com.trs.modules.gremlin.entity.GremlinQueryHistory;
import com.trs.modules.gremlin.mapper.GremlinQueryMapper;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.groovy.jsr223.GremlinGroovyScriptEngine;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.SqlgGraph;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

/**
 * Gremlin 控制台服务,封装 Gremlin 脚本执行、安全检查、结果序列化和历史/收藏管理。
 *
 * @author czh
 * @date 2026/07/10
 */
@Service
public class GremlinConsoleService {

    private static final Logger log = LoggerFactory.getLogger(GremlinConsoleService.class);
    private static final int HISTORY_LIMIT = 200;

    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "drop", "addV", "addE", "property(", "sideEffect",
            "io(", "tx().rollback", "tx().commit"
    );

    private static final Set<String> WRITE_KEYWORDS = Set.of(
            "drop", "addV", "addE", "property("
    );

    private static final Set<String> ADMIN_KEYWORDS = Set.of(
            "sideEffect", "io(", "tx().rollback", "tx().commit"
    );

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final GremlinQueryMapper queryMapper;
    private final ScriptEngine scriptEngine;
    private final PlatformConfig platformConfig;

    public GremlinConsoleService(SqlgGraphRegistry registry,
                                  GraphConnectionMapper connectionMapper,
                                  GremlinQueryMapper queryMapper,
                                  PlatformConfig platformConfig) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.queryMapper = queryMapper;
        this.platformConfig = platformConfig;
        this.scriptEngine = new GremlinGroovyScriptEngine();
    }

    // ==================== 连接列表 ====================

    public List<Map<String, Object>> listConnections() {
        return connectionMapper.selectAll(null).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
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

    // ==================== 执行 Gremlin 查询 ====================

    public Map<String, Object> execute(Long connectionId, Long userId, String query, String mode, Map<String, Object> params) {
        String effectiveMode = (mode == null || mode.isBlank())
                ? (platformConfig.getGremlin().isReadonlyMode() ? "READONLY" : "READWRITE")
                : mode.toUpperCase();
        validateQuery(query, effectiveMode);

        int maxResults = platformConfig.getGremlin().getMaxResultSize();
        int timeoutMs = platformConfig.getGremlin().getTimeoutSeconds() * 1000;

        SqlgGraph graph = registry.get(connectionId);
        GraphTraversalSource g = graph.traversal();

        Bindings bindings = scriptEngine.createBindings();
        bindings.put("g", g);
        bindings.put("graph", graph);
        if (params != null) {
            params.forEach(bindings::put);
        }

        long start = System.currentTimeMillis();
        boolean success = true;
        String errorMessage = null;
        List<Object> serializedResults = Collections.emptyList();
        int resultCount = 0;
        boolean truncated = false;

        try {
            FutureTask<Object> task = new FutureTask<>(() -> scriptEngine.eval(query, bindings));
            Thread execThread = new Thread(task);
            execThread.setDaemon(true);
            execThread.start();

            Object raw;
            try {
                raw = task.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                execThread.interrupt();
                throw new SecurityException("查询超时 (超过 " + platformConfig.getGremlin().getTimeoutSeconds() + "s)");
            }

            if (raw instanceof Iterator<?> it) {
                List<Object> list = new ArrayList<>();
                while (it.hasNext()) {
                    if (list.size() >= maxResults) { truncated = true; break; }
                    list.add(it.next());
                }
                serializedResults = list.stream().map(this::serializeResult).collect(Collectors.toList());
            } else if (raw instanceof Iterable<?> it) {
                List<Object> list = new ArrayList<>();
                for (Object o : it) {
                    if (list.size() >= maxResults) { truncated = true; break; }
                    list.add(o);
                }
                serializedResults = list.stream().map(this::serializeResult).collect(Collectors.toList());
            } else if (raw != null) {
                serializedResults = List.of(serializeResult(raw));
            }

            resultCount = serializedResults.size();

            if (!graph.tx().isOpen() || isWriteQuery(query)) {
                graph.tx().commit();
            }
        } catch (java.util.concurrent.ExecutionException e) {
            success = false;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            errorMessage = cause.getMessage();
            if (errorMessage == null && cause.getCause() != null) {
                errorMessage = cause.getCause().getMessage();
            }
            try { graph.tx().rollback(); } catch (Exception ignored) {}
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            try { graph.tx().rollback(); } catch (Exception ignored) {}
        }

        long cost = System.currentTimeMillis() - start;

        saveHistory(userId, connectionId, query, effectiveMode, success, errorMessage, (int) cost, resultCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("costMs", cost);
        result.put("resultCount", resultCount);
        result.put("truncated", truncated);
        result.put("results", serializedResults);

        if (success) {
            result.put("resultTypes", detectResultTypes(serializedResults));
        } else {
            result.put("error", errorMessage);
        }

        return result;
    }

    // ==================== 安全检查 ====================

    private void validateQuery(String query, String mode) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }

        String normalized = query.toLowerCase().replaceAll("\\s+", " ").trim();

        List<String> configBlocked = platformConfig.getGremlin().getBlockedKeywords();
        if (configBlocked != null) {
            for (String kw : configBlocked) {
                if (!kw.isBlank() && normalized.contains(kw.toLowerCase())) {
                    throw new SecurityException(
                            "查询中包含被配置禁止的关键字: '" + kw + "'");
                }
            }
        }

        switch (mode) {
            case "READONLY" -> {
                for (String keyword : DANGEROUS_KEYWORDS) {
                    if (normalized.contains(keyword.toLowerCase())) {
                        throw new SecurityException(
                                "只读模式下禁止使用危险操作: '" + keyword + "'。请切换到读写模式或管理员模式。");
                    }
                }
            }
            case "READWRITE" -> {
                for (String keyword : ADMIN_KEYWORDS) {
                    if (normalized.contains(keyword.toLowerCase())) {
                        throw new SecurityException(
                                "读写模式下禁止使用管理员级操作: '" + keyword + "'。请切换到管理员模式。");
                    }
                }
            }
            case "ADMIN" -> {
            }
            default -> throw new IllegalArgumentException("无效的执行模式: " + mode + " (应为 READONLY/READWRITE/ADMIN)");
        }
    }

    private boolean isWriteQuery(String query) {
        String lower = query.toLowerCase();
        return WRITE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    // ==================== 结果序列化 ====================

    private Object serializeResult(Object obj) {
        if (obj == null) return null;

        if (obj instanceof Vertex v) {
            return serializeVertex(v);
        }
        if (obj instanceof Edge e) {
            return serializeEdge(e);
        }
        if (obj instanceof Property<?> p) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", p.key());
            m.put("value", normalizeValue(p.value()));
            return m;
        }
        if (obj instanceof Path path) {
            return serializePath(path);
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (var entry : map.entrySet()) {
                m.put(String.valueOf(entry.getKey()), serializeValue(entry.getValue()));
            }
            return m;
        }
        if (obj instanceof Number || obj instanceof Boolean || obj instanceof String) {
            return obj;
        }
        if (obj instanceof Iterable<?> iterable) {
            List<Object> list = new ArrayList<>();
            for (Object o : iterable) {
                list.add(serializeResult(o));
            }
            return list;
        }

        return String.valueOf(obj);
    }

    private Map<String, Object> serializeVertex(Vertex v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "vertex");
        m.put("id", String.valueOf(v.id()));
        m.put("label", v.label());
        Map<String, Object> props = new LinkedHashMap<>();
        v.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p.value())));
        m.put("properties", props);
        return m;
    }

    private Map<String, Object> serializeEdge(Edge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "edge");
        m.put("id", String.valueOf(e.id()));
        m.put("label", e.label());
        m.put("outVertex", String.valueOf(e.outVertex().id()));
        m.put("inVertex", String.valueOf(e.inVertex().id()));
        Map<String, Object> props = new LinkedHashMap<>();
        e.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p.value())));
        m.put("properties", props);
        return m;
    }

    private Map<String, Object> serializePath(Path path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "path");
        List<Object> objects = new ArrayList<>();
        for (Object obj : path) {
            objects.add(serializeResult(obj));
        }
        m.put("objects", objects);
        List<String> labels = new ArrayList<>();
        for (Set<String> labelSet : path.labels()) {
            labels.addAll(labelSet);
        }
        m.put("labels", labels);
        return m;
    }

    private Object serializeValue(Object val) {
        if (val instanceof Vertex v) return serializeVertex(v);
        if (val instanceof Edge e) return serializeEdge(e);
        if (val instanceof Property<?> p) return normalizeValue(p.value());
        return normalizeValue(val);
    }

    private Object normalizeValue(Object val) {
        if (val == null) return null;
        if (val instanceof JsonNode node) return node.toString();
        return val;
    }

    private Set<String> detectResultTypes(List<Object> results) {
        Set<String> types = new LinkedHashSet<>();
        for (Object r : results) {
            if (r instanceof Map<?, ?> m) {
                Object t = m.get("type");
                if (t != null) types.add(String.valueOf(t));
                else types.add("map");
            } else if (r instanceof List) {
                types.add("list");
            } else if (r instanceof Number) {
                types.add("number");
            } else if (r instanceof Boolean) {
                types.add("boolean");
            } else if (r instanceof String) {
                types.add("string");
            } else {
                types.add("object");
            }
        }
        return types;
    }

    // ==================== 查询历史 ====================

    public List<GremlinQueryHistory> getHistory(Long userId, Long connectionId) {
        return queryMapper.selectHistory(userId, connectionId, HISTORY_LIMIT);
    }

    public void deleteHistory(Long userId, Long historyId) {
        queryMapper.deleteHistory(historyId, userId);
    }

    public void clearHistory(Long userId, Long connectionId) {
        queryMapper.clearHistory(userId, connectionId);
    }

    private void saveHistory(Long userId, Long connectionId, String query, String mode,
                              boolean success, String errorMessage, int costMs, int resultCount) {
        try {
            GremlinQueryHistory h = new GremlinQueryHistory();
            h.setUserId(userId);
            h.setConnectionId(connectionId);
            h.setQueryText(query.length() > 10000 ? query.substring(0, 10000) : query);
            h.setMode(mode);
            h.setSuccess(success);
            h.setErrorMessage(errorMessage != null && errorMessage.length() > 2000
                    ? errorMessage.substring(0, 2000) : errorMessage);
            h.setCostMs(costMs);
            h.setResultCount(resultCount);
            queryMapper.insertHistory(h);
        } catch (Exception e) {
            log.warn("Failed to save Gremlin query history: {}", e.getMessage());
        }
    }

    // ==================== 收藏查询 ====================

    public List<GremlinQueryFavorite> getFavorites(Long userId) {
        return queryMapper.selectFavorites(userId);
    }

    public void addFavorite(Long userId, GremlinQueryFavorite fav) {
        if (fav.getTitle() == null || fav.getTitle().isBlank()) {
            throw new IllegalArgumentException("收藏标题不能为空");
        }
        if (fav.getQueryText() == null || fav.getQueryText().isBlank()) {
            throw new IllegalArgumentException("查询语句不能为空");
        }
        fav.setUserId(userId);
        fav.setMode(fav.getMode() == null ? "READONLY" : fav.getMode());
        fav.setSortOrder(fav.getSortOrder() == null ? 0 : fav.getSortOrder());
        queryMapper.insertFavorite(fav);
    }

    public void updateFavorite(Long userId, Long favId, GremlinQueryFavorite fav) {
        fav.setId(favId);
        fav.setUserId(userId);
        if (fav.getTitle() == null || fav.getTitle().isBlank()) {
            throw new IllegalArgumentException("收藏标题不能为空");
        }
        queryMapper.updateFavorite(fav);
    }

    public void deleteFavorite(Long userId, Long favId) {
        queryMapper.deleteFavorite(favId, userId);
    }

    // ==================== 缓存刷新 ====================

    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }
}
