package com.trs.modules.vertexType.service;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.vertexType.dto.*;
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
 * 点类型管理服务,封装对 sqlg VertexLabel 的列表、详情、增删改、清数据、
 * 关联边查询、底层表结构查看及 Gremlin/SQL 示例生成。
 *
 * @author czh
 * @date 2026/07/07
 */
@Service
public class VertexTypeService {

    private static final Logger log = LoggerFactory.getLogger(VertexTypeService.class);

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final OperationLogService logService;
    private final ConnectionVisibilityHelper connectionVisibilityHelper;

    public VertexTypeService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                              OperationLogService logService,
                              ConnectionVisibilityHelper connectionVisibilityHelper) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.logService = logService;
        this.connectionVisibilityHelper = connectionVisibilityHelper;
    }

    // ==================== 列表 ====================

    /**
     * 列出指定连接下所有 schema 中的 VertexLabel 概要信息。
     *
     * @param connectionId 图数据库连接 ID
     * @return 按 schema、label 排序的列表
     */
    public List<VertexTypeListDto> list(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        List<VertexTypeListDto> result = new ArrayList<>();
        for (Schema schema : sortedSchemas(topology)) {
            for (VertexLabel vl : sortedVertexLabels(schema)) {
                result.add(toListDto(graph, schema, vl));
            }
        }
        return result;
    }

    // ==================== 详情 ====================

    /**
     * 获取单个 VertexLabel 的详情,含属性、索引、关联边类型和点数据量。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 详情 DTO
     */
    public VertexTypeDetailDto getDetail(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        VertexTypeDetailDto dto = new VertexTypeDetailDto();
        dto.setSchema(schemaName);
        dto.setLabel(vl.getName());
        dto.setFullName(vl.getFullName());
        dto.setTableName(buildTableName(schemaName, vl.getName()));

        dto.setProperties(vl.getProperties().values().stream()
                .map(p -> new VertexTypeDetailDto.PropertyInfo(
                        p.getName(),
                        p.getPropertyType() == null ? "?" : p.getPropertyType().name()))
                .sorted(Comparator.comparing(p -> p.name))
                .collect(Collectors.toList()));

        dto.setIdentifiers(extractIdentifiers(vl));

        dto.setIndexes(vl.getIndexes().values().stream()
                .map(i -> new VertexTypeDetailDto.IndexInfo(
                        i.getName(),
                        i.getIndexType() == null ? "?" : i.getIndexType().getName(),
                        i.getProperties().stream()
                                .map(PropertyColumn::getName)
                                .sorted()
                                .collect(Collectors.toList())))
                .sorted(Comparator.comparing(i -> i.name))
                .collect(Collectors.toList()));

        dto.setInEdgeLabels(vl.getInEdgeLabels().values().stream()
                .map(e -> new VertexTypeDetailDto.EdgeBrief(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .sorted(Comparator.comparing(e -> e.fullName))
                .collect(Collectors.toList()));

        dto.setOutEdgeLabels(vl.getOutEdgeLabels().values().stream()
                .map(e -> new VertexTypeDetailDto.EdgeBrief(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .sorted(Comparator.comparing(e -> e.fullName))
                .collect(Collectors.toList()));

        dto.setVertexCount(countVertices(graph, schemaName, vl.getName()));
        return dto;
    }

    // ==================== 新增 ====================

    /**
     * 创建新的 VertexLabel。
     * <p>两种 ID 策略:
     * <ul>
     *   <li>无 identifier:使用 sqlg 默认自增 BIGINT 主键</li>
     *   <li>指定 identifier:将指定字段作为 STRING 属性并标记为业务主键</li>
     * </ul>
     *
     * @param connectionId 图数据库连接 ID
     * @param req          请求体(schema/label/identifiers)
     */
    public void create(Long connectionId, VertexTypeSaveRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        SqlgGraph graph = registry.get(connectionId);

        String schemaName = req.getSchema() != null ? req.getSchema() : "public";
        Schema schema = graph.getTopology().ensureSchemaExist(schemaName);

        List<String> identifiers = req.getIdentifiers() == null ? List.of()
                : req.getIdentifiers().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .toList();

        if (identifiers.isEmpty()) {
            schema.ensureVertexLabelExist(req.getLabel());
        } else {
            Map<String, PropertyDefinition> propDefs = new LinkedHashMap<>();
            ListOrderedSet<String> ids = new ListOrderedSet<>();
            for (String idName : identifiers) {
                propDefs.put(idName, PropertyDefinition.of(PropertyType.STRING));
                ids.add(idName);
            }
            schema.ensureVertexLabelExist(req.getLabel(), propDefs, ids);
        }

        graph.tx().commit();
        log.info("Created VertexLabel: {}.{} (identifiers={})", schemaName, req.getLabel(), identifiers);
        try {
            logService.log().module("点类型管理").action("创建 VertexLabel").httpMethod("POST")
                .type("CREATE").name("创建点类型: " + schemaName + "." + req.getLabel())
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("VertexLabel").objectName(schemaName + "." + req.getLabel()).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 编辑 ====================

    /**
     * 编辑现有 VertexLabel — 支持重命名(调用 VertexLabel.rename)。
     * 属性的增删改不在此处处理,由属性管理负责。
     *
     * @param connectionId 图数据库连接 ID
     * @param req          请求体(需含 originalSchema/originalLabel 定位)
     */
    public void update(Long connectionId, VertexTypeSaveRequest req) {
        if (req.getLabel() == null || req.getLabel().isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        SqlgGraph graph = registry.get(connectionId);

        String origSchema = req.getOriginalSchema() != null ? req.getOriginalSchema() : req.getSchema();
        String origLabel = req.getOriginalLabel() != null ? req.getOriginalLabel() : req.getLabel();
        VertexLabel vl = findVertexLabel(graph, origSchema, origLabel);

        String newLabel = req.getLabel().trim();
        if (!newLabel.equals(origLabel)) {
            // Schema 必须与原 schema 一致(sqlg 不支持跨 schema 重命名)
            if (req.getSchema() != null && !req.getSchema().equals(origSchema)) {
                throw new IllegalArgumentException("不支持修改 schema,只能重命名 label");
            }
            vl.rename(newLabel);
        }

        graph.tx().commit();
        log.info("Renamed VertexLabel: {}.{} -> {}.{}", origSchema, origLabel, origSchema, newLabel);
        try {
            logService.log().module("点类型管理").action("重命名 VertexLabel").httpMethod("PUT")
                .type("RENAME").name("重命名点类型: " + origSchema + "." + origLabel + " → " + origSchema + "." + newLabel)
                .status("SUCCESS").connection(connectionId).schema(origSchema)
                .objectType("VertexLabel").objectName(origSchema + "." + origLabel).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 清空点数据 ====================

    /**
     * 清空指定 VertexLabel 的所有点数据,但保留表结构和 VertexLabel 定义。
     * 对应 Gremlin: g.V().hasLabel('X').drop().iterate()
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 删除的点数量
     */
    public long clearVertices(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        long count = countVertices(graph, schemaName, labelName);
        graph.traversal().V().hasLabel(labelName).drop().iterate();
        graph.tx().commit();

        log.info("Cleared {} vertices from {}.{}", count, schemaName, labelName);
        try {
            logService.log().module("点类型管理").action("清空点数据").httpMethod("POST")
                .type("CLEAR").name("清空点数据: " + schemaName + "." + labelName).dangerous()
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("VertexLabel").objectName(schemaName + "." + labelName)
                .affected((int) count).result("影响 " + count + " 条").submit();
        } catch (Exception ignored) {}
        return count;
    }

    // ==================== 删除点类型 ====================

    /**
     * 删除 VertexLabel 及底层表,remove(false) 表示同时删除物理表 V_XXX。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     */
    public void delete(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        vl.remove(false);
        graph.tx().commit();

        log.info("Deleted VertexLabel: {}.{}", schemaName, labelName);
        try {
            logService.log().module("点类型管理").action("删除 VertexLabel").httpMethod("DELETE")
                .type("DELETE").name("删除点类型: " + schemaName + "." + labelName).dangerous()
                .status("SUCCESS").connection(connectionId).schema(schemaName)
                .objectType("VertexLabel").objectName(schemaName + "." + labelName).submit();
        } catch (Exception ignored) {}
    }

    // ==================== 关联边类型 ====================

    /**
     * 获取与该 VertexLabel 关联的所有 EdgeLabel(入边 + 出边)。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 关联边类型列表
     */
    public Map<String, List<VertexTypeDetailDto.EdgeBrief>> getRelatedEdges(
            Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, schemaName, labelName);

        List<VertexTypeDetailDto.EdgeBrief> inEdges = vl.getInEdgeLabels().values().stream()
                .map(e -> new VertexTypeDetailDto.EdgeBrief(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .sorted(Comparator.comparing(e -> e.fullName))
                .collect(Collectors.toList());

        List<VertexTypeDetailDto.EdgeBrief> outEdges = vl.getOutEdgeLabels().values().stream()
                .map(e -> new VertexTypeDetailDto.EdgeBrief(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .sorted(Comparator.comparing(e -> e.fullName))
                .collect(Collectors.toList());

        Map<String, List<VertexTypeDetailDto.EdgeBrief>> result = new LinkedHashMap<>();
        result.put("inEdgeLabels", inEdges);
        result.put("outEdgeLabels", outEdges);
        return result;
    }

    // ==================== 底层表结构 ====================

    /**
     * 通过 JDBC 查询底层物理表的列结构(information_schema.columns)。
     *
     * @param connectionId 图数据库连接 ID
     * @param schemaName   schema 名称
     * @param labelName    VertexLabel 名称
     * @return 列信息列表
     */
    public List<TableColumnInfo> getTableSchema(Long connectionId, String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        String tableName = buildTableName(schemaName, labelName);
        String physicalTable = "V_" + labelName;

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

    // ==================== Gremlin 示例 ====================

    /**
     * 根据点类型生成常用 Gremlin 查询示例。
     *
     * @param schemaName schema 名称
     * @param labelName  VertexLabel 名称
     * @return Gremlin 示例列表
     */
    public List<Map<String, String>> generateGremlinExamples(String schemaName, String labelName) {
        String full = schemaName + "." + labelName;
        List<Map<String, String>> examples = new ArrayList<>();

        examples.add(example("查询所有点 (limit 10)",
                "g.V().hasLabel('" + labelName + "').limit(10)"));
        examples.add(example("统计点数量",
                "g.V().hasLabel('" + labelName + "').count()"));
        examples.add(example("按属性查询",
                "g.V().hasLabel('" + labelName + "').has('name','value')"));
        examples.add(example("查看点属性",
                "g.V().hasLabel('" + labelName + "').valueMap()"));
        examples.add(example("查邻居 (出边)",
                "g.V().hasLabel('" + labelName + "').out()"));
        examples.add(example("查邻居 (入边)",
                "g.V().hasLabel('" + labelName + "').in()"));
        examples.add(example("新增点",
                "g.addV('" + labelName + "').property('name','value')"));
        examples.add(example("删除点",
                "g.V().hasLabel('" + labelName + "').drop()"));

        return examples;
    }

    // ==================== SQL 示例 ====================

    /**
     * 根据点类型生成对应 PostgreSQL SQL 示例。
     *
     * @param schemaName schema 名称
     * @param labelName  VertexLabel 名称
     * @return SQL 示例列表
     */
    public List<Map<String, String>> generateSqlExamples(String schemaName, String labelName) {
        String table = schemaName + ".\"V_" + labelName + "\"";
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
                        "' AND tablename = 'V_" + labelName + "';"));
        examples.add(example("清空表数据",
                "TRUNCATE " + table + ";"));
        examples.add(example("删除表",
                "DROP TABLE " + table + " CASCADE;"));

        return examples;
    }

    // ==================== 连接列表 ====================

    /**
     * 获取可用于 Topology 的连接列表(已启用),用于前端下拉。
     *
     * @return 连接简要信息列表
     */
    public List<Map<String, Object>> listConnections() {
        return connectionVisibilityHelper.listConnectionDtosForCurrentUser();
    }

    // ==================== 内部工具 ====================

    private VertexLabel findVertexLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getVertexLabels().values().stream()
                .filter(vl -> vl.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "VertexLabel 不存在: " + schemaName + "." + labelName));
    }

    private VertexTypeListDto toListDto(SqlgGraph graph, Schema schema, VertexLabel vl) {
        VertexTypeListDto dto = new VertexTypeListDto();
        dto.setSchema(schema.getName());
        dto.setLabel(vl.getName());
        dto.setFullName(vl.getFullName());
        dto.setTableName(buildTableName(schema.getName(), vl.getName()));
        dto.setPropertyCount(vl.getProperties().size());
        dto.setIdentifiers(extractIdentifiers(vl));
        dto.setIndexCount(vl.getIndexes().size());
        dto.setVertexCount(countVertices(graph, schema.getName(), vl.getName()));
        dto.setStatus(vl.getProperties().isEmpty() ? "empty" : "active");
        return dto;
    }

    /**
     * 统计点数量 — 直接走 SQL count,避免 Gremlin 遍历开销。
     * 表不存在时返回 0。
     */
    private long countVertices(SqlgGraph graph, String schemaName, String labelName) {
        String physicalTable = "V_" + labelName;
        try (Connection conn = getConnection(graph);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT count(*) FROM \"" + schemaName + "\".\"" + physicalTable + "\"")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            // 表可能不存在(刚创建或已被删)
            log.debug("Count vertices failed for {}.{}: {}", schemaName, labelName, e.getMessage());
            return 0;
        }
    }

    private Connection getConnection(SqlgGraph graph) throws SQLException {
        return graph.getConnection();
    }

    private List<String> extractIdentifiers(VertexLabel vl) {
        try {
            var ids = vl.getIdentifiers();
            return ids == null || ids.isEmpty()
                    ? List.of()
                    : new ArrayList<>(ids);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildTableName(String schema, String label) {
        return schema + ".\"V_" + label + "\"";
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
