package com.trs.modules.graphAlgorithm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

/**
 * 图算法参数及服务端资源上限。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AlgorithmRequest(String algorithm, String schema, List<String> vertexLabels,
        List<String> edgeLabels, String source, String target, Boolean directed, Boolean weighted,
        String weightProperty, String degreeMode, String componentMode, Integer topK,
        Double damping, Integer iterations, Double tolerance, Integer maxVertices,
        Integer maxEdges, Integer timeoutSeconds, String propertyName, String propertyValue) {

    /** 校验参数并填充默认值，资源上限只能向下调整。 */
    public AlgorithmRequest {
        if (!Set.of("SHORTEST_PATH", "DEGREE", "COMPONENTS", "PAGERANK").contains(algorithm == null ? "" : algorithm))
            throw new IllegalArgumentException("请选择有效的图算法");
        schema = schema == null || schema.isBlank() ? "public" : schema;
        vertexLabels = vertexLabels == null ? List.of() : List.copyOf(vertexLabels);
        edgeLabels = edgeLabels == null ? List.of() : List.copyOf(edgeLabels);
        directed = directed == null || directed;
        weighted = Boolean.TRUE.equals(weighted);
        weightProperty = weightProperty == null || weightProperty.isBlank() ? "weight" : weightProperty;
        degreeMode = degreeMode == null ? "TOTAL" : degreeMode;
        componentMode = componentMode == null ? "WEAK" : componentMode;
        topK = topK == null ? 100 : topK;
        damping = damping == null ? 0.85 : damping;
        iterations = iterations == null ? 100 : iterations;
        tolerance = tolerance == null ? 1e-6 : tolerance;
        maxVertices = maxVertices == null ? 5000 : maxVertices;
        maxEdges = maxEdges == null ? 20000 : maxEdges;
        timeoutSeconds = timeoutSeconds == null ? 30 : timeoutSeconds;
        if (!Set.of("IN", "OUT", "TOTAL").contains(degreeMode)
                || !Set.of("WEAK", "STRONG").contains(componentMode)
                || topK < 1 || topK > 20000 || maxVertices < 1 || maxVertices > 20000
                || maxEdges < 1 || maxEdges > 100000 || timeoutSeconds < 1 || timeoutSeconds > 120
                || iterations < 1 || iterations > 1000 || !Double.isFinite(damping) || damping <= 0 || damping >= 1
                || !Double.isFinite(tolerance) || tolerance <= 0 || tolerance >= 1
                || vertexLabels.size() > 100 || edgeLabels.size() > 100)
            throw new IllegalArgumentException("算法参数或资源上限不合法");
        if ("SHORTEST_PATH".equals(algorithm)
                && (source == null || source.isBlank() || target == null || target.isBlank()))
            throw new IllegalArgumentException("最短路径必须指定起点和终点的完整 ID");
        if ("PAGERANK".equals(algorithm) && !directed)
            throw new IllegalArgumentException("PageRank 需要有向关系");
    }
}
