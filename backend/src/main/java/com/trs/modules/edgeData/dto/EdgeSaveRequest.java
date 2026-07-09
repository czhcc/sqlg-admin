package com.trs.modules.edgeData.dto;

import java.util.Map;

/**
 * 新增/编辑边请求体,包含 EdgeLabel、出/入点 ID 及边属性。
 *
 * @author czh
 * @date 2026/07/09
 */
public class EdgeSaveRequest {

    private String schema;
    private String label;
    private String outVertexId;
    private String inVertexId;
    private Map<String, Object> properties;

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getOutVertexId() { return outVertexId; }
    public void setOutVertexId(String outVertexId) { this.outVertexId = outVertexId; }
    public String getInVertexId() { return inVertexId; }
    public void setInVertexId(String inVertexId) { this.inVertexId = inVertexId; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
