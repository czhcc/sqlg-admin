package com.trs.modules.graphAlgorithm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.junit.jupiter.api.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Schema;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 投影成功、失败、取消与类型校验后的事务释放测试。
 *
 * @author codexsandboxoffline
 * @date 2026/1014
 */
class GraphProjectionServiceTest {
    private final SqlgGraphRegistry registry = mock(SqlgGraphRegistry.class);
    private final SqlgGraph graph = mock(SqlgGraph.class, RETURNS_DEEP_STUBS);
    private final GraphProjectionService service = new GraphProjectionService(registry);
    private final AlgorithmRequest request = new ObjectMapper().convertValue(Map.of("algorithm", "DEGREE"), AlgorithmRequest.class);

    private void prepare() {
        when(registry.get(1L)).thenReturn(graph);
        var schema = mock(Schema.class);
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.of(schema));
        when(schema.getVertexLabels()).thenReturn(Map.of());
        when(schema.getEdgeLabels()).thenReturn(Map.of());
    }

    @Test
    void repeatedReadsReleaseTransactionsBeforeReturningDetachedGraphs() throws Exception {
        prepare();
        for (int i = 0; i < 25; i++) {
            assertTrue(service.read(1L, request, () -> {}).nodes.isEmpty());
            verify(graph.tx(), times(i + 1)).rollback();
        }
        verify(graph, never()).close();
    }

    @Test
    void schemaFailureReleasesTransaction() {
        when(registry.get(1L)).thenReturn(graph);
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.read(1L, request, () -> {}));
        verify(graph.tx()).rollback();
    }

    @Test
    void cancellationAndTimeoutReleaseTransaction() {
        prepare();
        assertThrows(IllegalStateException.class, () -> service.read(1L, request, () -> { throw new IllegalStateException("cancelled"); }));
        verify(graph.tx()).rollback();
    }

    @Test
    void unknownTypesAreRejectedWithoutLeakingTransactions() {
        prepare();
        var invalid = new ObjectMapper().convertValue(Map.of("algorithm", "DEGREE", "vertexLabels", List.of("missing")), AlgorithmRequest.class);
        assertThrows(IllegalArgumentException.class, () -> service.read(1L, invalid, () -> {}));
        verify(graph.tx()).rollback();
    }

    @Test
    void physicalTopologyKeysAreConvertedToLogicalLabelsAndFullIdsArePreserved() throws Exception {
        prepare();
        var schema = graph.getTopology().getSchema("public").orElseThrow();
        var label = mock(org.umlg.sqlg.structure.topology.VertexLabel.class);
        when(label.getName()).thenReturn("person");
        when(schema.getVertexLabels()).thenReturn(Map.of("public.V_person", label));
        var traversal = graph.traversal().V().hasLabel("public.person");
        var vertex = mock(org.apache.tinkerpop.gremlin.structure.Vertex.class, RETURNS_DEEP_STUBS);
        when(vertex.id()).thenReturn("public.person:::1");
        when(vertex.property("name").orElse("person")).thenReturn("marko");
        when(traversal.hasNext()).thenReturn(true, false);
        when(traversal.next()).thenReturn(vertex);
        var result = service.read(1L, request, () -> {});
        assertEquals("person", result.nodes.get("public.person:::1").get("label"));
        assertEquals("marko", result.nodes.get("public.person:::1").get("name"));
        verify(traversal).close();
        verify(graph.tx()).rollback();
    }

    @Test
    void vertexOverflowRejectsWholeProjectionAndReleasesTraversal() throws Exception {
        prepare();
        var schema = graph.getTopology().getSchema("public").orElseThrow();
        var label = mock(org.umlg.sqlg.structure.topology.VertexLabel.class);
        when(label.getName()).thenReturn("person");
        when(schema.getVertexLabels()).thenReturn(Map.of("public.V_person", label));
        var traversal = graph.traversal().V().hasLabel("public.person");
        var vertex = mock(org.apache.tinkerpop.gremlin.structure.Vertex.class, RETURNS_DEEP_STUBS);
        when(vertex.id()).thenReturn("public.person:::1");
        when(vertex.property("name").orElse("person")).thenReturn("marko");
        when(traversal.hasNext()).thenReturn(true);
        when(traversal.next()).thenReturn(vertex);
        var limited = new ObjectMapper().convertValue(Map.of("algorithm", "DEGREE", "maxVertices", 1), AlgorithmRequest.class);
        assertThrows(IllegalArgumentException.class, () -> service.read(1L, limited, () -> {}));
        verify(traversal).close();
        verify(graph.tx()).rollback();
    }
}
