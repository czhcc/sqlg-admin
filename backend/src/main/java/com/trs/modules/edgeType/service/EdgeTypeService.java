package com.trs.modules.edgeType.service;

import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.edgeType.dto.*;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.vertexType.dto.TableColumnInfo;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.PropertyDefinition;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.*;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 边类型管理服务,封装对 sqlg EdgeLabel 的列表、详情、新增、删除、清数据、
 * 底层表结构查看及 Gremlin/SQL 示例生成。
 *
 * @author czh
 * @date 2026/07/07
 */
@Service
public class EdgeTypeService {

    private static final Logger log = LoggerFactory.getLogger(EdgeTypeService.class);

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final OperationLogService logService;

    public EdgeTypeService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                            OperationLogService logService) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.logService = logService;
    }

    // ==================== 列表 ====================

    /**
     * 列出指定连接下所有 schema 中的 EdgeLabel 概要信息。
     *
     * @param connectionId 图数据库连接 ID
     * @return 按 schema、label 排序的列表
     */
    public List<EdgeTypeListDto> list(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<EdgeTypeListDto> result = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            for (EdgeLabel el : sortedEdgeLabels(schema)) {
                result.add(toListDto(graph, schema, el));
            }
        }
        return result;
    }

    // ==================== 详情 ====================

    /**
     * 获取单个 EdgeLabel 的详情,含属性、索引、出入点类型和边数据量。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    EdgeLabel 名称
     * @return 详情 DTO
     */
    public EdgeTypeDetailDto getDetail(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        EdgeLabel el = findEdgeLabel(graph, schemaName, labelName);

        EdgeTypeDetailDto dto = new EdgeTypeDetailDto();
        dto.setSchema(schemaName);
        dto.setLabel(el.getName());
        dto.setFullName(el.getFullName());
        dto.setTableName(buildTableName(schemaName, el.getName()));

        dto.setProperties(el.getProperties().values().stream()
                .map(p -> new EdgeTypeDetailDto.PropertyInfo(
                        p.getName(),
                        p.getPropertyType() == null ? "?" : p.getPropertyType().name()))
                .sorted(Comparator.comparing(p -> p.name))
                .collect(Collectors.toList()));

        dto.setIdentifiers(extractIdentifiers(el));

        dto.setIndexes(el.getIndexes().values().stream()
                .map(i -> new EdgeTypeDetailDto.IndexInfo(
                        i.getName(),
                        i.getIndexType() == null ? "?" : i.getIndexType().getName(),
                        i.getProperties().stream()
                                .map(PropertyColumn::getName)
                                .sorted()
                                .collect(Collectors.toList())))
                .sorted(Comparator.comparing(i -> i.name))
                .collect(Collectors.toList()));

        dto.setOutVertexLabels(el.getOutVertexLabels().stream()
                .map(v -> new EdgeTypeDetailDto.VertexBrief(
                        v.getName(),
                        v.getSchema() == null ? null : v.getSchema().getName(),
                        v.getFullName()))
                .sorted(Comparator.comparing(v -> v.fullName))
                .collect(Collectors.toList()));

        dto.setInVertexLabels(el.getInVertexLabels().stream()
                .map(v -> new EdgeTypeDetailDto.VertexBrief(
                        v.getName(),
                        v.getSchema() == null ? null : v.getSchema().getName(),
                        v.getFullName()))
                .sorted(Comparator.comparing(v -> v.fullName))
                .collect(Collectors.toList()));

        dto.setEdgeCount(countEdges(graph, schemaName, el.getName()));
        return dto;
    }

    // ==================== 新增 ====================

    /**
     * 创建新的 EdgeLabel,需指定出点和入点 VertexLabel。
     * <p>出/入点必须位于同一 schema,边表也将创建在该 schema 下。
     *
     * @param connectionId 图数据库连接 ID
     * @param req          请求体
     */
    public void create(Long connectionId, EdgeTypeSaveRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        if (req.getSchema() == null || req.getSchema().isBlank()) {
            throw new IllegalArgumentException("schema 不能为空");
        }
        if (req.getOutLabel() == null || req.getOutLabel().isBlank()) {
            throw new IllegalArgumentException("出点类型(outLabel)不能为空");
        }
        if (req.getInLabel() == null || req.getInLabel().isBlank()) {
            throw new IllegalArgumentException("入点类型(inLabel)不能为空");
        }

        SqlgGraph graph = registry.get(connectionId);
        VertexLabel outVL = findVertexLabel(graph, req.getSchema(), req.getOutLabel());
        VertexLabel inVL = findVertexLabel(graph, req.getSchema(), req.getInLabel());

        Schema schema = outVL.getSchema();

        // identifier 处理:无 identifier 走 sqlg 默认自增 BIGINT 主键;
        // 有 identifier 时将指定字段作为 STRING 属性并标记为业务主键
        List<String> identifiers = req.getIdentifiers() == null ? List.of()
                : req.getIdentifiers().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .toList();

        if (identifiers.isEmpty()) {
            schema.ensureEdgeLabelExist(req.getLabel(), outVL, inVL, Map.of());
        } else {
            Map<String, PropertyDefinition> propDefs = new LinkedHashMap<>();
            ListOrderedSet<String> ids = new ListOrderedSet<>();
            for (String idName : identifiers) {
                propDefs.put(idName, PropertyDefinition.of(PropertyType.STRING));
                ids.add(idName);
            }
            schema.ensureEdgeLabelExist(req.getLabel(), outVL, inVL, propDefs, ids);
        }

        graph.tx().commit();
        log.info("Created EdgeLabel: {} ({}.{}, out={}, in={}) identifiers={}",
                req.getLabel(), req.getSchema(), req.getInLabel(),
                outVL.getName(), inVL.getName(), identifiers);
        try {
            logService.log().module("边类型管理").action("创建 EdgeLabel").httpMethod("POST")
                .type("CREATE").name("创建边类型: " + req.getSchema() + "." + req.getLabel())
                .status("SUCCESS").connection(connectionId).schema(req.getSchema())
                .objectType("EdgeLabel").objectName(req.getSchema() + "." + req.getLabel()).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 清空边数据 ====================

    /**
     * 清空指定 EdgeLabel 的所有边数据,但保留表结构和 EdgeLabel 定义。
     * 对应 Gremlin: g.E().hasLabel('X').drop().iterate()
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    EdgeLabel 名称
     * @return 删除的边数量
     */
    public long clearEdges(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        findEdgeLabel(graph, schemaName, labelName);

        long count = countEdges(graph, schemaName, labelName);
        graph.traversal().E().hasLabel(labelName).drop().iterate();
        graph.tx().commit();

        log.info("Cleared {} edges from {}.{}", count, schemaName, labelName);
        try {
            logService.log().module("边类型管理").action("清空边数据").httpMethod("POST")
                .type("CLEAR").name("清空边数据: " + schemaName + "." + labelName).dangerous()
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("EdgeLabel").objectName(schemaName + "." + labelName)
                .affected((int) count).result("影响 " + count + " 条").submit();
        } catch (Exception ignored) {}
        return count;
    }

    // ==================== 删除边类型 ====================

    /**
     * 删除 EdgeLabel 及底层表,remove(false) 表示同时删除物理表 E_XXX。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    EdgeLabel 名称
     */
    public void delete(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        EdgeLabel el = findEdgeLabel(graph, schemaName, labelName);

        el.remove(false);
        graph.tx().commit();

        log.info("Deleted EdgeLabel: {}.{}", schemaName, labelName);
        try {
            logService.log().module("边类型管理").action("删除 EdgeLabel").httpMethod("DELETE")
                .type("DELETE").name("删除边类型: " + schemaName + "." + labelName).dangerous()
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("EdgeLabel").objectName(schemaName + "." + labelName).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 底层表结构 ====================

    /**
     * 通过 JDBC 查询底层物理表的列结构(information_schema.columns)。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    EdgeLabel 名称
     * @return 列信息列表
     */
    public List<TableColumnInfo> getTableSchema(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        String physicalTable = "E_" + labelName;

        List<TableColumnInfo> columns = new ArrayList<>();
        try (Connection conn = getConnection(graph);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name, data_type, is_nullable, column_default " +
                     "FROM information_schema.columns " +
                     "WHERE table_schema = ? AND table_name = ? " +
                     "ORDER BY ordinal_position")) {
            ps.setString(1, schemaName);
            ps.setString(2, physicalTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(new TableColumnInfo(
                            rs.getString("column_name"),
                            rs.getString("data_type"),
                            "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            rs.getString("column_default")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("查询表结构失败: " + e.getMessage(), e);
        }
        return columns;
    }

    // ==================== 点类型下拉(供新增边表单使用) ====================

    /**
     * 列出指定连接下所有 VertexLabel,用于新增边类型时选择出/入点。
     *
     * @param connectionId 图数据库连接 ID
     * @return 按 schema、label 排序的列表,每项含 schema/label/fullName
     */
    public List<Map<String, Object>> listVertexLabels(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            for (VertexLabel vl : sortedVertexLabels(schema)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("schema", schema.getName());
                m.put("label", vl.getName());
                m.put("fullName", vl.getFullName());
                result.add(m);
            }
        }
        return result;
    }

    // ==================== Gremlin 示例 ====================

    /**
     * 根据边类型生成常用 Gremlin 查询示例。
     *
     * @param schemaName schema 名称
     * @param labelName  EdgeLabel 名称
     * @return Gremlin 示例列表
     */
    public List<Map<String, String>> generateGremlinExamples(String schemaName, String labelName) {
        List<Map<String, String>> examples = new ArrayList<>();

        examples.add(example("查询所有边 (limit 10)",
                "g.E().hasLabel('" + labelName + "').limit(10)"));
        examples.add(example("统计边数量",
                "g.E().hasLabel('" + labelName + "').count()"));
        examples.add(example("查看边属性",
                "g.E().hasLabel('" + labelName + "').valueMap()"));
        examples.add(example("查询出顶点",
                "g.E().hasLabel('" + labelName + "').outV()"));
        examples.add(example("查询入顶点",
                "g.E().hasLabel('" + labelName + "').inV()"));
        examples.add(example("按属性查询",
                "g.E().hasLabel('" + labelName + "').has('name','value')"));
        examples.add(example("查询顶点的出边",
                "g.V().hasLabel('OutLabel').outE('" + labelName + "')"));
        examples.add(example("查询顶点的入边",
                "g.V().hasLabel('InLabel').inE('" + labelName + "')"));
        examples.add(example("新增边",
                "g.addE('" + labelName + "')\n" +
                        " .from(V().hasLabel('OutLabel'))\n" +
                        " .to(V().hasLabel('InLabel'))\n" +
                        " .property('name','value')"));
        examples.add(example("删除边",
                "g.E().hasLabel('" + labelName + "').drop()"));

        return examples;
    }

    // ==================== SQL 示例 ====================

    /**
     * 根据边类型生成对应 PostgreSQL SQL 示例。
     *
     * @param schemaName schema 名称
     * @param labelName  EdgeLabel 名称
     * @return SQL 示例列表
     */
    public List<Map<String, String>> generateSqlExamples(String schemaName, String labelName) {
        String table = schemaName + ".\"E_" + labelName + "\"";
        List<Map<String, String>> examples = new ArrayList<>();

        examples.add(example("查询前 10 行",
                "SELECT * FROM " + table + " LIMIT 10;"));
        examples.add(example("统计行数",
                "SELECT count(*) FROM " + table + ";"));
        examples.add(example("按列查询",
                "SELECT * FROM " + table + " WHERE \"name\" = 'value';"));
        examples.add(example("查看表结构",
                "\\d " + table));
        examples.add(example("查看索引",
                "SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = '" + schemaName +
                        "' AND tablename = 'E_" + labelName + "';"));
        examples.add(example("清空表数据",
                "TRUNCATE " + table + ";"));
        examples.add(example("删除表",
                "DROP TABLE " + table + " CASCADE;"));

        return examples;
    }

    // ==================== 连接列表 ====================

    /**
     * 获取可用的连接列表(已启用),用于前端下拉。
     *
     * @return 连接简要信息列表
     */
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

    // ==================== 内部工具 ====================

    private EdgeLabel findEdgeLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getEdgeLabels().values().stream()
                .filter(el -> el.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "EdgeLabel 不存在: " + schemaName + "." + labelName));
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

    private EdgeTypeListDto toListDto(SqlgGraph graph, Schema schema, EdgeLabel el) {
        EdgeTypeListDto dto = new EdgeTypeListDto();
        dto.setSchema(schema.getName());
        dto.setLabel(el.getName());
        dto.setFullName(el.getFullName());
        dto.setTableName(buildTableName(schema.getName(), el.getName()));
        dto.setPropertyCount(el.getProperties().size());
        dto.setIdentifiers(extractIdentifiers(el));
        dto.setIndexCount(el.getIndexes().size());
        dto.setOutVertexLabels(el.getOutVertexLabels().stream()
                .map(AbstractLabel::getFullName)
                .sorted()
                .collect(Collectors.toList()));
        dto.setInVertexLabels(el.getInVertexLabels().stream()
                .map(AbstractLabel::getFullName)
                .sorted()
                .collect(Collectors.toList()));
        dto.setEdgeCount(countEdges(graph, schema.getName(), el.getName()));
        return dto;
    }

    /**
     * 统计边数量 — 直接走 SQL count,避免 Gremlin 遍历开销。
     * 表不存在时返回 0。
     */
    private long countEdges(SqlgGraph graph, String schemaName, String labelName) {
        String physicalTable = "E_" + labelName;
        try (Connection conn = getConnection(graph);
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

    private Connection getConnection(SqlgGraph graph) throws SQLException {
        return graph.getConnection();
    }

    private List<String> extractIdentifiers(AbstractLabel label) {
        try {
            var ids = label.getIdentifiers();
            return ids == null || ids.isEmpty()
                    ? List.of()
                    : new ArrayList<>(ids);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildTableName(String schema, String label) {
        return schema + ".\"E_" + label + "\"";
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

    private List<EdgeLabel> sortedEdgeLabels(Schema schema) {
        return schema.getEdgeLabels().values().stream()
                .sorted(Comparator.comparing(AbstractLabel::getName))
                .collect(Collectors.toList());
    }

    private List<VertexLabel> sortedVertexLabels(Schema schema) {
        return schema.getVertexLabels().values().stream()
                .sorted(Comparator.comparing(AbstractLabel::getName))
                .collect(Collectors.toList());
    }

    private Map<String, String> example(String title, String code) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("code", code);
        return m;
    }
}
