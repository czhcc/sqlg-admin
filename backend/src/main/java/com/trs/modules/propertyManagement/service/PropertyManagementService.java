package com.trs.modules.propertyManagement.service;

import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.propertyManagement.dto.PropertyDetailDto;
import com.trs.modules.propertyManagement.dto.PropertySaveRequest;
import com.trs.modules.propertyManagement.entity.PropertyMeta;
import com.trs.modules.propertyManagement.mapper.PropertyMetaMapper;
import com.trs.modules.topology.dto.TopologyDto;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import com.trs.modules.topology.service.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.PropertyDefinition;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.IndexType;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 属性管理服务,封装对 sqlg VertexLabel / EdgeLabel 上属性的列表、新增、删除、
 * 索引创建/删除,以及 UI 元数据(displayName / searchable / listDisplay)的读写。
 *
 * @author czh
 * @date 2026/07/08
 */
@Service
public class PropertyManagementService {

    private static final Logger log = LoggerFactory.getLogger(PropertyManagementService.class);

    /**
     * 前端属性类型下拉可选值 — 对应 sqlg PropertyType 枚举。
     * 仅暴露常用类型,数组类型以 _ARRAY 后缀呈现。
     */
    private static final List<String> SUPPORTED_PROPERTY_TYPES = List.of(
            "STRING", "INTEGER", "LONG", "FLOAT", "DOUBLE", "BOOLEAN",
            "JSON", "LOCALDATE", "LOCALDATETIME", "LOCALTIME",
            "UUID", "BIG_DECIMAL",
            "STRING_ARRAY", "INTEGER_ARRAY", "LONG_ARRAY", "DOUBLE_ARRAY",
            "BOOLEAN_ARRAY", "JSON_ARRAY"
    );

    private final SqlgGraphRegistry registry;
    private final TopologyService topologyService;
    private final PropertyMetaMapper metaMapper;
    private final GraphConnectionMapper connectionMapper;

    public PropertyManagementService(SqlgGraphRegistry registry,
                                     TopologyService topologyService,
                                     PropertyMetaMapper metaMapper,
                                     GraphConnectionMapper connectionMapper) {
        this.registry = registry;
        this.topologyService = topologyService;
        this.metaMapper = metaMapper;
        this.connectionMapper = connectionMapper;
    }

    // ==================== 连接列表 ====================

    /**
     * 获取可用连接列表(已启用),用于前端下拉。
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

    // ==================== 左侧树 ====================

    /**
     * 获取指定连接的完整拓扑(Schema / VertexLabel / EdgeLabel),
     * 复用 TopologyService 已有逻辑,用于左侧树渲染。
     *
     * @param connectionId 图数据库连接 ID
     * @return 拓扑 DTO
     */
    public TopologyDto getTopology(Long connectionId) {
        return topologyService.getTopology(connectionId);
    }

    /**
     * 获取指定连接的简化树结构(仅 schema + label 名),用于前端树渲染。
     *
     * @param connectionId 图数据库连接 ID
     * @return {schemas: [{name, vertexLabels:[...], edgeLabels:[...]}]}
     */
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

    // ==================== 属性列表 ====================

    /**
     * 列出指定 label 的所有属性详情,合并 sqlg topology 与 sys_property_meta。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @return 属性详情列表
     */
    public List<PropertyDetailDto> listProperties(Long connectionId, String labelKind,
                                                  String schemaName, String labelName) {
        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);

        Set<String> identifiers = extractIdentifiers(label);

        // 已有索引: propertyName -> Index 信息
        Map<String, Map<String, String>> indexMap = new HashMap<>();
        label.getIndexes().values().forEach(idx -> {
            String idxType = idx.getIndexType() == null ? "?" : idx.getIndexType().getName();
            for (PropertyColumn pc : idx.getProperties()) {
                indexMap.computeIfAbsent(pc.getName(), k -> new LinkedHashMap<>())
                        .putAll(Map.of(
                                "indexName", idx.getName(),
                                "indexType", idxType));
            }
        });

        // 数据库物理类型: propertyName -> PG type
        Map<String, String> dbTypes = loadDbColumnTypes(graph, labelKind, schemaName, labelName);

