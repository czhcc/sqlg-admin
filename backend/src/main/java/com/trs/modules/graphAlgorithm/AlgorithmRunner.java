package com.trs.modules.graphAlgorithm;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.AsUndirectedGraph;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * 有界内存图算法执行器，支持计算过程中的协作取消和截止时间检查。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@Component
public class AlgorithmRunner {

    /** 计算算法结果，调用方负责将 check 绑定到任务取消标志和截止时间。 */
    public Map<String, Object> run(AlgorithmGraph data, AlgorithmRequest request, Runnable check) {
        check.run();
        // JGraphT 在读取邻接结构时检查取消，避免仅取消 HTTP 等待而计算继续运行。
        Graph<String, String> checked = new org.jgrapht.graph.GraphDelegator<>(data.graph) {
            /** 检查取消后读取所有关联边。 */
            @Override public Set<String> edgesOf(String vertex) { check.run(); return super.edgesOf(vertex); }
            /** 检查取消后读取出边。 */
            @Override public Set<String> outgoingEdgesOf(String vertex) { check.run(); return super.outgoingEdgesOf(vertex); }
            /** 检查取消后读取入边。 */
            @Override public Set<String> incomingEdgesOf(String vertex) { check.run(); return super.incomingEdgesOf(vertex); }
        };
        Graph<String, String> scoringGraph = request.weighted() ? checked
                : new org.jgrapht.graph.AsUnweightedGraph<>(checked);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        switch (request.algorithm()) {
            case "SHORTEST_PATH" -> {
                if (!checked.containsVertex(request.source()) || !checked.containsVertex(request.target()))
                    throw new IllegalArgumentException("起点或终点不在所选子图中");
                Graph<String, String> pathGraph = request.directed() ? scoringGraph : new AsUndirectedGraph<>(scoringGraph);
                var path = new DijkstraShortestPath<>(pathGraph).getPath(request.source(), request.target());
                result.put("found", path != null);
                result.put("pathNodes", path == null ? List.of() : path.getVertexList());
                result.put("pathEdges", path == null ? List.of() : path.getEdgeList());
                result.put("hops", path == null ? 0 : path.getLength());
                result.put("cost", path == null ? 0 : path.getWeight());
                if (path != null) for (int i = 0; i < path.getVertexList().size(); i++)
                    rows.add(Map.of("id", path.getVertexList().get(i), "value", i));
            }
            case "DEGREE" -> {
                for (String id : checked.vertexSet()) {
                    check.run();
                    int degree = switch (request.degreeMode()) {
                        case "IN" -> checked.inDegreeOf(id);
                        case "OUT" -> checked.outDegreeOf(id);
                        default -> checked.degreeOf(id);
                    };
                    rows.add(Map.of("id", id, "value", degree));
                }
                sortScores(rows);
            }
            case "COMPONENTS" -> {
                List<Set<String>> groups = "STRONG".equals(request.componentMode())
                        ? new KosarajuStrongConnectivityInspector<>(checked).stronglyConnectedSets()
                        : new ConnectivityInspector<>(checked).connectedSets();
                groups.sort(Comparator.<Set<String>>comparingInt(Set::size).reversed()
                        .thenComparing(group -> Collections.min(group)));
                int index = 0;
                for (Set<String> group : groups) {
                    check.run();
                    index++;
                    for (String id : new TreeSet<>(group))
                        rows.add(Map.of("id", id, "value", index, "size", group.size()));
                }
                result.put("componentCount", groups.size());
            }
            case "PAGERANK" -> pageRank(scoringGraph, request, check, rows, result);
            default -> throw new IllegalArgumentException("不支持的算法");
        }
        check.run();
        result.put("rows", rows);
        result.put("topRows", rows.stream().limit(request.topK()).toList());
        return result;
    }

    private void sortScores(List<Map<String, Object>> rows) {
        rows.sort(Comparator.<Map<String, Object>>comparingDouble(row -> ((Number) row.get("value")).doubleValue())
                .reversed().thenComparing(row -> row.get("id").toString()));
    }

    private void pageRank(Graph<String, String> graph, AlgorithmRequest request, Runnable check,
                          List<Map<String, Object>> rows, Map<String, Object> result) {
        // 使用 JGraphT 图模型实现迭代，以提供准确的迭代数、收敛状态和协作取消。
        int count = graph.vertexSet().size();
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, Double> totals = new HashMap<>();
        for (String id : graph.vertexSet()) {
            check.run();
            scores.put(id, 1.0 / count);
            totals.put(id, graph.outgoingEdgesOf(id).stream().mapToDouble(graph::getEdgeWeight).sum());
        }
        double residual = 0;
        int iterations = 0;
        boolean converged = count == 0;
        for (; iterations < request.iterations() && !converged;) {
            check.run();
            double dangling = 0;
            for (String id : scores.keySet()) if (totals.get(id) == 0) dangling += scores.get(id);
            Map<String, Double> next = new LinkedHashMap<>();
            residual = 0;
            for (String id : graph.vertexSet()) {
                check.run();
                double score = (1 - request.damping()) / count + request.damping() * dangling / count;
                for (String edge : graph.incomingEdgesOf(id)) {
                    check.run();
                    String source = Graphs.getOppositeVertex(graph, edge, id);
                    if (totals.get(source) > 0)
                        score += request.damping() * scores.get(source) * graph.getEdgeWeight(edge) / totals.get(source);
                }
                next.put(id, score);
                residual = Math.max(residual, Math.abs(score - scores.get(id)));
            }
            scores = next;
            iterations++;
            converged = residual <= request.tolerance();
        }
        scores.forEach((id, score) -> rows.add(Map.of("id", id, "value", score)));
        sortScores(rows);
        result.put("iterations", iterations);
        result.put("converged", converged);
        result.put("residual", residual);
    }
}
