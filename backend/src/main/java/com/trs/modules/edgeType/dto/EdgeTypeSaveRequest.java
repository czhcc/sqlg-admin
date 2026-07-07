package com.trs.modules.edgeType.dto;

import java.util.List;

/**
 * 新增边类型请求体。
 *
 * <p>边表所在 schema 由 outSchema 决定(sqlg 约定:边表跟随出点的 schema)。
 *
 * @author czh
 * @date 2026/07/07
 */
public class EdgeTypeSaveRequest {

    /** 出点 schema 名称 */
    private String outSchema;

    /** 出点 VertexLabel 名称 */
    private String outLabel;

    /** 入点 schema 名称 */
    private String inSchema;

    /** 入点 VertexLabel 名称 */
    private String inLabel;

    /** EdgeLabel 名称 */
    private String label;

    /** 属性列表 name -> 类型(STRING/INTEGER/JSON 等) */
    private List<PropertyEntry> properties;

    /** identifier 属性名列表(可选) */
    private List<String> identifiers;

    /** 属性项 */
    public static class PropertyEntry {
        public String name;
        public String type;
        public PropertyEntry() {}
        public PropertyEntry(String name, String type) { this.name = name; this.type = type; }
    }

    public String getOutSchema() { return outSchema; }
    public void setOutSchema(String outSchema) { this.outSchema = outSchema; }
    public String getOutLabel() { return outLabel; }
    public void setOutLabel(String outLabel) { this.outLabel = outLabel; }
    public String getInSchema() { return inSchema; }
    public void setInSchema(String inSchema) { this.inSchema = inSchema; }
    public String getInLabel() { return inLabel; }
    public void setInLabel(String inLabel) { this.inLabel = inLabel; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public List<PropertyEntry> getProperties() { return properties; }
    public void setProperties(List<PropertyEntry> properties) { this.properties = properties; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
}
