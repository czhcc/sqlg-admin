package com.trs.modules.graphAlgorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jgrapht.alg.scoring.PageRank;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Modern 图与边界结构的算法正确性、取消和参数限制测试。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
class AlgorithmRunnerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AlgorithmRunner runner = new AlgorithmRunner();

    private AlgorithmRequest request(String algorithm, Object... values) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("algorithm", algorithm);
        for (int i = 0; i < values.length; i += 2) params.put(values[i].toString(), values[i + 1]);
        return mapper.convertValue(params, AlgorithmRequest.class);
    }

    private AlgorithmGraph modern() {
        AlgorithmGraph graph = new AlgorithmGraph();
        for (String id : List.of("marko", "vadas", "lop", "josh", "ripple", "peter"))
            graph.addNode(id, "person", "public", id);
        graph.addEdge("7", "marko", "vadas", "knows", .5);
        graph.addEdge("8", "marko", "josh", "knows", 1);
        graph.addEdge("9", "marko", "lop", "created", .4);
        graph.addEdge("10", "josh", "ripple", "created", 1);
        graph.addEdge("11", "josh", "lop", "created", .4);
        graph.addEdge("12", "peter", "lop", "created", .2);
        return graph;
    }

    private Map<String, Object> run(AlgorithmGraph graph, String algorithm, Object... params) {
        return runner.run(graph, request(algorithm, params), () -> {});
    }

    private Map<String, Double> scores(Map<String, Object> result) {
        Map<String, Double> scores = new HashMap<>();
        for (Object value : (List<?>) result.get("rows")) {
            Map<?, ?> row = (Map<?, ?>) value;
            scores.put(row.get("id").toString(), ((Number) row.get("value")).doubleValue());
        }
        return scores;
    }

    @Test
    void modernPathsRespectDirectionAndUnitCosts() {
        var graph = modern();
        var result = run(graph, "SHORTEST_PATH", "source", "marko", "target", "ripple");
        assertEquals(List.of("marko", "josh", "ripple"), result.get("pathNodes"));
        assertEquals(List.of("8", "10"), result.get("pathEdges"));
        assertEquals(2.0, result.get("cost"));
        assertEquals(false, run(graph, "SHORTEST_PATH", "source", "ripple", "target", "marko").get("found"));
        assertEquals(true, run(graph, "SHORTEST_PATH", "source", "ripple", "target", "marko", "directed", false).get("found"));
        assertEquals(0, run(graph, "SHORTEST_PATH", "source", "marko", "target", "marko").get("hops"));
    }

    @Test
    void weightsChangePathAndPreserveParallelEdgeIds() {
        var graph = modern();
        graph.addEdge("short", "marko", "ripple", "created", 10);
        graph.addEdge("parallel", "marko", "josh", "knows", .1);
        assertEquals(List.of("short"), run(graph, "SHORTEST_PATH", "source", "marko", "target", "ripple").get("pathEdges"));
        var weighted = run(graph, "SHORTEST_PATH", "source", "marko", "target", "ripple", "weighted", true);
        assertEquals(List.of("parallel", "10"), weighted.get("pathEdges"));
        assertEquals(1.1, (Double) weighted.get("cost"), 1e-12);
    }

    @Test
    void degreesIncludeParallelEdgesLoopsAndIsolatedVertices() {
        var graph = modern();
        graph.addNode("alone", "person", "other", "marko");
        graph.addEdge("loop", "marko", "marko", "knows", 1);
        graph.addEdge("parallel", "marko", "josh", "knows", 1);
        assertEquals(6.0, scores(run(graph, "DEGREE")).get("marko"));
        assertEquals(5.0, scores(run(graph, "DEGREE", "degreeMode", "OUT")).get("marko"));
        assertEquals(1.0, scores(run(graph, "DEGREE", "degreeMode", "IN")).get("marko"));
        assertEquals(0.0, scores(run(graph, "DEGREE")).get("alone"));
        assertEquals(1, ((List<?>) run(graph, "DEGREE", "topK", 1).get("topRows")).size());
        assertEquals(7, ((List<?>) run(graph, "DEGREE", "topK", 1).get("rows")).size());
    }

    @Test
    void componentsDistinguishDirectionAndIncludeIsolatedVertices() {
        var graph = modern();
        assertEquals(1, run(graph, "COMPONENTS").get("componentCount"));
        assertEquals(6, run(graph, "COMPONENTS", "componentMode", "STRONG").get("componentCount"));
        graph.addEdge("reverse", "josh", "marko", "knows", 1);
        graph.addNode("alone", "person", "public", "alone");
        assertEquals(2, run(graph, "COMPONENTS").get("componentCount"));
        assertEquals(6, run(graph, "COMPONENTS", "componentMode", "STRONG").get("componentCount"));
    }

    @Test
    void pageRankMatchesJGraphTWithWeightedDanglingVerticesAndLoops() {
        var graph = modern();
        graph.addNode("alone", "person", "public", "alone");
        graph.addEdge("loop", "marko", "marko", "knows", .3);
        graph.addEdge("parallel", "marko", "josh", "knows", .1);
        var result = run(graph, "PAGERANK", "weighted", true, "iterations", 1000, "tolerance", 1e-12);
        var expected = new PageRank<>(graph.graph, .85, 1000, 1e-12).getScores();
        var actual = scores(result);
        expected.forEach((id, score) -> assertEquals(score, actual.get(id), 1e-10));
        assertEquals(1, actual.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        assertEquals(true, result.get("converged"));
        assertEquals(false, run(graph, "PAGERANK", "iterations", 1, "tolerance", 1e-12).get("converged"));
    }

    @Test
    void emptyGraphsAndZeroWeightGraphsProduceFiniteScores() {
        assertEquals(true, run(new AlgorithmGraph(), "PAGERANK").get("converged"));
        var graph = new AlgorithmGraph();
        graph.addNode("a", "n", "public", "a"); graph.addNode("b", "n", "public", "b");
        graph.addEdge("zero", "a", "b", "e", 0);
        assertEquals(Map.of("a", .5, "b", .5), scores(run(graph, "PAGERANK", "weighted", true)));
    }

    @Test
    void computationCanBeCancelledDuringEachAlgorithm() {
        for (String algorithm : List.of("SHORTEST_PATH", "DEGREE", "COMPONENTS", "PAGERANK")) {
            var request = request(algorithm, "source", "marko", "target", "ripple");
            AtomicInteger checks = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> runner.run(modern(), request, () -> {
                if (checks.incrementAndGet() >= 3) throw new IllegalStateException("cancelled");
            }), algorithm);
        }
    }

    @Test
    void invalidParametersWeightsAndMissingEndpointsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> request("PAGERANK", "maxVertices", 20001));
        assertThrows(IllegalArgumentException.class, () -> request("PAGERANK", "maxEdges", 100001));
        assertThrows(IllegalArgumentException.class, () -> request("PAGERANK", "timeoutSeconds", 121));
        assertThrows(IllegalArgumentException.class, () -> request("PAGERANK", "damping", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> request("SHORTEST_PATH"));
        assertThrows(IllegalArgumentException.class, () -> run(modern(), "SHORTEST_PATH", "source", "missing", "target", "lop"));
        assertThrows(IllegalArgumentException.class, () -> modern().addEdge("bad", "marko", "lop", "e", -1));
        assertThrows(IllegalArgumentException.class, () -> modern().addEdge("bad", "marko", "lop", "e", Double.POSITIVE_INFINITY));
    }
}
