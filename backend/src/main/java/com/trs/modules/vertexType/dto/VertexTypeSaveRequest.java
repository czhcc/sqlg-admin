package com.trs.modules.vertexType.dto;

import java.util.List;

/**
 * 新增/编辑点类型请求体。
 *
 * @author czh
 * @date 2026/07/07
 */
public class VertexTypeSaveRequest {

    /** 目标 schema,默认 public */
    private String schema;

    /** VertexLabel 名称 */
    private String label;

    /** 原始 schema(编辑时用于定位现有 label) */
    private String originalSchema;

    /** 原始 label 名(编辑时用于定位现有 label) */
    private String originalLabel;

    /** 属性列表 name -> 类型(STRING/INTEGER/JSON 等) */
    private List<PropertyEntry> properties;

    /** identifier 属性名列表 */
    private List<String> identifiers;

    public static class PropertyEntry {
        public String name;
        public String type;
        public PropertyEntry() {}
        public PropertyEntry(String name, String type) { this.name = name; this.type = type; }
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getOriginalSchema() { return originalSchema; }
    public void setOriginalSchema(String originalSchema) { this.originalSchema = originalSchema; }
    public String getOriginalLabel() { return originalLabel; }
    public void setOriginalLabel(String originalLabel) { this.originalLabel = originalLabel; }
    public List<PropertyEntry> getProperties() { return properties; }
    public void setProperties(List<PropertyEntry> properties) { this.properties = properties; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
}
