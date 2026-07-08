package com.trs.modules.vertexData.dto;

import java.util.List;
import java.util.Map;

/**
 * 点详情 DTO,含完整属性、关联边和邻接点信息。
 *
 * @author czh
 * @date 2026/07/08
 */
public class VertexDetailDto {

    private String id;
    private String label;
    private String schema;
    private Map<String, Object> properties;
    private List<Map<String, Object>> outEdges;
    private List<Map<String, Object>> inEdges;
    private List<Map<String, Object>> adjacentVertices;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public List<Map<String, Object>> getOutEdges() { return outEdges; }
    public void setOutEdges(List<Map<String, Object>> outEdges) { this.outEdges = outEdges; }
    public List<Map<String, Object>> getInEdges() { return inEdges; }
    public void setInEdges(List<Map<String, Object>> inEdges) { this.inEdges = inEdges; }
    public List<Map<String, Object>> getAdjacentVertices() { return adjacentVertices; }
    public void setAdjacentVertices(List<Map<String, Object>> adjacentVertices) { this.adjacentVertices = adjacentVertices; }
}
