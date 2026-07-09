package com.trs.modules.edgeData.dto;

import java.util.Map;

/**
 * 边详情 DTO,含完整属性、出点和入点的简要信息。
 *
 * @author czh
 * @date 2026/07/09
 */
public class EdgeDetailDto {

    private String id;
    private String label;
    private String schema;
    private Map<String, Object> properties;
    private Map<String, Object> outVertex;
    private Map<String, Object> inVertex;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public Map<String, Object> getOutVertex() { return outVertex; }
    public void setOutVertex(Map<String, Object> outVertex) { this.outVertex = outVertex; }
    public Map<String, Object> getInVertex() { return inVertex; }
    public void setInVertex(Map<String, Object> inVertex) { this.inVertex = inVertex; }
}
