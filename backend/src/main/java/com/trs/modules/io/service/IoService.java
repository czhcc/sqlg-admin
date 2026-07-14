package com.trs.modules.io.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trs.config.PlatformConfig;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.PropertyDefinition;
import org.umlg.sqlg.structure.PropertyType;
import org.umlg.sqlg.structure.RecordId;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.AbstractLabel;
import org.umlg.sqlg.structure.topology.EdgeLabel;
import org.umlg.sqlg.structure.topology.PropertyColumn;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.Topology;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据导入导出服务,支持 CSV/JSON 格式的点边数据导入导出,以及 Topology 结构的导出。
 *
 * @author czh
 * @date 2026/07/10
 */
@Service
public class IoService {

    private static final Logger log = LoggerFactory.getLogger(IoService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int EXPORT_MAX_ROWS = 10000;
    private static final int PREVIEW_ROWS = 20;

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final PlatformConfig platformConfig;
    private final OperationLogService logService;

    public IoService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                      PlatformConfig platformConfig, OperationLogService logService) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.platformConfig = platformConfig;
        this.logService = logService;
    }

    private int importBatchSize() {
        return platformConfig.getImportConfig().getBatchSize();
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

    // ==================== Schema / Label 信息 ====================

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
                        vm.put("properties", vl.getProperties().values().stream()
                                .map(this::propBrief)
                                .collect(Collectors.toList()));
                        try {
                            vm.put("identifiers", vl.getIdentifiers() == null ? List.of() : new ArrayList<>(vl.getIdentifiers()));
                        } catch (Exception e) { vm.put("identifiers", List.of()); }
                        return vm;
                    })
                    .collect(Collectors.toList()));

            sMap.put("edgeLabels", schema.getEdgeLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .map(el -> {
                        Map<String, Object> em = new LinkedHashMap<>();
                        em.put("name", el.getName());
                        em.put("schema", schema.getName());
                        em.put("properties", el.getProperties().values().stream()
                                .map(this::propBrief)
                                .collect(Collectors.toList()));
                        em.put("outVertexLabels", el.getOutVertexLabels().stream()
                                .map(AbstractLabel::getFullName).collect(Collectors.toList()));
                        em.put("inVertexLabels", el.getInVertexLabels().stream()
                                .map(AbstractLabel::getFullName).collect(Collectors.toList()));
                        return em;
                    })
                    .collect(Collectors.toList()));

            schemas.add(sMap);
        }
        return schemas;
    }

    private Map<String, Object> propBrief(PropertyColumn pc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", pc.getName());
        m.put("type", pc.getPropertyType() == null ? "?" : pc.getPropertyType().name());
        return m;
    }

    // ==================== 导出点数据 ====================

    public Map<String, Object> exportVertices(Long connectionId, String schemaName, String labelName, String format) {
        SqlgGraph graph = registry.get(connectionId);
        var traversal = graph.traversal().V().hasLabel(labelName).limit(EXPORT_MAX_ROWS);

        List<Map<String, Object>> rows = new ArrayList<>();
        traversal.forEachRemaining(v -> rows.add(vertexToMap(v)));

        String effectiveFormat = format == null ? "csv" : format.toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        if ("json".equals(effectiveFormat)) {
            result.put("content", MAPPER.valueToTree(rows).toString());
            result.put("filename", labelName + "_vertices.json");
            result.put("binary", false);
        } else {
            result.put("content", verticesToCsv(rows));
            result.put("filename", labelName + "_vertices.csv");
            result.put("binary", false);
        }

        result.put("format", effectiveFormat);
        result.put("rowCount", rows.size());
        result.put("truncated", rows.size() >= EXPORT_MAX_ROWS);

        try {
            logService.log()
                .module("导入导出").action("导出点数据").httpMethod("GET")
                .type("EXPORT").name("导出点: " + schemaName + "." + labelName + " (" + effectiveFormat + ")")
                .status("SUCCESS").connection(connectionId)
                .schema(schemaName).objectType("VertexLabel").objectName(schemaName + "." + labelName)
                .affected(rows.size()).result("导出 " + rows.size() + " 条").submit();
        } catch (Exception ignored) {}

        return result;
    }

    // ==================== 导出边数据 ====================

    public Map<String, Object> exportEdges(Long connectionId, String schemaName, String labelName, String format) {
        SqlgGraph graph = registry.get(connectionId);
        List<Edge> all = new ArrayList<>();
        graph.traversal().E().hasLabel(labelName).limit(EXPORT_MAX_ROWS).forEachRemaining(all::add);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Edge e : all) {
            rows.add(edgeToMap(e));
        }

        String effectiveFormat = format == null ? "csv" : format.toLowerCase();
        Map<String, Object> result = new LinkedHashMap<>();

        if ("json".equals(effectiveFormat)) {
            result.put("content", MAPPER.valueToTree(rows).toString());
            result.put("filename", labelName + "_edges.json");
            result.put("binary", false);
        } else {
            result.put("content", edgesToCsv(rows));
            result.put("filename", labelName + "_edges.csv");
            result.put("binary", false);
        }

        result.put("format", effectiveFormat);
        result.put("rowCount", rows.size());
        result.put("truncated", rows.size() >= EXPORT_MAX_ROWS);
        return result;
    }

    // ==================== 导出 Topology ====================

    public Map<String, Object> exportTopology(Long connectionId) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", "1.0");
        ArrayNode schemasNode = MAPPER.createArrayNode();

        for (Schema schema : sortedSchemas(topology)) {
            ObjectNode schemaNode = MAPPER.createObjectNode();
            schemaNode.put("name", schema.getName());

            ArrayNode vLabels = MAPPER.createArrayNode();
            schema.getVertexLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .forEach(vl -> vLabels.add(labelToJson(vl)));
            schemaNode.set("vertexLabels", vLabels);

            ArrayNode eLabels = MAPPER.createArrayNode();
            schema.getEdgeLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .forEach(el -> eLabels.add(labelToJson(el)));
            schemaNode.set("edgeLabels", eLabels);

            schemasNode.add(schemaNode);
        }
        root.set("schemas", schemasNode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", root.toPrettyString());
        result.put("filename", "topology_" + connectionId + ".json");
        result.put("binary", false);
        result.put("format", "json");
        result.put("rowCount", schemasNode.size());
        result.put("truncated", false);

        try {
            logService.log()
                .module("导入导出").action("导出 Topology").httpMethod("GET")
                .type("EXPORT").name("导出 Topology JSON")
                .status("SUCCESS").connection(connectionId)
                .objectType("Topology").affected(schemasNode.size())
                .result("导出 " + schemasNode.size() + " 个 Schema").submit();
        } catch (Exception ignored) {}

        return result;
    }

    private ObjectNode labelToJson(AbstractLabel label) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("name", label.getName());
        try { node.put("fullName", label.getFullName()); } catch (Exception ignored) {}

        ObjectNode props = MAPPER.createObjectNode();
        label.getProperties().forEach((name, pc) -> {
            props.put(name, pc.getPropertyType() == null ? "STRING" : pc.getPropertyType().name());
        });
        node.set("properties", props);

        try {
            var ids = label.getIdentifiers();
            if (ids != null && !ids.isEmpty()) {
                ArrayNode idArr = MAPPER.createArrayNode();
                ids.forEach(idArr::add);
                node.set("identifiers", idArr);
            }
        } catch (Exception ignored) {}

        if (label instanceof VertexLabel vl) {
            ArrayNode inEdges = MAPPER.createArrayNode();
            vl.getInEdgeLabels().values().stream()
                    .map(e -> e.getSchema() != null ? e.getSchema().getName() + "." + e.getName() : e.getName())
                    .forEach(inEdges::add);
            node.set("inEdgeLabels", inEdges);

            ArrayNode outEdges = MAPPER.createArrayNode();
            vl.getOutEdgeLabels().values().stream()
                    .map(e -> e.getSchema() != null ? e.getSchema().getName() + "." + e.getName() : e.getName())
                    .forEach(outEdges::add);
            node.set("outEdgeLabels", outEdges);
        }

        if (label instanceof EdgeLabel el) {
            ArrayNode outV = MAPPER.createArrayNode();
            el.getOutVertexLabels().stream().map(AbstractLabel::getFullName).forEach(outV::add);
            node.set("outVertexLabels", outV);

            ArrayNode inV = MAPPER.createArrayNode();
            el.getInVertexLabels().stream().map(AbstractLabel::getFullName).forEach(inV::add);
            node.set("inVertexLabels", inV);
        }

        return node;
    }

    // ==================== 导入预览 ====================

    public Map<String, Object> previewImport(Long connectionId, String content, String format, String type) {
        List<Map<String, String>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if ("csv".equalsIgnoreCase(format)) {
            parseCsv(content, PREVIEW_ROWS, rows, columns, errors);
        } else if ("json".equalsIgnoreCase(format)) {
            parseJson(content, PREVIEW_ROWS, rows, columns, errors);
        } else {
            throw new IllegalArgumentException("不支持的预览格式: " + format);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("errorCount", errors.size());
        result.put("errors", errors.size() > 5 ? errors.subList(0, 5) : errors);
        result.put("totalRows", rows.size());
        return result;
    }

    // ==================== 导入点数据 ====================

    public Map<String, Object> importVertices(Long connectionId, ImportVerticesRequest req) {
        SqlgGraph graph = registry.get(connectionId);
        VertexLabel vl = findVertexLabel(graph, req.getSchema(), req.getLabel());
        Set<String> identifiers = extractIdentifiers(vl);
        Map<String, PropertyColumn> propTypes = new LinkedHashMap<>();
        vl.getProperties().forEach((k, v) -> propTypes.put(k, v));

        List<Map<String, String>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();

        if ("csv".equalsIgnoreCase(req.getFormat())) {
            parseCsv(req.getContent(), Integer.MAX_VALUE, rows, columns, parseErrors);
        } else {
            parseJson(req.getContent(), Integer.MAX_VALUE, rows, columns, parseErrors);
        }

        int imported = 0;
        int updated = 0;
        List<Map<String, String>> errorRows = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            try {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (var entry : req.getFieldMapping().entrySet()) {
                    String sourceCol = entry.getKey();
                    String targetProp = entry.getValue();
                    if (targetProp == null || targetProp.isBlank()) continue;
                    String rawValue = row.get(sourceCol);
                    if (rawValue == null || rawValue.isBlank()) continue;
                    converted.put(targetProp, convertValue(rawValue, propTypes.get(targetProp)));
                }

                if (identifiers.isEmpty()) {
                    Vertex v = graph.addVertex(req.getLabel());
                    for (var entry : converted.entrySet()) {
                        v.property(entry.getKey(), entry.getValue());
                    }
                    imported++;
                } else {
                    Map<String, Object> filterMap = new LinkedHashMap<>();
                    for (String idName : identifiers) {
                        filterMap.put(idName, converted.get(idName));
                    }

                    Vertex existing = null;
                    if (req.isOverwrite()) {
                        var trav = graph.traversal().V().hasLabel(req.getLabel());
                        for (var e : filterMap.entrySet()) {
                            trav = trav.has(e.getKey(), e.getValue());
                        }
                        var results = trav.limit(1).toList();
                        if (!results.isEmpty()) existing = results.get(0);
                    }

                    if (existing != null) {
                        for (var entry : converted.entrySet()) {
                            existing.property(entry.getKey(), entry.getValue());
                        }
                        updated++;
                    } else {
                        Vertex v = graph.addVertex(req.getLabel(), converted);
                        imported++;
                    }
                }

                if ((imported + updated) % importBatchSize() == 0) {
                    graph.tx().commit();
                }
            } catch (Exception e) {
                errorRows.add(row);
                errorMessages.add("第 " + (i + 1) + " 行: " + e.getMessage());
                log.warn("Import vertex row {} failed: {}", i + 1, e.getMessage());
            }
        }

        graph.tx().commit();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("updated", updated);
        result.put("errors", errorRows.size());
        result.put("totalRows", rows.size());
        result.put("errorRows", errorRows.size() > 0 ? errorRows.subList(0, Math.min(100, errorRows.size())) : List.of());
        result.put("errorMessages", errorMessages.size() > 0 ? errorMessages.subList(0, Math.min(20, errorMessages.size())) : List.of());

        try {
            logService.log()
                .module("导入导出").action("导入点数据").httpMethod("POST")
                .type("IMPORT").name("导入点: " + req.getSchema() + "." + req.getLabel() + " (" + req.getFormat() + ")")
                .status(errorRows.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS").connection(connectionId)
                .schema(req.getSchema()).objectType("VertexLabel").objectName(req.getSchema() + "." + req.getLabel())
                .affected(imported + updated).affected(imported)
                .result("新增 " + imported + ", 更新 " + updated + ", 失败 " + errorRows.size())
                .error(errorMessages.isEmpty() ? null : errorMessages.get(0))
                .submit();
        } catch (Exception ignored) {}

        return result;
    }

    // ==================== 导入边数据 ====================

    public Map<String, Object> importEdges(Long connectionId, ImportEdgesRequest req) {
        SqlgGraph graph = registry.get(connectionId);
        EdgeLabel el = findEdgeLabel(graph, req.getSchema(), req.getLabel());
        Map<String, PropertyColumn> propTypes = new LinkedHashMap<>();
        el.getProperties().forEach((k, v) -> propTypes.put(k, v));

        List<Map<String, String>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> parseErrors = new ArrayList<>();

        if ("csv".equalsIgnoreCase(req.getFormat())) {
            parseCsv(req.getContent(), Integer.MAX_VALUE, rows, columns, parseErrors);
        } else {
            parseJson(req.getContent(), Integer.MAX_VALUE, rows, columns, parseErrors);
        }

        int imported = 0;
        List<Map<String, String>> errorRows = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            try {
                String outVValue = row.get(req.getOutVertexField());
                String inVValue = row.get(req.getInVertexField());
                if (outVValue == null || inVValue == null) {
                    throw new IllegalArgumentException("出点或入点匹配字段为空");
                }

                Vertex outV = findVertexByField(graph, req.getOutVertexLabel(), req.getOutVertexField(), outVValue);
                Vertex inV = findVertexByField(graph, req.getInVertexLabel(), req.getInVertexField(), inVValue);
                if (outV == null) throw new IllegalArgumentException("出点不存在: " + outVValue);
                if (inV == null) throw new IllegalArgumentException("入点不存在: " + inVValue);

                Map<String, Object> converted = new LinkedHashMap<>();
                for (var entry : req.getFieldMapping().entrySet()) {
                    String sourceCol = entry.getKey();
                    String targetProp = entry.getValue();
                    if (targetProp == null || targetProp.isBlank()) continue;
                    String rawValue = row.get(sourceCol);
                    if (rawValue == null || rawValue.isBlank()) continue;
                    converted.put(targetProp, convertValue(rawValue, propTypes.get(targetProp)));
                }

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
                imported++;

                if (imported % importBatchSize() == 0) {
                    graph.tx().commit();
                }
            } catch (Exception e) {
                errorRows.add(row);
                errorMessages.add("第 " + (i + 1) + " 行: " + e.getMessage());
                log.warn("Import edge row {} failed: {}", i + 1, e.getMessage());
            }
        }

        graph.tx().commit();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("errors", errorRows.size());
        result.put("totalRows", rows.size());
        result.put("errorRows", errorRows.size() > 0 ? errorRows.subList(0, Math.min(100, errorRows.size())) : List.of());
        result.put("errorMessages", errorMessages.size() > 0 ? errorMessages.subList(0, Math.min(20, errorMessages.size())) : List.of());

        try {
            logService.log()
                .module("导入导出").action("导入边数据").httpMethod("POST")
                .type("IMPORT").name("导入边: " + req.getSchema() + "." + req.getLabel() + " (" + req.getFormat() + ")")
                .status(errorRows.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS").connection(connectionId)
                .schema(req.getSchema()).objectType("EdgeLabel").objectName(req.getSchema() + "." + req.getLabel())
                .affected(imported)
                .result("新增 " + imported + ", 失败 " + errorRows.size())
                .error(errorMessages.isEmpty() ? null : errorMessages.get(0))
                .submit();
        } catch (Exception ignored) {}

        return result;
    }

    // ==================== 导入 Topology ====================

    public Map<String, Object> importTopology(Long connectionId, String content) {
        SqlgGraph graph = registry.get(connectionId);
        Topology topology = graph.getTopology();

        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 JSON: " + e.getMessage());
        }

        JsonNode schemasNode = root.get("schemas");
        if (schemasNode == null || !schemasNode.isArray()) {
            throw new IllegalArgumentException("JSON 缺少 schemas 数组");
        }

        int schemasCreated = 0;
        int vertexLabelsCreated = 0;
        int edgeLabelsCreated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        Map<String, VertexLabel> vlCache = new HashMap<>();

        for (JsonNode schemaNode : schemasNode) {
            String schemaName = schemaNode.get("name").asText();
            Schema schema = topology.ensureSchemaExist(schemaName);
            schemasCreated++;

            JsonNode vLabelsNode = schemaNode.get("vertexLabels");
            if (vLabelsNode != null && vLabelsNode.isArray()) {
                for (JsonNode vlNode : vLabelsNode) {
                    String labelName = vlNode.get("name").asText();
                    try {
                        if (schema.getVertexLabels().containsKey(labelName)) {
                            skipped++;
                            vlCache.put(schemaName + "." + labelName,
                                    schema.getVertexLabels().get(labelName));
                            continue;
                        }

                        Map<String, PropertyDefinition> propDefs = new LinkedHashMap<>();
                        ListOrderedSet<String> identifiers = new ListOrderedSet<>();

                        JsonNode propsNode = vlNode.get("properties");
                        if (propsNode != null && propsNode.isObject()) {
                            Iterator<String> fieldIter = propsNode.fieldNames();
                            while (fieldIter.hasNext()) {
                                String propName = fieldIter.next();
                                String typeName = propsNode.get(propName).asText("STRING");
                                propDefs.put(propName, PropertyDefinition.of(parsePropertyType(typeName)));
                            }
                        }

                        JsonNode idsNode = vlNode.get("identifiers");
                        if (idsNode != null && idsNode.isArray()) {
                            for (JsonNode idNode : idsNode) {
                                String idName = idNode.asText();
                                if (!propDefs.containsKey(idName)) {
                                    propDefs.put(idName, PropertyDefinition.of(PropertyType.STRING));
                                }
                                identifiers.add(idName);
                            }
                        }

                        VertexLabel vl;
                        if (propDefs.isEmpty() && identifiers.isEmpty()) {
                            vl = schema.ensureVertexLabelExist(labelName);
                        } else if (identifiers.isEmpty()) {
                            vl = schema.ensureVertexLabelExist(labelName, propDefs);
                        } else {
                            vl = schema.ensureVertexLabelExist(labelName, propDefs, identifiers);
                        }
                        vlCache.put(schemaName + "." + labelName, vl);
                        vertexLabelsCreated++;
                    } catch (Exception e) {
                        errors.add("VertexLabel " + schemaName + "." + labelName + ": " + e.getMessage());
                    }
                }
            }

            graph.tx().commit();
        }

        for (JsonNode schemaNode : schemasNode) {
            String schemaName = schemaNode.get("name").asText();
            Schema schema = topology.getSchema(schemaName).orElse(null);
            if (schema == null) continue;

            JsonNode eLabelsNode = schemaNode.get("edgeLabels");
            if (eLabelsNode != null && eLabelsNode.isArray()) {
                for (JsonNode elNode : eLabelsNode) {
                    String labelName = elNode.get("name").asText();
                    try {
                        if (schema.getEdgeLabels().containsKey(labelName)) {
                            skipped++;
                            continue;
                        }

                        JsonNode outVNode = elNode.get("outVertexLabels");
                        JsonNode inVNode = elNode.get("inVertexLabels");
                        if (outVNode == null || !outVNode.isArray() || outVNode.size() == 0 ||
                            inVNode == null || !inVNode.isArray() || inVNode.size() == 0) {
                            errors.add("EdgeLabel " + schemaName + "." + labelName + ": 缺少 out/in VertexLabel");
                            continue;
                        }

                        String outFullName = outVNode.get(0).asText();
                        String inFullName = inVNode.get(0).asText();

                        VertexLabel outVL = vlCache.get(outFullName);
                        VertexLabel inVL = vlCache.get(inFullName);

                        if (outVL == null) outVL = findVertexLabelByFullName(graph, outFullName);
                        if (inVL == null) inVL = findVertexLabelByFullName(graph, inFullName);

                        if (outVL == null) {
                            errors.add("EdgeLabel " + labelName + ": 出点 " + outFullName + " 不存在");
                            continue;
                        }
                        if (inVL == null) {
                            errors.add("EdgeLabel " + labelName + ": 入点 " + inFullName + " 不存在");
                            continue;
                        }

                        Map<String, PropertyDefinition> propDefs = new LinkedHashMap<>();
                        JsonNode propsNode = elNode.get("properties");
                        if (propsNode != null && propsNode.isObject()) {
                            Iterator<String> fieldIter = propsNode.fieldNames();
                            while (fieldIter.hasNext()) {
                                String propName = fieldIter.next();
                                String typeName = propsNode.get(propName).asText("STRING");
                                propDefs.put(propName, PropertyDefinition.of(parsePropertyType(typeName)));
                            }
                        }

                        Schema edgeSchema = outVL.getSchema();
                        if (propDefs.isEmpty()) {
                            edgeSchema.ensureEdgeLabelExist(labelName, outVL, inVL, Map.of());
                        } else {
                            edgeSchema.ensureEdgeLabelExist(labelName, outVL, inVL, propDefs);
                        }
                        edgeLabelsCreated++;
                    } catch (Exception e) {
                        errors.add("EdgeLabel " + schemaName + "." + labelName + ": " + e.getMessage());
                    }
                }
            }

            graph.tx().commit();
        }

        registry.evict(connectionId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemasCreated", schemasCreated);
        result.put("vertexLabelsCreated", vertexLabelsCreated);
        result.put("edgeLabelsCreated", edgeLabelsCreated);
        result.put("skipped", skipped);
        result.put("errorCount", errors.size());
        result.put("errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
        return result;
    }

    private PropertyType parsePropertyType(String typeName) {
        if (typeName == null || typeName.isBlank()) return PropertyType.STRING;
        try {
            return PropertyType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PropertyType.STRING;
        }
    }

    private VertexLabel findVertexLabelByFullName(SqlgGraph graph, String fullName) {
        int dotIdx = fullName.indexOf('.');
        if (dotIdx < 0) return null;
        String schemaName = fullName.substring(0, dotIdx);
        String labelName = fullName.substring(dotIdx + 1);
        try {
            return graph.getTopology().getSchema(schemaName)
                    .flatMap(s -> java.util.Optional.ofNullable(s.getVertexLabels().get(labelName)))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 缓存刷新 ====================

    public void evict(Long connectionId) {
        registry.evict(connectionId);
    }

    // ==================== 内部工具 ====================

    private Map<String, Object> vertexToMap(Vertex v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(v.id()));
        m.put("label", v.label());
        Map<String, Object> props = extractProperties(v);
        m.put("properties", props);
        return m;
    }

    private Map<String, Object> edgeToMap(Edge e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", String.valueOf(e.id()));
        m.put("label", e.label());
        m.put("outVertexId", String.valueOf(e.outVertex().id()));
        m.put("outVertexLabel", e.outVertex().label());
        m.put("inVertexId", String.valueOf(e.inVertex().id()));
        m.put("inVertexLabel", e.inVertex().label());
        Map<String, Object> props = extractProperties(e);
        m.put("properties", props);
        return m;
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

    private Object normalizeValue(Property<Object> p) {
        Object val = p.value();
        if (val instanceof JsonNode node) return node.toString();
        return val;
    }

    private String verticesToCsv(List<Map<String, Object>> rows) {
        LinkedHashSet<String> propCols = new LinkedHashSet<>();
        for (var row : rows) {
            var props = (Map<String, Object>) row.get("properties");
            if (props != null) propCols.addAll(props.keySet());
        }

        List<String> headers = new ArrayList<>();
        headers.add("id");
        headers.add("label");
        headers.addAll(propCols);

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append(String.join(",", headers.stream().map(IoService::csvQuote).toList())).append("\r\n");

        for (var row : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(row.get("id")));
            cells.add(String.valueOf(row.get("label")));
            var props = (Map<String, Object>) row.get("properties");
            for (String col : propCols) {
                Object val = props == null ? null : props.get(col);
                cells.add(val == null ? "" : String.valueOf(val));
            }
            sb.append(String.join(",", cells.stream().map(IoService::csvQuote).toList())).append("\r\n");
        }
        return sb.toString();
    }

    private String edgesToCsv(List<Map<String, Object>> rows) {
        LinkedHashSet<String> propCols = new LinkedHashSet<>();
        for (var row : rows) {
            var props = (Map<String, Object>) row.get("properties");
            if (props != null) propCols.addAll(props.keySet());
        }

        List<String> headers = new ArrayList<>();
        headers.add("id");
        headers.add("label");
        headers.add("outVertexId");
        headers.add("outVertexLabel");
        headers.add("inVertexId");
        headers.add("inVertexLabel");
        headers.addAll(propCols);

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff');
        sb.append(String.join(",", headers.stream().map(IoService::csvQuote).toList())).append("\r\n");

        for (var row : rows) {
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(row.get("id")));
            cells.add(String.valueOf(row.get("label")));
            cells.add(String.valueOf(row.get("outVertexId")));
            cells.add(String.valueOf(row.get("outVertexLabel")));
            cells.add(String.valueOf(row.get("inVertexId")));
            cells.add(String.valueOf(row.get("inVertexLabel")));
            var props = (Map<String, Object>) row.get("properties");
            for (String col : propCols) {
                Object val = props == null ? null : props.get(col);
                cells.add(val == null ? "" : String.valueOf(val));
            }
            sb.append(String.join(",", cells.stream().map(IoService::csvQuote).toList())).append("\r\n");
        }
        return sb.toString();
    }

    static String csvQuote(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private void parseCsv(String content, int maxRows, List<Map<String, String>> rows,
                           List<String> columns, List<String> errors) {
        try {
            BufferedReader reader = new BufferedReader(new StringReader(content));
            String headerLine = reader.readLine();
            if (headerLine == null) {
                errors.add("文件为空");
                return;
            }

            String[] headers = parseCsvLine(headerLine);
            columns.addAll(Arrays.asList(headers));

            String line;
            int rowNum = 0;
            while ((line = reader.readLine()) != null && rowNum < maxRows) {
                if (line.trim().isEmpty()) continue;
                try {
                    String[] values = parseCsvLine(line);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        row.put(headers[i], i < values.length ? values[i] : "");
                    }
                    rows.add(row);
                    rowNum++;
                } catch (Exception e) {
                    errors.add("第 " + (rowNum + 2) + " 行解析失败: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("CSV 解析失败: " + e.getMessage());
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private void parseJson(String content, int maxRows, List<Map<String, String>> rows,
                            List<String> columns, List<String> errors) {
        try {
            JsonNode root = MAPPER.readTree(content);
            if (!root.isArray()) {
                errors.add("JSON 根必须是数组");
                return;
            }

            LinkedHashSet<String> allKeys = new LinkedHashSet<>();
            int rowNum = 0;
            for (JsonNode node : root) {
                if (rowNum >= maxRows) break;
                Map<String, String> row = new LinkedHashMap<>();
                Iterator<String> fieldNames = node.fieldNames();
                while (fieldNames.hasNext()) {
                    String field = fieldNames.next();
                    JsonNode val = node.get(field);
                    String strVal = val.isNull() ? "" : val.isTextual() ? val.asText() : val.toString();
                    row.put(field, strVal);
                    allKeys.add(field);
                }
                rows.add(row);
                rowNum++;
            }
            columns.addAll(allKeys);
        } catch (Exception e) {
            errors.add("JSON 解析失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Object convertValue(String rawValue, PropertyColumn pc) {
        if (pc == null) return rawValue;
        String typeName = pc.getPropertyType() == null ? "" : pc.getPropertyType().name();
        try {
            return switch (typeName) {
                case "BOOLEAN" -> "true".equalsIgnoreCase(rawValue.trim()) || "1".equals(rawValue.trim());
                case "INTEGER" -> Integer.valueOf(rawValue.trim());
                case "LONG" -> Long.valueOf(rawValue.trim());
                case "FLOAT", "DOUBLE" -> Double.valueOf(rawValue.trim());
                case "JSON", "JSON_ARRAY" -> MAPPER.readTree(rawValue);
                default -> rawValue;
            };
        } catch (Exception e) {
            return rawValue;
        }
    }

    private Vertex findVertexByField(SqlgGraph graph, String label, String field, String value) {
        var results = graph.traversal().V().hasLabel(label).has(field, value).limit(1).toList();
        return results.isEmpty() ? null : results.get(0);
    }

    private Set<String> extractIdentifiers(AbstractLabel label) {
        try {
            var ids = label.getIdentifiers();
            return ids == null || ids.isEmpty() ? Set.of() : new HashSet<>(ids);
        } catch (Exception e) {
            return Set.of();
        }
    }

    private VertexLabel findVertexLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getVertexLabels().values().stream()
                .filter(vl -> vl.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("VertexLabel 不存在: " + schemaName + "." + labelName));
    }

    private EdgeLabel findEdgeLabel(SqlgGraph graph, String schemaName, String labelName) {
        Schema schema = graph.getTopology().getSchema(schemaName)
                .orElseThrow(() -> new IllegalArgumentException("Schema 不存在: " + schemaName));
        return schema.getEdgeLabels().values().stream()
                .filter(el -> el.getName().equals(labelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("EdgeLabel 不存在: " + schemaName + "." + labelName));
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

    // ==================== DTO ====================

    public static class ImportVerticesRequest {
        private String content;
        private String format;
        private String schema;
        private String label;
        private Map<String, String> fieldMapping;
        private boolean overwrite;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Map<String, String> getFieldMapping() { return fieldMapping; }
        public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping; }
        public boolean isOverwrite() { return overwrite; }
        public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }
    }

    public static class ImportEdgesRequest {
        private String content;
        private String format;
        private String schema;
        private String label;
        private String outVertexLabel;
        private String inVertexLabel;
        private String outVertexField;
        private String inVertexField;
        private Map<String, String> fieldMapping;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getOutVertexLabel() { return outVertexLabel; }
        public void setOutVertexLabel(String outVertexLabel) { this.outVertexLabel = outVertexLabel; }
        public String getInVertexLabel() { return inVertexLabel; }
        public void setInVertexLabel(String inVertexLabel) { this.inVertexLabel = inVertexLabel; }
        public String getOutVertexField() { return outVertexField; }
        public void setOutVertexField(String outVertexField) { this.outVertexField = outVertexField; }
        public String getInVertexField() { return inVertexField; }
        public void setInVertexField(String inVertexField) { this.inVertexField = inVertexField; }
        public Map<String, String> getFieldMapping() { return fieldMapping; }
        public void setFieldMapping(Map<String, String> fieldMapping) { this.fieldMapping = fieldMapping; }
    }
}
