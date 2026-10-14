package com.trs.modules.graphAlgorithm;

import org.jgrapht.Graph;
import org.jgrapht.graph.DirectedWeightedPseudograph;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 脱离数据库的有向带权多重图，保留平行边、自环及 Sqlg 完整 ID。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
public class AlgorithmGraph {
    final Graph<String, String> graph = new DirectedWeightedPseudograph<>(null, null);
    final Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
    final Map<String, Map<String, Object>> edges = new LinkedHashMap<>();

    /** 添加普通点数据，不保留 Sqlg 实体引用。 */
    public void addNode(String id, String label, String schema, String name) {
        graph.addVertex(id);
        nodes.put(id, Map.of("id", id, "label", label, "schema", schema, "name", name));
    }

    /** 添加原始边 ID 和数值权重。 */
    public void addEdge(String id, String source, String target, String label, double weight) {
        if (!Double.isFinite(weight) || weight < 0)
            throw new IllegalArgumentException("边权重必须为有限的非负数: " + id);
        if (graph.addEdge(source, target, id)) {
            graph.setEdgeWeight(id, weight);
            edges.put(id, Map.of("id", id, "source", source, "target", target, "label", label, "weight", weight));
        }
    }
}
