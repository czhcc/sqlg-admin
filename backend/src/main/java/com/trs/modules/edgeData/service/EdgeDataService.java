package com.trs.modules.edgeData.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.edgeData.dto.EdgeDetailDto;
import com.trs.modules.edgeData.dto.EdgeRowDto;
import com.trs.modules.edgeData.dto.EdgeSaveRequest;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 边数据管理服务,封装 Edge 实例数据的分页查询、详情、新增、编辑、删除和清空。
 * 支持按出点/入点过滤,支持从指定顶点查询出边/入边/双向边。
 *
 * @author czh
 * @date 2026/07/09
 */
@Service
public class EdgeDataService {

    private static final Logger log = LoggerFactory.getLogger(EdgeDataService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int EXPORT_MAX_ROWS = 10000;

    private static final Set<String> RESERVED_FILTER_KEYS = Set.of(
            "outVertexLabel", "inVertexLabel", "outVertexId", "inVertexId");

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final OperationLogService logService;

    public EdgeDataService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                            OperationLogService logService) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.logService = logService;
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

    // ==================== 拓扑树(EdgeLabel) ====================

    public Map<String, Object> getTree(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("name", schema.getName());
            sMap.put("edgeLabels", schema.getEdgeLabels().values().stream()
                    .map(AbstractLabel::getName)
                    .sorted()
                    .collect(Collectors.toList()));
            schemas.add(sMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", schemas);
        return result;
    }

    // ==================== EdgeLabel 属性定义 ====================

    public List<Map<String, Object>> getEdgeLabelProperties(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        EdgeLabel el = findEdgeLabel(graph, schemaName, labelName);

        return el.getProperties().values().stream()
                .map(pc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", pc.getName());
                    m.put("type", pc.getPropertyType() == null ? "?" : pc.getPropertyType().name());
                    return m;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .collect(Collectors.toList());
    }

    // ==================== EdgeLabel 的出/入点类型 ====================

    public Map<String, Object> getEdgeVertexLabels(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        EdgeLabel el = findEdgeLabel(graph, schemaName, labelName);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outVertexLabels", el.getOutVertexLabels().stream()
                .map(v -> vertexLabelBrief(v))
                .sorted(Comparator.comparing(m -> String.valueOf(m.get("fullName"))))
                .collect(Collectors.toList()));
        result.put("inVertexLabels", el.getInVertexLabels().stream()
                .map(v -> vertexLabelBrief(v))
                .sorted(Comparator.comparing(m -> String.valueOf(m.get("fullName"))))
                .collect(Collectors.toList()));
        return result;
    }

    private Map<String, Object> vertexLabelBrief(VertexLabel vl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("schema", vl.getSchema() == null ? null : vl.getSchema().getName());
        m.put("label", vl.getName());
        m.put("fullName", vl.getFullName());
        return m;
    }

    // ==================== 分页查询边数据 ====================

    public Map<String, Object> page(Long connectionId, String schemaName, String labelName,
                                    int page, int size, Map<String, Object> filters) {
        SqlgGraph graph = registry.get(connectionId);
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        int offset = (page - 1) * size;

        List<Edge> all = collectEdges(graph, labelName, filters);
        long total = all.size();
        int fromIndex = Math.min(offset, all.size());
        int toIndex = Math.min(offset + size, all.size());

        List<EdgeRowDto> rows = new ArrayList<>();
        for (Edge e : all.subList(fromIndex, toIndex)) {
            rows.add(toRowDto(e, schemaName));
        }

        return Map.of("total", total, "rows", rows, "page", page, "size", size);
    }

    /**
     * 收集满足过滤条件的全部 Edge。为兼顾出点/入点锚定场景(V().outE 返回 SqlgTraversal,
     * 与 GraphTraversal 类型不兼容),这里统一把锚定路径与全局路径的结果拉平为 List,
     * 在内存中应用剩余过滤、分页与导出。
     */
    private List<Edge> collectEdges(SqlgGraph graph, String labelName, Map<String, Object> filters) {
        String outVertexId = getFilter(filters, "outVertexId");
        String inVertexId = getFilter(filters, "inVertexId");
        String outVertexLabel = getFilter(filters, "outVertexLabel");
        String inVertexLabel = getFilter(filters, "inVertexLabel");

        List<Edge> edges = new ArrayList<>();
        if (outVertexId != null) {
            Vertex outV = findVertex(graph, outVertexId);
            outV.edges(Direction.OUT, labelName).forEachRemaining(edges::add);
        } else if (inVertexId != null) {
            Vertex inV = findVertex(graph, inVertexId);
            inV.edges(Direction.IN, labelName).forEachRemaining(edges::add);
        } else {
            graph.traversal().E().hasLabel(labelName).forEachRemaining(edges::add);
        }

        return edges.stream()
                .filter(e -> outVertexLabel == null || outVertexLabel.equals(e.outVertex().label()))
                .filter(e -> inVertexLabel == null || inVertexLabel.equals(e.inVertex().label()))
                .filter(e -> matchesEdgeProperties(e, filters))
                .collect(Collectors.toList());
    }

    private boolean matchesEdgeProperties(Edge e, Map<String, Object> filters) {
        if (filters == null) return true;
        for (var entry : filters.entrySet()) {
            String key = entry.getKey();
            if (RESERVED_FILTER_KEYS.contains(key)) continue;
            String val = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            if (val.isEmpty()) continue;
            Property<Object> p = e.property(key);
            if (!p.isPresent()) return false;
            String actual = String.valueOf(p.value());
            if (!actual.equals(val)) return false;
        }
        return true;
    }

    private String getFilter(Map<String, Object> filters, String key) {
        if (filters == null) return null;
        Object v = filters.get(key);
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
    }

    // ==================== 边详情 ====================

    public EdgeDetailDto getDetail(Long connectionId, String edgeIdStr) {
        SqlgGraph graph = registry.get(connectionId);
        Edge e = findEdge(graph, edgeIdStr);

        EdgeDetailDto dto = new EdgeDetailDto();
        dto.setId(edgeIdStr);
        dto.setLabel(e.label());
        Schema schema = getEdgeSchema(graph, e);
        dto.setSchema(schema != null ? schema.getName() : null);
        dto.setProperties(extractProperties(e));
        dto.setOutVertex(buildVertexBrief(graph, e.outVertex()));
        dto.setInVertex(buildVertexBrief(graph, e.inVertex()));
        return dto;
    }

    private Map<String, Object> buildVertexBrief(SqlgGraph graph, Vertex v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(v.id()));
        m.put("label", v.label());
        Schema s = getVertexSchema(graph, v);
        m.put("schema", s != null ? s.getName() : null);
        m.put("properties", extractProperties(v));
        return m;
    }