        // UI 元数据
        List<PropertyMeta> metas = metaMapper.selectByLabel(connectionId, labelKind, schemaName, labelName);
        Map<String, PropertyMeta> metaMap = metas.stream()
                .collect(Collectors.toMap(PropertyMeta::getPropertyName, m -> m, (a, b) -> a));

        return label.getProperties().values().stream()
                .map(pc -> {
                    String pname = pc.getName();
                    PropertyDetailDto dto = new PropertyDetailDto();
                    dto.setName(pname);
                    dto.setPropertyType(pc.getPropertyType() == null ? "?" : pc.getPropertyType().name());
                    dto.setDbType(dbTypes.getOrDefault(pname, ""));
                    dto.setIdentifier(identifiers.contains(pname));
                    if (indexMap.containsKey(pname)) {
                        dto.setIndexed(true);
                        dto.setIndexName(indexMap.get(pname).get("indexName"));
                        dto.setIndexType(indexMap.get(pname).get("indexType"));
                    } else {
                        dto.setIndexed(false);
                    }
                    PropertyMeta m = metaMap.get(pname);
                    if (m != null) {
                        dto.setDisplayName(m.getDisplayName());
                        dto.setSearchable(m.getIsSearchable() != null && m.getIsSearchable() == 1);
                        dto.setListDisplay(m.getIsListDisplay() != null && m.getIsListDisplay() == 1);
                        dto.setRemark(m.getRemark());
                    }
                    return dto;
                })
                .sorted(Comparator.comparing(PropertyDetailDto::getName))
                .collect(Collectors.toList());
    }

    // ==================== 新增属性 ====================

    /**
     * 向指定 label 新增属性,可选同时创建索引和保存 UI 元数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param req          请求体
     */
    public void addProperty(Long connectionId, String labelKind, String schemaName,
                            String labelName, PropertySaveRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new IllegalArgumentException("属性名不能为空");
        }
        String propTypeStr = req.getPropertyType() == null ? "STRING" : req.getPropertyType().toUpperCase();
        if (!SUPPORTED_PROPERTY_TYPES.contains(propTypeStr)) {
            throw new IllegalArgumentException("不支持的属性类型: " + propTypeStr);
        }

        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);

        // 检查是否已存在
        if (label.getProperties().containsKey(req.getName())) {
            throw new IllegalArgumentException("属性已存在: " + req.getName());
        }

        PropertyType propType = PropertyType.valueOf(propTypeStr);
        PropertyDefinition propDef = PropertyDefinition.of(propType);

        // ensurePropertiesExist 在 VertexLabel / EdgeLabel 上,不在 AbstractLabel,按 kind 调用
        Map<String, PropertyDefinition> propDefs = Map.of(req.getName(), propDef);
        if ("vertex".equalsIgnoreCase(labelKind)) {
            ((VertexLabel) label).ensurePropertiesExist(propDefs);
        } else {
            ((EdgeLabel) label).ensurePropertiesExist(propDefs);
        }
        graph.tx().commit();
        log.info("Added property '{}' ({}.{}) to {}.{} [{}]",
                req.getName(), schemaName, labelName, schemaName, labelName, propTypeStr);

        // 可选创建索引
        boolean wantIndex = req.isCreateIndex() || "NON_UNIQUE".equalsIgnoreCase(req.getIndexType())
                || "UNIQUE".equalsIgnoreCase(req.getIndexType());
        if (wantIndex) {
            IndexType idxType = "UNIQUE".equalsIgnoreCase(req.getIndexType())
                    ? IndexType.UNIQUE : IndexType.NON_UNIQUE;
            graph.getTopology().getSchema(schemaName).ifPresent(schema -> {
                AbstractLabel fresh = findLabel(graph, labelKind, schemaName, labelName);
                PropertyColumn pc = fresh.getProperty(req.getName()).orElse(null);
                if (pc != null) {
                    fresh.ensureIndexExists(idxType, List.of(pc));
                    graph.tx().commit();
                    log.info("Created {} index on property '{}'.{}", idxType.getName(), req.getName(), labelName);
                }
            });
        }

        // 保存 UI 元数据
        saveMeta(connectionId, labelKind, schemaName, labelName, req);
    }

    // ==================== 编辑属性元数据 ====================

    /**
     * 编辑属性的名称、UI 元数据和索引状态(属性类型不可改)。
     * <p>若 name 与当前不同,调用 sqlg 重命名;元数据记录随之迁移到新名称。
     * 若 createIndex 与当前索引状态不同,会创建或删除索引。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 原属性名
     * @param req          请求体
     */
    public void updatePropertyMeta(Long connectionId, String labelKind, String schemaName,
                                   String labelName, String propertyName,
                                   com.trs.modules.propertyManagement.dto.PropertyUpdateRequest req) {
        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);
        PropertyColumn pc = label.getProperty(propertyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "属性不存在: " + propertyName));

        // 属性名重命名
        String newName = req.getName() == null ? propertyName : req.getName().trim();
        boolean renamed = false;
        if (!newName.isEmpty() && !newName.equals(propertyName)) {
            if (label.getProperties().containsKey(newName)) {
                throw new IllegalArgumentException("属性名已存在: " + newName);
            }
            pc.rename(newName);
            graph.tx().commit();
            log.info("Renamed property '{}.{}({}) -> {}'", schemaName, labelName, propertyName, newName);
            // 重命名后重新获取 label 和 pc(拓扑已变更)
            label = findLabel(graph, labelKind, schemaName, labelName);
            pc = label.getProperty(newName).orElseThrow();
            renamed = true;
        }

        String effectiveName = renamed ? newName : propertyName;

        boolean currentlyIndexed = label.getIndexes().values().stream()
                .anyMatch(idx -> idx.getProperties().size() == 1
                        && idx.getProperties().get(0).getName().equals(effectiveName));

        boolean wantIndex = req.isCreateIndex() || "NON_UNIQUE".equalsIgnoreCase(req.getIndexType())
                || "UNIQUE".equalsIgnoreCase(req.getIndexType());

        if (wantIndex && !currentlyIndexed && !extractIdentifiers(label).contains(effectiveName)) {
            IndexType idxType = "UNIQUE".equalsIgnoreCase(req.getIndexType())
                    ? IndexType.UNIQUE : IndexType.NON_UNIQUE;
            label.ensureIndexExists(idxType, List.of(pc));
            graph.tx().commit();
            log.info("Created {} index on property '{}'.{}", idxType.getName(), effectiveName, labelName);
        } else if (!wantIndex && currentlyIndexed) {
            var targetIdx = label.getIndexes().values().stream()
                    .filter(idx -> idx.getProperties().size() == 1
                            && idx.getProperties().get(0).getName().equals(effectiveName))
                    .findFirst()
                    .orElse(null);
            if (targetIdx != null) {
                targetIdx.remove(false);
                graph.tx().commit();
                log.info("Removed index on property '{}'.{}", effectiveName, labelName);
            }
        }

        if (renamed) {
            metaMapper.delete(connectionId, labelKind, schemaName, labelName, propertyName);
        }
        PropertyMeta meta = new PropertyMeta();
        meta.setConnectionId(connectionId);
        meta.setLabelKind(labelKind);
        meta.setSchemaName(schemaName);
        meta.setLabelName(labelName);
        meta.setPropertyName(effectiveName);
        meta.setDisplayName(req.getDisplayName());
        meta.setIsSearchable(req.isSearchable() ? 1 : 0);
        meta.setIsListDisplay(req.isListDisplay() ? 1 : 0);
        meta.setRemark(req.getRemark());
        metaMapper.upsert(meta);
        log.info("Updated property meta for '{}.{}({})'", schemaName, labelName, effectiveName);
    }

    // ==================== 删除属性 ====================

    /**
     * 从指定 label 删除属性,同时删除 UI 元数据。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 属性名
     */
    public void removeProperty(Long connectionId, String labelKind, String schemaName,
                               String labelName, String propertyName) {
        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);
        PropertyColumn pc = label.getProperty(propertyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "属性不存在: " + propertyName));

        // false = 同时删除物理列
        pc.remove(false);
        graph.tx().commit();
        log.info("Removed property '{}' from {}.{}", propertyName, schemaName, labelName);

        metaMapper.delete(connectionId, labelKind, schemaName, labelName, propertyName);
    }

    // ==================== 索引管理 ====================

    /**
     * 在指定属性上创建非唯一索引。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 属性名
     * @param unique       是否唯一索引
     */
    public void createIndex(Long connectionId, String labelKind, String schemaName,
                            String labelName, String propertyName, boolean unique) {
        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);
        PropertyColumn pc = label.getProperty(propertyName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "属性不存在: " + propertyName));

        label.ensureIndexExists(
                unique ? IndexType.UNIQUE : IndexType.NON_UNIQUE,
                List.of(pc));
        graph.tx().commit();
        log.info("Created {} index on {}.{} ({})",
                unique ? "UNIQUE" : "NON_UNIQUE", schemaName, labelName, propertyName);
    }

    /**
     * 删除指定属性上的索引。
     *
     * @param connectionId 图数据库连接 ID
     * @param labelKind    vertex / edge
     * @param schemaName   schema 名称
     * @param labelName    label 名称
     * @param propertyName 属性名
     */
    public void removeIndex(Long connectionId, String labelKind, String schemaName,
                            String labelName, String propertyName) {
        SqlgGraph graph = registry.get(connectionId);
        AbstractLabel label = findLabel(graph, labelKind, schemaName, labelName);

        var targetIdx = label.getIndexes().values().stream()
                .filter(idx -> idx.getProperties().size() == 1
                        && idx.getProperties().get(0).getName().equals(propertyName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "属性上没有索引: " + propertyName));

        targetIdx.remove(false);
        graph.tx().commit();
        log.info("Removed index on {}.{} ({})", schemaName, labelName, propertyName);
    }

    // ==================== 属性类型枚举 ====================

    /**
     * 获取前端属性类型下拉的可用类型列表。
     *
     * @return 类型名称列表
     */
    public List<String> listSupportedPropertyTypes() {
        return SUPPORTED_PROPERTY_TYPES;
    }

    // ==================== 缓存刷新 ====================

    /**
     * 刷新指定连接的拓扑缓存。
     *
     * @param connectionId 图数据库连接 ID
     */
    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }

    // ==================== 内部工具 ====================

    private AbstractLabel findLabel(SqlgGraph graph, String labelKind, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        if ("vertex".equalsIgnoreCase(labelKind)) {
            return schema.getVertexLabels().values().stream()
                    .filter(vl -> vl.getName().equals(labelName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "VertexLabel 不存在: " + schemaName + "." + labelName));
        } else if ("edge".equalsIgnoreCase(labelKind)) {
            return schema.getEdgeLabels().values().stream()
                    .filter(el -> el.getName().equals(labelName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "EdgeLabel 不存在: " + schemaName + "." + labelName));
        }
        throw new IllegalArgumentException("labelKind 必须是 vertex 或 edge,实际: " + labelKind);
    }

    private Set<String> extractIdentifiers(AbstractLabel label) {
        try {
            var ids = label.getIdentifiers();
            return ids == null || ids.isEmpty()
                    ? Set.of()
                    : new HashSet<>(ids);
        } catch (Exception e) {
            return Set.of();
        }
    }

    /**
     * 通过 JDBC 查询底层物理表的列类型(V_/E_ 前缀),用于「数据库类型」列。
     */
    private Map<String, String> loadDbColumnTypes(SqlgGraph graph, String labelKind,
                                                  String schemaName, String labelName) {
        String prefix = "vertex".equalsIgnoreCase(labelKind) ? "V_" : "E_";
        String physicalTable = prefix + labelName;
        Map<String, String> types = new LinkedHashMap<>();
        try (Connection conn = graph.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name, data_type FROM information_schema.columns " +
                     "WHERE table_schema = ? AND table_name = ?")) {
            ps.setString(1, schemaName);
            ps.setString(2, physicalTable);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    types.put(rs.getString("column_name"), rs.getString("data_type"));
                }
            }
        } catch (SQLException e) {
            log.debug("Load column types failed for {}.{}: {}", schemaName, labelName, e.getMessage());
        }
        return types;
    }

    private void saveMeta(Long connectionId, String labelKind, String schemaName,
                          String labelName, PropertySaveRequest req) {
        PropertyMeta meta = new PropertyMeta();
        meta.setConnectionId(connectionId);
        meta.setLabelKind(labelKind);
        meta.setSchemaName(schemaName);
        meta.setLabelName(labelName);
        meta.setPropertyName(req.getName());
        meta.setDisplayName(req.getDisplayName());
        meta.setIsSearchable(req.isSearchable() ? 1 : 0);
        meta.setIsListDisplay(req.isListDisplay() ? 1 : 0);
        meta.setRemark(req.getRemark());
        metaMapper.upsert(meta);
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
