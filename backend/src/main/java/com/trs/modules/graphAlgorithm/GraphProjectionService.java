package com.trs.modules.graphAlgorithm;

import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.SqlgGraph;
import java.util.*;

/**
 * 有界子图投影，在读取线程完整转换数据并关闭 Sqlg 事务。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@Service
public class GraphProjectionService {
    private final SqlgGraphRegistry registry;

    /** 创建投影服务。 */
    public GraphProjectionService(SqlgGraphRegistry registry) { this.registry = registry; }

    /** 读取选定 Schema 的诱导子图；包括孤立点，排除端点未选中的边。 */
    public AlgorithmGraph read(Long connectionId, AlgorithmRequest request, Runnable check) throws Exception {
        SqlgGraph graph = registry.get(connectionId);
        try {
            check.run();
            // SQL 超时由 PostgreSQL 实际中止，事务结束后自动恢复连接设置。
            if (graph.getSqlDialect().isPostgresql()) {
                try (var statement = graph.tx().getConnection().createStatement()) {
                    statement.execute("SET LOCAL statement_timeout = '" + request.timeoutSeconds() * 1000 + "ms'");
                }
            }
            var schema = graph.getTopology().getSchema(request.schema())
                    .orElseThrow(() -> new IllegalArgumentException("Schema 不存在"));
            // Schema 目录的键是 schema.V_label / schema.E_label，查询必须使用逻辑名称。
            Map<String, org.umlg.sqlg.structure.topology.VertexLabel> vertexLabels = new TreeMap<>();
            schema.getVertexLabels().values().forEach(label -> vertexLabels.put(label.getName(), label));
            Map<String, org.umlg.sqlg.structure.topology.EdgeLabel> edgeLabels = new TreeMap<>();
            schema.getEdgeLabels().values().forEach(label -> edgeLabels.put(label.getName(), label));
            List<String> selectedVertices = request.vertexLabels().isEmpty()
                    ? new ArrayList<>(vertexLabels.keySet()) : request.vertexLabels();
            List<String> selectedEdges = request.edgeLabels().isEmpty()
                    ? new ArrayList<>(edgeLabels.keySet()) : request.edgeLabels();
            if (!vertexLabels.keySet().containsAll(selectedVertices) || !edgeLabels.keySet().containsAll(selectedEdges))
                throw new IllegalArgumentException("所选点类型或边类型不属于该 Schema");
            AlgorithmGraph result = new AlgorithmGraph();
            List<Object> ids = new ArrayList<>();
            for (String label : selectedVertices) {
                check.run();
                boolean filtered = request.propertyName() != null && !request.propertyName().isBlank();
                if (filtered) {
                    var property = vertexLabels.get(label).getProperty(request.propertyName())
                            .orElseThrow(() -> new IllegalArgumentException("过滤属性不存在: " + label));
                    if (!"STRING".equals(property.getPropertyType().name()) || request.propertyValue() == null)
                        throw new IllegalArgumentException("第一期属性过滤仅支持文本属性的精确匹配");
                }
                try (var traversal = graph.traversal().V().hasLabel(request.schema() + "." + label)) {
                    if (filtered) traversal.has(request.propertyName(), request.propertyValue());
                    traversal.limit(request.maxVertices() - result.nodes.size() + 1L);
                    while (traversal.hasNext()) {
                        check.run();
                        var vertex = traversal.next();
                        if (result.nodes.size() >= request.maxVertices())
                            throw new IllegalArgumentException("点数超过上限，请缩小分析范围");
                        String id = vertex.id().toString();
                        Object name = vertex.property("name").orElse(label);
                        String text = String.valueOf(name);
                        result.addNode(id, label, request.schema(), text.substring(0, Math.min(256, text.length())));
                        ids.add(vertex.id());
                    }
                }
            }
            if (ids.isEmpty() || selectedEdges.isEmpty()) return result;
            String[] labels = selectedEdges.stream().map(label -> request.schema() + "." + label).toArray(String[]::new);
            Object[] nodeIds = ids.toArray();
            try (var traversal = graph.traversal().E().hasLabel(labels[0], Arrays.copyOfRange(labels, 1, labels.length))
                    .where(__.outV().hasId(nodeIds)).where(__.inV().hasId(nodeIds))
                    .limit(request.maxEdges() + 1L)) {
                while (traversal.hasNext()) {
                    check.run();
                    var edge = traversal.next();
                    if (result.edges.size() >= request.maxEdges())
                        throw new IllegalArgumentException("边数超过上限，请缩小分析范围");
                    double weight = 1;
                    if (request.weighted() && Set.of("SHORTEST_PATH", "PAGERANK").contains(request.algorithm())) {
                        Object value = edge.property(request.weightProperty()).orElse(null);
                        if (!(value instanceof Number number))
                            throw new IllegalArgumentException("边缺少数值权重: " + edge.id());
                        weight = number.doubleValue();
                        if (!Double.isFinite(weight) || weight < 0 || weight > 1e12)
                            throw new IllegalArgumentException("权重须在 0 到 1e12 之间: " + edge.id());
                    }
                    result.addEdge(edge.id().toString(), edge.outVertex().id().toString(),
                            edge.inVertex().id().toString(), edge.label(), weight);
                }
            }
            return result;
        } finally {
            graph.tx().rollback();
        }
    }
}
