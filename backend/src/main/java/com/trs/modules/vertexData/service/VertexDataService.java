package com.trs.modules.vertexData.service;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.vertexData.dto.VertexDetailDto;
import com.trs.modules.vertexData.dto.VertexRowDto;
import com.trs.modules.vertexData.dto.VertexSaveRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 点数据管理服务,封装 Vertex 实例数据的分页查询、详情、新增、编辑、删除和清空。
 *
 * @author czh
 * @date 2026/07/08
 */
@Service
public class VertexDataService {

    private static final Logger log = LoggerFactory.getLogger(VertexDataService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_PAGE_SIZE = 1000;
    private static final int EXPORT_MAX_ROWS = 10000;

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;

    public VertexDataService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper) {
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

    // ==================== 拓扑树 ====================

    public Map<String, Object> getTree(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<Map<String, Object>> schemas = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("name", schema.getName());
            sMap.put("vertexLabels", schema.getVertexLabels().values().stream()
                    .map(AbstractLabel::getName)
                    .sorted()
                    .collect(Collectors.toList()));
            schemas.add(sMap);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", schemas);
        return result;
    }

    // ==================== VertexLabel 属性定义(供前端生成表单) ====================

    /**
     * 获取指定 VertexLabel 的属性定义,用于新增/编辑表单。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 属性列表 [{name, type, identifier}]
     */
    public List<Map<String, Object>> getVertexLabelProperties(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        Set<String> identifiers = extractIdentifiers(vl);

        return vl.getProperties().values().stream()
                .map(pc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", pc.getName());
                    m.put("type", pc.getPropertyType() == null ? "?" : pc.getPropertyType().name());
                    m.put("identifier", identifiers.contains(pc.getName()));
                    return m;
                })
                .sorted(Comparator.comparing(m -> (String) m.get("name")))
                .collect(Collectors.toList());
    }

    // ==================== 分页查询点数据 ====================

    /**
     * 分页查询指定 VertexLabel 下的点数据,支持按属性值过滤。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @param page         页码(从 1 开始)
     * @param size         每页条数
     * @param filters      属性过滤条件 {propName: value}
     * @return 分页结果
     */
    public Map<String, Object> page(Long connectionId, String schemaName, String labelName,
                                    int page, int size, Map<String, Object> filters) {
        SqlgGraph graph = registry.get(connectionId);
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        int offset = (page - 1) * size;

        long total = countVertices(graph, schemaName, labelName, filters);

        var traversal = graph.traversal().V().hasLabel(labelName);
        if (filters != null && !filters.isEmpty()) {
            for (var entry : filters.entrySet()) {
                if (entry.getValue() != null && !String.valueOf(entry.getValue()).isEmpty()) {
                    traversal = traversal.has(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        var resultTraversal = traversal.range(offset, offset + size);

        List<VertexRowDto> rows = new ArrayList<>();
        resultTraversal.forEachRemaining(v -> rows.add(toRowDto(v, schemaName)));

        return Map.of("total", total, "rows", rows, "page", page, "size", size);
    }

    // ==================== 点详情 ====================

    /**
     * 获取单个点的详情,含完整属性、关联边和邻接点。
     *
     * @param connectionId 图数据库连接 ID
     * @param vertexIdStr  点 ID 字符串
     * @return 点详情 DTO
     */
    public VertexDetailDto getDetail(Long connectionId, String vertexIdStr) {
        SqlgGraph graph = registry.get(connectionId);
        Vertex v = findVertex(graph, vertexIdStr);

        VertexDetailDto dto = new VertexDetailDto();
        dto.setId(vertexIdStr);
        dto.setLabel(v.label());
        Schema schema = getVertexSchema(graph, v);
        dto.setSchema(schema != null ? schema.getName() : null);
        dto.setProperties(extractProperties(v));

        List<Map<String, Object>> outEdges = new ArrayList<>();
        List<Map<String, Object>> inEdges = new ArrayList<>();
        Set<String> adjacentIds = new LinkedHashSet<>();

        v.edges(Direction.OUT).forEachRemaining(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(e.id()));
            m.put("label", e.label());
            Vertex inV = e.inVertex();
            m.put("inVertexId", String.valueOf(inV.id()));
            m.put("inVertexLabel", inV.label());
            Map<String, Object> props = new LinkedHashMap<>();
            e.properties().forEachRemaining(p -> props.put(p.key(), p.value()));
            m.put("properties", props);
            outEdges.add(m);
            adjacentIds.add(String.valueOf(inV.id()));
        });

        v.edges(Direction.IN).forEachRemaining(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(e.id()));
            m.put("label", e.label());
            Vertex outV = e.outVertex();
            m.put("outVertexId", String.valueOf(outV.id()));
            m.put("outVertexLabel", outV.label());
            Map<String, Object> props = new LinkedHashMap<>();
            e.properties().forEachRemaining(p -> props.put(p.key(), p.value()));
            m.put("properties", props);
            inEdges.add(m);
            adjacentIds.add(String.valueOf(outV.id()));
        });

        dto.setOutEdges(outEdges);
        dto.setInEdges(inEdges);

        List<Map<String, Object>> adjacent = new ArrayList<>();
        for (String adjId : adjacentIds) {
            try {
                Vertex adj = findVertex(graph, adjId);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", adjId);
                m.put("label", adj.label());
                Schema adjSchema = getVertexSchema(graph, adj);
                m.put("schema", adjSchema != null ? adjSchema.getName() : null);
                m.put("properties", extractProperties(adj));
                adjacent.add(m);
            } catch (Exception ignored) {}
        }
        dto.setAdjacentVertices(adjacent);

        return dto;
    }

