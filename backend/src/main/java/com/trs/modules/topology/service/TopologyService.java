package com.trs.modules.topology.service;

import com.trs.modules.connection.ConnectionVisibilityHelper;
import com.trs.modules.connection.entity.GraphConnection;
import com.trs.modules.connection.mapper.GraphConnectionMapper;
import com.trs.modules.topology.dto.*;
import com.trs.modules.topology.support.SqlgGraphRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.umlg.sqlg.structure.topology.*;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class TopologyService {

    private static final Logger log = LoggerFactory.getLogger(TopologyService.class);

    private final SqlgGraphRegistry registry;
    private final GraphConnectionMapper connectionMapper;
    private final ConnectionVisibilityHelper connectionVisibilityHelper;

    public TopologyService(SqlgGraphRegistry registry, GraphConnectionMapper connectionMapper,
                            ConnectionVisibilityHelper connectionVisibilityHelper) {
        this.registry = registry;
        this.connectionMapper = connectionMapper;
        this.connectionVisibilityHelper = connectionVisibilityHelper;
    }

    public List<Map<String, Object>> listConnectionsForTopology() {
        return connectionVisibilityHelper.listConnectionDtosForCurrentUser();
    }

    public TopologyDto getTopology(Long connectionId) {
        GraphConnection conn = connectionMapper.selectById(connectionId);
        if (conn == null) {
            throw new IllegalArgumentException("连接不存在: id=" + connectionId);
        }

        var graph = registry.get(connectionId);
        var topology = graph.getTopology();

        List<SchemaDto> schemas = new ArrayList<>();

        for (Schema schema : sortedSchemas(topology)) {
            List<VertexLabelDto> vertexDtos = schema.getVertexLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .map(this::toVertexDto)
                    .collect(Collectors.toList());
            List<EdgeLabelDto> edgeDtos = schema.getEdgeLabels().values().stream()
                    .sorted(Comparator.comparing(AbstractLabel::getName))
                    .map(this::toEdgeDto)
                    .collect(Collectors.toList());

            schemas.add(new SchemaDto(schema.getName(), vertexDtos, edgeDtos));
        }

        return new TopologyDto(connectionId, conn.getName(), conn.getDbType(), schemas);
    }

    public void evict(Long connectionId) {
        registry.evict(connectionId);
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

    private VertexLabelDto toVertexDto(VertexLabel vl) {
        VertexLabelDto dto = new VertexLabelDto();
        fillLabel(dto, vl);

        dto.setInEdgeLabels(vl.getInEdgeLabels().values().stream()
                .map(e -> new EdgeBriefDto(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .distinct()
                .sorted(Comparator.comparing(EdgeBriefDto::getFullName))
                .collect(Collectors.toList()));

        dto.setOutEdgeLabels(vl.getOutEdgeLabels().values().stream()
                .map(e -> new EdgeBriefDto(
                        e.getName(),
                        e.getSchema() == null ? null : e.getSchema().getName(),
                        e.getFullName()))
                .distinct()
                .sorted(Comparator.comparing(EdgeBriefDto::getFullName))
                .collect(Collectors.toList()));

        return dto;
    }

    private EdgeLabelDto toEdgeDto(EdgeLabel el) {
        EdgeLabelDto dto = new EdgeLabelDto();
        fillLabel(dto, el);

        dto.setOutVertexLabels(el.getOutVertexLabels().stream()
                .map(AbstractLabel::getFullName)
                .sorted()
                .collect(Collectors.toList()));
        dto.setInVertexLabels(el.getInVertexLabels().stream()
                .map(AbstractLabel::getFullName)
                .sorted()
                .collect(Collectors.toList()));

        return dto;
    }

    private void fillLabel(LabelDto dto, AbstractLabel label) {
        dto.setName(label.getName());
        try {
            dto.setFullName(label.getFullName());
        } catch (Exception ignored) {}
        try {
            dto.setPartitioned(label.isPartition());
        } catch (Exception ignored) {
            dto.setPartitioned(false);
        }

        dto.setProperties(label.getProperties().values().stream()
                .map(p -> new PropertyDto(
                        p.getName(),
                        p.getPropertyType() == null ? "?" : p.getPropertyType().name()))
                .sorted(Comparator.comparing(PropertyDto::getName))
                .collect(Collectors.toList()));

        dto.setIndexes(label.getIndexes().values().stream()
                .map(i -> new IndexDto(
                        i.getName(),
                        i.getIndexType() == null ? "?" : i.getIndexType().getName(),
                        i.getProperties().stream()
                                .map(PropertyColumn::getName)
                                .sorted()
                                .collect(Collectors.toList())))
                .sorted(Comparator.comparing(IndexDto::getName))
                .collect(Collectors.toList()));

        try {
            var ids = label.getIdentifiers();
            dto.setIdentifiers(ids == null || ids.isEmpty()
                    ? List.of()
                    : new ArrayList<>(ids));
        } catch (Exception e) {
            dto.setIdentifiers(List.of());
        }
    }
}
