package com.trs.modules.graphExplore.service;

import com.trs.config.PlatformConfig;
import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.log.service.OperationLogService;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.umlg.sqlg.structure.SqlgGraph;
import org.umlg.sqlg.structure.topology.Schema;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 图关系读取事务的回归测试，覆盖连续查询、展开及异常后的连接释放。
 *
 * @author codexsandboxoffline
 * @date 2026/1013
 */
class GraphExploreServiceTest {

    private final SqlgGraphRegistry registry = mock(SqlgGraphRegistry.class);
    private final SqlgGraph graph = mock(SqlgGraph.class, RETURNS_DEEP_STUBS);
    private final GraphExploreService service = new GraphExploreService(registry,
            mock(GraphConnectionMapper.class), new PlatformConfig(),
            mock(OperationLogService.class), mock(ConnectionVisibilityHelper.class));

    @BeforeEach
    void prepareGraph() {
        when(registry.get(1L)).thenReturn(graph);
    }

    @Test
    void repeatedSearchReleasesTransactionAfterMaterializingEachResult() {
        Vertex vertex = vertex();
        var traversal = graph.traversal().V().hasLabel("person").limit(200);
        doAnswer(invocation -> {
            invocation.<Consumer<Vertex>>getArgument(0).accept(vertex);
            return null;
        }).when(traversal).forEachRemaining(any());

        for (int i = 0; i < 25; i++) {
            List<Map<String, Object>> rows = service.searchVertices(1L, "public", "person", null, null);
            assertEquals("public.person:::1", rows.getFirst().get("id"));
            assertEquals("person", rows.getFirst().get("label"));
            // 本次响应返回前必须已经释放，避免多次请求积累到连接池上限。
            verify(graph.tx(), times(i + 1)).rollback();
        }
        verify(graph.tx(), never()).commit();
    }

    @Test
    void queryFailureStillReleasesTransaction() {
        var traversal = graph.traversal().V().hasLabel("person").limit(200);
        doThrow(new IllegalStateException("query failed")).when(traversal).forEachRemaining(any());

        assertThrows(IllegalStateException.class,
                () -> service.searchVertices(1L, "public", "person", null, null));

        verify(graph.tx()).rollback();
    }

    @Test
    void repeatedNeighborExpansionReleasesTransaction() {
        prepareVertexLookup(vertex());

        for (int i = 0; i < 25; i++) {
            Map<String, Object> result = service.expandNeighbors(1L, "public.person:::1", "BOTH", null);
            assertEquals(List.of(), result.get("edges"));
            verify(graph.tx(), times(i + 1)).rollback();
        }
    }

    @Test
    void failedNeighborReadReleasesTransaction() {
        Vertex vertex = vertex();
        prepareVertexLookup(vertex);
        when(vertex.edges(eq(Direction.BOTH), any(String[].class)))
                .thenThrow(new IllegalStateException("edge read failed"));

        assertThrows(IllegalStateException.class,
                () -> service.expandNeighbors(1L, "public.person:::1", "BOTH", null));

        verify(graph.tx()).rollback();
    }

    @Test
    void batchExpansionReleasesEvenWhenOneVertexIsSkipped() {
        prepareVertexLookup(vertex());

        Map<String, Object> result = service.expandMultiple(1L,
                List.of("invalid-id", "public.person:::1"), "BOTH", null);

        assertEquals(1, ((List<?>) result.get("nodes")).size());
        verify(graph.tx()).rollback();
    }

    @Test
    void topologyReadAndMissingLabelReleaseTransactions() {
        Schema schema = mock(Schema.class);
        when(schema.getName()).thenReturn("public");
        when(schema.getVertexLabels()).thenReturn(Map.of());
        when(graph.getTopology().getPublicSchema()).thenReturn(schema);
        when(graph.getTopology().getSchemas()).thenReturn(Set.of(schema));
        when(graph.getTopology().getSchema("public")).thenReturn(Optional.of(schema));

        assertEquals(1, service.getSchemas(1L).size());
        assertThrows(IllegalArgumentException.class,
                () -> service.getVertexLabelProperties(1L, "public", "missing"));

        verify(graph.tx(), times(2)).rollback();
    }

    private Vertex vertex() {
        Vertex vertex = mock(Vertex.class);
        when(vertex.id()).thenReturn("public.person:::1");
        when(vertex.label()).thenReturn("person");
        when(vertex.properties()).thenAnswer(invocation -> Collections.emptyIterator());
        when(vertex.edges(eq(Direction.BOTH), any(String[].class)))
                .thenAnswer(invocation -> Collections.emptyIterator());
        return vertex;
    }

    private void prepareVertexLookup(Vertex vertex) {
        when(graph.vertices(any(Object[].class))).thenAnswer(invocation -> List.of(vertex).iterator());
    }
}