    // ==================== 新增点 ====================

    /**
     * 向指定 VertexLabel 新增一个点。
     * JSON 类型属性值会转换为 JsonNode。
     *
     * @param connectionId 图数据库连接 ID
     * @param req          请求体
     */
    public void create(Long connectionId, VertexSaveRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        SqlgGraph graph = registry.get(connectionId);
        String schemaName = req.getSchema() != null ? req.getSchema() : "public";
        VertexLabel vl = findVertexLabel(graph, schemaName, req.getLabel());

        Map<String, Object> converted = convertPropertyValues(graph, schemaName, req.getLabel(), req.getProperties());

        // VertexLabel 带自定义 identifier 时,必须用 addVertex(label, map) 一次性传入,
        // 因为 identifier 在创建时即作为主键写入,不能用 property() 后置设置。
        // sqlg 的 addVertex(label, map) 内部会从 map 提取 identifier 值。
        Set<String> identifiers = extractIdentifiers(vl);

        Vertex v;
        if (identifiers.isEmpty()) {
            v = graph.addVertex(req.getLabel());
            for (var entry : converted.entrySet()) {
                v.property(entry.getKey(), entry.getValue());
            }
        } else {
            // 校验 identifier 值是否齐全
            for (String idName : identifiers) {
                if (!converted.containsKey(idName) || converted.get(idName) == null) {
                    throw new IllegalArgumentException("缺少 identifier 属性值: " + idName);
                }
            }
            // addVertex(String, Map) 内部走 addVertex(Object...),需要 T.label + 全部属性
            v = graph.addVertex(req.getLabel(), converted);
        }
        graph.tx().commit();
        log.info("Created vertex in {}.{} with properties {}", schemaName, req.getLabel(), converted.keySet());
    }

    // ==================== 编辑点 ====================

    /**
     * 编辑指定点的属性值(整体覆盖传入的属性)。
     *
     * @param connectionId 图数据库连接 ID
     * @param vertexIdStr  点 ID 字符串
     * @param req          请求体(包含要更新的属性)
     */
    public void update(Long connectionId, String vertexIdStr, VertexSaveRequest req) {
        SqlgGraph graph = registry.get(connectionId);
        Vertex v = findVertex(graph, vertexIdStr);
        String schemaName = req.getSchema() != null ? req.getSchema() : getVertexSchema(graph, v).getName();
        String labelName = v.label();

        Map<String, Object> converted = convertPropertyValues(graph, schemaName, labelName, req.getProperties());

        for (var entry : converted.entrySet()) {
            v.property(entry.getKey(), entry.getValue());
        }
        graph.tx().commit();
        log.info("Updated vertex {}: {}", vertexIdStr, converted.keySet());
    }

    // ==================== 删除单个点 ====================

