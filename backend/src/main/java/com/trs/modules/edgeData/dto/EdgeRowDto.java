package com.trs.modules.edgeData.dto;

import java.util.Map;

/**
 * 边数据列表行 DTO,包含边 ID、Label、Schema、出/入点信息和属性摘要。
 *
 * @author czh
 * @date 2026/07/09
 */
public class EdgeRowDto {

    private String id;
    private String label;
    private String schema;
    private String outVertexId;
    private String outVertexLabel;
    private String inVertexId;
    private String inVertexLabel;
    private Map<String, Object> properties;
    private String propertySummary;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getOutVertexId() { return outVertexId; }
    public void setOutVertexId(String outVertexId) { this.outVertexId = outVertexId; }
    public String getOutVertexLabel() { return outVertexLabel; }
    public void setOutVertexLabel(String outVertexLabel) { this.outVertexLabel = outVertexLabel; }
    public String getInVertexId() { return inVertexId; }
    public void setInVertexId(String inVertexId) { this.inVertexId = inVertexId; }
    public String getInVertexLabel() { return inVertexLabel; }
    public void setInVertexLabel(String inVertexLabel) { this.inVertexLabel = inVertexLabel; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
    public String getPropertySummary() { return propertySummary; }
    public void setPropertySummary(String propertySummary) { this.propertySummary = propertySummary; }
}
