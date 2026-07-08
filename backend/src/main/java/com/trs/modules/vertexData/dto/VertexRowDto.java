package com.trs.modules.vertexData.dto;

import java.util.Map;

/**
 * 点数据列表行 DTO。
 *
 * @author czh
 * @date 2026/07/08
 */
public class VertexRowDto {

    private String id;
    private String label;
    private String schema;
    private Map<String, Object> properties;
    private String propertySummary;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public String getPropertySummary() { return propertySummary; }
    public void setPropertySummary(String propertySummary) { this.propertySummary = propertySummary; }
}