    /**
     * 删除指定的点实例。
     *
     * @param connectionId 图数据库连接 ID
     * @param vertexIdStr  点 ID 字符串
     */
    public void delete(Long connectionId, String vertexIdStr) {
        SqlgGraph graph = registry.get(connectionId);
        Vertex v = findVertex(graph, vertexIdStr);
        Object id = v.id();
        v.remove();
        graph.tx().commit();
        log.info("Deleted vertex {}", vertexIdStr);
    }

    // ==================== 批量删除 ====================

    /**
     * 批量删除多个点。
     *
     * @param connectionId 图数据库连接 ID
     * @param vertexIds    点 ID 字符串列表
     * @return 删除数量
     */
    public int batchDelete(Long connectionId, List<String> vertexIds) {
        if (vertexIds == null || vertexIds.isEmpty()) return 0;
        if (vertexIds.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("批量删除不能超过 " + MAX_PAGE_SIZE + " 条");
        }
        SqlgGraph graph = registry.get(connectionId);
        int count = 0;
        for (String idStr : vertexIds) {
            try {
                Vertex v = findVertex(graph, idStr);
                v.remove();
                count++;
            } catch (Exception e) {
                log.warn("Failed to delete vertex {}: {}", idStr, e.getMessage());
            }
        }
        graph.tx().commit();
        log.info("Batch deleted {} vertices", count);
        return count;
    }

    // ==================== 清空点数据 ====================