    // ==================== 新增边 ====================

    public void create(Long connectionId, EdgeSaveRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        if (req.getOutVertexId() == null || req.getOutVertexId().isBlank()) {
            throw new IllegalArgumentException("出点 ID (outVertexId) 不能为空");
        }
        if (req.getInVertexId() == null || req.getInVertexId().isBlank()) {
            throw new IllegalArgumentException("入点 ID (inVertexId) 不能为空");
        }
        SqlgGraph graph = registry.get(connectionId);
        Vertex outV = findVertex(graph, req.getOutVertexId());
        Vertex inV = findVertex(graph, req.getInVertexId());

        String schemaName = req.getSchema() != null ? req.getSchema() : "public";
        Map<String, Object> converted = convertPropertyValues(graph, schemaName, req.getLabel(), req.getProperties());

        // TinkerPop addEdge: outV.addEdge(label, inV, key1, val1, key2, val2, ...)
        if (converted.isEmpty()) {
            outV.addEdge(req.getLabel(), inV);
        } else {
            List<Object> keyValues = new ArrayList<>();
            for (var entry : converted.entrySet()) {
                keyValues.add(entry.getKey());
                keyValues.add(entry.getValue());
            }
            outV.addEdge(req.getLabel(), inV, keyValues.toArray());
        }
        graph.tx().commit();
        log.info("Created edge {} from {} to {}", req.getLabel(), req.getOutVertexId(), req.getInVertexId());
        try {
            logService.log().module("边数据管理").action("新增边").httpMethod("POST")
                .type("CREATE").name("新增边: " + req.getLabel() + " [" + req.getOutVertexId() + " → " + req.getInVertexId() + "]")
                .status("SUCCESS").connection(connectionId).schema(req.getSchema())
                .objectType("Edge").objectName(req.getLabel())
                .objectId(req.getOutVertexId() + "→" + req.getInVertexId()).affected(1).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 编辑边 ====================

    public void update(Long connectionId, String edgeIdStr, EdgeSaveRequest req) {
        SqlgGraph graph = registry.get(connectionId);
        Edge e = findEdge(graph, edgeIdStr);
        Schema schema = getEdgeSchema(graph, e);
        String schemaName = req.getSchema() != null ? req.getSchema()
                : (schema != null ? schema.getName() : "public");
        String labelName = e.label();

        Map<String, Object> converted = convertPropertyValues(graph, schemaName, labelName, req.getProperties());
        for (var entry : converted.entrySet()) {
            e.property(entry.getKey(), entry.getValue());
        }
        graph.tx().commit();
        log.info("Updated edge {}: {}", edgeIdStr, converted.keySet());
        try {
            logService.log().module("边数据管理").action("编辑边").httpMethod("PUT")
                .type("UPDATE").name("编辑边: " + labelName + " #" + edgeIdStr)
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("Edge").objectName(labelName).objectId(edgeIdStr).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 删除单条边 ====================

    public void delete(Long connectionId, String edgeIdStr) {
        SqlgGraph graph = registry.get(connectionId);
        Edge e = findEdge(graph, edgeIdStr);
        e.remove();
        graph.tx().commit();
        log.info("Deleted edge {}", edgeIdStr);
        try {
            logService.log().module("边数据管理").action("删除边").httpMethod("DELETE")
                .type("DELETE").name("删除边: " + edgeIdStr)
                .status("SUCCESS").connection(connectionId)
                .objectType("Edge").objectId(edgeIdStr).result("已删除").submit();
        } catch (Exception ignored) {}
    }

    // ==================== 批量删除 ====================

    public int batchDelete(Long connectionId, List<String> edgeIds) {
        if (edgeIds == null || edgeIds.isEmpty()) return 0;
        if (edgeIds.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("批量删除不能超过 " + MAX_PAGE_SIZE + " 条");
        }
        SqlgGraph graph = registry.get(connectionId);
        int count = 0;
        for (String idStr : edgeIds) {
            try {
                Edge e = findEdge(graph, idStr);
                e.remove();
                count++;
            } catch (Exception e) {
                log.warn("Failed to delete edge {}: {}", idStr, e.getMessage());
            }
        }
        graph.tx().commit();
        log.info("Batch deleted {} edges", count);
        try {
            logService.log().module("边数据管理").action("批量删除边").httpMethod("POST")
                .type("DELETE").name("批量删除边: " + count + " 条").dangerous()
                .status("SUCCESS").connection(connectionId)
                .objectType("Edge").affected(count).result("删除 " + count + " 条").submit();
        } catch (Exception ignored) {}
        return count;
    }

    // ==================== 清空边数据 ====================

    public long clearEdges(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        findEdgeLabel(graph, schemaName, labelName);
        long count = countEdges(graph, schemaName, labelName, null);
        graph.tx().rollback();
        String physicalTable = "E_" + labelName;
        try {
            Connection conn = graph.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM \"" + schemaName + "\".\"" + physicalTable + "\"")) {
                ps.executeUpdate();
            }
            graph.tx().commit();
        } catch (SQLException e) {
            graph.tx().rollback();
            throw new IllegalStateException("清空边数据失败: " + e.getMessage(), e);
        }
        registry.evict(connectionId);
        log.info("Cleared {} edges from {}.{}", count, schemaName, labelName);
        try {
            logService.log().module("边数据管理").action("清空边数据").httpMethod("POST")
                .type("CLEAR").name("清空边数据: " + schemaName + "." + labelName).dangerous()
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("EdgeLabel").objectName(schemaName + "." + labelName)
                .affected((int) count).result("影响 " + count + " 条").submit();
        } catch (Exception ignored) {}
        return count;
    }

    // ==================== 数据导出 ====================

    public Map<String, Object> exportEdges(Long connectionId, String schemaName, String labelName,
                                           Map<String, Object> filters, String format) {
        SqlgGraph graph = registry.get(connectionId);

        List<Edge> all = collectEdges(graph, labelName, filters);
        List<Edge> limited = all.size() > EXPORT_MAX_ROWS ? all.subList(0, EXPORT_MAX_ROWS) : all;

        List<EdgeRowDto> rows = new ArrayList<>();
        for (Edge e : limited) {
            rows.add(toRowDto(e, schemaName));
        }

        LinkedHashSet<String> propNames = new LinkedHashSet<>();
        for (EdgeRowDto row : rows) {
            if (row.getProperties() != null) propNames.addAll(row.getProperties().keySet());
        }

        List<String> headers = new ArrayList<>();
        headers.add("id");
        headers.add("schema");
        headers.add("label");
        headers.add("outVertexId");
        headers.add("outVertexLabel");
        headers.add("inVertexId");
        headers.add("inVertexLabel");
        headers.addAll(propNames);

        List<List<String>> dataRows = new ArrayList<>();
        for (EdgeRowDto row : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(row.getId());
            cells.add(row.getSchema());
            cells.add(row.getLabel());
            cells.add(row.getOutVertexId());
            cells.add(row.getOutVertexLabel());
            cells.add(row.getInVertexId());
            cells.add(row.getInVertexLabel());
            for (String pn : propNames) {
                Object val = row.getProperties() == null ? null : row.getProperties().get(pn);
                cells.add(val == null ? "" : String.valueOf(val));
            }
            dataRows.add(cells);
        }

        String effectiveFormat = format == null ? "csv" : format.toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        switch (effectiveFormat) {
            case "json" -> {
                result.put("content", exportJson(headers, dataRows));
                result.put("filename", labelName + ".json");
                result.put("binary", false);
            }
            case "excel" -> {
                result.put("content", Base64.getEncoder().encodeToString(
                        exportXlsx(headers, dataRows, schemaName, labelName)));
                result.put("filename", labelName + ".xlsx");
                result.put("binary", true);
            }
            default -> {
                result.put("content", exportCsv(headers, dataRows));
                result.put("filename", labelName + ".csv");
                result.put("binary", false);
            }
        }

        result.put("format", effectiveFormat);
        result.put("rowCount", rows.size());
        result.put("truncated", rows.size() >= EXPORT_MAX_ROWS);
        return result;
    }

    private String exportCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append(String.join(",", headers.stream().map(this::csvQuote).toList())).append("\r\n");
        for (List<String> row : rows) {
            sb.append(String.join(",", row.stream().map(this::csvQuote).toList())).append("\r\n");
        }
        return sb.toString();
    }

    private String csvQuote(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String exportJson(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            for (int j = 0; j < headers.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(headers.get(j)).append("\":");
                String val = rows.get(i).get(j);
                sb.append(val.isEmpty() ? "null" : jsonQuote(val));
            }
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String jsonQuote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private byte[] exportXlsx(List<String> headers, List<List<String>> rows,
                              String schemaName, String labelName) {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(labelName);

            var headerStyle = wb.createCellStyle();
            var headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            int rowIdx = 0;
            var headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < headers.size(); c++) {
                var cell = headerRow.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }
            for (List<String> dataRow : rows) {
                var row = sheet.createRow(rowIdx++);
                for (int c = 0; c < dataRow.size(); c++) {
                    row.createCell(c).setCellValue(dataRow.get(c));
                }
            }
            for (int c = 0; c < headers.size(); c++) sheet.autoSizeColumn(c);

            var baos = new java.io.ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("生成 Excel 失败: " + e.getMessage(), e);
        }
    }

    // ==================== 顶点搜索(供新增边选择出/入点) ====================

    public Map<String, Object> vertexSearch(Long connectionId, String schemaName, String labelName,
                                            int page, int size, String search) {
        SqlgGraph graph = registry.get(connectionId);
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        int offset = (page - 1) * size;

        boolean hasSearch = search != null && !search.isBlank();
        long total;
        if (hasSearch) {
            // 搜索时需要把范围放大再在内存里过滤,因为 sqlg 不便于跨属性 OR
            int fetchSize = Math.min(size * 10, MAX_PAGE_SIZE);
            var rawTraversal = graph.traversal().V().hasLabel(labelName).limit(fetchSize);
            List<Map<String, Object>> raw = new ArrayList<>();
            rawTraversal.forEachRemaining(v -> raw.add(vertexPickRow(v, schemaName)));

            String lower = search.toLowerCase();
            List<Map<String, Object>> filtered = raw.stream()
                    .filter(m -> {
                        String summary = String.valueOf(m.get("propertySummary")).toLowerCase();
                        String id = String.valueOf(m.get("id")).toLowerCase();
                        return summary.contains(lower) || id.contains(lower);
                    })
                    .collect(Collectors.toList());

            total = filtered.size();
            int fromIndex = Math.min(offset, filtered.size());
            int toIndex = Math.min(offset + size, filtered.size());
            List<Map<String, Object>> paged = filtered.subList(fromIndex, toIndex);

            return Map.of("total", total, "rows", paged, "page", page, "size", size);
        }

        total = graph.traversal().V().hasLabel(labelName).count().next();
        var pageTraversal = graph.traversal().V().hasLabel(labelName).range(offset, offset + size);
        List<Map<String, Object>> rows = new ArrayList<>();
        pageTraversal.forEachRemaining(v -> rows.add(vertexPickRow(v, schemaName)));

        return Map.of("total", total, "rows", rows, "page", page, "size", size);
    }

    private Map<String, Object> vertexPickRow(Vertex v, String schemaName) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(v.id()));
        m.put("label", v.label());
        m.put("schema", schemaName);
        Map<String, Object> props = extractProperties(v);
        m.put("propertySummary", buildSummary(props));
        return m;
    }

    // ==================== Gremlin 示例 ====================

    public List<Map<String, String>> generateGremlinExamples(String schemaName, String labelName) {
        List<Map<String, String>> examples = new ArrayList<>();
        examples.add(example("查询所有边 (limit 20)",
                "g.E().hasLabel('" + labelName + "').limit(20).valueMap(true)"));
        examples.add(example("统计边数量",
                "g.E().hasLabel('" + labelName + "').count()"));
        examples.add(example("查询边及出/入点信息",
                "g.E().hasLabel('" + labelName + "')\n" +
                        " .limit(20)\n" +
                        " .project('edge','outV','inV')\n" +
                        " .by(valueMap(true))\n" +
                        " .by(outV().valueMap(true))\n" +
                        " .by(inV().valueMap(true))"));
        examples.add(example("按边属性查询",
                "g.E().hasLabel('" + labelName + "').has('name','value').valueMap(true)"));
        examples.add(example("查询出点",
                "g.E().hasLabel('" + labelName + "').outV()"));
        examples.add(example("查询入点",
                "g.E().hasLabel('" + labelName + "').inV()"));
        examples.add(example("从某个出点查其出边",
                "g.V().hasLabel('OutLabel')\n" +
                        " .has('object_id','xxx')\n" +
                        " .outE('" + labelName + "')\n" +
                        " .valueMap(true)"));
        examples.add(example("从某个入点查其入边",
                "g.V().hasLabel('InLabel')\n" +
                        " .has('object_id','xxx')\n" +
                        " .inE('" + labelName + "')\n" +
                        " .valueMap(true)"));
        examples.add(example("新增边",
                "g.addE('" + labelName + "')\n" +
                        " .from(V().hasLabel('OutLabel').has('object_id','out_id'))\n" +
                        " .to(V().hasLabel('InLabel').has('object_id','in_id'))\n" +
                        " .property('name','value')"));
        examples.add(example("按 ID 删除边",
                "g.E('edge_id').drop().iterate()"));
        examples.add(example("清空该类型所有边",
                "g.E().hasLabel('" + labelName + "').drop().iterate()"));
        return examples;
    }

    // ==================== 缓存刷新 ====================

    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }

    // ==================== 内部工具 ====================

    private EdgeRowDto toRowDto(Edge e, String schemaName) {
        EdgeRowDto dto = new EdgeRowDto();
        dto.setId(String.valueOf(e.id()));
        dto.setLabel(e.label());
        dto.setSchema(schemaName);
        Vertex outV = e.outVertex();
        Vertex inV = e.inVertex();
        dto.setOutVertexId(String.valueOf(outV.id()));
        dto.setOutVertexLabel(outV.label());
        dto.setInVertexId(String.valueOf(inV.id()));
        dto.setInVertexLabel(inV.label());
        Map<String, Object> props = extractProperties(e);
        dto.setProperties(props);
        dto.setPropertySummary(buildSummary(props));
        return dto;
    }

    private String buildSummary(Map<String, Object> props) {
        if (props == null || props.isEmpty()) return "";
        return props.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .limit(3)
                .map(e -> e.getKey() + "=" + truncate(String.valueOf(e.getValue()), 30))
                .collect(Collectors.joining(", "));
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private Map<String, Object> extractProperties(Edge e) {
        Map<String, Object> props = new LinkedHashMap<>();
        e.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p)));
        return props;
    }

    private Map<String, Object> extractProperties(Vertex v) {
        Map<String, Object> props = new LinkedHashMap<>();
        v.properties().forEachRemaining(p -> props.put(p.key(), normalizeValue(p)));
        return props;
    }

    private Object normalizeValue(Property<Object> p) {
        Object val = p.value();
        if (val instanceof JsonNode) {
            return val.toString();
        }
        return val;
    }

    private Map<String, Object> convertPropertyValues(SqlgGraph graph, String schemaName,
                                                     String labelName, Map<String, Object> input) {
        if (input == null) return Map.of();
        EdgeLabel el = findEdgeLabel(graph, schemaName, labelName);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            if (entry.getValue() == null) continue;
            PropertyColumn pc = el.getProperties().get(entry.getKey());
            if (pc == null) {
                result.put(entry.getKey(), entry.getValue());
                continue;
            }
            String typeName = pc.getPropertyType() == null ? "" : pc.getPropertyType().name();
            Object value = entry.getValue();
            if ("JSON".equals(typeName) || "JSON_ARRAY".equals(typeName)) {
                if (value instanceof String s) {
                    try {
                        result.put(entry.getKey(), MAPPER.readTree(s));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("属性 " + entry.getKey() + " 不是合法 JSON: " + e.getMessage());
                    }
                } else {
                    result.put(entry.getKey(), value);
                }
            } else if ("BOOLEAN".equals(typeName)) {
                result.put(entry.getKey(), parseBoolean(value));
            } else if ("INTEGER".equals(typeName)) {
                result.put(entry.getKey(), Integer.valueOf(String.valueOf(value)));
            } else if ("LONG".equals(typeName)) {
                result.put(entry.getKey(), Long.valueOf(String.valueOf(value)));
            } else if ("FLOAT".equals(typeName) || "DOUBLE".equals(typeName)) {
                result.put(entry.getKey(), Double.valueOf(String.valueOf(value)));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private Boolean parseBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String s = String.valueOf(value).trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    private Edge findEdge(SqlgGraph graph, String edgeIdStr) {
        RecordId rid = RecordId.from(graph, edgeIdStr);
        Iterator<Edge> it = graph.edges(rid);
        if (!it.hasNext()) {
            throw new IllegalArgumentException("边不存在: " + edgeIdStr);
        }
        return it.next();
    }

    private Vertex findVertex(SqlgGraph graph, String vertexIdStr) {
        RecordId rid = RecordId.from(graph, vertexIdStr);
        Iterator<Vertex> it = graph.vertices(rid);
        if (!it.hasNext()) {
            throw new IllegalArgumentException("点不存在: " + vertexIdStr);
        }
        return it.next();
    }

    private EdgeLabel findEdgeLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getEdgeLabels().values().stream()
                .filter(el -> el.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "EdgeLabel 不存在: " + schemaName + "." + labelName));
    }

    private Schema getEdgeSchema(SqlgGraph graph, Edge e) {
        if (e.id() instanceof RecordId rid) {
            try {
                return rid.getSchemaTable() != null
                        ? graph.getTopology().getSchema(rid.getSchemaTable().getSchema()).orElse(null)
                        : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private Schema getVertexSchema(SqlgGraph graph, Vertex v) {
        if (v.id() instanceof RecordId rid) {
            try {
                return rid.getSchemaTable() != null
                        ? graph.getTopology().getSchema(rid.getSchemaTable().getSchema()).orElse(null)
                        : null;
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    private long countEdges(SqlgGraph graph, String schemaName, String labelName, Map<String, Object> filters) {
        boolean hasFilters = filters != null && filters.values().stream()
                .anyMatch(v -> v != null && !String.valueOf(v).isEmpty());
        if (!hasFilters) {
            String physicalTable = "E_" + labelName;
            try (Connection conn = graph.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT count(*) FROM \"" + schemaName + "\".\"" + physicalTable + "\"")) {
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0;
                }
            } catch (SQLException e) {
                log.debug("Count edges failed for {}.{}: {}", schemaName, labelName, e.getMessage());
                return 0;
            }
        }
        return collectEdges(graph, labelName, filters).size();
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

    private Map<String, String> example(String title, String code) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("code", code);
        return m;
    }
}
