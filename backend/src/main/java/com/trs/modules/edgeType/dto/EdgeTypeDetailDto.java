package com.trs.modules.edgeType.dto;

import java.util.List;

/**
 * 边类型详情 DTO,含属性、索引、出入点类型和边数据量。
 *
 * @author czh
 * @date 2026/07/07
 */
public class EdgeTypeDetailDto {

    private String schema;
    private String label;
    private String fullName;
    private String tableName;

    private List<PropertyInfo> properties;
    private List<String> identifiers;
    private List<IndexInfo> indexes;
    private List<VertexBrief> outVertexLabels;
    private List<VertexBrief> inVertexLabels;
    private long edgeCount;

    /** 边属性信息 */
    public static class PropertyInfo {
        public String name;
        public String type;
        public PropertyInfo() {}
        public PropertyInfo(String name, String type) { this.name = name; this.type = type; }
    }

    /** 索引信息 */
    public static class IndexInfo {
        public String name;
        public String indexType;
        public List<String> properties;
        public IndexInfo() {}
        public IndexInfo(String name, String indexType, List<String> properties) {
            this.name = name; this.indexType = indexType; this.properties = properties;
        }
    }

    /** 关联点类型简要信息 */
    public static class VertexBrief {
        public String label;
        public String schema;
        public String fullName;
        public VertexBrief() {}
        public VertexBrief(String label, String schema, String fullName) {
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
    public List<VertexBrief> getOutVertexLabels() { return outVertexLabels; }
    public void setOutVertexLabels(List<VertexBrief> outVertexLabels) { this.outVertexLabels = outVertexLabels; }
    public List<VertexBrief> getInVertexLabels() { return inVertexLabels; }
    public void setInVertexLabels(List<VertexBrief> inVertexLabels) { this.inVertexLabels = inVertexLabels; }
    public long getEdgeCount() { return edgeCount; }
    public void setEdgeCount(long edgeCount) { this.edgeCount = edgeCount; }
}
