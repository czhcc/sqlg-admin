package com.trs.modules.vertexType.dto;

import java.util.List;

/**
 * 点类型详情 DTO,含属性、索引、关联边类型。
 *
 * @author czh
 * @date 2026/07/07
 */
public class VertexTypeDetailDto {

    private String schema;
    private String label;
    private String fullName;
    private String tableName;

    private List<PropertyInfo> properties;
    private List<String> identifiers;
    private List<IndexInfo> indexes;
    private List<EdgeBrief> inEdgeLabels;
    private List<EdgeBrief> outEdgeLabels;
    private long vertexCount;

    public static class PropertyInfo {
        public String name;
        public String type;
        public PropertyInfo() {}
        public PropertyInfo(String name, String type) { this.name = name; this.type = type; }
    }

    public static class IndexInfo {
        public String name;
        public String indexType;
        public List<String> properties;
        public IndexInfo() {}
        public IndexInfo(String name, String indexType, List<String> properties) {
            this.name = name; this.indexType = indexType; this.properties = properties;
        }
    }

    public static class EdgeBrief {
        public String label;
        public String schema;
        public String fullName;
        public EdgeBrief() {}
        public EdgeBrief(String label, String schema, String fullName) {
            this.label = label; this.schema = schema; this.fullName = fullName;
        }
    }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }
    public List<PropertyInfo> getProperties() { return properties; }
    public void setProperties(List<PropertyInfo> properties) { this.properties = properties; }
    public List<String> getIdentifiers() { return identifiers; }
    public void setIdentifiers(List<String> identifiers) { this.identifiers = identifiers; }
    public List<IndexInfo> getIndexes() { return indexes; }
    public void setIndexes(List<IndexInfo> indexes) { this.indexes = indexes; }
    public List<EdgeBrief> getInEdgeLabels() { return inEdgeLabels; }
    public void setInEdgeLabels(List<EdgeBrief> inEdgeLabels) { this.inEdgeLabels = inEdgeLabels; }
    public List<EdgeBrief> getOutEdgeLabels() { return outEdgeLabels; }
    public void setOutEdgeLabels(List<EdgeBrief> outEdgeLabels) { this.outEdgeLabels = outEdgeLabels; }
    public long getVertexCount() { return vertexCount; }
    public void setVertexCount(long vertexCount) { this.vertexCount = vertexCount; }
}
