package com.trs.modules.topology.dto;

import java.util.List;

public class SchemaDto {
    private String name;
    private List<VertexLabelDto> vertexLabels;
    private List<EdgeLabelDto> edgeLabels;

    public SchemaDto() {}

    public SchemaDto(String name, List<VertexLabelDto> vertexLabels, List<EdgeLabelDto> edgeLabels) {
        this.name = name;
        this.vertexLabels = vertexLabels;
        this.edgeLabels = edgeLabels;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<VertexLabelDto> getVertexLabels() { return vertexLabels; }
    public void setVertexLabels(List<VertexLabelDto> vertexLabels) { this.vertexLabels = vertexLabels; }
    public List<EdgeLabelDto> getEdgeLabels() { return edgeLabels; }
    public void setEdgeLabels(List<EdgeLabelDto> edgeLabels) { this.edgeLabels = edgeLabels; }
}
