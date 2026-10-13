package com.trs.modules.connection.service;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Schema;
import org.umlg.sqlg.structure.topology.VertexLabel;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 验证演示数据的访问范围、冲突保护及图事务提交边界。
 *
 * @author codexsandboxoffline
 * @date 2026/1013
 */
class DemoDataServiceTest {

    private final SqlgGraphRegistry registry = mock(SqlgGraphRegistry.class);
    private final ConnectionVisibilityHelper visibility = mock(ConnectionVisibilityHelper.class);
    private final OperationLogService audit = mock(OperationLogService.class, RETURNS_DEEP_STUBS);
    private final SqlgGraph graph = mock(SqlgGraph.class, RETURNS_DEEP_STUBS);
    private final DemoDataService service = new DemoDataService(registry, visibility, audit);

    @Test
    void invisibleConnectionCannotBeOpened() {
        when(visibility.listEnabledForCurrentUser()).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.create(7L));
        verifyNoInteractions(registry);
    }

    @Test
    void existingTypePreventsAnyWrite() {
        allowConnection();
        Schema schema = mock(Schema.class);
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.of(schema));
        when(schema.getVertexLabel("person")).thenReturn(Optional.of(mock(VertexLabel.class)));

        assertThrows(IllegalArgumentException.class, () -> service.create(7L));

        verify(graph, never()).addVertex(any(Object[].class));
        verify(graph.tx(), never()).commit();
    }

    @Test
    void edgeFailureRollsBackInsteadOfCommittingPartialGraph() {
        allowConnection();
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.empty());
        Vertex vertex = mock(Vertex.class);
        when(graph.addVertex(any(Object[].class))).thenReturn(vertex);
        when(vertex.addEdge(anyString(), any(Vertex.class), any(Object[].class)))
                .thenThrow(new IllegalStateException("write failed"));

        assertThrows(IllegalStateException.class, () -> service.create(7L));

        verify(graph.tx(), never()).commit();
        verify(graph.tx(), times(2)).rollback();
    }

    @Test
    void successfulGraphPreservesModernDirectionsAndWeights() {
        allowConnection();
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.empty());
        Vertex marko = mock(Vertex.class);
        Vertex vadas = mock(Vertex.class);
        Vertex lop = mock(Vertex.class);
        Vertex josh = mock(Vertex.class);
        Vertex ripple = mock(Vertex.class);
        Vertex peter = mock(Vertex.class);
        when(graph.addVertex(any(Object[].class))).thenReturn(marko, vadas, lop, josh, ripple, peter);

        service.create(7L);

        verify(graph).addVertex(T.label, "public.person", "name", "marko", "age", 29);
        verify(graph).addVertex(T.label, "public.software", "name", "lop", "lang", "java");
        verify(marko).addEdge("knows", vadas, "weight", 0.5d);
        verify(marko).addEdge("knows", josh, "weight", 1.0d);
        verify(marko).addEdge("created", lop, "weight", 0.4d);
        verify(josh).addEdge("created", ripple, "weight", 1.0d);
        verify(josh).addEdge("created", lop, "weight", 0.4d);
        verify(peter).addEdge("created", lop, "weight", 0.2d);
        verify(graph.tx()).commit();
    }

    private void allowConnection() {
        GraphConnection connection = new GraphConnection();
        connection.setId(7L);
        when(visibility.listEnabledForCurrentUser()).thenReturn(List.of(connection));
        when(registry.get(7L)).thenReturn(graph);
    }
}
