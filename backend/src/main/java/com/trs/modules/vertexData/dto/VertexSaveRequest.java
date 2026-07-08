package com.trs.modules.vertexData.dto;

import java.util.Map;

/**
 * 新增/编辑点请求体。
 *
 * @author czh
 * @date 2026/07/08
 */
public class VertexSaveRequest {

    private String schema;
    private String label;
    private Map<String, Object> properties;

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Map<String, Object> getProperties() { return properties; }
    public void setProperties(Map<String, Object> properties) { this.properties = properties; }
}