    /**
     * 清空指定 VertexLabel 下的所有点实例,保留 VertexLabel 定义和底层表。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 删除的点数量
     */
    public long clearVertices(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        long count = countVertices(graph, schemaName, labelName, null);
        // 关闭 Gremlin 查询事务,确保后续 JDBC 操作拿到干净的连接
        graph.tx().rollback();
        // 用 graph 自身的 JDBC 连接执行 DELETE FROM,通过 sqlg tx() 提交。
        // 不用 TRUNCATE — TRUNCATE 需要 ACCESS EXCLUSIVE 锁,会与 sqlg 内部操作死锁。
        // 不用独立 DriverManager 连接 — 会与 Hikari 连接池竞争,触发 I/O 错误。
        String physicalTable = "V_" + labelName;
        try {
            Connection conn = graph.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM \"" + schemaName + "\".\"" + physicalTable + "\"")) {
                ps.executeUpdate();
            }
            graph.tx().commit();
        } catch (SQLException e) {
            graph.tx().rollback();
            throw new IllegalStateException("清空点数据失败: " + e.getMessage(), e);
        }
        // 操作后 sqlg 的 vertex ID 缓存可能失效,evict 重建
        registry.evict(connectionId);
        log.info("Cleared {} vertices from {}.{}", count, schemaName, labelName);
        return count;
    }

    // ==================== 数据导出 ====================

    /**
     * 导出指定 VertexLabel 的点数据。
     * <p>导出全部匹配数据(不限分页),上限 EXPORT_MAX_ROWS 条。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @param filters      属性过滤条件
     * @param format       导出格式: csv / json / excel
     * @return 导出结果 {headers, rows, format}
     */
    public Map<String, Object> exportVertices(Long connectionId, String schemaName, String labelName,
                                              Map<String, Object> filters, String format) {
        SqlgGraph graph = registry.get(connectionId);
        var traversal = graph.traversal().V().hasLabel(labelName);
        if (filters != null && !filters.isEmpty()) {
            for (var entry : filters.entrySet()) {
                if (entry.getValue() != null && !String.valueOf(entry.getValue()).isEmpty()) {
                    traversal = traversal.has(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        var limited = traversal.limit(EXPORT_MAX_ROWS);

        List<VertexRowDto> rows = new ArrayList<>();
        limited.forEachRemaining(v -> rows.add(toRowDto(v, schemaName)));

        // 收集所有出现过的属性名作为列(保持稳定顺序)
        LinkedHashSet<String> propNames = new LinkedHashSet<>();
        for (VertexRowDto row : rows) {
            if (row.getProperties() != null) propNames.addAll(row.getProperties().keySet());
        }

        List<String> headers = new ArrayList<>();
        headers.add("id");
        headers.add("schema");
        headers.add("label");
        headers.addAll(propNames);

        List<List<String>> dataRows = new ArrayList<>();
        for (VertexRowDto row : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(row.getId());
            cells.add(row.getSchema());
            cells.add(row.getLabel());
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
        // UTF-8 BOM 让 Excel 正确识别编码
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

    /**
     * 使用 Apache POI 生成真正的 .xlsx 文件。
     */
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
            // 表头行
            var headerRow = sheet.createRow(rowIdx++);
            for (int c = 0; c < headers.size(); c++) {
                var cell = headerRow.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(headerStyle);
            }
            // 数据行
            for (List<String> dataRow : rows) {
                var row = sheet.createRow(rowIdx++);
                for (int c = 0; c < dataRow.size(); c++) {
                    row.createCell(c).setCellValue(dataRow.get(c));
                }
            }
            // 自动列宽
            for (int c = 0; c < headers.size(); c++) sheet.autoSizeColumn(c);

            var baos = new java.io.ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("生成 Excel 失败: " + e.getMessage(), e);
        }
    }

    // ==================== Gremlin 示例 ====================

    public List<Map<String, String>> generateGremlinExamples(String schemaName, String labelName) {
        List<Map<String, String>> examples = new ArrayList<>();
        examples.add(example("查询所有点 (limit 20)",
                "g.V().hasLabel('" + labelName + "').limit(20).valueMap(true)"));
        examples.add(example("统计点数量",
                "g.V().hasLabel('" + labelName + "').count()"));
        examples.add(example("按属性查询",
                "g.V().hasLabel('" + labelName + "').has('name','value').valueMap(true)"));
        examples.add(example("新增点",
                "g.addV('" + labelName + "').property('name','value')"));
        examples.add(example("删除点",
                "g.V().hasLabel('" + labelName + "').has('name','value').drop()"));
        examples.add(example("查看邻接点",
                "g.V().hasLabel('" + labelName + "').both()"));
        examples.add(example("查看关联边",
                "g.V().hasLabel('" + labelName + "').bothE()"));
        return examples;
    }

    // ==================== 缓存刷新 ====================

    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }

    // ==================== 内部工具 ====================

    private VertexRowDto toRowDto(Vertex v, String schemaName) {
        VertexRowDto dto = new VertexRowDto();
        dto.setId(String.valueOf(v.id()));
        dto.setLabel(v.label());
        dto.setSchema(schemaName);
        Map<String, Object> props = extractProperties(v);
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

    private Map<String, Object> extractProperties(Vertex v) {
        Map<String, Object> props = new LinkedHashMap<>();
        v.properties().forEachRemaining(p -> {
            Object val = p.value();
            if (val instanceof JsonNode) {
                props.put(p.key(), val.toString());
            } else {
                props.put(p.key(), val);
            }
        });
        return props;
    }

    /**
     * 将前端传来的属性值转换为 sqlg 期望的 Java 类型。
     * JSON 类型属性的字符串值会被解析为 JsonNode。
     */
    private Map<String, Object> convertPropertyValues(SqlgGraph graph, String schemaName,
                                                       String labelName, Map<String, Object> input) {
        if (input == null) return Map.of();
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : input.entrySet()) {
            if (entry.getValue() == null) continue;
            PropertyColumn pc = vl.getProperties().get(entry.getKey());
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

    private Vertex findVertex(SqlgGraph graph, String vertexIdStr) {
        // 带 identifier 的点 ID 形如 public.Phone:::[id2],必须用 from(graph, id) 解析
        RecordId rid = RecordId.from(graph, vertexIdStr);
        Iterator<Vertex> it = graph.vertices(rid);
        if (!it.hasNext()) {
            throw new IllegalArgumentException("点不存在: " + vertexIdStr);
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

    private Schema getVertexSchema(SqlgGraph graph, Vertex v) {
        if (v.id() instanceof RecordId rid) {
            try {
                return rid.getSchemaTable() != null
                        ? graph.getTopology().getSchema(rid.getSchemaTable().getSchema()).orElse(null)
                        : null;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private Set<String> extractIdentifiers(AbstractLabel label) {
        try {
            var ids = label.getIdentifiers();
            return ids == null || ids.isEmpty() ? Set.of() : new HashSet<>(ids);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private long countVertices(SqlgGraph graph, String schemaName, String labelName, Map<String, Object> filters) {
        var traversal = graph.traversal().V().hasLabel(labelName);
        if (filters != null && !filters.isEmpty()) {
            for (var entry : filters.entrySet()) {
                if (entry.getValue() != null && !String.valueOf(entry.getValue()).isEmpty()) {
                    traversal = traversal.has(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        }
        return traversal.count().next();
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
