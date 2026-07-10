package com.trs.modules.graphExplore.service;

import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图关系展开服务,提供连接列表、Schema/VertexLabel 信息、顶点查询和关系扩展。
 * 支持按 VertexLabel + 属性值查询顶点,以及从指定顶点展开邻接关系。
 *
 * @author czh
 * @date 2026/07/10
 */
@Service
public class GraphExploreService {

    private static final Logger log = LoggerFactory.getLogger(GraphExploreService.class);

    /** 单次展开最大邻居数,防止超大图爆炸 */
    private static final int MAX_NEIGHBORS = 500;
    /** 顶点搜索最大返回数 */
    private static final int MAX_SEARCH_RESULTS = 200;

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;

    public GraphExploreService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
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

    // ==================== Schema 与 VertexLabel 信息 ====================

    /**
     * 列出连接下所有 Schema 及其 VertexLabel,供前端选择。
     */
    public List<Map<String, Object>> getSchemas(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("name", schema.getName());
            sMap.put("vertexLabels", schema.getVertexLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .map(vl -> {
                        Map<String, Object> vm = new LinkedHashMap<>();
                        vm.put("name", vl.getName());
                        vm.put("schema", schema.getName());
                        vm.put("fullName", vl.getFullName());
                        return vm;
                    })
                    .collect(Collectors.toList()));
            schemas.add(sMap);
        }
        return schemas;
    }

    /**
     * 获取 VertexLabel 的属性列表,供前端查询字段下拉框使用。
     */
    public List<Map<String, Object>> getVertexLabelProperties(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        return vl.getProperties().values().stream()
                .map(pc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", pc.getName());
                    m.put("type", pc.getPropertyType() == null ? "?" : pc.getPropertyType().name());
                    return m;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .collect(Collectors.toList());
    }

    // ==================== 顶点查询 ====================

    /**
     * 按 VertexLabel + 属性值查询顶点,返回列表供用户选择上图。
     *
     * @param connectionId 图连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @param propertyName 属性名(可空,空则不过滤)
     * @param propertyValue 属性值(可空,空则不过滤)
     * @return 顶点列表 [{id, label, schema, properties, propertySummary}]
     */
    public List<Map<String, Object>> searchVertices(Long connectionId, String schemaName,
                                                     String labelName, String propertyName, String propertyValue) {
        SqlgGraph graph = registry.get(connectionId);

        boolean hasFilter = propertyName != null && !propertyName.isBlank()
                && propertyValue != null && !propertyValue.isBlank();

        var traversal = graph.traversal().V().hasLabel(labelName);
        if (hasFilter) {
            traversal = traversal.has(propertyName, propertyValue);
        }
        traversal = traversal.limit(MAX_SEARCH_RESULTS);

        List<Map<String, Object>> rows = new ArrayList<>();
        traversal.forEachRemaining(v -> rows.add(vertexToMap(v, schemaName)));
        return rows;
    }

    // ==================== 关系展开 ====================

    /**
     * 从指定顶点展开邻接关系,返回邻居节点和边。
     *
     * @param connectionId 图连接 ID
     * @param vertexIdStr  起始顶点 ID 字符串
     * @param direction    展开方向: BOTH / OUT / IN
     * @param edgeLabel    边类型过滤(可空,空则展开全部边类型)
     * @return {nodes: [...], edges: [...]}
     */
    public Map<String, Object> expandNeighbors(Long connectionId, String vertexIdStr,
                                                String direction, String edgeLabel) {
        SqlgGraph graph = registry.get(connectionId);
        Vertex center = findVertex(graph, vertexIdStr);

        Direction dir = parseDirection(direction);
        String[] edgeLabels = (edgeLabel != null && !edgeLabel.isBlank()) ? new String[]{edgeLabel} : new String[0];

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> seenNodeIds = new LinkedHashSet<>();
        Set<String> seenEdgeIds = new LinkedHashSet<>();

        seenNodeIds.add(vertexIdStr);

        Iterator<Edge> edgeIt = center.edges(dir, edgeLabels);
        int count = 0;
        while (edgeIt.hasNext() && count < MAX_NEIGHBORS) {
            Edge e = edgeIt.next();
            String edgeId = String.valueOf(e.id());
            if (seenEdgeIds.contains(edgeId)) continue;
            seenEdgeIds.add(edgeId);
            count++;

            Vertex outV = e.outVertex();
            Vertex inV = e.inVertex();
            String outId = String.valueOf(outV.id());
            String inId = String.valueOf(inV.id());

            String neighborId;
            if (outId.equals(vertexIdStr)) {
                neighborId = inId;
            } else if (inId.equals(vertexIdStr)) {
                neighborId = outId;
            } else {
                neighborId = inId;
            }

            if (!seenNodeIds.contains(neighborId)) {
                seenNodeIds.add(neighborId);
                Vertex neighbor = neighborId.equals(outId) ? outV : inV;
                nodes.add(vertexToMap(neighbor, getSchemaName(graph, neighbor)));
            }

            Map<String, Object> edgeMap = new LinkedHashMap<>();
            edgeMap.put("id", edgeId);
            edgeMap.put("label", e.label());
            edgeMap.put("source", outId);
            edgeMap.put("target", inId);
            edgeMap.put("direction", outId.equals(vertexIdStr) ? "OUT" : "IN");
            edgeMap.put("properties", extractProperties(e));
            edges.add(edgeMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("truncated", count >= MAX_NEIGHBORS);
        return result;
    }

    /**
     * 批量展开多个顶点的关系(用于「点查询」后批量上图场景)。
     * 返回所有这些顶点及其直接邻居。
     */
    public Map<String, Object> expandMultiple(Long connectionId, List<String> vertexIdStrs,
                                               String direction, String edgeLabel) {
        SqlgGraph graph = registry.get(connectionId);
        Direction dir = parseDirection(direction);
        String[] edgeLabels = (edgeLabel != null && !edgeLabel.isBlank()) ? new String[]{edgeLabel} : new String[0];

        Set<String> seenNodeIds = new LinkedHashSet<>();
        Set<String> seenEdgeIds = new LinkedHashSet<>();
        List<Map<String, Object>> allNodes = new ArrayList<>();
        List<Map<String, Object>> allEdges = new ArrayList<>();
        int totalNeighbors = 0;

        for (String vertexIdStr : vertexIdStrs) {
            if (totalNeighbors >= MAX_NEIGHBORS) break;
            try {
                Vertex center = findVertex(graph, vertexIdStr);
                if (!seenNodeIds.contains(vertexIdStr)) {
                    seenNodeIds.add(vertexIdStr);
                    allNodes.add(vertexToMap(center, getSchemaName(graph, center)));
                }

                Iterator<Edge> edgeIt = center.edges(dir, edgeLabels);
                while (edgeIt.hasNext() && totalNeighbors < MAX_NEIGHBORS) {
                    Edge e = edgeIt.next();
                    String edgeId = String.valueOf(e.id());
                    if (seenEdgeIds.contains(edgeId)) continue;
                    seenEdgeIds.add(edgeId);
                    totalNeighbors++;

                    Vertex outV = e.outVertex();
                    Vertex inV = e.inVertex();
                    String outId = String.valueOf(outV.id());
                    String inId = String.valueOf(inV.id());

                    if (!seenNodeIds.contains(outId)) {
                        seenNodeIds.add(outId);
                        allNodes.add(vertexToMap(outV, getSchemaName(graph, outV)));
                    }
                    if (!seenNodeIds.contains(inId)) {
                        seenNodeIds.add(inId);
                        allNodes.add(vertexToMap(inV, getSchemaName(graph, inV)));
                    }

                    Map<String, Object> edgeMap = new LinkedHashMap<>();
                    edgeMap.put("id", edgeId);
                    edgeMap.put("label", e.label());
                    edgeMap.put("source", outId);
                    edgeMap.put("target", inId);
                    edgeMap.put("properties", extractProperties(e));
                    allEdges.add(edgeMap);
                }
            } catch (Exception ex) {
                log.warn("Failed to expand vertex {}: {}", vertexIdStr, ex.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", allNodes);
        result.put("edges", allEdges);
        result.put("truncated", totalNeighbors >= MAX_NEIGHBORS);
        return result;
    }

    // ==================== 缓存刷新 ====================

    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> vertexToMap(Vertex v, String schemaName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(v.id()));
        m.put("label", v.label());
        m.put("schema", schemaName);
        Map<String, Object> props = extractProperties(v);
        m.put("properties", props);
        m.put("propertySummary", buildSummary(props));
        return m;
    }

    private String buildSummary(Map<String, Object> props) {
        if (props == null || props.isEmpty()) return "";
        return props.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .limit(3)
                .map(e -> e.getKey() + "=" + truncate(String.valueOf(e.getValue()), 20))
                .collect(Collectors.joining(", "));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private Map<String, Object> extractProperties(Vertex v) {
        Map<String, Object> props = new LinkedHashMap<>();
        v.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p)));
        return props;
    }

    private Map<String, Object> extractProperties(Edge e) {
        Map<String, Object> props = new LinkedHashMap<>();
        e.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p)));
        return props;
    }

    @SuppressWarnings("unchecked")
    private Object normalizeValue(Property<Object> p) {
        Object val = p.value();
        if (val instanceof com.fasterxml.jackson.databind.JsonNode node) {
            return node.toString();
        }
        return val;
    }

    private String getSchemaName(SqlgGraph graph, Vertex v) {
        if (v.id() instanceof RecordId rid) {
            try {
                return rid.getSchemaTable() != null
                        ? graph.getTopology().getSchema(rid.getSchemaTable().getSchema())
                                .map(Schema::getName).orElse(null)
                        : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank()) return Direction.BOTH;
        return switch (direction.toUpperCase()) {
            case "OUT" -> Direction.OUT;
            case "IN" -> Direction.IN;
            default -> Direction.BOTH;
        };
    }

    private Vertex findVertex(SqlgGraph graph, String vertexIdStr) {
        RecordId rid = RecordId.from(graph, vertexIdStr);
        Iterator<Vertex> it = graph.vertices(rid);
        if (!it.hasNext()) {
            throw new IllegalArgumentException("顶点不存在: " + vertexIdStr);
        }
        return it.next();
    }

    private VertexLabel findVertexLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getVertexLabels().values().stream()
                .filter(vl -> vl.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "VertexLabel 不存在: " + schemaName + "." + labelName));
    }

    private List<Schema> sortedSchemas(Topology topology) {
        List<Schema> all = new ArrayList<>();
        all.add(topology.getPublicSchema());
        topology.getSchemas().stream()
                .filter(s -> !s.getName().equals(topology.getPublicSchema().getName()))
                .sorted(Comparator.comparing(Schema::getName))
                .forEach(all::add);
        return all;
    }
}
